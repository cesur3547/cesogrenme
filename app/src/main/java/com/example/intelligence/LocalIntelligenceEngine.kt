package com.example.intelligence

import java.util.Locale
import java.util.regex.Pattern

object LocalIntelligenceEngine {

    // Common Turkish stop words to ignore during word frequency calculation
    private val TURKISH_STOPWORDS = setOf(
        "ve", "veya", "ile", "da", "de", "bir", "bu", "o", "ki", "en", "daha", 
        "için", "ise", "gibi", "kadar", "olan", "olarak", "ama", "fakat", "çünkü",
        "her", "hiç", "bazı", "tüm", "şunlar", "böyle", "şöyle", "yok", "var",
        "yani", "şu", "onlar", "biz", "siz", "ben", "sen", "mi", "mı", "mu", "mü",
        "beri", "diye", "göre", "biri", "neyse", "ancak", "yalnız", "zaten"
    )

    data class RawQuestion(
        val type: String, // "MCQ" (Multiple Choice), "TF" (True/False)
        val questionText: String,
        val options: List<String>, // Empty for TF
        val correctAnswer: String // "A", "B", "C", "D" or "True", "False" (or "Doğru", "Yanlış" in Turkish)
    )

    data class RawFlashcard(
        val front: String,
        val back: String
    )

    /**
     * Splits a text block into sentences using a regex pattern.
     */
    fun splitIntoSentences(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val sentenceBoundaries = Pattern.compile("(?<=[.!?])\\s+(?=[A-ZÇĞİÖŞÜa-zçgıoşu0-9])")
        return text.split(sentenceBoundaries)
            .map { it.trim() }
            .filter { it.length > 5 }
    }

    /**
     * Extracts a highly accurate local summary of the text using an extractive NLP algorithm.
     */
    fun summarizeText(text: String, targetSentenceCount: Int = 4): String {
        val sentences = splitIntoSentences(text)
        if (sentences.size <= targetSentenceCount) {
            return text // Text is already short, return as is
        }

        val trLocale = Locale("tr", "TR")
        val wordFrequency = mutableMapOf<String, Int>()

        // 1. Calculate word frequencies (excluding stopwords)
        for (sentence in sentences) {
            val words = sentence.split(Pattern.compile("\\s+"))
            for (rawWord in words) {
                val word = rawWord.replace(Regex("[^a-zA-ZçÇğĞıİöÖşŞüÜ]"), "")
                    .lowercase(trLocale)
                if (word.length >= 3 && word !in TURKISH_STOPWORDS) {
                    wordFrequency[word] = wordFrequency.getOrDefault(word, 0) + 1
                }
            }
        }

        // 2. Score sentences based on word frequencies
        val sentenceScores = mutableMapOf<Int, Double>()
        for (i in sentences.indices) {
            val sentence = sentences[i]
            val words = sentence.split(Pattern.compile("\\s+"))
            var score = 0.0
            var wordCount = 0
            for (rawWord in words) {
                val word = rawWord.replace(Regex("[^a-zA-ZçÇğĞıİöÖşŞüÜ]"), "")
                    .lowercase(trLocale)
                if (word.length >= 3 && word !in TURKISH_STOPWORDS) {
                    score += wordFrequency.getOrDefault(word, 0)
                    wordCount++
                }
            }
            // Normalize score by word count to prevent favoring excessively long sentences
            if (wordCount > 0) {
                sentenceScores[i] = score / wordCount
            } else {
                sentenceScores[i] = 0.0
            }
        }

        // 3. Select top sentences and sort them by original position to maintain context flow
        val topSentenceIndices = sentenceScores.entries
            .sortedByDescending { it.value }
            .take(targetSentenceCount)
            .map { it.key }
            .sorted()

        return topSentenceIndices.joinToString(" ") { sentences[it] }
    }

