/*
 * Forked from:
 * https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/src/main/kotlin/app/revanced/patches/googlephotos/misc/gms/GmsCoreSupportPatch.kt
 */
package app.morphe.patches.googlephotos.misc.gms

import app.morphe.patches.googlephotos.misc.extension.sharedExtensionPatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.googlephotos.misc.gms.Constants.MORPHE_PHOTOS_PACKAGE_NAME
import app.morphe.patches.googlephotos.misc.gms.Constants.PHOTOS_PACKAGE_NAME
import app.morphe.patches.googlephotos.misc.gms.HomeActivityOnCreateFingerprint
import app.morphe.patches.shared.misc.gms.PackageNameConfig
import app.morphe.patches.shared.misc.gms.gmsCoreSupportPatch
import app.morphe.patcher.patch.InstallerType
import app.morphe.patcher.patch.PatchAvailability
import app.morphe.patches.shared.misc.settings.preference.BasePreferenceScreen
import app.morphe.patches.shared.misc.settings.preference.PreferenceScreenPreference
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.util.asSequence
import app.morphe.util.findMutableMethodOf
import app.morphe.util.getNode
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.indexOfFirstInstructionOrThrow
import app.morphe.util.returnEarly
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.googlephotos.misc.bento.BentoModelBuilderFingerprint
import app.morphe.util.addInstructionsAtControlFlowLabel
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import org.w3c.dom.Element

