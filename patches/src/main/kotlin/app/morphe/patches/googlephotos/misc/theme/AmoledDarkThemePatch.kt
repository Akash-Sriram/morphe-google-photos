package app.morphe.patches.googlephotos.misc.theme

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File

private const val AMOLED_BLACK = "#ff000000"

private val photosAmoledAnyFileColorNames = setOf(
    "background_floating_material_dark",
    "background_material_dark",
    "cardview_dark_background",
    "color_surface_elevation_plus_two_dark",
    "design_dark_default_color_background",
    "design_dark_default_color_surface",
    "gm3_legacy_sys_color_dark_background",
    "gm3_sys_color_dark_background",
    "gm3_sys_color_dark_surface",
    "gm3_sys_color_dark_surface_bright",
    "gm3_sys_color_dark_surface_container",
    "gm3_sys_color_dark_surface_container_high",
    "gm3_sys_color_dark_surface_container_highest",
    "gm3_sys_color_dark_surface_container_low",
    "gm3_sys_color_dark_surface_container_lowest",
    "gm3_sys_color_dark_surface_dim",
    "gm3_sys_color_dynamic_dark_background",
    "gm3_sys_color_dynamic_dark_surface",
    "gm3_sys_color_dynamic_dark_surface_bright",
    "gm3_sys_color_dynamic_dark_surface_container",
    "gm3_sys_color_dynamic_dark_surface_container_high",
    "gm3_sys_color_dynamic_dark_surface_container_highest",
    "gm3_sys_color_dynamic_dark_surface_container_low",
    "gm3_sys_color_dynamic_dark_surface_container_lowest",
    "gm3_sys_color_dynamic_dark_surface_dim",
    "gm3_ref_palette_dynamic_neutral10",
    "gm3_ref_palette_dynamic_neutral20",
    "gm3_ref_palette_dynamic_neutral_variant4",
    "gm3_ref_palette_dynamic_neutral_variant6",
    "gm3_ref_palette_dynamic_neutral_variant10",
    "gm3_ref_palette_dynamic_neutral_variant12",
    "gm3_ref_palette_dynamic_neutral_variant17",
    "gm3_ref_palette_dynamic_neutral_variant22",
    "gm3_ref_palette_dynamic_neutral_variant24",
    "gm3_ref_palette_dynamic_neutral_variant30",
    "gm_sys_color_dark_background",
    "gm_sys_color_dark_surface",
    "google_dark_default_color_background",
    "google_dark_default_color_primary",
    "google_dark_default_color_primary_dark",
    "google_dark_default_color_primary_variant",
    "google_dark_default_color_surface",
    "google_dark_default_color_surface_variant",
    "m3_sys_color_dark_background",
    "m3_sys_color_dark_surface",
    "m3_sys_color_dark_surface_bright",
    "m3_sys_color_dark_surface_container",
    "m3_sys_color_dark_surface_container_high",
    "m3_sys_color_dark_surface_container_highest",
    "m3_sys_color_dark_surface_container_low",
    "m3_sys_color_dark_surface_container_lowest",
    "m3_sys_color_dark_surface_dim",
    "m3_sys_color_dynamic_dark_background",
    "m3_sys_color_dynamic_dark_surface",
    "m3_sys_color_dynamic_dark_surface_bright",
    "m3_sys_color_dynamic_dark_surface_container",
    "m3_sys_color_dynamic_dark_surface_container_high",
    "m3_sys_color_dynamic_dark_surface_container_highest",
    "m3_sys_color_dynamic_dark_surface_container_low",
    "m3_sys_color_dynamic_dark_surface_container_lowest",
    "m3_sys_color_dynamic_dark_surface_dim",
    "settingslib_materialColorSurfaceContainer",
    "settingslib_materialColorSurfaceContainerHigh",
    "settingslib_materialColorSurfaceContainerHighest",
    "settingslib_materialColorSurfaceVariant",
    "settingslib_neutral_variant12",
    "settingslib_neutral_variant17",
    "settingslib_neutral_variant22",
    "settingslib_surface_dark",
    "sud_color_background_dark",
    "sud_glif_v3_dialog_background_color_dark",
    "sud_glif_window_bg_dark_color",
    "sud_glif_edit_text_bg_dark_color",
    "sud_system_background_dark",
    "sud_system_surface_bright_dark",
    "sud_system_surface_container_dark",
    "sud_system_surface_container_high_dark",
    "sud_system_surface_container_highest_dark",
    "sud_system_surface_container_low_dark",
    "sud_system_surface_container_lowest_dark",
    "sud_system_surface_dark",
    "sud_system_surface_dim_dark",
    "sud_system_background_surface",
    "sud_system_surface",
)

