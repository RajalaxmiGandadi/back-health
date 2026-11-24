package com.backhealth.ui.splash

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.backhealth.ui.home.HomeActivity
import com.backhealth.ui.onboarding.OnboardingActivity

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("spinehealth_user_prefs", Context.MODE_PRIVATE)
        val setupVersion = prefs.getInt("setup_version", 0)
        val isOnboardingCompleted = prefs.getBoolean("onboarding_complete", false) && (setupVersion >= 2)

        val targetIntent = if (!isOnboardingCompleted) {
            Intent(this, OnboardingActivity::class.java)
        } else {
            Intent(this, HomeActivity::class.java)
        }

        targetIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(targetIntent)
        finish()
    }
}