    /**
     * Generates a list of smart, personalized quizzes and questions 100% on-device.
     */
    fun generateQuestionsAndQuizzes(text: String): List<RawQuestion> {
        val sentences = splitIntoSentences(text)
        if (sentences.isEmpty()) return emptyList()

        val trLocale = Locale("tr", "TR")
        val questions = mutableListOf<RawQuestion>()
        
        // Extract key terms (capitalized words or highly frequent words)
        val keyTerms = mutableSetOf<String>()
        val termWordFrequency = mutableMapOf<String, Int>()

        for (sentence in sentences) {
            val words = sentence.split(Pattern.compile("\\s+"))
            for (rawWord in words) {
                val cleaned = rawWord.replace(Regex("[^a-zA-ZçÇğĞıİöÖşŞüÜ]"), "")
                if (cleaned.length >= 3) {
                    val lower = cleaned.lowercase(trLocale)
                    if (lower !in TURKISH_STOPWORDS) {
                        termWordFrequency[cleaned] = termWordFrequency.getOrDefault(cleaned, 0) + 1
                        // If it's a capitalized word (proper noun or major concept)
                        if (cleaned.isNotEmpty() && cleaned[0].isUpperCase() && cleaned.lowercase(trLocale) != cleaned) {
                            keyTerms.add(cleaned)
                        }
                    }
                }
            }
        }

        // Add some high frequency terms to keyTerms if not enough proper nouns
        val sortedTerms = termWordFrequency.entries.sortedByDescending { it.value }
        for (entry in sortedTerms.take(15)) {
            if (keyTerms.size < 10) {
                keyTerms.add(entry.key)
            }
        }

        val keyTermsList = keyTerms.filter { it.length > 2 }.take(20)

        // Try to find definition patterns
        // e.g., "X, Y'dir", "X, Y denir", "X, Y anlamına gelir", "X, Y olarak bilinir", "X, Y olarak tanımlanır"
        for (sentence in sentences) {
            var isDefinition = false
            var concept = ""
            var definitionBody = ""

            val lowerSentence = sentence.lowercase(trLocale)
            val definitionKeywords = listOf(" denir", " anlamına gelir", " olarak tanımlanır", " olarak adlandırılır", " olarak bilinir", " ifade eder", " gösterir")
            
            val keywordFound = definitionKeywords.firstOrNull { lowerSentence.contains(it) }

            if (keywordFound != null) {
                // Try to extract the concept (usually before the first comma, or first few words)
                val commaIndex = sentence.indexOf(",")
                if (commaIndex > 3 && commaIndex < sentence.length / 2) {
                    concept = sentence.substring(0, commaIndex).trim()
                    definitionBody = sentence.substring(commaIndex + 1).trim()
                    isDefinition = true
                } else {
                    // Split by first few words
                    val words = sentence.split(" ")
                    if (words.size > 3) {
                        concept = words.take(2).joinToString(" ").trim()
                        definitionBody = words.drop(2).joinToString(" ").trim()
                        isDefinition = true
                    }
                }
            } else if (sentence.contains(" - ") || sentence.contains(": ")) {
                val separator = if (sentence.contains(" - ")) " - " else ": "
                val parts = sentence.split(separator, limit = 2)
                if (parts.size == 2 && parts[0].trim().split(" ").size <= 4) {
                    concept = parts[0].trim()
                    definitionBody = parts[1].trim()
                    isDefinition = true
                }
            }

            // Cleanup concept to be a clean term
            concept = concept.replace(Regex("^[^a-zA-ZçÇğĞıİöÖşŞüÜ]+"), "").trim()

            if (isDefinition && concept.length in 3..35 && definitionBody.length > 10) {
                // MCQ - Blank filling
                val questionText = "Dokümana göre, \"_______, $definitionBody\" ifadesindeki boşluğa hangi kavram gelmelidir?"
                val distractors = keyTermsList.filter { it.lowercase(trLocale) != concept.lowercase(trLocale) }
                    .shuffled()
                    .take(3)
                
                if (distractors.size >= 3) {
                    val options = (distractors + concept).shuffled()
                    val correctIndex = options.indexOf(concept)
                    val letterAnswers = listOf("A", "B", "C", "D")
                    val correctLetter = letterAnswers.getOrNull(correctIndex) ?: "A"

                    questions.add(
                        RawQuestion(
                            type = "MCQ",
                            questionText = questionText,
                            options = options,
                            correctAnswer = correctLetter
                        )
                    )
                }

                // Or MCQ - Direct question "X nedir?"
                val directQuestionText = "Yandaki tanım hangi kavrama aittir?\n\"$definitionBody\""
                val directDistractors = keyTermsList.filter { it.lowercase(trLocale) != concept.lowercase(trLocale) }.shuffled().take(3)
                if (directDistractors.size >= 3) {
                    val options = (directDistractors + concept).shuffled()
                    val correctIndex = options.indexOf(concept)
                    val letterAnswers = listOf("A", "B", "C", "D")
                    val correctLetter = letterAnswers.getOrNull(correctIndex) ?: "A"

                    questions.add(
                        RawQuestion(
                            type = "MCQ",
                            questionText = directQuestionText,
                            options = options,
                            correctAnswer = correctLetter
                        )
                    )
                }
            }
        }

        // If not enough questions generated from definition patterns, generate from general key sentences
        val shuffledSentences = sentences.shuffled()
        for (sentence in shuffledSentences) {
            if (questions.size >= 10) break

            // Skip very long or short sentences
            if (sentence.length !in 30..150) continue

            // Find a key word in this sentence to blank out
            val wordsInSentence = sentence.split(" ")
                .map { it.replace(Regex("[^a-zA-ZçÇğĞıİöÖşŞüÜ]"), "") }
                .filter { it.length > 4 && it !in TURKISH_STOPWORDS }
            
            if (wordsInSentence.isNotEmpty()) {
                val keywordToBlank = wordsInSentence.maxByOrNull { termWordFrequency[it] ?: 0 } ?: wordsInSentence.first()
                val questionText = "Cümledeki boşluğu doldurunuz:\n\"" + sentence.replaceFirst(keywordToBlank, "_______") + "\""
                
                val distractors = keyTermsList.filter { it.lowercase(trLocale) != keywordToBlank.lowercase(trLocale) }
                    .shuffled()
                    .take(3)
                
                if (distractors.size >= 3) {
                    val options = (distractors + keywordToBlank).shuffled()
                    val correctIndex = options.indexOf(keywordToBlank)
                    val letterAnswers = listOf("A", "B", "C", "D")
                    val correctLetter = letterAnswers.getOrNull(correctIndex) ?: "A"

                    questions.add(
                        RawQuestion(
                            type = "MCQ",
                            questionText = questionText,
                            options = options,
                            correctAnswer = correctLetter
                        )
                    )
                }
            }
        }

        // Generate True/False questions based on key sentences
        for (sentence in shuffledSentences) {
            if (questions.size >= 15) break
            if (sentence.length !in 40..120) continue

            val isTrue = (0..1).random() == 1
            if (isTrue) {
                questions.add(
                    RawQuestion(
                        type = "TF",
                        questionText = "Aşağıdaki ifade, dokümandaki bilgilere göre DOĞRU mudur?\n\n\"$sentence\"",
                        options = listOf("Doğru", "Yanlış"),
                        correctAnswer = "Doğru"
                    )
                )
            } else {
                // Make the sentence false by altering a key word or negating it
                val words = sentence.split(" ").toMutableList()
                var altered = false
                
                // Try to find a verb ending like "sağlar", "gerekir", "olur", "yapar", "sahiptir" and negate it
                for (i in words.indices) {
                    val word = words[i].lowercase(trLocale)
                    if (word.endsWith("dır") || word.endsWith("dir") || word.endsWith("dur") || word.endsWith("dür")) {
                        words[i] = words[i] + " değildir"
                        altered = true
                        break
                    } else if (word == "var") {
                        words[i] = "yoktur"
                        altered = true
                        break
                    } else if (word == "evet") {
                        words[i] = "hayır"
                        altered = true
                        break
                    } else if (word.endsWith("sağlar")) {
                        words[i] = words[i].replace("sağlar", "sağlamaz")
                        altered = true
                        break
                    } else if (word.endsWith("olur")) {
                        words[i] = words[i].replace("olur", "olmaz")
                        altered = true
                        break
                    } else if (word.endsWith("bulunur")) {
                        words[i] = words[i].replace("bulunur", "bulunmaz")
                        altered = true
                        break
                    }
                }

                // Fallback: If no verb negation happened, swap a key word with another term
                if (!altered && words.size > 4) {
                    val targetIndex = (2..words.size - 2).random()
                    val originalWord = words[targetIndex].replace(Regex("[^a-zA-ZçÇğĞıİöÖşŞüÜ]"), "")
                    if (originalWord.length > 3 && keyTermsList.isNotEmpty()) {
                        val replacement = keyTermsList.firstOrNull { it != originalWord } ?: "başka bir unsur"
                        words[targetIndex] = words[targetIndex].replace(originalWord, replacement)
                        altered = true
                    }
                }

                if (altered) {
                    val alteredSentence = words.joinToString(" ")
                    questions.add(
                        RawQuestion(
                            type = "TF",
                            questionText = "Aşağıdaki ifade, dokümandaki bilgilere göre DOĞRU mudur?\n\n\"$alteredSentence\"",
                            options = listOf("Doğru", "Yanlış"),
                            correctAnswer = "Yanlış"
                        )
                    )
                }
            }
        }

        // Limit to max 10-12 highly engaging questions per quiz
        return questions.shuffled().take(10)
    }

