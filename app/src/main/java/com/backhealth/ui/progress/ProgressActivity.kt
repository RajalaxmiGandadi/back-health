package com.backhealth.ui.progress

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.backhealth.BackHealthApp
import com.backhealth.databinding.ActivityProgressBinding
import com.backhealth.repository.ProgressRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProgressActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProgressBinding
    private lateinit var progressRepo: ProgressRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProgressBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val db = (application as BackHealthApp).database
        progressRepo = ProgressRepository(db.exerciseLogDao(), db.walkLogDao())

        binding.toolbarProgress.setNavigationOnClickListener { finish() }

        loadProgressData()
    }

    private fun loadProgressData() {
        lifecycleScope.launch {
            val totalEx = progressRepo.getTotalExercises()
            val totalWalks = progressRepo.getTotalWalks()
            binding.tvTotalStats.text = "Total Exercises Completed: $totalEx\nTotal Walks Completed: $totalWalks"

            progressRepo.getAllExerciseLogs().collectLatest { logs ->
                if (logs.isEmpty()) {
                    binding.tvLogsList.text = "No routines recorded yet. Stay consistent!"
                } else {
                    val sdf = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())
                    val sb = StringBuilder()
                    logs.take(20).forEach { log ->
                        val formattedDate = sdf.format(Date(log.timestamp))
                        sb.append("• $formattedDate\n")
                        sb.append("  🧘 ${log.exercise1Name} + ${log.exercise2Name}\n\n")
                    }
                    binding.tvLogsList.text = sb.toString()
                }
            }
        }
    }
}
