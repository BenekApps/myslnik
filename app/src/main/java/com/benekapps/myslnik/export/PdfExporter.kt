package com.benekapps.myslnik.export

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.StaticLayout
import android.text.TextPaint
import com.benekapps.myslnik.R
import com.benekapps.myslnik.data.db.BehavioralExperiment
import com.benekapps.myslnik.data.db.ExposureAttempt
import com.benekapps.myslnik.data.db.MoodEntry
import com.benekapps.myslnik.data.db.PlannedActivity
import com.benekapps.myslnik.data.db.ProblemSolving
import com.benekapps.myslnik.data.db.SleepEntry
import com.benekapps.myslnik.data.db.ThoughtRecord
import com.benekapps.myslnik.data.db.chosenSolution
import com.benekapps.myslnik.data.db.hotThought
import com.benekapps.myslnik.domain.Distortions
import com.benekapps.myslnik.domain.Emotions
import com.benekapps.myslnik.domain.SleepMath
import com.benekapps.myslnik.domain.TimeRange
import com.benekapps.myslnik.domain.appLocale
import com.benekapps.myslnik.domain.dailyAverages
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Builds a plain A4 PDF for the user's therapist with Android's own
 * [PdfDocument], so no extra library is needed. Texts come from the
 * [Resources] passed in, so the PDF is in the app's current language.
 */
class PdfExporter {

    /** One exposure attempt with the names of its ladder and step. */
    data class ExposureRow(val ladder: String, val step: String, val attempt: ExposureAttempt)

    /** Null lists are left out of the PDF; empty ones show "no entries". */
    data class Content(
        val range: TimeRange,
        val thoughtRecords: List<ThoughtRecord>? = null,
        val moodEntries: List<MoodEntry>? = null,
        val activities: List<PlannedActivity>? = null,
        val sleepEntries: List<SleepEntry>? = null,
        val exposure: List<ExposureRow>? = null,
        val experiments: List<BehavioralExperiment>? = null,
        val problems: List<ProblemSolving>? = null,
    )

