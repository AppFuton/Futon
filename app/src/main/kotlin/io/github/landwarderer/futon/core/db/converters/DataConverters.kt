package io.github.landwarderer.futon.core.db.converters

import androidx.room.TypeConverter

class DataConverters {
    @TypeConverter
    fun fromLongArray(value: LongArray?): String? {
        return value?.joinToString(",")
    }

    @TypeConverter
    fun toLongArray(value: String?): LongArray? {
        return value?.split(",")?.filter { it.isNotEmpty() }?.map { it.toLong() }?.toLongArray()
    }
}