    /**
     * Generates on-device flashcards from a text block.
     */
    fun generateFlashcards(text: String): List<RawFlashcard> {
        val sentences = splitIntoSentences(text)
        if (sentences.isEmpty()) return emptyList()

        val flashcards = mutableListOf<RawFlashcard>()
        val trLocale = Locale("tr", "TR")

        for (sentence in sentences) {
            var concept = ""
            var definition = ""
            var isDefinition = false

            val lowerSentence = sentence.lowercase(trLocale)
            val definitionKeywords = listOf(" denir", " anlamına gelir", " olarak tanımlanır", " olarak adlandırılır", " olarak bilinir", " ifade eder", " gösterir")
            val keywordFound = definitionKeywords.firstOrNull { lowerSentence.contains(it) }

            if (keywordFound != null) {
                val commaIndex = sentence.indexOf(",")
                if (commaIndex > 3 && commaIndex < sentence.length / 2) {
                    concept = sentence.substring(0, commaIndex).trim()
                    definition = sentence.substring(commaIndex + 1).trim()
                    isDefinition = true
                } else {
                    val words = sentence.split(" ")
                    if (words.size > 3) {
                        concept = words.take(2).joinToString(" ").trim()
                        definition = words.drop(2).joinToString(" ").trim()
                        isDefinition = true
                    }
                }
            } else if (sentence.contains(" - ") || sentence.contains(": ")) {
                val separator = if (sentence.contains(" - ")) " - " else ": "
                val parts = sentence.split(separator, limit = 2)
                if (parts.size == 2 && parts[0].trim().split(" ").size <= 4) {
                    concept = parts[0].trim()
                    definition = parts[1].trim()
                    isDefinition = true
                }
            }

            concept = concept.replace(Regex("^[^a-zA-ZçÇğĞıİöÖşŞüÜ]+"), "").trim()

            if (isDefinition && concept.length in 3..40 && definition.length > 10) {
                flashcards.add(
                    RawFlashcard(
                        front = "$concept Nedir?",
                        back = "$concept: $definition"
                    )
                )
            } else if (sentence.length in 30..120) {
                // Standard visual pairing flashcard
                val words = sentence.split(" ")
                if (words.size > 4) {
                    val keyHeader = words.take(3).joinToString(" ").trim()
                    val restOfSentence = words.drop(3).joinToString(" ").trim()
                    flashcards.add(
                        RawFlashcard(
                            front = "$keyHeader...",
                            back = sentence
                        )
                    )
                }
            }
        }

        return flashcards.shuffled().take(12)
    }

