import com.android.tools.r8.CompilationMode
import com.android.tools.r8.D8
import com.android.tools.r8.D8Command
import com.android.tools.r8.OutputMode
import com.android.tools.r8.utils.ArchiveResourceProvider
import ru.vyarus.gradle.plugin.animalsniffer.AnimalSniffer

plugins {
    // Fails the build if the patches file uses JDK APIs that do not exist on Android.
    alias(libs.plugins.animalsniffer)
}

group = "app.morphe"

patches {
    about {
        name = "Morphe Google Photos Patches"
        description = "Advanced Google Photos patches featuring decoupled DCIM, independent folder backup control, native Morphe Toolkit, AMOLED dark theme, and GmsCore support."
        source = "https://github.com/Akash-Sriram/morphe-google-photos"
        author = "Akash Sriram"
        contact = "https://github.com/Akash-Sriram/morphe-google-photos/issues"
        website = "https://github.com/Akash-Sriram/morphe-google-photos"
        license = "GNU General Public License v3.0, with additional GPL section 7 requirements"
    }
}

repositories {
    mavenCentral()
    google()
    maven { url = uri("${rootDir}/local-repo") }
    maven { url = uri("https://jitpack.io") }
}

val patchListGeneratorClasspath = configurations.create("patchListGeneratorClasspath")

configurations.all {
    resolutionStrategy {
        force(libs.guava)
        force("com.google.errorprone:error_prone_annotations:2.50.0")
    }
}

dependencies {
    compileOnly(libs.gson)
    patchListGeneratorClasspath(libs.gson)

    compileOnly(libs.guava)

    // Android API stubs defined here.
    compileOnly(project(":patches:stub"))

    // Android API level 26 is the minimum API the patches are compiled for (see buildAndroid).
    signature(libs.android.api.signature) { artifact { type = "signature" } }
}

animalsniffer {
    // Checking only the compiled sources would miss bundled dependencies,
    // so the whole patches file is checked by checkAndroidApi below.
    sourceSets = emptyList()
}

tasks {
    register<JavaExec>("checkStringResources") {
        description = "Checks resource strings for invalid formatting"

        dependsOn(compileKotlin)

        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("app.morphe.util.resource.CheckStringResourcesKt")
    }

    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath
        mainClass.set("app.morphe.util.PatchListGeneratorKt")
    }

    // Runs D8 on every class in the patches file (the patches and all bundled dependencies)
    // but outputs class files instead of dex, so the check sees the code as it runs on Android,
    // after D8 has replaced methods it backports such as List.of() and Optional.isEmpty().
    val desugarPatchesClasses = register("desugarPatchesClasses") {
        val patchesFile = jar.flatMap { it.archiveFile }
        val classpath = sourceSets["main"].compileClasspath
        val output = layout.buildDirectory.file("animalsniffer/patches-desugared.jar")

        inputs.file(patchesFile)
        inputs.files(classpath)
        outputs.file(output)

        doLast {
            val patchesPath = patchesFile.get().asFile.toPath()
            val outputPath = output.get().asFile.toPath()

            D8.run(
                D8Command.builder()
                    .addProgramResourceProvider(ArchiveResourceProvider.fromArchive(patchesPath, true))
                    .addClasspathFiles(classpath.files.filter { it.exists() }.map { it.toPath() })
                    .setMode(CompilationMode.DEBUG)
                    .setMinApiLevel(26)
                    .setOutput(outputPath, OutputMode.ClassFile)
                    .build()
            )
        }
    }

    val extractPatchesClasses = register<Sync>("extractPatchesClasses") {
        from(desugarPatchesClasses.map { zipTree(it.outputs.files.singleFile) }) {
            include("**/*.class")
        }
        into(layout.buildDirectory.dir("animalsniffer/patches-classes"))
    }

    val checkAndroidApi = register<AnimalSniffer>("checkAndroidApi") {
        description = "Checks that the patches file only uses APIs available on Android."
        group = "verification"

        source(extractPatchesClasses)
        classpath = sourceSets["main"].compileClasspath
        animalsnifferSignatures = configurations["signature"]
        animalsnifferClasspath = configurations["animalsniffer"]
        // Only used to show source lines in the report.
        sourcesDirs = sourceSets["main"].allSource.sourceDirectories
        reports.text.outputLocation = layout.buildDirectory.file("reports/animalsniffer/patches.text")
        reports.csv.outputLocation = layout.buildDirectory.file("reports/animalsniffer/patches.csv")
    }

    check {
        dependsOn(checkAndroidApi)
    }

    // buildAndroid does not depend on check.
    named("buildAndroid") {
        dependsOn(checkAndroidApi)
    }

    // Used by gradle-semantic-release-plugin.
    publish {
        dependsOn("generatePatchesList")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs = listOf("-Xskip-metadata-version-check")
    }
}