private val photosAmoledNightOnlyColorNames = setOf(
    "material_grey_white_1000",
    "photos_daynight_white",
    "photos_daynight_white_elevation_1dp",
    "photos_daynight_white_elevation_3dp",
    "photos_daynight_white_elevation_8dp",
    "photos_daynight_white_elevation_12dp",
    "photos_pager_background_daynight_white",
    "photos_photoeditor_background_daynight_white",
    "photos_printingskus_common_ui_education_splash_background",
    "photos_printingskus_editing_background",
    "photos_printingskus_storefront_config_contentrow_alpha50",
    "photos_theme_activity_background",
    "photos_theme_bottom_nav_background",
    "photos_theme_dark_translucent_system_ui_color",
)

private val neutralDarkSurfaceRgb = setOf(
    "0e0e0e",
    "121212",
    "131313",
    "131314",
    "1b1b1b",
    "1e1f20",
    "1f1f1f",
    "202124",
    "28292c",
    "282a2c",
    "2a2a2a",
    "2d2e30",
    "303030",
    "303134",
    "333537",
    "343434",
    "353639",
    "37393b",
    "3b3b3e",
    "3c4043",
    "424242",
)

private val ignoredColorNameParts = listOf(
    "accent",
    "alert",
    "border",
    "button",
    "disabled",
    "divider",
    "error",
    "foreground",
    "gradient",
    "hairline",
    "icon",
    "inverse",
    "label",
    "on_",
    "outline",
    "primary_container",
    "progress",
    "secondary_container",
    "stroke",
    "tertiary_container",
    "text",
    "tint",
    "warning",
)

private val amoledStyleItems = mapOf(
    "android:colorBackground" to AMOLED_BLACK,
    "android:navigationBarColor" to AMOLED_BLACK,
    "android:statusBarColor" to AMOLED_BLACK,
    "android:windowBackground" to AMOLED_BLACK,
    "android:windowLightNavigationBar" to "false",
    "android:windowLightStatusBar" to "false",
    "colorBackgroundFloating" to AMOLED_BLACK,
    "colorSurface" to AMOLED_BLACK,
    "colorSurfaceBright" to AMOLED_BLACK,
    "colorSurfaceDim" to AMOLED_BLACK,
    "colorSurfaceContainer" to AMOLED_BLACK,
    "colorSurfaceContainerHigh" to AMOLED_BLACK,
    "colorSurfaceContainerHighest" to AMOLED_BLACK,
    "colorSurfaceContainerLow" to AMOLED_BLACK,
    "colorSurfaceContainerLowest" to AMOLED_BLACK,
    "photosSurface2" to AMOLED_BLACK,
)

private val mc3DarkSchemeItems = mapOf(
    "mc3_background" to AMOLED_BLACK,
    "mc3_surface" to AMOLED_BLACK,
    "mc3_surface_dim" to AMOLED_BLACK,
    "mc3_surface_bright" to AMOLED_BLACK,
    "mc3_surface_container" to AMOLED_BLACK,
    "mc3_surface_container_high" to AMOLED_BLACK,
    "mc3_surface_container_highest" to AMOLED_BLACK,
    "mc3_surface_container_low" to AMOLED_BLACK,
    "mc3_surface_container_lowest" to AMOLED_BLACK,
)

