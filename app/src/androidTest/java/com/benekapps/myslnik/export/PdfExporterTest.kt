package com.benekapps.myslnik.export

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.benekapps.myslnik.data.db.EmotionRating
import com.benekapps.myslnik.data.db.MoodEntry
import com.benekapps.myslnik.data.db.ThoughtRecord
import com.benekapps.myslnik.domain.TimeRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class PdfExporterTest {
    private val exporter = PdfExporter(ApplicationProvider.getApplicationContext())
    private val day = 24 * 60 * 60 * 1000L
    private val start = 1_760_000_000_000L

    private fun record(i: Int) = ThoughtRecord(
        createdAt = start + i * day,
        updatedAt = start + i * day,
        isDraft = false,
        situation = "Sytuacja numer $i. " + "Długi opis sytuacji, który zawija się w kilku liniach. ".repeat(4),
        emotions = listOf(EmotionRating("anxiety", 80, 40), EmotionRating("custom:żal", 50, 30)),
        automaticThoughts = listOf("Na pewno się pomylę", "Wszyscy to zauważą"),
        hotThoughtIndex = 0,
        distortions = listOf("fortune_telling"),
        evidenceFor = "za",
        evidenceAgainst = "przeciw",
        alternativeThought = "Mogę się pomylić i to nie koniec świata.",
    )

    @Test
    fun writesAValidPdf() {
        val out = ByteArrayOutputStream()
        val pages = exporter.write(
            out,
            PdfExporter.Content(
                range = TimeRange.ALL,
                thoughtRecords = listOf(record(0)),
                moodEntries = (0 until 10).map { MoodEntry(createdAt = start + it * day, updatedAt = 0, rating = it % 11, emotions = listOf("calm"), note = "notatka $it") },
            ),
            generatedAt = start,
        )
        val bytes = out.toByteArray()
        assertTrue(String(bytes.copyOfRange(0, 5), Charsets.US_ASCII) == "%PDF-")
        assertTrue(pages >= 1)
    }

    @Test
    fun longExportsFlowOverPages() {
        val out = ByteArrayOutputStream()
        val pages = exporter.write(
            out,
            PdfExporter.Content(TimeRange.ALL, thoughtRecords = (0 until 30).map(::record), moodEntries = null),
            generatedAt = start,
        )
        assertTrue("Expected several pages, got $pages", pages > 5)
    }

    @Test
    fun emptyExportStillHasOnePage() {
        val out = ByteArrayOutputStream()
        val pages = exporter.write(out, PdfExporter.Content(TimeRange(0, 1), emptyList(), emptyList()), generatedAt = start)
        assertEquals(1, pages)
    }
}
