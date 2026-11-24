package app.brokenn.patches.officereader.shared

import org.w3c.dom.Document
import org.w3c.dom.Element

internal const val NS_ANDROID = "http://schemas.android.com/apk/res/android"

internal fun Element?.applicationOrNull(): Element? =
    this?.getElementsByTagName("application")?.item(0) as? Element

internal fun ensureThemeItem(doc: Document, style: Element, attrName: String, value: String) {
    val items = style.getElementsByTagName("item")
    for (i in 0 until items.length) {
        val item = items.item(i) as? Element ?: continue
        if (item.getAttribute("name") == attrName) {
            item.textContent = value
            return
        }
    }
    val item = doc.createElement("item")
    item.setAttribute("name", attrName)
    item.textContent = value
    style.appendChild(item)
}
