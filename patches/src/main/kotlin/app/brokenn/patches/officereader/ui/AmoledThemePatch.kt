package app.brokenn.patches.officereader.ui

import app.brokenn.patches.officereader.shared.Constants.COMPATIBILITY_OFFICEREADER
import app.brokenn.patches.officereader.shared.ensureThemeItem
import app.morphe.patcher.patch.resourcePatch
import java.util.logging.Logger
import org.w3c.dom.Element

private const val AMOLED = "#FF000000"

@Suppress("unused")
val amoledThemePatch = resourcePatch(
    name = "AMOLED theme",
    description = "Makes dark mode pure black for AMOLED screens, including the status and navigation bars.",
) {
    compatibleWith(COMPATIBILITY_OFFICEREADER)

    execute {
        val logger = Logger.getLogger(this::class.java.name)

        val styleTargets = listOf(
            "android:colorBackground",
            "android:colorBackgroundFloating",
            "android:windowBackground",
            "android:colorSurface",
            "android:colorPrimarySurface",
            "android:statusBarColor",
            "android:navigationBarColor",
            "colorSurface", "colorSurfaceVariant",
            "colorSurfaceContainer", "colorSurfaceContainerLow", "colorSurfaceContainerLowest",
            "colorSurfaceContainerHigh", "colorSurfaceContainerHighest",
            "colorSurfaceBright", "colorSurfaceDim",
            "colorPrimaryContainer", "colorSecondaryContainer", "colorTertiaryContainer",
            "background", "surface", "surfaceVariant",
            "outline", "outlineVariant", "scrim", "inverseSurface", "inverseOnSurface", "inversePrimary",
            "elevationOverlayColor", "surfaceColor", "surfaceContainer",
        )
        val textTargets = listOf("colorOnSurface", "android:colorOnSurface")
        val flagTargets = listOf("android:windowLightStatusBar", "android:windowLightNavigationBar")

        var updatedStyles = 0
        var updatedColors = 0
        var updatedColorStateLists = 0
        var updatedDrawables = 0

        val valuesFiles = listDecodableFiles("res/values*/*.xml", null)
        val knownAppAttrs = mutableSetOf<String>()
        for (path in valuesFiles) {
            runCatching {
                document(path).use { doc ->
                    val attrs = doc.getElementsByTagName("attr")
                    for (i in 0 until attrs.length) {
                        val a = attrs.item(i) as? Element ?: continue
                        a.getAttribute("name")?.takeIf { it.isNotEmpty() }?.let { knownAppAttrs.add(it) }
                    }
                    val items = doc.getElementsByTagName("item")
                    for (i in 0 until items.length) {
                        val it = items.item(i) as? Element ?: continue
                        val n = it.getAttribute("name") ?: continue
                        if (n.isNotEmpty() && !n.startsWith("android:")) knownAppAttrs.add(n)
                    }
                }
            }
        }

        fun shouldReplaceColor(value: String): Boolean {
            val v = value.trim()
            if (v.equals("@android:color/transparent", ignoreCase = true) || v.equals("@color/transparent", ignoreCase = true)) return false
            if (v.startsWith("@") || v.startsWith("?")) return false
            return v.matches(Regex("^#([0-9a-fA-F]{3}|[0-9a-fA-F]{4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$"))
        }

        for (relativePath in valuesFiles) {
            val dirName = relativePath.split("/")[1]
            if (!dirName.startsWith("values-night", ignoreCase = true)) continue
            runCatching {
                document(relativePath).use { doc ->
                    if (relativePath.endsWith("colors.xml", ignoreCase = true)) {
                        val colors = doc.getElementsByTagName("color")
                        var changed = false
                        for (i in 0 until colors.length) {
                            val c = colors.item(i) as? Element ?: continue
                            val name = c.getAttribute("name") ?: continue
                            val lower = name.lowercase()
                            val isSurfaceColor = lower.contains("background") ||
                                lower.contains("surface") ||
                                lower.contains("card") ||
                                lower.contains("window_background") ||
                                lower.contains("colorprimarysurface") ||
                                lower.contains("elevation")
                            if (!isSurfaceColor) continue
                            val value = c.textContent?.trim() ?: continue
                            if (shouldReplaceColor(value)) {
                                c.textContent = AMOLED
                                changed = true
                            }
                        }
                        if (changed) updatedColors++
                        return@use
                    }

                    val styles = doc.getElementsByTagName("style")
                    var changed = false
                    for (i in 0 until styles.length) {
                        val style = styles.item(i) as? Element ?: continue
                        val name = style.getAttribute("name")
                        val parent = style.getAttribute("parent")
                        val isTheme = name.contains("Theme", ignoreCase = true) ||
                            parent.contains("Theme", ignoreCase = true)
                        val items = style.getElementsByTagName("item")
                        var hasTarget = false
                        for (k in 0 until items.length) {
                            val it = items.item(k) as? Element ?: continue
                            val n = it.getAttribute("name")
                            if (n in styleTargets || n in textTargets) { hasTarget = true; break }
                        }
                        if (!isTheme && !hasTarget) continue

                        var styleChanged = false
                        for (attribute in styleTargets) {
                            if (!attribute.startsWith("android:") && attribute !in knownAppAttrs) continue
                            ensureThemeItem(doc, style, attribute, AMOLED)
                            styleChanged = true
                        }
                        for (textAttr in textTargets) {
                            if (!textAttr.startsWith("android:") && textAttr !in knownAppAttrs) continue
                            ensureThemeItem(doc, style, textAttr, "#FFFFFFFF")
                            styleChanged = true
                        }
                        if (isTheme) {
                            for (flagAttr in flagTargets) {
                                ensureThemeItem(doc, style, flagAttr, "false")
                                styleChanged = true
                            }
                        }
                        if (styleChanged) changed = true
                    }
                    if (changed) updatedStyles++
                }
            }.onFailure { logger.warning("Could not parse $relativePath: ${it.message}") }
        }

        for (relativePath in listDecodableFiles("res/color*/**", null)) {
            if (!relativePath.endsWith(".xml")) continue
            val dirName = relativePath.split("/")[1]
            if (!dirName.startsWith("color-night", ignoreCase = true)) continue
            runCatching {
                document(relativePath).use { doc ->
                    val colors = doc.getElementsByTagName("color")
                    var changed = false
                    for (i in 0 until colors.length) {
                        val c = colors.item(i) as? Element ?: continue
                        val value = c.getAttribute("android:color")?.takeIf { it.isNotEmpty() }
                            ?: c.textContent?.trim() ?: continue
                        if (!shouldReplaceColor(value)) continue
                        if (c.getAttribute("android:color").isNotEmpty()) {
                            c.setAttribute("android:color", AMOLED)
                            changed = true
                        } else if (c.textContent?.trim()?.startsWith("#") == true) {
                            c.textContent = AMOLED
                            changed = true
                        }
                    }
                    if (changed) updatedColorStateLists++
                }
            }.onFailure { logger.warning("Could not parse $relativePath: ${it.message}") }
        }

        for (relativePath in listDecodableFiles("res/drawable*/**", null)) {
            if (!relativePath.endsWith(".xml")) continue
            val dirName = relativePath.split("/")[1]
            if (!dirName.startsWith("drawable-night", ignoreCase = true)) continue
            runCatching {
                document(relativePath).use { doc ->
                    val solids = doc.getElementsByTagName("solid")
                    var changed = false
                    for (i in 0 until solids.length) {
                        val s = solids.item(i) as? Element ?: continue
                        val color = s.getAttribute("android:color")?.takeIf { it.isNotEmpty() } ?: continue
                        if (!shouldReplaceColor(color)) continue
                        s.setAttribute("android:color", AMOLED)
                        changed = true
                    }
                    if (changed) updatedDrawables++
                }
            }.onFailure { logger.warning("Could not parse $relativePath: ${it.message}") }
        }

        if (updatedStyles > 0 || updatedColors > 0 || updatedColorStateLists > 0 || updatedDrawables > 0) {
            logger.info("Applied AMOLED to $updatedStyles night theme style(s), $updatedColors night color file(s), $updatedColorStateLists ColorStateList(s), $updatedDrawables drawable(s)")
        } else {
            logger.warning("No night resources found. No changes applied.")
        }
    }
}
