package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.intelligence.LocalIntelligenceEngine
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AppViewModel(private val repository: AppRepository) : ViewModel() {

    // Document Lists
    val documents: StateFlow<List<DocumentEntity>> = repository.allDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All Quizzes for progress tracking
    val quizzes: StateFlow<List<QuizEntity>> = repository.allQuizzes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI States
    private val _activeDocument = MutableStateFlow<DocumentEntity?>(null)
    val activeDocument: StateFlow<DocumentEntity?> = _activeDocument.asStateFlow()

    private val _activeQuiz = MutableStateFlow<QuizEntity?>(null)
    val activeQuiz: StateFlow<QuizEntity?> = _activeQuiz.asStateFlow()

    private val _activeQuizQuestions = MutableStateFlow<List<QuestionEntity>>(emptyList())
    val activeQuizQuestions: StateFlow<List<QuestionEntity>> = _activeQuizQuestions.asStateFlow()

    private val _activeFlashcards = MutableStateFlow<List<FlashcardEntity>>(emptyList())
    val activeFlashcards: StateFlow<List<FlashcardEntity>> = _activeFlashcards.asStateFlow()

    // Loading & Parsing States
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    /**
     * Imports a document 100% locally. Triggers smart parsing, 
     * extractive summarization, and interactive quiz/flashcard generation on the fly.
     */
    fun importDocument(title: String, content: String, fileType: String) {
        if (content.isBlank() || title.isBlank()) return

        viewModelScope.launch {
            _isProcessing.value = true
            try {
                // 1. Generate local extractive summary
                val summary = LocalIntelligenceEngine.summarizeText(content, targetSentenceCount = 5)

                // 2. Save Document to Database
                val docId = repository.insertDocument(
                    DocumentEntity(
                        title = title,
                        content = content,
                        fileType = fileType,
                        summary = summary
                    )
                ).toInt()

                // 3. Generate and save smart local Quizzes & Questions
                val rawQuestions = LocalIntelligenceEngine.generateQuestionsAndQuizzes(content)
                if (rawQuestions.isNotEmpty()) {
                    val quizId = repository.insertQuiz(
                        QuizEntity(
                            documentId = docId,
                            title = "$title - Değerlendirme Testi",
                            totalQuestions = rawQuestions.size
                        )
                    ).toInt()

                    val questionEntities = rawQuestions.map {
                        val optionsStr = if (it.options.isNotEmpty()) {
                            // Quick manual JSON-array builder to avoid external parsing overhead
                            it.options.joinToString(separator = "\",\"", prefix = "[\"", postfix = "\"]")
                        } else ""
                        
                        QuestionEntity(
                            quizId = quizId,
                            type = it.type,
                            questionText = it.questionText,
                            optionsJson = optionsStr,
                            correctAnswer = it.correctAnswer
                        )
                    }
                    repository.insertQuestions(questionEntities)
                }

                // 4. Generate and save smart interactive Flashcards
                val rawFlashcards = LocalIntelligenceEngine.generateFlashcards(content)
                for (card in rawFlashcards) {
                    repository.insertFlashcard(
                        FlashcardEntity(
                            documentId = docId,
                            front = card.front,
                            back = card.back
                        )
                    )
                }

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isProcessing.value = false
            }
        }
    }

    /**
     * Sets active document and collects its specific flashcards reactively
     */
    fun selectDocument(document: DocumentEntity) {
        _activeDocument.value = document
        viewModelScope.launch {
            repository.getFlashcardsForDocument(document.id).collect {
                _activeFlashcards.value = it
            }
        }
    }

    /**
     * Deletes document and its cascading contents cleanly
     */
    fun deleteDocument(documentId: Int) {
        viewModelScope.launch {
            repository.deleteDocument(documentId)
            if (_activeDocument.value?.id == documentId) {
                _activeDocument.value = null
                _activeFlashcards.value = emptyList()
            }
        }
    }

    /**
     * Loads a Quiz and its Questions into the state machine
     */
    fun selectQuiz(quiz: QuizEntity) {
        _activeQuiz.value = quiz
        viewModelScope.launch {
            val questions = repository.getQuestionsForQuiz(quiz.id)
            _activeQuizQuestions.value = questions
        }
    }

    /**
     * Handles dynamic option selection during an active quiz session
     */
    fun selectQuestionAnswer(questionId: Int, answer: String) {
        _activeQuizQuestions.value = _activeQuizQuestions.value.map { q ->
            if (q.id == questionId) q.copy(userAnswer = answer) else q
        }
    }

    /**
     * Computes final results, updates database stats and completes quiz session
     */
    fun submitQuizResults() {
        val quiz = _activeQuiz.value ?: return
        val questions = _activeQuizQuestions.value
        
        var correctCount = 0
        questions.forEach { q ->
            if (q.userAnswer != null) {
                // If MCQ, the userAnswer might be direct text, let's map index to A, B, C, D
                val isCorrect = if (q.type == "MCQ") {
                    val index = parseOptions(q.optionsJson).indexOf(q.userAnswer)
                    val letterAnswers = listOf("A", "B", "C", "D")
                    val chosenLetter = letterAnswers.getOrNull(index) ?: ""
                    chosenLetter == q.correctAnswer || q.userAnswer == q.correctAnswer
                } else {
                    q.userAnswer == q.correctAnswer
                }
                
                if (isCorrect) correctCount++
            }
        }

        val calculatedScore = if (questions.isNotEmpty()) {
            (correctCount * 100) / questions.size
        } else 0

        viewModelScope.launch {
            // Update quiz with high score if higher
            val updatedQuiz = quiz.copy(score = maxOf(quiz.score, calculatedScore))
            repository.updateQuiz(updatedQuiz)
            _activeQuiz.value = updatedQuiz
            
            // Persist user answers in database
            questions.forEach { q ->
                repository.updateQuestion(q)
            }
        }
    }

    /**
     * Resets the quiz user answers for a fresh run
     */
    fun resetActiveQuiz() {
        _activeQuizQuestions.value = _activeQuizQuestions.value.map { q ->
            q.copy(userAnswer = null)
        }
    }

    /**
     * Toggles learned tag for a local flashcard
     */
    fun toggleFlashcardLearned(card: FlashcardEntity) {
        viewModelScope.launch {
            repository.updateFlashcard(card.copy(isLearned = !card.isLearned))
        }
    }

    /**
     * Clean helper to parse JSON string array of choices
     */
    fun parseOptions(json: String): List<String> {
        if (json.isBlank()) return emptyList()
        return try {
            json.removeSurrounding("[", "]")
                .split("\",\"")
                .map { it.removeSurrounding("\"").trim() }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

class AppViewModelFactory(private val repository: AppRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AppViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
