package com.example.intelligence

import android.content.Context
import android.net.Uri
import com.itextpdf.text.pdf.PdfReader
import com.itextpdf.text.pdf.parser.PdfTextExtractor
import java.io.InputStream
import java.util.zip.ZipInputStream

object DocumentParser {

    fun parseUri(context: Context, uri: Uri): ParseResult {
        val contentResolver = context.contentResolver
        val cursor = contentResolver.query(uri, null, null, null, null)
        var fileName = "Bilinmeyen Doküman"
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    fileName = it.getString(nameIndex)
                }
            }
        }

        val type = when {
            fileName.endsWith(".pdf", ignoreCase = true) -> "PDF"
            fileName.endsWith(".docx", ignoreCase = true) -> "Word"
            fileName.endsWith(".xlsx", ignoreCase = true) -> "Excel"
            fileName.endsWith(".pptx", ignoreCase = true) -> "SLIDE"
            fileName.endsWith(".txt", ignoreCase = true) || fileName.endsWith(".csv", ignoreCase = true) -> "TXT"
            else -> "Bilinmeyen"
        }

        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return ParseResult.Error("Dosya açılamadı.")
            val extractedText = when (type) {
                "PDF" -> extractPdfText(inputStream)
                "Word" -> extractDocxText(inputStream)
                "Excel" -> extractXlsxText(inputStream)
                "SLIDE" -> extractPptxText(inputStream)
                "TXT" -> extractTxtText(inputStream)
                else -> {
                    // Try txt reader as a fallback
                    extractTxtText(inputStream)
                }
            }
            if (extractedText.isBlank()) {
                ParseResult.Error("Dosyadan anlamlı bir metin okunamadı.")
            } else {
                ParseResult.Success(fileName, extractedText, type)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ParseResult.Error("Hata: ${e.localizedMessage ?: "Dosya okunurken bir hata oluştu."}")
        }
    }

    private fun extractPdfText(inputStream: InputStream): String {
        val builder = StringBuilder()
        var reader: PdfReader? = null
        try {
            reader = PdfReader(inputStream)
            val n = reader.numberOfPages
            for (i in 1..n) {
                builder.append(PdfTextExtractor.getTextFromPage(reader, i)).append("\n")
            }
        } finally {
            reader?.close()
        }
        return builder.toString().trim()
    }

    private fun extractDocxText(inputStream: InputStream): String {
        val zipInputStream = ZipInputStream(inputStream)
        var entry = zipInputStream.nextEntry
        val stringBuilder = StringBuilder()
        while (entry != null) {
            if (entry.name == "word/document.xml") {
                val content = zipInputStream.bufferedReader().readText()
                val regex = Regex("<w:t[^>]*>(.*?)</w:t>")
                val matches = regex.findAll(content)
                for (match in matches) {
                    stringBuilder.append(match.groups[1]?.value).append(" ")
                }
                break
            }
            entry = zipInputStream.nextEntry
        }
        return cleanXmlEntities(stringBuilder.toString())
    }

    private fun extractXlsxText(inputStream: InputStream): String {
        val zipInputStream = ZipInputStream(inputStream)
        var entry = zipInputStream.nextEntry
        val stringBuilder = StringBuilder()
        while (entry != null) {
            if (entry.name == "xl/sharedStrings.xml") {
                val content = zipInputStream.bufferedReader().readText()
                val regex = Regex("<t[^>]*>(.*?)</t>")
                val matches = regex.findAll(content)
                for (match in matches) {
                    stringBuilder.append(match.groups[1]?.value).append("\n")
                }
                break
            }
            entry = zipInputStream.nextEntry
        }
        return cleanXmlEntities(stringBuilder.toString())
    }

    private fun extractPptxText(inputStream: InputStream): String {
        val zipInputStream = ZipInputStream(inputStream)
        var entry = zipInputStream.nextEntry
        val stringBuilder = StringBuilder()
        while (entry != null) {
            if (entry.name.startsWith("ppt/slides/slide") && entry.name.endsWith(".xml")) {
                val content = zipInputStream.bufferedReader().readText()
                val regex = Regex("<a:t[^>]*>(.*?)</a:t>")
                val matches = regex.findAll(content)
                for (match in matches) {
                    stringBuilder.append(match.groups[1]?.value).append(" ")
                }
                stringBuilder.append("\n")
            }
            entry = zipInputStream.nextEntry
        }
        return cleanXmlEntities(stringBuilder.toString())
    }

    private fun extractTxtText(inputStream: InputStream): String {
        return inputStream.bufferedReader().use { it.readText() }.trim()
    }

    private fun cleanXmlEntities(input: String): String {
        return input
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .trim()
    }

    sealed class ParseResult {
        data class Success(val title: String, val content: String, val type: String) : ParseResult()
        data class Error(val message: String) : ParseResult()
    }
}
