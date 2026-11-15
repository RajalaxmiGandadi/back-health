package com.backhealth.alarm

import java.util.Calendar

object SleepWindowChecker {

    /**
     * Returns true if the given time is within the user's active awake window.
     * wakeTime: "HH:mm" (e.g., "07:00")
     * bedTime: "HH:mm" (e.g., "22:00")
     */
    fun isAwakeTime(wakeTime: String, bedTime: String, nowCalendar: Calendar = Calendar.getInstance()): Boolean {
        val (wakeHour, wakeMin) = parseTime(wakeTime, 7, 0)
        val (bedHour, bedMin) = parseTime(bedTime, 22, 0)

        val currentHour = nowCalendar.get(Calendar.HOUR_OF_DAY)
        val currentMin = nowCalendar.get(Calendar.MINUTE)
        val currentTotal = currentHour * 60 + currentMin

        val wakeTotal = wakeHour * 60 + wakeMin
        val bedTotal = bedHour * 60 + bedMin

        return if (wakeTotal < bedTotal) {
            // Normal day: e.g. 07:00 to 22:00
            currentTotal in wakeTotal until bedTotal
        } else {
            // Night shift: e.g. 20:00 to 06:00
            currentTotal >= wakeTotal || currentTotal < bedTotal
        }
    }

    /**
     * Calculates epoch millis until next wake-up time.
     */
    fun getNextWakeUpEpochMillis(wakeTime: String): Long {
        val (wakeHour, wakeMin) = parseTime(wakeTime, 7, 0)
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, wakeHour)
        cal.set(Calendar.MINUTE, wakeMin)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    private fun parseTime(timeStr: String, defaultHour: Int, defaultMin: Int): Pair<Int, Int> {
        return try {
            val parts = timeStr.split(":")
            Pair(parts[0].trim().toInt(), parts[1].trim().toInt())
        } catch (e: Exception) {
            Pair(defaultHour, defaultMin)
        }
    }
}
