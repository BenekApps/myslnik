package com.benekapps.myslnik.export

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.benekapps.myslnik.data.db.BehavioralExperiment
import com.benekapps.myslnik.data.db.EmotionRating
import com.benekapps.myslnik.data.db.ExposureAttempt
import com.benekapps.myslnik.data.db.MoodEntry
import com.benekapps.myslnik.data.db.PlannedActivity
import com.benekapps.myslnik.data.db.ProblemSolving
import com.benekapps.myslnik.data.db.SleepEntry
import com.benekapps.myslnik.data.db.SolutionOption
import com.benekapps.myslnik.data.db.ThoughtRecord
import com.benekapps.myslnik.domain.TimeRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class PdfExporterTest {
    private val exporter = PdfExporter()
    private val res = ApplicationProvider.getApplicationContext<android.content.Context>().resources
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
            res = res,
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
            res = res,
        )
        assertTrue("Expected several pages, got $pages", pages > 5)
    }

    @Test
    fun everyModuleFitsInOnePdf() {
        val out = ByteArrayOutputStream()
        val pages = exporter.write(
            out,
            PdfExporter.Content(
                range = TimeRange.ALL,
                thoughtRecords = listOf(record(0)),
                moodEntries = listOf(MoodEntry(createdAt = start, updatedAt = start, rating = 5)),
                activities = listOf(PlannedActivity(createdAt = start, updatedAt = start, day = 20_000, timeMinutes = 480, title = "Spacer", done = true, pleasure = 6, mastery = 5)),
                sleepEntries = listOf(SleepEntry(createdAt = start, updatedAt = start, night = 20_000, inBed = 660, triedToSleep = 675, minutesToFallAsleep = 15, awakenings = 1, minutesAwake = 10, finalWake = 1110, outOfBed = 1125, quality = 3)),
                exposure = listOf(PdfExporter.ExposureRow("Autobus", "Jeden przystanek", ExposureAttempt(stepId = 1, createdAt = start, updatedAt = start, anxietyBefore = 60, anxietyAfter = 30))),
                experiments = listOf(BehavioralExperiment(createdAt = start, updatedAt = start, belief = "Wyśmieją mnie", beliefBefore = 80, beliefAfter = 30)),
                problems = listOf(ProblemSolving(createdAt = start, updatedAt = start, isDraft = false, problem = "Rachunki", solutions = listOf(SolutionOption("Budżet", "porządek", "czas")), chosenIndex = 0)),
            ),
            generatedAt = start,
            res = res,
        )
        assertTrue(pages >= 1 && out.size() > 2_000)
    }

    @Test
    fun emptyExportStillHasOnePage() {
        val out = ByteArrayOutputStream()
        val pages = exporter.write(out, PdfExporter.Content(TimeRange(0, 1), emptyList(), emptyList()), generatedAt = start, res = res)
        assertEquals(1, pages)
    }
}