private val mc3PaletteItems = mapOf(
    "mc3_neutral_0" to AMOLED_BLACK,
    "mc3_neutral_10" to AMOLED_BLACK,
    "mc3_neutral_20" to AMOLED_BLACK,
    "mc3_neutral_variant_0" to AMOLED_BLACK,
    "mc3_neutral_variant_10" to AMOLED_BLACK,
    "mc3_neutral_variant_20" to AMOLED_BLACK,
)

@Suppress("unused")
val amoledDarkThemePatch = resourcePatch(
    name = "AMOLED dark theme",
    description = "Makes Google Photos dark surfaces true black while keeping light mode untouched.",
    default = true,
) {
    compatibleWith(AppCompatibilities.GOOGLE_PHOTOS)

    execute {
        val resDirectory = get("res")
        if (!resDirectory.isDirectory) {
            throw PatchException("Decoded res directory was not found.")
        }

        var changedColors = 0
        var changedStyles = 0

        resDirectory.walkTopDown()
            .onEnter { dir -> dir == resDirectory || dir.name.startsWith("values", ignoreCase = true) }
            .filter { file ->
                file.isValuesXml() && (
                    file.isNightValuesXml() ||
                    file.name.startsWith("color", ignoreCase = true) ||
                    file.name.startsWith("style", ignoreCase = true) ||
                    file.name.startsWith("theme", ignoreCase = true)
                )
            }
            .forEach { file ->
                val source = file.readText()
                if (!source.contains("<color") && !source.contains("<style")) return@forEach

                val resourcePath = "res/${file.relativeTo(resDirectory).invariantSeparatorsPath}"
                val isNightFile = file.isNightValuesXml()
                document(resourcePath).use { document ->
                    changedColors += document.blackenAmoledColors(isNightFile)
                    changedStyles += document.forceAmoledThemeItems(isNightFile)
                }
            }

        val changed = changedColors + changedStyles
        if (changed == 0) {
            throw PatchException("No Google Photos AMOLED theme resources were patched.")
        }
    }
}

private fun Document.blackenAmoledColors(isNightFile: Boolean): Int {
    var changed = 0
    val colors = getElementsByTagName("color")
    for (i in 0 until colors.length) {
        val color = colors.item(i) as? Element ?: continue
        val name = color.getAttribute("name")
        if (!name.shouldBlackenAmoledColor(isNightFile, color.textContent)) continue
        if (color.textContent == AMOLED_BLACK) continue

        color.textContent = AMOLED_BLACK
        changed++
    }
    return changed
}

private fun Document.forceAmoledThemeItems(isNightFile: Boolean): Int {
    var changed = 0
    val styles = getElementsByTagName("style")
    for (i in 0 until styles.length) {
        val style = styles.item(i) as? Element ?: continue
        val styleName = style.getAttribute("name")
        when (styleName) {
            "mc3_dark_scheme" -> {
                mc3DarkSchemeItems.forEach { (name, value) ->
                    style.ensureStyleItem(this, name, value)?.let { changed++ }
                }
            }
            "mc3_palette" -> {
                mc3PaletteItems.forEach { (name, value) ->
                    style.ensureStyleItem(this, name, value)?.let { changed++ }
                }
            }
            else -> {
                if (!style.isAmoledTargetThemeStyle(isNightFile)) continue
                amoledStyleItems.forEach { (name, value) ->
                    style.ensureStyleItem(this, name, value)?.let { changed++ }
                }
            }
        }
    }
    return changed
}

