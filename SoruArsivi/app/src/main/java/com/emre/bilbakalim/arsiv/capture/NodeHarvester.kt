package com.emre.bilbakalim.arsiv.capture

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Erişilebilirlik ağacını dolaşıp ekrandaki bütün metinleri konumlarıyla toplar.
 * Uygulama metni normal View'larla çiziyorsa OCR'a hiç gerek kalmaz —
 * bu yol hem çok daha doğrudur (Türkçe karakterlerde sıfır hata) hem de bedavadır.
 */
object NodeHarvester {

    private const val MAX_DEPTH = 60
    private const val MAX_NODES = 900

    fun harvest(root: AccessibilityNodeInfo?): List<TextItem> {
        if (root == null) return emptyList()
        val out = ArrayList<TextItem>(64)
        walk(root, 0, false, out)
        return out
    }

    private fun walk(
        node: AccessibilityNodeInfo,
        depth: Int,
        parentClickable: Boolean,
        out: MutableList<TextItem>
    ) {
        if (depth > MAX_DEPTH || out.size > MAX_NODES) return

        val clickable = parentClickable || node.isClickable

        val raw = node.text?.toString()?.takeIf { it.isNotBlank() }
            ?: node.contentDescription?.toString()?.takeIf { it.isNotBlank() }

        if (raw != null) {
            val r = Rect()
            node.getBoundsInScreen(r)
            if (r.width() > 0 && r.height() > 0) {
                out.add(TextItem(raw.trim(), r, clickable, depth))
            }
        }

        val count = node.childCount
        for (i in 0 until count) {
            val child = try { node.getChild(i) } catch (_: Throwable) { null } ?: continue
            walk(child, depth + 1, clickable, out)
        }
    }
}