@Suppress("unused")
val gmsCoreSupportPatch = gmsCoreSupportPatch(
    fromPackageName = PHOTOS_PACKAGE_NAME,
    toPackageName = MORPHE_PHOTOS_PACKAGE_NAME,
    mainActivityOnCreateFingerprint = HomeActivityOnCreateFingerprint,
    extensionPatch = sharedExtensionPatch,
    gmsCoreSupportResourcePatchFactory = ::gmsCoreSupportResourcePatch,
    executeBlock = {
        // 1. Account Avatar initialization
        HomeActivityOnCreateFingerprint.method.addInstructions(
            0,
            """
            invoke-static/range { p0 .. p0 }, Lapp/morphe/extension/shared/patches/GooglePhotosAccountAvatar;->install(Landroid/app/Activity;)V
            """.trimIndent(),
        )

        // 1.1 OneGoogle Blue Aura Ring for Avatar & Account Switcher
        classDefByOrNull("Lcom/google/android/libraries/onegoogle/account/disc/AccountParticleDisc;")?.let { apdClassDef ->
            val mutableApd = mutableClassDefBy(apdClassDef)

            // Hook 5-arg constructor: AccountParticleDisc(Context, AttributeSet, int, boolean, boolean)
            // Register p5 is the boolean flag for Aura Blue Ring (q).
            apdClassDef.methods.firstOrNull { method ->
                method.name == "<init>" && method.parameterTypes.toList() == listOf(
                    "Landroid/content/Context;",
                    "Landroid/util/AttributeSet;",
                    "I",
                    "Z",
                    "Z",
                )
            }?.let { apdInit5 ->
                val mutableInit5 = mutableApd.findMutableMethodOf(apdInit5)
                val superIndex = mutableInit5.implementation?.instructions?.indexOfFirst {
                    it.opcode == Opcode.INVOKE_DIRECT &&
                        (it as? ReferenceInstruction)?.reference is MethodReference &&
                        ((it as ReferenceInstruction).reference as MethodReference).name == "<init>"
                } ?: -1
                val insertIndex = if (superIndex != -1) superIndex + 1 else 0
                mutableInit5.addInstructions(
                    insertIndex,
                    """
                    invoke-static {p1}, Lapp/morphe/extension/shared/patches/GooglePhotosAccountAvatar;->isBlueRingEnabled(Landroid/content/Context;)Z
                    move-result p5
                    """.trimIndent(),
                )
            }

            // Hook 4-arg constructor: AccountParticleDisc(Context, AttributeSet, int, boolean)
            // It calls csya.d(context) to pass z2 to the 5-arg constructor.
            // Dynamically locate csya.d(Context) from this call.
            apdClassDef.methods.firstOrNull { method ->
                method.name == "<init>" && method.parameterTypes.toList() == listOf(
                    "Landroid/content/Context;",
                    "Landroid/util/AttributeSet;",
                    "I",
                    "Z",
                )
            }?.let { apdInit4 ->
                val csyaCall = apdInit4.implementation?.instructions?.firstOrNull { instr ->
                    instr.opcode == Opcode.INVOKE_STATIC &&
                        (instr as? ReferenceInstruction)?.reference is MethodReference &&
                        ((instr as ReferenceInstruction).reference as MethodReference).parameterTypes.toList() == listOf("Landroid/content/Context;") &&
                        ((instr as ReferenceInstruction).reference as MethodReference).returnType == "Z"
                }
                val methodRef = (csyaCall as? ReferenceInstruction)?.reference as? MethodReference
                if (methodRef != null) {
                    classDefByOrNull(methodRef.definingClass)?.let { csyaClassDef ->
                        val csyaMethod = csyaClassDef.methods.firstOrNull { it.name == methodRef.name }
                        if (csyaMethod != null) {
                            val mutableCsya = mutableClassDefBy(csyaClassDef).findMutableMethodOf(csyaMethod)
                            mutableCsya.addInstructions(
                                0,
                                """
                                invoke-static {p0}, Lapp/morphe/extension/shared/patches/GooglePhotosAccountAvatar;->isBlueRingEnabled(Landroid/content/Context;)Z
                                move-result v0
                                return v0
                                """.trimIndent(),
                            )
                        }
                    }
                }
            }
        }


        // 2. Google One Bento Badge
        val targetBentoMethod = BentoModelBuilderFingerprint.methodOrNull
        if (targetBentoMethod != null) {
            val bentoClass = mutableClassDefBy(targetBentoMethod.definingClass)
            val bentoMethod = bentoClass.findMutableMethodOf(targetBentoMethod)
            val instructions = targetBentoMethod.implementation?.instructions?.toList()
            if (instructions != null) {
                val fallbackIndex = instructions.indexOfFirst { inst ->
                    inst.opcode == Opcode.CONST_16 &&
                        (inst as? NarrowLiteralInstruction)?.narrowLiteral == 0 &&
                        instructions.getOrNull(instructions.indexOf(inst) + 1)?.let { nextInst ->
                            nextInst.opcode == Opcode.IF_EQZ &&
                                (nextInst as? OneRegisterInstruction)?.registerA == (inst as? OneRegisterInstruction)?.registerA
                        } == true
                }
                if (fallbackIndex != -1) {
                    val targetReg = (instructions[fallbackIndex] as OneRegisterInstruction).registerA
                    val valueOfIndex = (fallbackIndex - 1 downTo 0).firstOrNull { i ->
                        val inst = instructions[i]
                        inst.opcode == Opcode.INVOKE_STATIC &&
                            (inst as? ReferenceInstruction)?.reference?.toString()?.contains("Integer;->valueOf(I)Ljava/lang/Integer;") == true
                    }
                    if (valueOfIndex != null) {
                        val intReg = (instructions[valueOfIndex] as? FiveRegisterInstruction)?.registerC
                        val stringInitIndex = (valueOfIndex - 1 downTo 0).firstOrNull { i ->
                            val inst = instructions[i]
                            inst.opcode == Opcode.INVOKE_DIRECT &&
                                (inst as? ReferenceInstruction)?.reference?.toString()?.contains("<init>(Ljava/lang/String;)V") == true
                        }
                        if (stringInitIndex != null && intReg != null) {
                            val stringReg = (instructions[stringInitIndex] as? FiveRegisterInstruction)?.registerD
                            var entryIndex = -1
                            for (i in stringInitIndex - 1 downTo 0) {
                                if (instructions[i].opcode == Opcode.NEW_INSTANCE) {
                                    entryIndex = i
                                } else if (entryIndex != -1) {
                                    break
                                }
                            }
                            if (entryIndex != -1 && stringReg != null) {
                                var accountReg = 14
                                for (i in entryIndex - 1 downTo 0) {
                                    if (instructions[i].opcode == Opcode.IGET_BOOLEAN) {
                                        accountReg = (instructions[i] as TwoRegisterInstruction).registerB
                                        break
                                    }
                                }
                                bentoMethod.addInstructionsAtControlFlowLabel(
                                    fallbackIndex,
                                    """
                                    invoke-static { v$accountReg }, Lapp/morphe/extension/shared/patches/BentoDecorationPatch;->getBadgeText(Ljava/lang/Object;)Ljava/lang/String;
                                    move-result-object v$stringReg
                                    if-eqz v$stringReg, :no_badge
                                    const/4 v$intReg, 1
                                    goto :build_badge
                                    :no_badge
                                    const/16 v$targetReg, 0
                                    """.trimIndent(),
                                    ExternalLabel("build_badge", bentoMethod.getInstruction(entryIndex)),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Places Map photo grid scroll jump
        PlacesMapScrollJumpFingerprint.methodOrNull?.let { method ->
            val mutableClass = mutableClassDefBy(method.definingClass)
            val mutableMethod = mutableClass.findMutableMethodOf(method)
            mutableMethod.addInstruction(
                0,
                "invoke-static {v2}, Lapp/morphe/extension/shared/patches/GmsCoreSupportPatch;->adjustScrollPosition(I)I",
            )
            mutableMethod.addInstruction(
                1,
                "move-result v2",
            )
            mutableMethod.addInstruction(
                2,
                "invoke-static {v2, v3}, Lapp/morphe/extension/shared/patches/GmsCoreSupportPatch;->adjustScrollOffset(II)I",
            )
            mutableMethod.addInstruction(
                3,
                "move-result v3",
            )
        }

        // Map Explore bottom sheet header photo count
        MapExploreDateRangeFingerprint.methodOrNull?.let { method ->
            val mutableClass = mutableClassDefBy(method.definingClass)
            val mutableMethod = mutableClass.findMutableMethodOf(method)
            val impl = method.implementation
            if (impl != null) {
                var aPIndex = -1
                var ifNezIndex = -1
                impl.instructions.forEachIndexed { idx, instr ->
                    if (aPIndex == -1) {
                        if (instr is ReferenceInstruction && instr.opcode.name.lowercase().startsWith("iget-boolean")) {
                            val ref = instr.reference as? FieldReference
                            if (ref != null && ref.name == "aP") {
                                aPIndex = idx
                            }
                        }
                    } else if (ifNezIndex == -1 && idx == aPIndex + 1 && instr.opcode.name.lowercase().startsWith("if-nez")) {
                        ifNezIndex = idx
                    }
                }
                if (ifNezIndex != -1) {
                    mutableMethod.replaceInstruction(ifNezIndex, "nop")
                }
            }
        }

        // MarsGridActivity.onCreate hook
        classDefByOrNull("Lcom/google/android/apps/photos/mars/grid/MarsGridActivity;")?.let { classDef ->
            classDef.methods.find { it.name == "onCreate" }?.let { method ->
                mutableClassDefBy(classDef).findMutableMethodOf(method).addInstruction(
                    0,
                    "invoke-static {p0}, Lapp/morphe/extension/shared/patches/toolkit/StorageScanner;->onMarsGridActivityCreated(Landroid/app/Activity;)V",
                )
            }
        }

        // Hook photos.killswitch_info_panel_map to prevent crash when MicroG lacks location
        KillswitchInfoPanelMapFingerprint.methodOrNull?.let { clinitMethod ->
            var foundDist = -1
            var targetField: FieldReference? = null
            clinitMethod.implementation?.instructions?.forEach { instr ->
                val ref = (instr as? ReferenceInstruction)?.reference
                if (ref is StringReference && ref.string == "photos.killswitch_info_panel_map") {
                    foundDist = 0
                } else if (foundDist in 0..15 && ref is FieldReference && instr.opcode.name.startsWith("sput", ignoreCase = true)) {
                    targetField = ref
                    foundDist = -1
                } else if (foundDist >= 0) {
                    foundDist++
                }
            }

            if (targetField != null) {
                val killswitchClassDef = classDefBy(clinitMethod.definingClass)
                val supplierClassTypes = mutableSetOf<String>()
                killswitchClassDef.methods.filter { it.name == "<init>" }.forEach { initMethod ->
                    initMethod.implementation?.instructions?.forEach { instr ->
                        val ref = (instr as? ReferenceInstruction)?.reference
                        if (ref is com.android.tools.smali.dexlib2.iface.reference.TypeReference) {
                            val type = ref.type
                            if (!type.startsWith("Landroid/") && !type.startsWith("Ljava/") && !type.contains("Context")) {
                                supplierClassTypes.add(type)
                            }
                        }
                    }
                }

                fun patchMethodIfMatches(cDef: ClassDef, method: Method): Boolean {
                    val impl = method.implementation ?: return false
                    val instructions = impl.instructions.toList()
                    var sgetDistance = -1
                    var patched = false
                    instructions.forEachIndexed { index, instruction ->
                        val ref = (instruction as? ReferenceInstruction)?.reference
                        if (ref is FieldReference && ref.definingClass == targetField.definingClass && ref.name == targetField.name && instruction.opcode.name.startsWith("sget", ignoreCase = true)) {
                            sgetDistance = 0
                        } else if (sgetDistance in 0..5 && ref is MethodReference && ref.returnType == "Z" && instruction.opcode.name.startsWith("invoke", ignoreCase = true)) {
                            sgetDistance = -1
                            val nextIndex = index + 1
                            if (nextIndex < instructions.size) {
                                val nextIns = instructions[nextIndex]
                                if (nextIns is OneRegisterInstruction && nextIns.opcode.name.startsWith("move-result", ignoreCase = true)) {
                                    val reg = nextIns.registerA
                                    val mutableMethod = mutableClassDefBy(cDef).findMutableMethodOf(method)
                                    mutableMethod.addInstruction(
                                        nextIndex + 1,
                                        "invoke-static {v$reg}, Lapp/morphe/extension/shared/patches/GmsCoreSupportPatch;->isInfoPanelMapKillswitched(Z)Z",
                                    )
                                    mutableMethod.addInstruction(
                                        nextIndex + 2,
                                        "move-result v$reg",
                                    )
                                    patched = true
                                }
                            }
                        } else if (sgetDistance >= 0) {
                            sgetDistance++
                        }
                    }
                    return patched
                }

                var didPatch = false
                for (supplierType in supplierClassTypes) {
                    classDefByOrNull(supplierType)?.let { sClass ->
                        sClass.methods.forEach { m ->
                            if (patchMethodIfMatches(sClass, m)) didPatch = true
                        }
                    }
                    if (didPatch) break
                }
            }
        }

        // Fast metadata scan: Google Play Services availability & signature check hooks
        classDefForEach { classDef ->
            val mutableClass by lazy { mutableClassDefBy(classDef) }

            classDef.methods.forEach classLoop@{ method ->
                // Check A: Google Play Services availability check
                val isAvailabilityCheck = method.returnType == "I" &&
                    (
                        (method.parameterTypes.size == 2 && method.parameterTypes[0] == "Landroid/content/Context;" && method.parameterTypes[1] == "I") ||
                        (method.parameterTypes.size == 1 && method.parameterTypes[0] == "Landroid/content/Context;")
                    )
                if (isAvailabilityCheck) {
                    val impl = method.implementation
                    if (impl != null && impl.instructions.any { instr ->
                        val str = (instr.getReference<StringReference>())?.string
                        str?.contains("android.gms") == true || str?.contains("GooglePlayServices") == true
                    }) {
                        mutableClass.findMutableMethodOf(method).returnEarly(0)
                        return@classLoop
                    }
                }

                // Check B: PackageInfo signature verification
                val isSignatureCheck = method.returnType == "Z" &&
                    method.parameterTypes.size in 1..2 &&
                    method.parameterTypes[0] == "Landroid/content/pm/PackageInfo;"
                if (isSignatureCheck) {
                    mutableClass.findMutableMethodOf(method).returnEarly(true)
                    return@classLoop
                }
            }
        }

        // 2) Disable the AccountValidityMonitor check that runs on resume.
        val avmClass = mutableClassDefBy(AccountValidityMonitorFingerprint.method.definingClass)
        val checkAccountMethod = avmClass.methods.first {
            it.name == "d" && it.parameterTypes.isEmpty() && it.returnType == "V"
        }
        checkAccountMethod.addInstruction(
            0,
            "return-void",
        )

        // 3) Keep the frictionless eligibility result intact, but prevent the
        //    MicroG failure path from clearing the selected account.
        val frictionlessClass = mutableClassDefBy(FrictionlessEligibilityTaskFingerprint.method.definingClass)
        val eligibilityMethod = frictionlessClass.methods.first {
            it.name == "f" && it.parameterTypes.isEmpty() && it.returnType == "Z"
        }
        eligibilityMethod.apply {
            val clearSelectedAccountIndex = indexOfFirstInstructionOrThrow {
                getReference<MethodReference>()?.let { ref ->
                    ref.name == "o" &&
                        ref.returnType == "V" &&
                        ref.parameterTypes.toList() == listOf("I")
                } == true
            }
            val accountHandlerClass = getInstruction(clearSelectedAccountIndex)
                .getReference<MethodReference>()!!
                .definingClass

            replaceInstruction(clearSelectedAccountIndex, "invoke-virtual {p0}, $accountHandlerClass->p()V")
        }

        // 4, 5, 6) Hook CurrentLocationMixin methods
        val currentLocationClass = mutableClassDefBy(CurrentLocationMixinOnViewCreatedFingerprint.method.definingClass)
        val currentLocationMethodA = currentLocationClass.methods.first {
            it.name == "a" && it.parameterTypes.isEmpty() && it.returnType == "V"
        }
        currentLocationMethodA.apply {
            addInstruction(
                0,
                "invoke-static {p0}, Lapp/morphe/extension/shared/patches/GmsCoreSupportPatch;->handleCurrentLocation(Ljava/lang/Object;)V",
            )
            addInstruction(
                1,
                "return-void",
            )
        }

        CurrentLocationMixinOnViewCreatedFingerprint.method.addInstruction(
            0,
            "invoke-static {p0}, Lapp/morphe/extension/shared/patches/GmsCoreSupportPatch;->initMapLocation(Ljava/lang/Object;)V",
        )

        val currentLocationMethodB = currentLocationClass.methods.first {
            it.name == "b" && it.parameterTypes.toList() == listOf("Z") && it.returnType == "V"
        }
        currentLocationMethodB.addInstruction(
            0,
            "invoke-static {p0, p1}, Lapp/morphe/extension/shared/patches/GmsCoreSupportPatch;->onCurrentLocationMixinTintUpdated(Ljava/lang/Object;Z)V",
        )


        // 8) Bypass concurrent permission request crash guard (Cannot request permissions X when already requested Y)
        val permCoordMethod = PermissionCoordinatorRequestPermissionsFingerprint.methodOrNull
        if (permCoordMethod != null) {
            val mutableClass = mutableClassDefBy(permCoordMethod.definingClass)
            val mutableMethod = mutableClass.findMutableMethodOf(permCoordMethod)
            val instructions = permCoordMethod.implementation?.instructions?.toList()
            if (instructions != null) {
                val sizeIndex = instructions.indexOfFirst {
                    it.opcode == Opcode.INVOKE_VIRTUAL &&
                        (it as? ReferenceInstruction)?.reference?.toString()?.contains("SparseArray;->size()I") == true
                }
                if (sizeIndex != -1) {
                    val branchRel = instructions.drop(sizeIndex).indexOfFirst {
                        it.opcode.name.startsWith("if-")
                    }
                    if (branchRel != -1) {
                        val branchIndex = sizeIndex + branchRel
                        mutableMethod.replaceInstruction(branchIndex, "nop")
                    }
                }
            }
        }

        // 9) Google Lens Activity Binder caller_package spoofing for renamed packages
        val lensBinderMethod = LensActivityBinderHandshakeFingerprint.methodOrNull
        if (lensBinderMethod != null) {
            val mutableClass = mutableClassDefBy(lensBinderMethod.definingClass)
            val mutableMethod = mutableClass.findMutableMethodOf(lensBinderMethod)
            val instructions = lensBinderMethod.implementation?.instructions?.toList()
            if (instructions != null) {
                val getPkgIndex = instructions.indexOfFirst {
                    val ref = (it as? ReferenceInstruction)?.reference as? MethodReference
                    ref?.name == "getPackageName" && ref.definingClass == "Landroid/content/Context;"
                }
                if (getPkgIndex != -1 && getPkgIndex + 1 < instructions.size) {
                    val moveResult = instructions[getPkgIndex + 1]
                    if (moveResult.opcode == Opcode.MOVE_RESULT_OBJECT) {
                        val reg = (moveResult as OneRegisterInstruction).registerA
                        mutableMethod.replaceInstruction(
                            getPkgIndex,
                            "const-string v$reg, \"$PHOTOS_PACKAGE_NAME\"",
                        )
                        mutableMethod.replaceInstruction(
                            getPkgIndex + 1,
                            "nop",
                        )
                    }
                }
            }
        }

        // 10) Suppress repeated 24-hour notification permission prompt after denial
        SecondChanceNotificationOptInFingerprint.methodOrNull?.let { clinitMethod ->
            val arktClass = mutableClassDefBy(clinitMethod.definingClass)
            for (method in arktClass.methods) {
                if (method.name == "go" && method.parameterTypes.toList() == listOf("Landroid/os/Bundle;")) {
                    method.addInstructions(
                        0,
                        """
                        return-void
                        """.trimIndent(),
                    )
                }
                val hasPostNotif = method.implementation?.instructions?.any { instr ->
                    (instr as? ReferenceInstruction)?.reference?.let { ref ->
                        (ref as? StringReference)?.string == "android.permission.POST_NOTIFICATIONS"
                    } == true
                } == true
                if (hasPostNotif) {
                    method.addInstructions(
                        0,
                        """
                        return-void
                        """.trimIndent(),
                    )
                }
            }
        }

        // 11) Backup Settings: Sanitize GMS Core backup settings (uyc) on MicroG/non-certified devices
        GmsCoreBackupAccountSyncFingerprint.methodOrNull?.let { syncMethod ->
            val syncClassDef = classDefBy(syncMethod.definingClass)
            val mutableSyncClass = mutableClassDefBy(syncClassDef)
            val mutableSyncMethod = mutableSyncClass.findMutableMethodOf(syncMethod)
            val instructions = syncMethod.implementation?.instructions?.toList()
            if (instructions != null) {
                // Find CHECK_CAST targeting the GmsSettings class (Luyc;)
                val emailIgetInst = instructions.mapNotNull { inst ->
                    if (inst.opcode == Opcode.IGET_OBJECT &&
                        ((inst as? ReferenceInstruction)?.reference as? FieldReference)?.type == "Ljava/lang/String;"
                    ) inst else null
                }.firstOrNull()

                val emailFr = (emailIgetInst as? ReferenceInstruction)?.reference as? FieldReference
                val checkCastType = emailFr?.definingClass
                val gmsSettingsReg = (emailIgetInst as? TwoRegisterInstruction)?.registerB

                val emailIgetIndex = emailIgetInst?.let { instructions.indexOf(it) } ?: -1
                val checkCastIndex = if (emailIgetIndex != -1 && checkCastType != null) {
                    (emailIgetIndex downTo 0).firstOrNull {
                        instructions[it].opcode == Opcode.CHECK_CAST &&
                            ((instructions[it] as? ReferenceInstruction)?.reference as? TypeReference)?.type == checkCastType
                    }
                } else null

                val contextField = syncClassDef.fields.firstOrNull { it.type == "Landroid/content/Context;" }?.name
                val thisReg = syncMethod.implementation!!.registerCount - syncMethod.parameters.size - 1

                if (checkCastIndex != null && gmsSettingsReg != null && contextField != null && checkCastType != null) {
                    mutableSyncMethod.addInstructions(
                        checkCastIndex + 1,
                        """
                        iget-object v5, v$thisReg, ${syncMethod.definingClass}->$contextField:Landroid/content/Context;
                        invoke-static {v$gmsSettingsReg, v5}, Lapp/morphe/extension/shared/patches/GmsCoreSupportPatch;->sanitizeGmsBackupSettings(Ljava/lang/Object;Landroid/content/Context;)Ljava/lang/Object;
                        move-result-object v$gmsSettingsReg
                        check-cast v$gmsSettingsReg, $checkCastType
                        """.trimIndent(),
                    )
                }

                // 12) Backup Preferences & State: Dynamically resolve Ludh and Luvh, and sanitize + trace
                val wMethod = syncClassDef.methods.firstOrNull {
                    it.parameterTypes.size == 2 && it.parameterTypes[0] == "Ljava/lang/Boolean;"
                }
                val ludhClassName = wMethod?.parameterTypes?.getOrNull(1)
                val uvhMethodRef = wMethod?.implementation?.instructions?.mapNotNull {
                    if (it.opcode == Opcode.INVOKE_STATIC) {
                        (it as? ReferenceInstruction)?.reference as? MethodReference
                    } else null
                }?.firstOrNull()

                // Hook 1: Entry of Luvh.a(Ludh, Boolean)Luvk
                if (uvhMethodRef != null && ludhClassName != null) {
                    val uvhClassDef = classDefBy(uvhMethodRef.definingClass)
                    val mutableUvhClass = mutableClassDefBy(uvhClassDef)
                    val uvhMethod = uvhClassDef.methods.first { it.name == uvhMethodRef.name && it.parameterTypes == uvhMethodRef.parameterTypes }
                    val mutableUvhMethod = mutableUvhClass.findMutableMethodOf(uvhMethod)
                    val ludhParamReg = uvhMethod.implementation!!.registerCount - uvhMethod.parameters.size
                    val boolParamReg = ludhParamReg + 1
                    mutableUvhMethod.addInstructions(
                        0,
                        """
                        invoke-static/range {v$ludhParamReg .. v$boolParamReg}, Lapp/morphe/extension/shared/patches/GmsCoreSupportPatch;->onLuvhAEntry(Ljava/lang/Object;Ljava/lang/Boolean;)Ljava/lang/Object;
                        move-result-object v$ludhParamReg
                        check-cast v$ludhParamReg, $ludhClassName
                        """.trimIndent(),
                    )
                }
            }
        }

        // 12) Backup Settings: Observe & organically guard LaunchedEffect account chooser dialog
        BackupSettingsEntryLaunchedEffectFingerprint.methodOrNull?.let { effectMethod ->
            val effectClass = mutableClassDefBy(effectMethod.definingClass)
            val mutableEffectMethod = effectClass.findMutableMethodOf(effectMethod)
            val instructions = effectMethod.implementation?.instructions?.toList()
            if (instructions != null) {
                val isBackupEnabledIndices = instructions.mapIndexedNotNull { idx, inst ->
                    if (inst.opcode == Opcode.INVOKE_INTERFACE &&
                        ((inst as? ReferenceInstruction)?.reference as? MethodReference)?.let { ref ->
                            ref.parameterTypes.isEmpty() && ref.returnType == "Z"
                        } == true
                    ) idx else null
                }
                for (isBackupEnabledIndex in isBackupEnabledIndices.reversed()) {
                    val moveResultRel = instructions.drop(isBackupEnabledIndex + 1).indexOfFirst {
                        it.opcode == Opcode.MOVE_RESULT
                    }
                    if (moveResultRel != -1) {
                        val moveResultIndex = isBackupEnabledIndex + 1 + moveResultRel
                        val reg = (instructions[moveResultIndex] as OneRegisterInstruction).registerA
                        mutableEffectMethod.addInstructions(
                            moveResultIndex + 1,
                            """
                            invoke-static {v$reg, p0}, Lapp/morphe/extension/shared/patches/GmsCoreSupportPatch;->onCheckBackupAccountLinked(ZLjava/lang/Object;)Z
                            move-result v$reg
                            """.trimIndent(),
                        )
                    }
                }
            }
        }

        // 12) GNP Certificate Provider Spoofing (SHA-1 hash returned to GNP network layer)
        GnpCertProviderFingerprint.methodOrNull?.let { certMethod ->
            val certClass = mutableClassDefBy(certMethod.definingClass)
            val mutableCertMethod = certClass.findMutableMethodOf(certMethod)
            mutableCertMethod.addInstructions(
                0,
                """
                const-string v0, "24bb24c05e47e0aefa68a58a766179d9b613a600"
                return-object v0
                """.trimIndent(),
            )
        }

        // 13) GNP ("Chime") HTTP Request Builder Header Spoofing
        GnpRequestBuilderFingerprint.methodOrNull?.let { gnpHeaderMethod ->
            val gnpClass = mutableClassDefBy(gnpHeaderMethod.definingClass)

            // Hook all (headerKey, headerValue: String) setters in the builder
            gnpClass.methods.filter { m ->
                m.returnType == "V" &&
                    m.parameterTypes.size == 2 &&
                    m.parameterTypes[1] == "Ljava/lang/String;"
            }.forEach { headerMethod ->
                val mutableMethod = gnpClass.findMutableMethodOf(headerMethod)
                mutableMethod.addInstructions(
                    0,
                    """
                    invoke-static {p1, p2}, Lapp/morphe/extension/shared/patches/GmsCoreSupportPatch;->spoofHeaderValue(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/String;
                    move-result-object p2
                    """.trimIndent(),
                )
            }

            // Hook all (metadataKey, metadataValue: Object) setters in the builder
            gnpClass.methods.filter { m ->
                m.returnType == "V" &&
                    m.parameterTypes.size == 2 &&
                    m.parameterTypes[0] == "Ljava/lang/Object;" &&
                    m.parameterTypes[1] == "Ljava/lang/Object;"
            }.forEach { mapPutMethod ->
                val mutableMapMethod = gnpClass.findMutableMethodOf(mapPutMethod)
                mutableMapMethod.addInstructions(
                    0,
                    """
                    invoke-static {p1, p2}, Lapp/morphe/extension/shared/patches/GmsCoreSupportPatch;->spoofMetadataValue(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
                    move-result-object p2
                    """.trimIndent(),
                )
            }
        }

        // 14) gRPC Metadata Header Spoofing
        GrpcMetadataPutFingerprint.methodOrNull?.let { grpcPutMethod ->
            val grpcClass = mutableClassDefBy(grpcPutMethod.definingClass)
            val mutableGrpcPutMethod = grpcClass.findMutableMethodOf(grpcPutMethod)
            mutableGrpcPutMethod.addInstructions(
                0,
                """
                invoke-static {p1, p2}, Lapp/morphe/extension/shared/patches/GmsCoreSupportPatch;->spoofMetadataValue(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
                move-result-object p2
                """.trimIndent(),
            )
        }

        // 15) HTTP Request Header Spoofing (URLConnection, HttpURLConnection, Cronet UrlRequest, etc.)
        classDefForEach { classDef ->
            val mutableClass by lazy { mutableClassDefBy(classDef) }
            classDef.methods.forEach { method ->
                val implementation = method.implementation ?: return@forEach
                val instructions = implementation.instructions.toList()
                val indicesToPatch = mutableListOf<Triple<Int, Int, Int>>() // (index, keyReg, valReg)

                instructions.forEachIndexed { index, inst ->
                    if (inst.opcode == Opcode.INVOKE_VIRTUAL || inst.opcode == Opcode.INVOKE_VIRTUAL_RANGE ||
                        inst.opcode == Opcode.INVOKE_INTERFACE || inst.opcode == Opcode.INVOKE_INTERFACE_RANGE) {
                        val ref = (inst as? ReferenceInstruction)?.reference as? MethodReference
                        if (ref != null &&
                            (ref.name == "addRequestProperty" || ref.name == "setRequestProperty" || ref.name == "addHeader") &&
                            ref.parameterTypes.toList() == listOf("Ljava/lang/String;", "Ljava/lang/String;")) {
                            val regs = when (inst) {
                                is FiveRegisterInstruction -> Pair(inst.registerD, inst.registerE)
                                is RegisterRangeInstruction -> Pair(inst.startRegister + 1, inst.startRegister + 2)
                                else -> null
                            }
                            if (regs != null) {
                                indicesToPatch.add(Triple(index, regs.first, regs.second))
                            }
                        }
                    }
                }

                if (indicesToPatch.isNotEmpty()) {
                    val mutableMethod = mutableClass.findMutableMethodOf(method)
                    for ((idx, keyReg, valReg) in indicesToPatch.asReversed()) {
                        val invokeSmali = if (keyReg < 16 && valReg < 16) {
                            "invoke-static {v$keyReg, v$valReg}, Lapp/morphe/extension/shared/patches/GmsCoreSupportPatch;->spoofHeaderValue(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/String;"
                        } else {
                            "invoke-static/range {v$keyReg .. v$valReg}, Lapp/morphe/extension/shared/patches/GmsCoreSupportPatch;->spoofHeaderValue(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/String;"
                        }
                        mutableMethod.addInstructions(
                            idx,
                            """
                            $invokeSmali
                            move-result-object v$valReg
                            """.trimIndent(),
                        )
                    }
                }
            }
        }

        // 16) GNP & Cronet HTTP Response Logging: Hook GnpHttpResponse and UrlRequest$Callback
        classDefForEach { classDef ->
            val mutableClass by lazy { mutableClassDefBy(classDef) }

            // Hook GnpHttpResponse constructor to log the response object
            val hasGnpHttpResponse = classDef.methods.any { m ->
                m.name == "toString" && m.implementation?.instructions?.any { inst ->
                    ((inst as? ReferenceInstruction)?.reference as? StringReference)?.string?.contains("GnpHttpResponse{statusCode=") == true
                } == true
            }
            if (hasGnpHttpResponse) {
                classDef.methods.firstOrNull { it.name == "<init>" && it.parameterTypes.size == 6 && it.parameterTypes[0] == "Ljava/lang/Integer;" }?.let { initMethod ->
                    val implementation = initMethod.implementation
                    if (implementation != null) {
                        val returnIdx = implementation.instructions.indexOfLast { it.opcode == Opcode.RETURN_VOID }
                        if (returnIdx != -1) {
                            val thisReg = implementation.registerCount - initMethod.parameters.size - 1
                            val invokeSmali = if (thisReg < 16) {
                                "invoke-static {v$thisReg}, Lapp/morphe/extension/shared/patches/GmsCoreSupportPatch;->onGnpResponseObject(Ljava/lang/Object;)V"
                            } else {
                                "invoke-static/range {v$thisReg .. v$thisReg}, Lapp/morphe/extension/shared/patches/GmsCoreSupportPatch;->onGnpResponseObject(Ljava/lang/Object;)V"
                            }
                            mutableClass.findMutableMethodOf(initMethod).addInstructions(returnIdx, invokeSmali)
                        }
                    }
                }
            }

            // Hook UrlRequest$Callback methods for Cronet network-level responses
            if (classDef.superclass == "Lorg/chromium/net/UrlRequest\$Callback;") {
                classDef.methods.forEach { method ->
                    val implementation = method.implementation ?: return@forEach
                    val mutableMethod by lazy { mutableClass.findMutableMethodOf(method) }
                    val rCount = implementation.registerCount
                    when (method.name) {
                        "onResponseStarted" -> {
                            val infoReg = rCount - 1
                            val invokeSmali = if (infoReg < 16) {
                                "invoke-static {v$infoReg}, Lapp/morphe/extension/shared/patches/GmsCoreSupportPatch;->onCronetResponseStarted(Ljava/lang/Object;)V"
                            } else {
                                "invoke-static/range {v$infoReg .. v$infoReg}, Lapp/morphe/extension/shared/patches/GmsCoreSupportPatch;->onCronetResponseStarted(Ljava/lang/Object;)V"
                            }
                            mutableMethod.addInstructions(0, invokeSmali)
                        }
                        "onSucceeded" -> {
                            val infoReg = rCount - 1
                            val invokeSmali = if (infoReg < 16) {
                                "invoke-static {v$infoReg}, Lapp/morphe/extension/shared/patches/GmsCoreSupportPatch;->onCronetSucceeded(Ljava/lang/Object;)V"
                            } else {
                                "invoke-static/range {v$infoReg .. v$infoReg}, Lapp/morphe/extension/shared/patches/GmsCoreSupportPatch;->onCronetSucceeded(Ljava/lang/Object;)V"
                            }
                            mutableMethod.addInstructions(0, invokeSmali)
                        }
                        "onFailed" -> {
                            val infoReg = rCount - 2
                            val errReg = rCount - 1
                            val invokeSmali = if (infoReg < 16 && errReg < 16) {
                                "invoke-static {v$infoReg, v$errReg}, Lapp/morphe/extension/shared/patches/GmsCoreSupportPatch;->onCronetFailed(Ljava/lang/Object;Ljava/lang/Object;)V"
                            } else {
                                "invoke-static/range {v$infoReg .. v$errReg}, Lapp/morphe/extension/shared/patches/GmsCoreSupportPatch;->onCronetFailed(Ljava/lang/Object;Ljava/lang/Object;)V"
                            }
                            mutableMethod.addInstructions(0, invokeSmali)
                        }
                    }
                }
            }
        }
    },
    finalizeBlock = {
        if (PackageNameConfig.isPackageNameChangeEnabled) {
            val targetPackageName = PackageNameConfig.effectivePackageName.ifEmpty { MORPHE_PHOTOS_PACKAGE_NAME }
            val marsPackagePrefixes = listOf(
                "$PHOTOS_PACKAGE_NAME.mars.",
                "content://$PHOTOS_PACKAGE_NAME.mars.",
            )
            val marsLibPrefixes = listOf(
                "com.google.android.libraries.photos.api.mars",
                "content://com.google.android.libraries.photos.api.mars",
            )

            classDefForEach { classDef ->
                val mutableClass by lazy { mutableClassDefBy(classDef) }
                classDef.methods.forEach classLoop@{ method ->
                    val implementation = method.implementation ?: return@classLoop
                    val mutableMethod by lazy { mutableClass.findMutableMethodOf(method) }

                    implementation.instructions.forEachIndexed { index, instruction ->
                        val ref = (instruction as? com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction)?.reference
                        if (instruction is com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21c && ref is StringReference) {
                            val original = ref.string
                            if (!original.endsWith("Activity") && !original.contains("Activity$") &&
                                !original.endsWith("Task") && !original.contains("Task$") &&
                                !original.endsWith("Service") && !original.contains("Service$")) {
                                val transformed = when {
                                    marsLibPrefixes.any { original.startsWith(it) } ->
                                        original.replace("com.google.android.libraries.photos.api.mars", "$targetPackageName.api.mars")
                                    marsPackagePrefixes.any { original.startsWith(it) } ->
                                        original.replace(PHOTOS_PACKAGE_NAME, targetPackageName)
                                    else -> null
                                }
                                if (transformed != null) {
                                    mutableMethod.replaceInstruction(
                                        index,
                                        com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c(
                                            com.android.tools.smali.dexlib2.Opcode.CONST_STRING,
                                            (instruction as OneRegisterInstruction).registerA,
                                            com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference(transformed),
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    },
) {
    availability { installer, _ ->
        when (installer) {
            InstallerType.MOUNT -> PatchAvailability.UNAVAILABLE
            InstallerType.STANDARD, InstallerType.SHIZUKU -> PatchAvailability.REQUIRED
        }
    }

    dependsOn(
        deviceComplianceCheckPatch,
    )

    compatibleWith(AppCompatibilities.GOOGLE_PHOTOS)
}

/**
 * Minimal preference screen used only to satisfy the shared GmsCore support
 * resource patch API. Google Photos does not currently expose a dedicated
 * Morphe settings UI, so the committed screen is intentionally a no-op.
 */
private object DummyPreferenceScreen : BasePreferenceScreen() {
    val SCREEN = Screen(
        key = "morphe_settings_googlephotos_screen_1_misc",
        summaryKey = null,
    )

    override fun commit(screen: PreferenceScreenPreference) {
        // No-op: Google Photos does not have a dedicated Morphe settings screen yet.
    }
}

private fun gmsCoreSupportResourcePatch() =
    app.morphe.patches.shared.misc.gms.gmsCoreSupportResourcePatch(
        fromPackageName = PHOTOS_PACKAGE_NAME,
        toPackageName = MORPHE_PHOTOS_PACKAGE_NAME,
        spoofedPackageSignature = "24bb24c05e47e0aefa68a58a766179d9b613a600",
        screen = DummyPreferenceScreen.SCREEN,
    ) {
        finalize {
            if (PackageNameConfig.isPackageNameChangeEnabled) {
                val targetPackageName = PackageNameConfig.effectivePackageName.ifEmpty { MORPHE_PHOTOS_PACKAGE_NAME }

                document("AndroidManifest.xml").use { document ->
                    val manifest = document.getNode("manifest") as Element
                    val originalPackageName = manifest.getAttribute("package")

                    manifest.setAttribute("package", targetPackageName)

                    val permissions = manifest.getElementsByTagName("permission").asSequence()
                    val usesPermissions = manifest.getElementsByTagName("uses-permission").asSequence()

                    (permissions + usesPermissions)
                        .map { it as Element }
                        .forEach { elem ->
                            val name = elem.getAttribute("android:name")
                            if (name.startsWith("$originalPackageName.")) {
                                elem.setAttribute("android:name", name.replace(originalPackageName, targetPackageName))
                            }
                        }

                    val components = (
                        manifest.getElementsByTagName("activity").asSequence() +
                        manifest.getElementsByTagName("service").asSequence() +
                        manifest.getElementsByTagName("provider").asSequence() +
                        manifest.getElementsByTagName("receiver").asSequence()
                    ).map { it as Element }

                    for (comp in components) {
                        val perm = comp.getAttribute("android:permission")
                        if (perm.startsWith("$originalPackageName.")) {
                            comp.setAttribute("android:permission", perm.replace(originalPackageName, targetPackageName))
                        }
                    }

                    val providers = manifest.getElementsByTagName("provider").asSequence()
                    for (node in providers) {
                        val provider = node as Element
                        val authorities = provider.getAttribute("android:authorities")
                        if (authorities.contains("api.mars")) {
                            provider.setAttribute("android:authorities", "$targetPackageName.api.mars")
                        } else if (authorities.startsWith("$originalPackageName.")) {
                            provider.setAttribute("android:authorities", authorities.replace(originalPackageName, targetPackageName))
                        }
                    }
                }
            }

            document("AndroidManifest.xml").use { document ->
                val manifest = document.getNode("manifest") as Element
                val targetPackageName = if (PackageNameConfig.isPackageNameChangeEnabled) {
                    PackageNameConfig.effectivePackageName.ifEmpty { MORPHE_PHOTOS_PACKAGE_NAME }
                } else {
                    PHOTOS_PACKAGE_NAME
                }

                // Fix for Issue #80: On Android < 13, AndroidX dynamic receivers require this permission in manifest.
                val dynamicReceiverPerm = "$targetPackageName.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
                val existingPerms = manifest.getElementsByTagName("permission").asSequence()
                    .map { (it as Element).getAttribute("android:name") }
                    .toSet()
                if (dynamicReceiverPerm !in existingPerms) {
                    val permNode = document.createElement("permission").apply {
                        setAttribute("android:name", dynamicReceiverPerm)
                        setAttribute("android:protectionLevel", "signature")
                    }
                    manifest.appendChild(permNode)
                }

                val existingUsesPerms = manifest.getElementsByTagName("uses-permission").asSequence()
                    .map { (it as Element).getAttribute("android:name") }
                    .toSet()
                if (dynamicReceiverPerm !in existingUsesPerms) {
                    val usesPermNode = document.createElement("uses-permission").apply {
                        setAttribute("android:name", dynamicReceiverPerm)
                    }
                    manifest.appendChild(usesPermNode)
                }

                if ("android.permission.MANAGE_EXTERNAL_STORAGE" !in existingUsesPerms) {
                    val permNode = document.createElement("uses-permission").apply {
                        setAttribute("android:name", "android.permission.MANAGE_EXTERNAL_STORAGE")
                    }
                    manifest.appendChild(permNode)
                }
            }

            // Bundle official phenotype baseline assets into APK assets
            val phenotypeAssets = listOf(
                "com.google.android.apps.photos.phenotype.xml",
                "phenotype_account_file.xml",
            )
            for (name in phenotypeAssets) {
                val stream = object {}.javaClass.classLoader.getResourceAsStream("app/morphe/patches/googlephotos/phenotype/$name")
                if (stream != null) {
                    val target = this["assets/phenotype/$name", false]
                    target.parentFile?.mkdirs()
                    stream.use { input ->
                        target.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }

            // Bundle authentic Google Sans font assets into APK assets
            val fontAssets = listOf(
                "Google_Sans_Text-Bold.ttf",
                "Google_Sans_Text-Medium.ttf",
                "Google_Sans_Text-Regular.ttf",
            )
            for (name in fontAssets) {
                val stream = object {}.javaClass.classLoader.getResourceAsStream("app/morphe/patches/googlephotos/fonts/$name")
                if (stream != null) {
                    val target = this["assets/fonts/$name", false]
                    target.parentFile?.mkdirs()
                    stream.use { input ->
                        target.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }
