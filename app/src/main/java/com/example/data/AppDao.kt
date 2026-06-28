package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    // Documents
    @Query("SELECT * FROM documents ORDER BY addedDate DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentById(id: Int): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity): Long

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocument(id: Int)

    // Quizzes
    @Query("SELECT * FROM quizzes ORDER BY dateCreated DESC")
    fun getAllQuizzes(): Flow<List<QuizEntity>>

    @Query("SELECT * FROM quizzes WHERE documentId = :documentId ORDER BY dateCreated DESC")
    fun getQuizzesForDocument(documentId: Int): Flow<List<QuizEntity>>

    @Query("SELECT * FROM quizzes WHERE id = :id LIMIT 1")
    suspend fun getQuizById(id: Int): QuizEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuiz(quiz: QuizEntity): Long

    @Update
    suspend fun updateQuiz(quiz: QuizEntity)

    @Query("DELETE FROM quizzes WHERE id = :id")
    suspend fun deleteQuiz(id: Int)

    @Query("DELETE FROM quizzes WHERE documentId = :documentId")
    suspend fun deleteQuizzesByDocument(documentId: Int)

    // Questions
    @Query("SELECT * FROM questions WHERE quizId = :quizId")
    suspend fun getQuestionsForQuiz(quizId: Int): List<QuestionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<QuestionEntity>)

    @Update
    suspend fun updateQuestion(question: QuestionEntity)

    // Flashcards
    @Query("SELECT * FROM flashcards WHERE documentId = :documentId")
    fun getFlashcardsForDocument(documentId: Int): Flow<List<FlashcardEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlashcard(flashcard: FlashcardEntity)

    @Update
    suspend fun updateFlashcard(flashcard: FlashcardEntity)

    @Query("DELETE FROM flashcards WHERE id = :id")
    suspend fun deleteFlashcard(id: Int)

    @Query("DELETE FROM flashcards WHERE documentId = :documentId")
    suspend fun deleteFlashcardsByDocument(documentId: Int)
}
