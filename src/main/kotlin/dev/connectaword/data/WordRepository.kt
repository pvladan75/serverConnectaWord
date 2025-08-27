package dev.connectaword.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader

data class WordEntry(
    val id: Int,
    val word: String,
    val length: Int,
    val form: String,
    val frequency: Long,
    val dialect: String?
)

object WordRepository {

    private val englishWords: List<WordEntry>
    // 👇 ДОДАЈЕМО ЛИСТУ ЗА СРПСКЕ РЕЧИ 👇
    private val serbianWords: List<WordEntry>

    init {
        val gson = Gson()

        // Учитавамо енглеске речи из једног фајла
        englishWords = loadWords(listOf("words_en.json"), gson)

        // 👇 ДЕФИНИШЕМО ЛИСТУ ФАЈЛОВА ЗА СРПСКИ ЈЕЗИК 👇
        val serbianFiles = listOf(
            "words_sr_1.json",
            "words_sr_2.json"
        )
        // Учитавамо српске речи из оба фајла
        serbianWords = loadWords(serbianFiles, gson)

        println("Successfully loaded ${englishWords.size} English words.")
        println("Successfully loaded ${serbianWords.size} Serbian words.")
    }

    private fun loadWords(fileNames: List<String>, gson: Gson): List<WordEntry> {
        val allWords = mutableListOf<WordEntry>()
        val wordListType = object : TypeToken<List<WordEntry>>() {}.type

        for (fileName in fileNames) {
            val inputStream = this::class.java.classLoader.getResourceAsStream(fileName)
            if (inputStream != null) {
                val reader = InputStreamReader(inputStream)
                try {
                    val wordsFromFile: List<WordEntry> = gson.fromJson(reader, wordListType)
                    allWords.addAll(wordsFromFile)
                } catch (e: Exception) {
                    println("ERROR parsing $fileName: ${e.message}")
                } finally {
                    reader.close()
                }
            } else {
                println("ERROR: $fileName not found in resources!")
            }
        }
        return allWords
    }

    /**
     * Рачуна дужину речи за српски језик, третирајући диграфе (nj, lj, dž) као једно слово.
     * Ова функција је `public` да би била доступна у Game.kt.
     */
    fun getSerbianWordLength(word: String): Int {
        return word.uppercase()
            .replace("DŽ", "1")
            .replace("LJ", "1")
            .replace("NJ", "1")
            .length
    }

    fun getFiveRandomWords(language: String): List<String> {
        val wordList = when (language.lowercase()) {
            "english" -> englishWords
            // 👇 ДОДАЈЕМО СРПСКИ ЈЕЗИК 👇
            "serbian" -> serbianWords
            else -> {
                println("Language '$language' not supported yet.")
                return emptyList()
            }
        }

        val isSerbian = language.lowercase() == "serbian"

        return wordList
            .filter {
                // ИЗМЕНА: Користимо нову функцију за дужину ако је језик српски
                val wordLength = if (isSerbian) getSerbianWordLength(it.word) else it.length
                it.form == "singular" &&
                        it.id <= 2000 &&
                        wordLength in 5..6 // Филтер се сада примењује на исправну дужину
            }
            .shuffled()
            .take(5)
            .map { it.word.uppercase() }
    }

    fun isValidGuess(guess: String, language: String): Boolean {
        val wordList = when (language.lowercase()) {
            "english" -> englishWords
            // 👇 ДОДАЈЕМО СРПСКИ ЈЕЗИК 👇
            "serbian" -> serbianWords
            else -> return false
        }

        val upperGuess = guess.uppercase()

        return wordList.any { it.word.uppercase() == upperGuess }
    }
}