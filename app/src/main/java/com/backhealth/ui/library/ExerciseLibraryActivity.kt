package com.backhealth.ui.library

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.backhealth.BackHealthApp
import com.backhealth.databinding.ActivityExerciseLibraryBinding
import com.backhealth.repository.ExerciseRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ExerciseLibraryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExerciseLibraryBinding
    private lateinit var adapter: ExerciseAdapter
    private lateinit var exerciseRepo: ExerciseRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExerciseLibraryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val db = (application as BackHealthApp).database
        exerciseRepo = (application as BackHealthApp).exerciseRepository

        setupToolbar()
        setupRecyclerView()
        observeExercises()
    }

    private fun setupToolbar() {
        binding.toolbarLibrary.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = ExerciseAdapter { exercise, isEnabled ->
            lifecycleScope.launch {
                exerciseRepo.setExerciseEnabled(exercise.id, isEnabled)
            }
        }
        binding.rvExercises.layoutManager = LinearLayoutManager(this)
        binding.rvExercises.adapter = adapter
    }

    private fun observeExercises() {
        lifecycleScope.launch {
            exerciseRepo.getAllExercises().collectLatest { list ->
                adapter.submitList(list)
            }
        }
    }
}
