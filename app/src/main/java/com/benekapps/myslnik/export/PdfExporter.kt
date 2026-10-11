package com.benekapps.myslnik.export

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.StaticLayout
import android.text.TextPaint
import com.benekapps.myslnik.R
import com.benekapps.myslnik.data.db.MoodEntry
import com.benekapps.myslnik.data.db.ThoughtRecord
import com.benekapps.myslnik.data.db.hotThought
import com.benekapps.myslnik.domain.Distortions
import com.benekapps.myslnik.domain.Emotions
import com.benekapps.myslnik.domain.TimeRange
import com.benekapps.myslnik.domain.dailyAverages
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Builds a plain A4 PDF for the user's therapist with Android's own
 * [PdfDocument], so no extra library is needed.
 */
class PdfExporter(private val context: Context) {

    data class Content(
        val range: TimeRange,
        val thoughtRecords: List<ThoughtRecord>?,
        val moodEntries: List<MoodEntry>?,
    )

    /** Writes the PDF and returns the number of pages. */
    fun write(
        out: OutputStream,
        content: Content,
        generatedAt: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Int {
        val res = context.resources
        val doc = PdfDocument()
        try {
            val w = PdfWriter(doc) { page -> res.getString(R.string.pdf_footer, page) }
            val dates = Dates(zone)
            w.text(res.getString(R.string.pdf_title), w.titlePaint, after = 6f)
            w.text(rangeText(res, content, dates), w.bodyPaint, after = 2f)
            w.text(res.getString(R.string.pdf_generated, dates.dateTime(generatedAt)), w.smallPaint, after = 2f)
            w.text(res.getString(R.string.pdf_privacy_note), w.smallPaint, after = 12f)

            content.moodEntries?.let { writeMood(w, res, it, dates, zone) }
            content.thoughtRecords?.let { writeThoughts(w, res, it, dates) }
            w.finish()
            doc.writeTo(out)
            return w.pageCount
        } finally {
            doc.close()
        }
    }

    private fun rangeText(res: Resources, content: Content, dates: Dates): String {
        val times = (content.moodEntries.orEmpty().map { it.createdAt } +
            content.thoughtRecords.orEmpty().map { it.createdAt })
        if (content.range == TimeRange.ALL) {
            if (times.isEmpty()) return res.getString(R.string.pdf_range_all)
            return res.getString(R.string.pdf_range, dates.date(times.min()), dates.date(times.max()))
        }
        return res.getString(R.string.pdf_range, dates.date(content.range.from), dates.date(content.range.to))
    }

    private fun writeMood(w: PdfWriter, res: Resources, entries: List<MoodEntry>, dates: Dates, zone: ZoneId) {
        w.heading(res.getString(R.string.pdf_mood_heading))
        if (entries.isEmpty()) {
            w.text(res.getString(R.string.pdf_no_entries), w.bodyPaint, after = 12f)
            return
        }
        val average = entries.map { it.rating }.average()
        w.text(
            res.getString(R.string.pdf_mood_summary, String.format(POLISH, "%.1f", average), entries.size),
            w.bodyPaint,
            after = 8f,
        )

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
            val line = buildString {
                append(dates.dateTime(e.createdAt)).append("  ·  ").append(e.rating).append("/10")
                if (emotions.isNotEmpty()) append("  ·  ").append(emotions)
            }
            w.text(line, w.bodyPaint, after = if (e.note.isBlank()) 4f else 1f)
            if (e.note.isNotBlank()) w.text(e.note, w.smallPaint, indent = 12f, after = 5f)
        }
        w.space(14f)
    }

    private fun writeThoughts(w: PdfWriter, res: Resources, records: List<ThoughtRecord>, dates: Dates) {
        w.heading(res.getString(R.string.pdf_thoughts_heading))
        if (records.isEmpty()) {
            w.text(res.getString(R.string.pdf_no_entries), w.bodyPaint)
            return
        }
        records.sortedBy { it.createdAt }.forEachIndexed { index, r ->
            if (index > 0) w.rule()
            w.keepTogether(60f)
            w.text(dates.dateTime(r.createdAt), w.subheadingPaint, after = 4f)
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
            color = Color.rgb(47, 111, 115); strokeWidth = 1.6f; style = Paint.Style.STROKE
        }
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(47, 111, 115) }
        val plotLeft = left + 22f
        val plotWidth = width - 22f
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

    private class Dates(private val zone: ZoneId) {
        private val dateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", POLISH)
        private val dateTimeFormat = DateTimeFormatter.ofPattern("EEE, d.MM.yyyy, HH:mm", POLISH)
        fun date(time: Long): String = dateFormat.format(Instant.ofEpochMilli(time).atZone(zone))
        fun dateTime(time: Long): String = dateTimeFormat.format(Instant.ofEpochMilli(time).atZone(zone))
    }

    private companion object {
        val POLISH: Locale = Locale.forLanguageTag("pl-PL")
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
