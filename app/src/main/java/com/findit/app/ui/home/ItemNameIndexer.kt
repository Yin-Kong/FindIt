package com.findit.app.ui.home

import android.icu.text.Transliterator
import com.findit.app.data.model.ItemWithDetails
import java.util.Locale

private val HanToLatinTransliterator: Transliterator by lazy {
    Transliterator.getInstance("Han-Latin; Latin-ASCII")
}

fun itemIndexLetter(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return "#"

    val first = trimmed.first()
    val normalized = if (first.code in 0x4E00..0x9FFF) {
        HanToLatinTransliterator.transliterate(trimmed)
    } else {
        trimmed
    }

    val firstLetter = normalized.firstOrNull { it.isLetterOrDigit() } ?: return "#"
    return when {
        firstLetter.isLetter() -> firstLetter.uppercaseChar().toString()
        firstLetter.isDigit() -> "#"
        else -> "#"
    }
}

fun normalizedItemSortKey(name: String): String {
    val trimmed = name.trim()
    val normalized = if (trimmed.firstOrNull()?.code in 0x4E00..0x9FFF) {
        HanToLatinTransliterator.transliterate(trimmed)
    } else {
        trimmed
    }
    return normalized.lowercase(Locale.getDefault())
}

fun sortItemsForDrawer(items: List<ItemWithDetails>): List<ItemWithDetails> {
    return items.sortedWith(
        compareBy<ItemWithDetails> { itemIndexLetter(it.item.name) == "#" }
            .thenBy { itemIndexLetter(it.item.name) }
            .thenBy { normalizedItemSortKey(it.item.name) }
            .thenByDescending { it.item.createdAt }
    )
}