    /**
     * Fallback extractor that reads raw printable ASCII/UTF-8 strings out of custom binary files 
     * (e.g. PDF/DOCX stream sequences) without external library bloat, ensuring 100% on-device private parsing.
     */
    fun extractPrintableText(bytes: ByteArray): String {
        val sb = StringBuilder()
        var currentWord = StringBuilder()
        
        for (b in bytes) {
            val char = b.toInt().toChar()
            // Support Turkish characters and printable ASCII
            if (char.isLetterOrDigit() || char.isWhitespace() || 
                char == '.' || char == ',' || char == '!' || char == '?' || char == '-' || char == ':' || char == '\'' ||
                "çÇğĞıİöÖşŞüÜ".contains(char)) {
                
                if (char.isWhitespace()) {
                    if (currentWord.isNotEmpty()) {
                        sb.append(currentWord).append(char)
                        currentWord = StringBuilder()
                    } else if (sb.isNotEmpty() && !sb.last().isWhitespace()) {
                        sb.append(char)
                    }
                } else {
                    currentWord.append(char)
                }
            } else {
                if (currentWord.isNotEmpty()) {
                    sb.append(currentWord)
                    currentWord = StringBuilder()
                }
            }
        }
        if (currentWord.isNotEmpty()) {
            sb.append(currentWord)
        }
        
        // Clean double spaces, extract paragraphs
        var cleaned = sb.toString()
            .replace(Regex("\\s+"), " ")
            .replace(Regex("([.!?])\\s*"), "$1\n\n")
            .trim()
            
        // Filter out binary residue headers/footers
        val lines = cleaned.split("\n\n")
        val filteredLines = lines.filter { line ->
            val letterCount = line.count { it.isLetter() }
            val totalCount = line.length
            // A line is mostly content if it contains letters and is not purely random code
            totalCount > 10 && letterCount.toDouble() / totalCount > 0.6
        }
        
        return if (filteredLines.isNotEmpty()) {
            filteredLines.joinToString("\n\n")
        } else {
            // Fallback to cleaned text directly
            cleaned
        }
    }
}