private fun String.shouldBlackenAmoledColor(isNightFile: Boolean, value: String): Boolean {
    val colorName = lowercase()
    if (colorName in photosAmoledAnyFileColorNames) return true
    if (isNightFile && colorName in photosAmoledNightOnlyColorNames) return true
    if (ignoredColorNameParts.any(colorName::contains)) return false

    val valLower = value.lowercase().trim()
    if (
        "system_background_dark" in valLower ||
        "system_surface_dark" in valLower ||
        "system_surface_container" in valLower ||
        "system_surface_dim_dark" in valLower ||
        "system_surface_bright_dark" in valLower ||
        "system_neutral1_900" in valLower ||
        "system_neutral1_1000" in valLower ||
        "system_neutral2_900" in valLower ||
        "system_neutral2_1000" in valLower
    ) {
        if ("dark" in colorName || isNightFile || "surface" in colorName || "background" in colorName || "container" in colorName) {
            return true
        }
    }

    if (!isNightFile && "dark" !in colorName) return false
    if (!listOf("background", "surface", "container", "canvas", "panel", "sheet", "bar").any(colorName::contains)) {
        return false
    }
    return value.amoledRgb() in neutralDarkSurfaceRgb
}

/**
 * Ensures style items are strictly targeted to Dark themes:
 * - If inside values-night*, applies to all DayNight, Dark, and Photos themes.
 * - If inside base values*, ONLY applies to styles explicitly named or inheriting Dark (never touching base DayNight or Light styles).
 */
private fun Element.isAmoledTargetThemeStyle(isNightFile: Boolean): Boolean {
    val name = getAttribute("name").lowercase()
    val parent = getAttribute("parent").lowercase()

    // Explicit light themes are never touched
    if ("light" in name && "dark" !in name && "daynight" !in name) return false
    if ("light" in parent && "dark" !in parent && "daynight" !in parent) return false

    if (isNightFile) {
        // In values-night*, we safely style all DayNight and Dark themes, as well as Theme.Photos variants
        return "dark" in name ||
            "daynight" in name ||
            "dark" in parent ||
            "daynight" in parent ||
            "theme.photos" in name ||
            "theme.photos" in parent ||
            "base.theme.photos" in name ||
            "base.theme.photos" in parent ||
            hasDarkThemeItem()
    } else {
        // In default values*, we MUST NOT touch DayNight themes as they serve Light mode!
        // Only target styles that are explicitly Dark and NOT DayNight
        val isExplicitlyDark = ("dark" in name || "dark" in parent) &&
            ("daynight" !in name && "daynight" !in parent)
        return isExplicitlyDark && hasDarkThemeItem()
    }
}

private fun Element.hasDarkThemeItem(): Boolean {
    val children = childNodes
    for (i in 0 until children.length) {
        val item = children.item(i) as? Element ?: continue
        if (item.tagName != "item") continue
        val value = item.textContent.lowercase()
        if (
            "design_dark" in value ||
            "gm3_dark" in value ||
            "gm3_sys_color_dark" in value ||
            "gm_sys_color_dark" in value ||
            "m3_sys_color_dark" in value ||
            "photos_daynight_white" in value ||
            "photos_theme_dark_translucent_system_ui_color" in value ||
            "system_background_dark" in value ||
            "system_surface_dark" in value ||
            "gm3_sys_color_dynamic_dark" in value ||
            "m3_sys_color_dynamic_dark" in value
        ) return true
    }
    return false
}

private fun Element.ensureStyleItem(document: Document, name: String, value: String): Boolean? {
    val children = childNodes
    for (i in 0 until children.length) {
        val item = children.item(i) as? Element ?: continue
        if (item.tagName != "item" || item.getAttribute("name") != name) continue
        if (item.textContent == value) return null

        item.textContent = value
        return true
    }

    val item = document.createElement("item")
    item.setAttribute("name", name)
    item.textContent = value
    appendChild(item)
    return true
}

private fun String.amoledRgb(): String {
    var color = trim().removePrefix("#").lowercase()
    if (color.length == 8 && color.startsWith("ff")) color = color.substring(2)
    return color
}

private fun File.isValuesXml(): Boolean {
    if (!isFile || !extension.equals("xml", ignoreCase = true)) return false
    val directoryName = parentFile?.name ?: return false
    return directoryName == "values" || directoryName.startsWith("values-")
}

private fun File.isNightValuesXml(): Boolean {
    val directoryName = parentFile?.name ?: return false
    return directoryName == "values-night" || directoryName.startsWith("values-night-")
}