    /** Writes the PDF and returns the number of pages. */
    fun write(
        out: OutputStream,
        content: Content,
        generatedAt: Long,
        res: Resources,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Int {
        val doc = PdfDocument()
        try {
            val w = PdfWriter(doc) { page -> res.getString(R.string.pdf_footer, page) }
            val f = Formats(res.appLocale(), zone)
            w.text(res.getString(R.string.pdf_title), w.titlePaint, after = 6f)
            w.text(rangeText(res, content, f, zone), w.bodyPaint, after = 2f)
            w.text(res.getString(R.string.pdf_generated, f.dateTime(generatedAt)), w.smallPaint, after = 2f)
            w.text(res.getString(R.string.pdf_privacy_note), w.smallPaint, after = 12f)

            content.moodEntries?.let { writeMood(w, res, it, f, zone) }
            content.thoughtRecords?.let { writeThoughts(w, res, it, f) }
            content.activities?.let { writeActivities(w, res, it, f) }
            content.sleepEntries?.let { writeSleep(w, res, it, f) }
            content.exposure?.let { writeExposure(w, res, it, f) }
            content.experiments?.let { writeExperiments(w, res, it, f) }
            content.problems?.let { writeProblems(w, res, it, f) }
            w.finish()
            doc.writeTo(out)
            return w.pageCount
        } finally {
            doc.close()
        }
    }

    private fun rangeText(res: Resources, content: Content, f: Formats, zone: ZoneId): String {
        if (content.range != TimeRange.ALL) {
            return res.getString(R.string.pdf_range, f.date(content.range.from), f.date(content.range.to))
        }
        fun dayStart(day: Long) = LocalDate.ofEpochDay(day).atStartOfDay(zone).toInstant().toEpochMilli()
        val times = content.moodEntries.orEmpty().map { it.createdAt } +
            content.thoughtRecords.orEmpty().map { it.createdAt } +
            content.activities.orEmpty().map { dayStart(it.day) } +
            content.sleepEntries.orEmpty().map { dayStart(it.night) } +
            content.exposure.orEmpty().map { it.attempt.createdAt } +
            content.experiments.orEmpty().map { it.createdAt } +
            content.problems.orEmpty().map { it.createdAt }
        if (times.isEmpty()) return res.getString(R.string.pdf_range_all)
        return res.getString(R.string.pdf_range, f.date(times.min()), f.date(times.max()))
    }

    private fun writeMood(w: PdfWriter, res: Resources, entries: List<MoodEntry>, f: Formats, zone: ZoneId) {
        w.heading(res.getString(R.string.pdf_mood_heading))
        if (entries.isEmpty()) return w.empty(res)
        val average = entries.map { it.rating }.average()
        w.text(res.getString(R.string.pdf_mood_summary, f.decimal(average), entries.size), w.bodyPaint, after = 8f)

        val first = Instant.ofEpochMilli(entries.minOf { it.createdAt }).atZone(zone).toLocalDate()
        val last = Instant.ofEpochMilli(entries.maxOf { it.createdAt }).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(first, last).toInt() + 1
        if (days >= 2) {
            val lastMillis = last.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
            val points = dailyAverages(entries.map { it.createdAt to it.rating }, days, lastMillis, zone)
            w.block(CHART_HEIGHT + 20f) { canvas, left, top, width ->
                drawMoodChart(canvas, left, top, width, CHART_HEIGHT, points.map { it.second }, w.smallPaint)
            }
        }
        entries.sortedBy { it.createdAt }.forEach { e ->
            val emotions = e.emotions.joinToString(", ") { Emotions.label(res, it) }
            val line = listOf(f.dateTime(e.createdAt), "${e.rating}/10", emotions).filter { it.isNotEmpty() }.joinToString("  ·  ")
            w.text(line, w.bodyPaint, after = if (e.note.isBlank()) 4f else 1f)
            if (e.note.isNotBlank()) w.text(e.note, w.smallPaint, indent = 12f, after = 5f)
        }
        w.space(14f)
    }

    private fun writeThoughts(w: PdfWriter, res: Resources, records: List<ThoughtRecord>, f: Formats) {
        w.heading(res.getString(R.string.pdf_thoughts_heading))
        if (records.isEmpty()) return w.empty(res)
        records.sortedBy { it.createdAt }.forEachIndexed { index, r ->
            if (index > 0) w.rule()
            w.keepTogether(60f)
            w.text(f.dateTime(r.createdAt), w.subheadingPaint, after = 4f)
            field(w, res.getString(R.string.tr_field_situation), r.situation)
            if (r.emotions.isNotEmpty()) {
                w.text(res.getString(R.string.tr_field_emotions), w.labelPaint, after = 1f)
                r.emotions.forEach { e ->
                    val after = e.intensityAfter?.let { " → $it" } ?: ""
                    w.text("${Emotions.label(res, e.emotion)}: ${e.intensityBefore}$after", w.bodyPaint, indent = 12f, after = 1f)
                }
                w.space(4f)
            }
            if (r.automaticThoughts.isNotEmpty()) {
                val hot = r.hotThought()
                w.text(res.getString(R.string.tr_field_thoughts), w.labelPaint, after = 1f)
                r.automaticThoughts.forEach { t ->
                    val marker = if (t == hot) " " + res.getString(R.string.pdf_hot_thought_marker) else ""
                    w.text("• $t$marker", w.bodyPaint, indent = 12f, after = 1f)
                }
                w.space(4f)
            }
            if (r.distortions.isNotEmpty()) {
                field(w, res.getString(R.string.tr_field_distortions), r.distortions.joinToString(", ") { Distortions.name(res, it) })
            }
            field(w, res.getString(R.string.tr_field_evidence_for), r.evidenceFor)
            field(w, res.getString(R.string.tr_field_evidence_against), r.evidenceAgainst)
            field(w, res.getString(R.string.tr_field_alternative), r.alternativeThought)
        }
        w.space(14f)
    }

    private fun writeActivities(w: PdfWriter, res: Resources, activities: List<PlannedActivity>, f: Formats) {
        w.heading(res.getString(R.string.activities_title))
        if (activities.isEmpty()) return w.empty(res)
        val done = activities.filter { it.done }
        val pleasure = done.mapNotNull { it.pleasure }.takeIf { it.isNotEmpty() }?.average()
        val mastery = done.mapNotNull { it.mastery }.takeIf { it.isNotEmpty() }?.average()
        w.text(res.getString(R.string.pdf_activities_summary, done.size, activities.size), w.bodyPaint, after = 2f)
        if (pleasure != null && mastery != null) {
            w.text(res.getString(R.string.activities_average, f.decimal(pleasure), f.decimal(mastery)), w.bodyPaint, after = 8f)
        }
        activities.groupBy { it.day }.toSortedMap().forEach { (day, items) ->
            w.keepTogether(40f)
            w.text(f.day(LocalDate.ofEpochDay(day)), w.subheadingPaint, after = 2f)
            items.forEach { a ->
                val parts = buildList {
                    a.timeMinutes?.let { add(f.clock(it)) }
                    add(a.title)
                    add(res.getString(if (a.done) R.string.pdf_done else R.string.pdf_not_done))
                    a.pleasure?.let { add(res.getString(R.string.activities_pleasure_value, it)) }
                    a.mastery?.let { add(res.getString(R.string.activities_mastery_value, it)) }
                }
                w.text(parts.joinToString("  ·  "), w.bodyPaint, indent = 12f, after = if (a.note.isBlank()) 2f else 1f)
                if (a.note.isNotBlank()) w.text(a.note, w.smallPaint, indent = 24f, after = 3f)
            }
            w.space(4f)
        }
        w.space(10f)
    }

    private fun writeSleep(w: PdfWriter, res: Resources, entries: List<SleepEntry>, f: Formats) {
        w.heading(res.getString(R.string.sleep_title))
        if (entries.isEmpty()) return w.empty(res)
        SleepMath.summary(entries)?.let { s ->
            w.text(
                res.getString(R.string.pdf_sleep_summary, s.nights, f.duration(res, s.averageSleepMinutes), s.averageEfficiency ?: 0),
                w.bodyPaint,
                after = 8f,
            )
        }
        entries.sortedBy { it.night }.forEach { e ->
            w.keepTogether(40f)
            w.text(res.getString(R.string.sleep_night_of, f.day(LocalDate.ofEpochDay(e.night))), w.subheadingPaint, after = 1f)
            val line = res.getString(
                R.string.pdf_sleep_line,
                f.clock(SleepMath.toMinuteOfDay(e.inBed)),
                f.clock(SleepMath.toMinuteOfDay(e.outOfBed)),
                f.duration(res, SleepMath.totalSleep(e)),
                SleepMath.efficiency(e) ?: 0,
                e.minutesToFallAsleep,
                e.awakenings,
                e.minutesAwake,
                e.quality,
            )
            w.text(line, w.bodyPaint, indent = 12f, after = if (e.note.isBlank()) 4f else 1f)
            if (e.note.isNotBlank()) w.text(e.note, w.smallPaint, indent = 12f, after = 5f)
        }
        w.space(10f)
    }

    private fun writeExposure(w: PdfWriter, res: Resources, rows: List<ExposureRow>, f: Formats) {
        w.heading(res.getString(R.string.exposure_title))
        if (rows.isEmpty()) return w.empty(res)
        rows.groupBy { it.ladder }.forEach { (ladder, ladderRows) ->
            w.keepTogether(40f)
            w.text(ladder, w.subheadingPaint, after = 2f)
            ladderRows.groupBy { it.step }.forEach { (step, stepRows) ->
                w.text(step, w.labelPaint, indent = 12f, after = 1f)
                stepRows.sortedBy { it.attempt.createdAt }.forEach { r ->
                    val a = r.attempt
                    w.text(
                        "${f.dateTime(a.createdAt)}  ·  " + res.getString(R.string.exposure_attempt_values, a.anxietyBefore, a.anxietyAfter),
                        w.bodyPaint,
                        indent = 24f,
                        after = if (a.note.isBlank()) 1f else 0f,
                    )
                    if (a.note.isNotBlank()) w.text(a.note, w.smallPaint, indent = 36f, after = 2f)
                }
                w.space(4f)
            }
        }
        w.space(10f)
    }

    private fun writeExperiments(w: PdfWriter, res: Resources, experiments: List<BehavioralExperiment>, f: Formats) {
        w.heading(res.getString(R.string.experiments_title))
        if (experiments.isEmpty()) return w.empty(res)
        experiments.sortedBy { it.createdAt }.forEachIndexed { index, e ->
            if (index > 0) w.rule()
            w.keepTogether(60f)
            w.text(f.dateTime(e.createdAt), w.subheadingPaint, after = 4f)
            field(w, res.getString(R.string.experiment_belief), e.belief)
            e.beliefBefore?.let { field(w, res.getString(R.string.experiment_belief_before), "$it%") }
            field(w, res.getString(R.string.experiment_plan), e.plan)
            field(w, res.getString(R.string.experiment_prediction), e.prediction)
            field(w, res.getString(R.string.experiment_obstacles), e.obstacles)
            field(w, res.getString(R.string.experiment_outcome), e.outcome)
            field(w, res.getString(R.string.experiment_learned), e.learned)
            e.beliefAfter?.let { field(w, res.getString(R.string.experiment_belief_after), "$it%") }
        }
        w.space(14f)
    }

    private fun writeProblems(w: PdfWriter, res: Resources, problems: List<ProblemSolving>, f: Formats) {
        w.heading(res.getString(R.string.problems_title))
        if (problems.isEmpty()) return w.empty(res)
        problems.sortedBy { it.createdAt }.forEachIndexed { index, p ->
            if (index > 0) w.rule()
            w.keepTogether(60f)
            w.text(f.dateTime(p.createdAt), w.subheadingPaint, after = 4f)
            field(w, res.getString(R.string.problem_field_problem), p.problem)
            if (p.solutions.isNotEmpty()) {
                w.text(res.getString(R.string.problem_field_solutions), w.labelPaint, after = 1f)
                p.solutions.forEach { s ->
                    w.text("• ${s.text}", w.bodyPaint, indent = 12f, after = 1f)
                    if (s.pros.isNotBlank()) w.text(res.getString(R.string.problem_pros_value, s.pros), w.smallPaint, indent = 24f, after = 1f)
                    if (s.cons.isNotBlank()) w.text(res.getString(R.string.problem_cons_value, s.cons), w.smallPaint, indent = 24f, after = 1f)
                }
                w.space(4f)
            }
            p.chosenSolution()?.let { field(w, res.getString(R.string.problem_field_chosen), it.text) }
            field(w, res.getString(R.string.problem_field_plan), p.plan)
            field(w, res.getString(R.string.problem_field_review), p.review)
        }
        w.space(14f)
    }

    private fun PdfWriter.empty(res: Resources) {
        text(res.getString(R.string.pdf_no_entries), bodyPaint, after = 14f)
    }

    private fun field(w: PdfWriter, label: String, value: String) {
        if (value.isBlank()) return
        w.text(label, w.labelPaint, after = 1f)
        w.text(value.trim(), w.bodyPaint, indent = 12f, after = 6f)
    }

    private fun drawMoodChart(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        values: List<Double?>,
        labelPaint: TextPaint,
    ) {
        val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(190, 190, 190); strokeWidth = 0.6f }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(43, 106, 110); strokeWidth = 1.6f; style = Paint.Style.STROKE
        }
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(43, 106, 110) }
        val plotLeft = left + 22f
        val plotWidth = width - 26f
        fun yFor(v: Double) = (top + height - (v / 10.0) * height).toFloat()
        listOf(0, 5, 10).forEach { v ->
            val y = yFor(v.toDouble())
            canvas.drawLine(plotLeft, y, plotLeft + plotWidth, y, axisPaint)
            canvas.drawText(v.toString(), left, y + 3f, labelPaint)
        }
        val step = if (values.size > 1) plotWidth / (values.size - 1) else 0f
        var previous: Pair<Float, Float>? = null
        values.forEachIndexed { i, v ->
            if (v == null) {
                previous = null
                return@forEachIndexed
            }
            val point = plotLeft + i * step to yFor(v)
            previous?.let { canvas.drawLine(it.first, it.second, point.first, point.second, linePaint) }
            if (values.size <= 62) canvas.drawCircle(point.first, point.second, 2f, dotPaint)
            previous = point
        }
    }

    private class Formats(private val locale: Locale, private val zone: ZoneId) {
        private val dateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", locale)
        private val dateTimeFormat = DateTimeFormatter.ofPattern("EEE, d.MM.yyyy, HH:mm", locale)
        private val dayFormat = DateTimeFormatter.ofPattern("EEEE, d.MM.yyyy", locale)
        fun date(time: Long): String = dateFormat.format(Instant.ofEpochMilli(time).atZone(zone))
        fun dateTime(time: Long): String = dateTimeFormat.format(Instant.ofEpochMilli(time).atZone(zone))
        fun day(date: LocalDate): String = dayFormat.format(date)
        fun clock(minuteOfDay: Int) = "%02d:%02d".format(minuteOfDay / 60 % 24, minuteOfDay % 60)
        fun decimal(value: Double): String = String.format(locale, "%.1f", value)
        fun duration(res: Resources, minutes: Int) = res.getString(R.string.duration_hm, minutes / 60, minutes % 60)
    }

    private companion object {
        const val CHART_HEIGHT = 110f
    }
}

