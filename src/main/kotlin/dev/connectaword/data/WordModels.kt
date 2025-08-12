package dev.connectaword.data

// Ова класа представља цео words.json фајл
data class WordData(
    val srpski: List<Word>,
    val english: List<Word>
)

// Ова класа представља један објекат у листи, нпр. {"word": "JABUKA"}
data class Word(
    val word: String
)