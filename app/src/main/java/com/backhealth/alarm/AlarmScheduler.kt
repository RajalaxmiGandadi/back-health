package com.backhealth.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object AlarmScheduler {

    const val ACTION_ALARM_TRIGGER = "com.backhealth.ACTION_ALARM_TRIGGER"
    const val ACTION_REST_END = "com.backhealth.ACTION_REST_END"

    const val EXTRA_ALARM_TYPE = "EXTRA_ALARM_TYPE" // "EXERCISE", "WALK", "SNOOZE"
    const val REQUEST_CODE_ALARM = 1001
    const val REQUEST_CODE_REST = 1002

    fun scheduleAlarm(context: Context, triggerAtMillis: Long, alarmType: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM_TRIGGER
            putExtra(EXTRA_ALARM_TYPE, alarmType)
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(context, REQUEST_CODE_ALARM, intent, flags)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    fun scheduleRestEnd(context: Context, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_REST_END
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(context, REQUEST_CODE_REST, intent, flags)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    fun cancelAlarms(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val alarmIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM_TRIGGER
        }
        val p1 = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_ALARM,
            alarmIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (p1 != null) {
            alarmManager.cancel(p1)
            p1.cancel()
        }

        val restIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_REST_END
        }
        val p2 = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_REST,
            restIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (p2 != null) {
            alarmManager.cancel(p2)
            p2.cancel()
        }
    }
}
