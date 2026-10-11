package com.benekapps.myslnik.data.backup

import com.benekapps.myslnik.data.db.JsonCodec
import com.benekapps.myslnik.data.db.MoodEntry
import com.benekapps.myslnik.data.db.ThoughtRecord
import org.json.JSONArray
import org.json.JSONObject

data class BackupData(
    val createdAt: Long,
    val thoughtRecords: List<ThoughtRecord>,
    val moodEntries: List<MoodEntry>,
)

/** Plain JSON inside the encrypted backup file. Keep old versions readable. */
object BackupSerializer {
    private const val FORMAT = "myslnik-backup"
    const val VERSION = 1

    class UnsupportedBackupException(message: String) : Exception(message)

    fun toJson(data: BackupData): String = JSONObject()
        .put("format", FORMAT)
        .put("version", VERSION)
        .put("createdAt", data.createdAt)
        .put("thoughtRecords", JSONArray().apply { data.thoughtRecords.forEach { put(thoughtToJson(it)) } })
        .put("moodEntries", JSONArray().apply { data.moodEntries.forEach { put(moodToJson(it)) } })
        .toString()

    fun fromJson(json: String): BackupData {
        val root = JSONObject(json)
        if (root.optString("format") != FORMAT) throw UnsupportedBackupException("Unknown format")
        val version = root.optInt("version", -1)
        if (version < 1 || version > VERSION) throw UnsupportedBackupException("Unsupported version $version")
        val thoughts = root.getJSONArray("thoughtRecords")
        val moods = root.getJSONArray("moodEntries")
        return BackupData(
            createdAt = root.getLong("createdAt"),
            thoughtRecords = List(thoughts.length()) { jsonToThought(thoughts.getJSONObject(it)) },
            moodEntries = List(moods.length()) { jsonToMood(moods.getJSONObject(it)) },
        )
    }

    private fun thoughtToJson(r: ThoughtRecord) = JSONObject()
        .put("id", r.id)
        .put("createdAt", r.createdAt)
        .put("updatedAt", r.updatedAt)
        .put("isDraft", r.isDraft)
        .put("draftStep", r.draftStep)
        .put("situation", r.situation)
        .put("emotions", JsonCodec.emotionsToJson(r.emotions))
        .put("automaticThoughts", JsonCodec.stringsToJson(r.automaticThoughts))
        .apply { r.hotThoughtIndex?.let { put("hotThoughtIndex", it) } }
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
        hotThoughtIndex = if (o.has("hotThoughtIndex") && !o.isNull("hotThoughtIndex")) o.getInt("hotThoughtIndex") else null,
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
}
