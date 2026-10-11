package com.benekapps.myslnik.data.db

import androidx.room.TypeConverter
import org.json.JSONArray

class Converters {
    @TypeConverter
    fun stringListToJson(value: List<String>): String = JsonCodec.stringsToJson(value).toString()

    @TypeConverter
    fun jsonToStringList(value: String): List<String> = JsonCodec.jsonToStrings(JSONArray(value))

    @TypeConverter
    fun emotionListToJson(value: List<EmotionRating>): String = JsonCodec.emotionsToJson(value).toString()

    @TypeConverter
    fun jsonToEmotionList(value: String): List<EmotionRating> = JsonCodec.jsonToEmotions(JSONArray(value))
}
