package com.benekapps.myslnik.data.backup

import com.benekapps.myslnik.data.db.BehavioralExperiment
import com.benekapps.myslnik.data.db.ExposureAttempt
import com.benekapps.myslnik.data.db.ExposureLadder
import com.benekapps.myslnik.data.db.ExposureStep
import com.benekapps.myslnik.data.db.JsonCodec
import com.benekapps.myslnik.data.db.MoodEntry
import com.benekapps.myslnik.data.db.PlannedActivity
import com.benekapps.myslnik.data.db.ProblemSolving
import com.benekapps.myslnik.data.db.SleepEntry
import com.benekapps.myslnik.data.db.ThoughtRecord
import com.benekapps.myslnik.data.db.Worry
import com.benekapps.myslnik.data.db.WorryStatus
import org.json.JSONArray
import org.json.JSONObject

data class BackupData(
    val createdAt: Long,
    val thoughtRecords: List<ThoughtRecord>,
    val moodEntries: List<MoodEntry>,
    // Version 2 (1.3):
    val activities: List<PlannedActivity> = emptyList(),
    val sleepEntries: List<SleepEntry> = emptyList(),
    val exposureLadders: List<ExposureLadder> = emptyList(),
    val exposureSteps: List<ExposureStep> = emptyList(),
    val exposureAttempts: List<ExposureAttempt> = emptyList(),
    val experiments: List<BehavioralExperiment> = emptyList(),
    val worries: List<Worry> = emptyList(),
    val problems: List<ProblemSolving> = emptyList(),
)

/**
 * Plain JSON inside the encrypted backup file. Older versions must stay
 * readable: version 1 (1.0) had only thought records and mood entries.
 */
object BackupSerializer {
    private const val FORMAT = "myslnik-backup"
    const val VERSION = 2

    class UnsupportedBackupException(message: String) : Exception(message)

    fun toJson(data: BackupData): String = JSONObject()
        .put("format", FORMAT)
        .put("version", VERSION)
        .put("createdAt", data.createdAt)
        .put("thoughtRecords", data.thoughtRecords.toJsonArray(::thoughtToJson))
        .put("moodEntries", data.moodEntries.toJsonArray(::moodToJson))
        .put("activities", data.activities.toJsonArray(::activityToJson))
        .put("sleepEntries", data.sleepEntries.toJsonArray(::sleepToJson))
        .put("exposureLadders", data.exposureLadders.toJsonArray(::ladderToJson))
        .put("exposureSteps", data.exposureSteps.toJsonArray(::stepToJson))
        .put("exposureAttempts", data.exposureAttempts.toJsonArray(::attemptToJson))
        .put("experiments", data.experiments.toJsonArray(::experimentToJson))
        .put("worries", data.worries.toJsonArray(::worryToJson))
        .put("problems", data.problems.toJsonArray(::problemToJson))
        .toString()

    fun fromJson(json: String): BackupData {
        val root = JSONObject(json)
        if (root.optString("format") != FORMAT) throw UnsupportedBackupException("Unknown format")
        val version = root.optInt("version", -1)
        if (version < 1 || version > VERSION) throw UnsupportedBackupException("Unsupported version $version")
        return BackupData(
            createdAt = root.getLong("createdAt"),
            thoughtRecords = root.list("thoughtRecords", ::jsonToThought),
            moodEntries = root.list("moodEntries", ::jsonToMood),
            activities = root.list("activities", ::jsonToActivity),
            sleepEntries = root.list("sleepEntries", ::jsonToSleep),
            exposureLadders = root.list("exposureLadders", ::jsonToLadder),
            exposureSteps = root.list("exposureSteps", ::jsonToStep),
            exposureAttempts = root.list("exposureAttempts", ::jsonToAttempt),
            experiments = root.list("experiments", ::jsonToExperiment),
            worries = root.list("worries", ::jsonToWorry),
            problems = root.list("problems", ::jsonToProblem),
        )
    }

    private fun <T> List<T>.toJsonArray(encode: (T) -> JSONObject) = JSONArray().apply { forEach { put(encode(it)) } }

    /** Missing arrays (older backups) read as empty. */
    private fun <T> JSONObject.list(name: String, decode: (JSONObject) -> T): List<T> {
        val array = optJSONArray(name) ?: return emptyList()
        return List(array.length()) { decode(array.getJSONObject(it)) }
    }

    private fun JSONObject.optIntOrNull(name: String): Int? = if (has(name) && !isNull(name)) getInt(name) else null

    private fun JSONObject.putIfNotNull(name: String, value: Any?): JSONObject = apply { if (value != null) put(name, value) }

