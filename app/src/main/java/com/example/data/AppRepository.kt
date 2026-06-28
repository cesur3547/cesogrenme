package com.example.data

import kotlinx.coroutines.flow.Flow

class AppRepository(private val appDao: AppDao) {
    // Documents
    val allDocuments: Flow<List<DocumentEntity>> = appDao.getAllDocuments()

    suspend fun getDocumentById(id: Int): DocumentEntity? = appDao.getDocumentById(id)

    suspend fun insertDocument(document: DocumentEntity): Long = appDao.insertDocument(document)

    suspend fun deleteDocument(id: Int) {
        appDao.deleteDocument(id)
        appDao.deleteQuizzesByDocument(id)
        appDao.deleteFlashcardsByDocument(id)
    }

    // Quizzes
    val allQuizzes: Flow<List<QuizEntity>> = appDao.getAllQuizzes()

    fun getQuizzesForDocument(documentId: Int): Flow<List<QuizEntity>> = 
        appDao.getQuizzesForDocument(documentId)

    suspend fun getQuizById(id: Int): QuizEntity? = appDao.getQuizById(id)

    suspend fun insertQuiz(quiz: QuizEntity): Long = appDao.insertQuiz(quiz)

    suspend fun updateQuiz(quiz: QuizEntity) = appDao.updateQuiz(quiz)

    suspend fun deleteQuiz(id: Int) = appDao.deleteQuiz(id)

    // Questions
    suspend fun getQuestionsForQuiz(quizId: Int): List<QuestionEntity> = 
        appDao.getQuestionsForQuiz(quizId)

    suspend fun insertQuestions(questions: List<QuestionEntity>) = 
        appDao.insertQuestions(questions)

    suspend fun updateQuestion(question: QuestionEntity) = 
        appDao.updateQuestion(question)

    // Flashcards
    fun getFlashcardsForDocument(documentId: Int): Flow<List<FlashcardEntity>> = 
        appDao.getFlashcardsForDocument(documentId)

    suspend fun insertFlashcard(flashcard: FlashcardEntity) = 
        appDao.insertFlashcard(flashcard)

    suspend fun updateFlashcard(flashcard: FlashcardEntity) = 
        appDao.updateFlashcard(flashcard)

    suspend fun deleteFlashcard(id: Int) = appDao.deleteFlashcard(id)
}
