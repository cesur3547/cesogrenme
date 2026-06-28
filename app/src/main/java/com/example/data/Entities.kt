package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String,
    val fileType: String, // "TXT", "PDF", "DOCX", "SLIDE", "MANUEL"
    val addedDate: Long = System.currentTimeMillis(),
    val summary: String = ""
)

@Entity(tableName = "quizzes")
data class QuizEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val documentId: Int,
    val title: String,
    val score: Int = -1, // -1 means not taken yet
    val totalQuestions: Int = 0,
    val dateCreated: Long = System.currentTimeMillis()
)

@Entity(tableName = "questions")
data class QuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val quizId: Int,
    val type: String, // "MCQ" (Multiple Choice), "TF" (True/False)
    val questionText: String,
    val optionsJson: String, // JSON string array of choices for MCQ, empty for TF
    val correctAnswer: String, // "A", "B", "C", "D" or "True", "False"
    val userAnswer: String? = null
)

@Entity(tableName = "flashcards")
data class FlashcardEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val documentId: Int,
    val front: String,
    val back: String,
    val isLearned: Boolean = false
)