    private fun thoughtToJson(r: ThoughtRecord) = JSONObject()
        .put("id", r.id)
        .put("createdAt", r.createdAt)
        .put("updatedAt", r.updatedAt)
        .put("isDraft", r.isDraft)
        .put("draftStep", r.draftStep)
        .put("situation", r.situation)
        .put("emotions", JsonCodec.emotionsToJson(r.emotions))
        .put("automaticThoughts", JsonCodec.stringsToJson(r.automaticThoughts))
        .putIfNotNull("hotThoughtIndex", r.hotThoughtIndex)
        .put("distortions", JsonCodec.stringsToJson(r.distortions))
        .put("evidenceFor", r.evidenceFor)
        .put("evidenceAgainst", r.evidenceAgainst)
        .put("alternativeThought", r.alternativeThought)

    private fun jsonToThought(o: JSONObject) = ThoughtRecord(
        id = o.getLong("id"),
        createdAt = o.getLong("createdAt"),
        updatedAt = o.getLong("updatedAt"),
        isDraft = o.getBoolean("isDraft"),
        draftStep = o.optInt("draftStep", 0),
        situation = o.optString("situation"),
        emotions = JsonCodec.jsonToEmotions(o.optJSONArray("emotions") ?: JSONArray()),
        automaticThoughts = JsonCodec.jsonToStrings(o.optJSONArray("automaticThoughts") ?: JSONArray()),
        hotThoughtIndex = o.optIntOrNull("hotThoughtIndex"),
        distortions = JsonCodec.jsonToStrings(o.optJSONArray("distortions") ?: JSONArray()),
        evidenceFor = o.optString("evidenceFor"),
        evidenceAgainst = o.optString("evidenceAgainst"),
        alternativeThought = o.optString("alternativeThought"),
    )

    private fun moodToJson(m: MoodEntry) = JSONObject()
        .put("id", m.id)
        .put("createdAt", m.createdAt)
        .put("updatedAt", m.updatedAt)
        .put("rating", m.rating)
        .put("emotions", JsonCodec.stringsToJson(m.emotions))
        .put("note", m.note)

    private fun jsonToMood(o: JSONObject) = MoodEntry(
        id = o.getLong("id"),
        createdAt = o.getLong("createdAt"),
        updatedAt = o.getLong("updatedAt"),
        rating = o.getInt("rating"),
        emotions = JsonCodec.jsonToStrings(o.optJSONArray("emotions") ?: JSONArray()),
        note = o.optString("note"),
    )

    private fun activityToJson(a: PlannedActivity) = JSONObject()
        .put("id", a.id)
        .put("createdAt", a.createdAt)
        .put("updatedAt", a.updatedAt)
        .put("day", a.day)
        .putIfNotNull("timeMinutes", a.timeMinutes)
        .put("title", a.title)
        .put("note", a.note)
        .put("done", a.done)
        .putIfNotNull("pleasure", a.pleasure)
        .putIfNotNull("mastery", a.mastery)

    private fun jsonToActivity(o: JSONObject) = PlannedActivity(
        id = o.getLong("id"),
        createdAt = o.getLong("createdAt"),
        updatedAt = o.getLong("updatedAt"),
        day = o.getLong("day"),
        timeMinutes = o.optIntOrNull("timeMinutes"),
        title = o.getString("title"),
        note = o.optString("note"),
        done = o.optBoolean("done"),
        pleasure = o.optIntOrNull("pleasure"),
        mastery = o.optIntOrNull("mastery"),
    )

    private fun sleepToJson(e: SleepEntry) = JSONObject()
        .put("id", e.id)
        .put("createdAt", e.createdAt)
        .put("updatedAt", e.updatedAt)
        .put("night", e.night)
        .put("inBed", e.inBed)
        .put("triedToSleep", e.triedToSleep)
        .put("minutesToFallAsleep", e.minutesToFallAsleep)
        .put("awakenings", e.awakenings)
        .put("minutesAwake", e.minutesAwake)
        .put("finalWake", e.finalWake)
        .put("outOfBed", e.outOfBed)
        .put("quality", e.quality)
        .put("note", e.note)

    private fun jsonToSleep(o: JSONObject) = SleepEntry(
        id = o.getLong("id"),
        createdAt = o.getLong("createdAt"),
        updatedAt = o.getLong("updatedAt"),
        night = o.getLong("night"),
        inBed = o.getInt("inBed"),
        triedToSleep = o.getInt("triedToSleep"),
        minutesToFallAsleep = o.getInt("minutesToFallAsleep"),
        awakenings = o.getInt("awakenings"),
        minutesAwake = o.getInt("minutesAwake"),
        finalWake = o.getInt("finalWake"),
        outOfBed = o.getInt("outOfBed"),
        quality = o.getInt("quality"),
        note = o.optString("note"),
    )

