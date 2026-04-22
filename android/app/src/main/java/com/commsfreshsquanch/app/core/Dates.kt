package com.commsfreshsquanch.app.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

private fun releaseDateToComparableDate(releaseDate: String, precision: String): LocalDate {
    val parts = releaseDate.split("-").mapNotNull { it.toIntOrNull() }
    val year = parts.getOrNull(0) ?: return LocalDate.ofEpochDay(0)
    return when (precision) {
        "year" -> LocalDate.of(year, 12, 31)
        "month" -> {
            val month = parts.getOrNull(1)?.coerceIn(1, 12) ?: 1
            LocalDate.of(year, month, 1).withDayOfMonth(LocalDate.of(year, month, 1).lengthOfMonth())
        }
        else -> {
            val month = parts.getOrNull(1)?.coerceIn(1, 12) ?: 1
            val day = parts.getOrNull(2)?.coerceIn(1, 28) ?: 1
            LocalDate.of(year, month, day)
        }
    }
}

fun ageInDays(releaseDate: String, precision: String, now: Instant = Instant.now()): Long {
    val comparable = releaseDateToComparableDate(releaseDate, precision)
    val today = now.atZone(ZoneOffset.UTC).toLocalDate()
    return ChronoUnit.DAYS.between(comparable, today)
}

fun bucketForReleaseDate(releaseDate: String, precision: String, now: Instant = Instant.now()): AgeBucket {
    return if (ageInDays(releaseDate, precision, now) > 365) AgeBucket.OLDER else AgeBucket.FRESH
}

fun matchesAgeBucket(releaseDate: String, precision: String, bucket: AgeBucket, now: Instant = Instant.now()): Boolean {
    return bucketForReleaseDate(releaseDate, precision, now) == bucket
}

fun yearHintsForBucket(bucket: AgeBucket, now: Instant = Instant.now()): List<Int> {
    val year = now.atZone(ZoneOffset.UTC).year
    return if (bucket == AgeBucket.FRESH) listOf(year, year - 1) else listOf(year - 2, year - 5, year - 10, year - 20)
}
