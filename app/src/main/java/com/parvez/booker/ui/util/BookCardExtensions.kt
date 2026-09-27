package com.parvez.booker.ui.util

import androidx.compose.ui.graphics.Color
import com.parvez.booker.data.model.Book
import kotlin.math.abs

/**
 * Extracts initials from the book title (e.g., "Effective Java" -> "EJ", "Clean Code" -> "CC").
 */
val Book.initials: String
    get() {
        val titleText = title?.trim()
        if (titleText.isNullOrEmpty()) return "B"
        val words = titleText.split("\\s+".toRegex()).filter { it.isNotBlank() }
        return when {
            words.size >= 3 -> "${words[0].first()}${words[1].first()}${words[2].first()}".uppercase()
            words.size == 2 -> "${words[0].first()}${words[1].first()}".uppercase()
            words.size == 1 && words[0].length >= 2 -> "${words[0][0]}${words[0][1]}".uppercase()
            words.size == 1 -> "${words[0][0]}".uppercase()
            else -> "B"
        }
    }

/**
 * Generates a deterministic gradient pair of dark colors based on book ID or title hash.
 */
val Book.coverColors: Pair<Color, Color>
    get() {
        val seed = ((id ?: 0L).toInt() * 31) + (title?.hashCode() ?: 0)
        val palettes = listOf(
            Pair(Color(0xFF382A54), Color(0xFF231A38)),
            Pair(Color(0xFF423B2A), Color(0xFF28241A)),
            Pair(Color(0xFF2B4239), Color(0xFF1B2B26)),
            Pair(Color(0xFF542A35), Color(0xFF381A22)),
            Pair(Color(0xFF2B3A54), Color(0xFF1B2438)),
            Pair(Color(0xFF4A2B54), Color(0xFF2F1B38))
        )
        val index = abs(seed) % palettes.size
        return palettes[index]
    }