    private fun ladderToJson(l: ExposureLadder) = JSONObject()
        .put("id", l.id)
        .put("createdAt", l.createdAt)
        .put("updatedAt", l.updatedAt)
        .put("title", l.title)
        .put("goal", l.goal)

    private fun jsonToLadder(o: JSONObject) = ExposureLadder(
        id = o.getLong("id"),
        createdAt = o.getLong("createdAt"),
        updatedAt = o.getLong("updatedAt"),
        title = o.getString("title"),
        goal = o.optString("goal"),
    )

    private fun stepToJson(s: ExposureStep) = JSONObject()
        .put("id", s.id)
        .put("ladderId", s.ladderId)
        .put("createdAt", s.createdAt)
        .put("updatedAt", s.updatedAt)
        .put("description", s.description)
        .put("expectedAnxiety", s.expectedAnxiety)

    private fun jsonToStep(o: JSONObject) = ExposureStep(
        id = o.getLong("id"),
        ladderId = o.getLong("ladderId"),
        createdAt = o.getLong("createdAt"),
        updatedAt = o.getLong("updatedAt"),
        description = o.getString("description"),
        expectedAnxiety = o.getInt("expectedAnxiety"),
    )

    private fun attemptToJson(a: ExposureAttempt) = JSONObject()
        .put("id", a.id)
        .put("stepId", a.stepId)
        .put("createdAt", a.createdAt)
        .put("updatedAt", a.updatedAt)
        .put("anxietyBefore", a.anxietyBefore)
        .put("anxietyAfter", a.anxietyAfter)
        .put("note", a.note)

    private fun jsonToAttempt(o: JSONObject) = ExposureAttempt(
        id = o.getLong("id"),
        stepId = o.getLong("stepId"),
        createdAt = o.getLong("createdAt"),
        updatedAt = o.getLong("updatedAt"),
        anxietyBefore = o.getInt("anxietyBefore"),
        anxietyAfter = o.getInt("anxietyAfter"),
        note = o.optString("note"),
    )

    private fun experimentToJson(e: BehavioralExperiment) = JSONObject()
        .put("id", e.id)
        .put("createdAt", e.createdAt)
        .put("updatedAt", e.updatedAt)
        .put("belief", e.belief)
        .putIfNotNull("beliefBefore", e.beliefBefore)
        .put("plan", e.plan)
        .put("prediction", e.prediction)
        .put("obstacles", e.obstacles)
        .put("outcome", e.outcome)
        .put("learned", e.learned)
        .putIfNotNull("beliefAfter", e.beliefAfter)

    private fun jsonToExperiment(o: JSONObject) = BehavioralExperiment(
        id = o.getLong("id"),
        createdAt = o.getLong("createdAt"),
        updatedAt = o.getLong("updatedAt"),
        belief = o.getString("belief"),
        beliefBefore = o.optIntOrNull("beliefBefore"),
        plan = o.optString("plan"),
        prediction = o.optString("prediction"),
        obstacles = o.optString("obstacles"),
        outcome = o.optString("outcome"),
        learned = o.optString("learned"),
        beliefAfter = o.optIntOrNull("beliefAfter"),
    )

    private fun worryToJson(w: Worry) = JSONObject()
        .put("id", w.id)
        .put("createdAt", w.createdAt)
        .put("updatedAt", w.updatedAt)
        .put("text", w.text)
        .put("status", w.status.name)

    private fun jsonToWorry(o: JSONObject) = Worry(
        id = o.getLong("id"),
        createdAt = o.getLong("createdAt"),
        updatedAt = o.getLong("updatedAt"),
        text = o.getString("text"),
        status = runCatching { WorryStatus.valueOf(o.optString("status")) }.getOrDefault(WorryStatus.OPEN),
    )

    private fun problemToJson(p: ProblemSolving) = JSONObject()
        .put("id", p.id)
        .put("createdAt", p.createdAt)
        .put("updatedAt", p.updatedAt)
        .put("isDraft", p.isDraft)
        .put("draftStep", p.draftStep)
        .put("problem", p.problem)
        .put("solutions", JsonCodec.solutionsToJson(p.solutions))
        .putIfNotNull("chosenIndex", p.chosenIndex)
        .put("plan", p.plan)
        .put("review", p.review)

    private fun jsonToProblem(o: JSONObject) = ProblemSolving(
        id = o.getLong("id"),
        createdAt = o.getLong("createdAt"),
        updatedAt = o.getLong("updatedAt"),
        isDraft = o.optBoolean("isDraft", true),
        draftStep = o.optInt("draftStep", 0),
        problem = o.optString("problem"),
        solutions = JsonCodec.jsonToSolutions(o.optJSONArray("solutions") ?: JSONArray()),
        chosenIndex = o.optIntOrNull("chosenIndex"),
        plan = o.optString("plan"),
        review = o.optString("review"),
    )
}
