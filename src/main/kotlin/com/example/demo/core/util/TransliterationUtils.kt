package com.example.demo.core.util

import com.ibm.icu.text.Transliterator
import org.springframework.stereotype.Component

@Component
class TransliterationUtils {
    // Правило: Любой язык -> Латиница; Латиница -> ASCII; В нижний регистр
    private val toLatinTrans = Transliterator.getInstance("Any-Latin; Latin-ASCII; Lower")

    fun transliterate(input: String): String {
        return toLatinTrans.transliterate(input)
            .replace("[^a-z0-9-]".toRegex(), "") // Убираем спецсимволы, оставляем буквы, цифры и дефис
    }
}