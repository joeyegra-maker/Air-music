package com.music.zenith.ui.components

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.Color

/**
 * Warna latar halaman detail yang sebenarnya dipakai halaman itu sendiri.
 * Fade atas/bawah di level app membacanya supaya warnanya sama dengan halaman,
 * bukan dihitung ulang dari sumber lain.
 */
object PageChromeTint {
    private val tints = mutableStateMapOf<String, Color>()

    fun set(pageId: String, color: Color) {
        if (tints[pageId] != color) tints[pageId] = color
    }

    fun of(pageId: String): Color? = tints[pageId]
}