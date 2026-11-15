package com.backhealth.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.backhealth.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getInstance(context)
                    val prefs = db.userPrefsDao().getPrefs()
                    if (prefs?.isOnboardingComplete == true && !prefs.isCyclePaused) {
                        AlarmCycleService.start(context)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