/** Simple top-to-bottom text flow over A4 pages. */
private class PdfWriter(
    private val doc: PdfDocument,
    private val footer: (Int) -> String,
) {
    private val pageWidth = 595
    private val pageHeight = 842
    private val margin = 48f
    private val contentWidth = pageWidth - 2 * margin
    private val bottom = pageHeight - margin - 14f

    private var page: PdfDocument.Page? = null
    private var y = margin
    var pageCount = 0
        private set

    val titlePaint = paint(20f, bold = true)
    val headingPaint = paint(15f, bold = true)
    val subheadingPaint = paint(12f, bold = true)
    val labelPaint = paint(10.5f, bold = true, color = Color.rgb(60, 60, 60))
    val bodyPaint = paint(10.5f)
    val smallPaint = paint(9f, color = Color.rgb(90, 90, 90))

    private fun paint(size: Float, bold: Boolean = false, color: Int = Color.BLACK) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

    private fun canvas(): Canvas {
        if (page == null) newPage()
        return page!!.canvas
    }

    private fun newPage() {
        page?.let { finishPage(it) }
        pageCount++
        page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageCount).create())
        y = margin
    }

    private fun finishPage(p: PdfDocument.Page) {
        p.canvas.drawText(footer(pageCount), margin, pageHeight - margin + 10f, smallPaint)
        doc.finishPage(p)
    }

    fun text(text: String, paint: TextPaint, indent: Float = 0f, after: Float = 4f) {
        canvas()
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, (contentWidth - indent).toInt())
            .setLineSpacing(0f, 1.15f)
            .build()
        for (i in 0 until layout.lineCount) {
            val height = (layout.getLineBottom(i) - layout.getLineTop(i)).toFloat()
            if (y + height > bottom) newPage()
            val baseline = y + (layout.getLineBaseline(i) - layout.getLineTop(i))
            canvas().drawText(text, layout.getLineStart(i), layout.getLineVisibleEnd(i), margin + indent, baseline, paint)
            y += height
        }
        y += after
    }

    fun heading(text: String) {
        keepTogether(80f)
        text(text, headingPaint, after = 6f)
    }

    fun keepTogether(height: Float) {
        canvas()
        if (y + height > bottom) newPage()
    }

    fun block(height: Float, draw: (Canvas, Float, Float, Float) -> Unit) {
        keepTogether(height)
        draw(canvas(), margin, y, contentWidth)
        y += height
    }

    fun rule() {
        keepTogether(16f)
        y += 6f
        canvas().drawLine(margin, y, margin + contentWidth, y, Paint().apply { color = Color.rgb(210, 210, 210) })
        y += 10f
    }

    fun space(height: Float) {
        y += height
    }

    fun finish() {
        page?.let { finishPage(it) }
        page = null
    }
}
