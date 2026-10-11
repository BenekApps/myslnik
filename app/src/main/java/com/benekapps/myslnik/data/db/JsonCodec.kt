package com.benekapps.myslnik.data.db

import org.json.JSONArray
import org.json.JSONObject

/** JSON encoding of list columns, shared by Room converters and backups. */
object JsonCodec {

    fun stringsToJson(values: List<String>): JSONArray = JSONArray().apply { values.forEach { put(it) } }

    fun jsonToStrings(array: JSONArray): List<String> = List(array.length()) { array.getString(it) }

    fun emotionsToJson(values: List<EmotionRating>): JSONArray = JSONArray().apply {
        values.forEach { e ->
            put(
                JSONObject()
                    .put("emotion", e.emotion)
                    .put("before", e.intensityBefore)
                    .apply { e.intensityAfter?.let { put("after", it) } },
            )
        }
    }

    fun jsonToEmotions(array: JSONArray): List<EmotionRating> = List(array.length()) { i ->
        val o = array.getJSONObject(i)
        EmotionRating(
            emotion = o.getString("emotion"),
            intensityBefore = o.getInt("before"),
            intensityAfter = if (o.has("after") && !o.isNull("after")) o.getInt("after") else null,
        )
    }
}
