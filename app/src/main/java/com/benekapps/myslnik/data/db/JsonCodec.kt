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

    fun solutionsToJson(values: List<SolutionOption>): JSONArray = JSONArray().apply {
        values.forEach { put(JSONObject().put("text", it.text).put("pros", it.pros).put("cons", it.cons)) }
    }

    fun jsonToSolutions(array: JSONArray): List<SolutionOption> = List(array.length()) { i ->
        val o = array.getJSONObject(i)
        SolutionOption(o.getString("text"), o.optString("pros"), o.optString("cons"))
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
