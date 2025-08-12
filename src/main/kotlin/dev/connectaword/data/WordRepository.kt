package dev.connectaword.data

import com.google.gson.Gson
import java.io.InputStreamReader

object WordRepository {

    private val serbianWords: List<String>
    private val englishWords: List<String>

    // init блок се извршава аутоматски чим се сервер покрене
    init {
        val gson = Gson()
        // Учитавамо words.json из resources фолдера
        val inputStream = this::class.java.classLoader.getResourceAsStream("words.json")

        if (inputStream != null) {
            val reader = InputStreamReader(inputStream)
            // Парсирамо JSON у наше WordData објекте
            val wordData = gson.fromJson(reader, WordData::class.java)

            // Извлачимо само стрингове речи у наше листе
            serbianWords = wordData.srpski.map { it.word.uppercase() }
            englishWords = wordData.english.map { it.word.uppercase() }

            println("Successfully loaded ${serbianWords.size} Serbian and ${englishWords.size} English words.")
        } else {
            println("ERROR: words.json not found in resources!")
            serbianWords = emptyList()
            englishWords = emptyList()
        }
    }

    fun getRandomWord(language: String): String? {
        val wordList = when (language.lowercase()) {
            "english" -> englishWords
            "serbian" -> serbianWords
            else -> null
        }

        return wordList?.ifEmpty { null }?.random()
    }
}