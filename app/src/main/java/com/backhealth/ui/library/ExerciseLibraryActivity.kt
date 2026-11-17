package com.backhealth.ui.library

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.backhealth.BackHealthApp
import com.backhealth.databinding.ActivityExerciseLibraryBinding
import com.backhealth.databinding.Dialog3dPreviewBinding
import com.backhealth.model.Exercise
import com.backhealth.repository.ExerciseRepository
import com.google.android.material.bottomsheet.BottomSheetDialog
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
        adapter = ExerciseAdapter(
            onToggleEnabled = { exercise, isEnabled ->
                lifecycleScope.launch {
                    exerciseRepo.setExerciseEnabled(exercise.id, isEnabled)
                }
            },
            onWatch3D = { exercise ->
                show3DModelDialog(exercise)
            }
        )
        binding.rvExercises.layoutManager = LinearLayoutManager(this)
        binding.rvExercises.adapter = adapter
    }

    private fun show3DModelDialog(exercise: Exercise) {
        val dialog = BottomSheetDialog(this)
        val previewBinding = Dialog3dPreviewBinding.inflate(layoutInflater)
        dialog.setContentView(previewBinding.root)

        val isSet1 = exercise.setId == "SET_1"
        previewBinding.tvPreviewSet.text = if (isSet1) "SET 1: LOWER BODY & CORE" else "SET 2: POSTURE RESET"
        previewBinding.tvPreviewSet.setTextColor(
            androidx.core.content.ContextCompat.getColor(
                this,
                if (isSet1) com.backhealth.R.color.neon_mint else com.backhealth.R.color.neon_cyan
            )
        )
        previewBinding.tvPreviewTitle.text = exercise.name
        previewBinding.tvPreviewCue.text = exercise.coachingCue

        if (exercise.warning.isNotEmpty()) {
            previewBinding.tvPreviewWarning.visibility = View.VISIBLE
            previewBinding.tvPreviewWarning.text = "⚠️ ${exercise.warning}"
        } else {
            previewBinding.tvPreviewWarning.visibility = View.GONE
        }

        // Videos commented out / hidden for Phase 1
        previewBinding.videoPreview.visibility = View.GONE
        previewBinding.ivPreviewPlaceholder.visibility = View.VISIBLE
        /*
        val videoUrl = exercise.videoUrl.ifEmpty {
            com.backhealth.utils.ExercisePicker.getExerciseById(exercise.id)?.videoUrl.orEmpty()
        }.ifEmpty { null }
        if (videoUrl != null) {
            try {
                previewBinding.ivPreviewPlaceholder.visibility = View.GONE
                previewBinding.videoPreview.visibility = View.VISIBLE
                previewBinding.videoPreview.setVideoURI(android.net.Uri.parse(videoUrl))
                previewBinding.videoPreview.setOnPreparedListener { mp ->
                    mp.isLooping = true
                    previewBinding.videoPreview.start()
                }
            } catch (e: Exception) {
                previewBinding.videoPreview.visibility = View.GONE
                previewBinding.ivPreviewPlaceholder.visibility = View.VISIBLE
            }
        } else {
            previewBinding.videoPreview.visibility = View.GONE
            previewBinding.ivPreviewPlaceholder.visibility = View.VISIBLE
        }
        */

        dialog.setOnDismissListener {
            try {
                previewBinding.videoPreview.stopPlayback()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        previewBinding.btnClosePreview.setOnClickListener { dialog.dismiss() }
        previewBinding.btnGotIt.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun observeExercises() {
        lifecycleScope.launch {
            exerciseRepo.getAllExercises().collectLatest { list ->
                adapter.submitList(list)
            }
        }
    }
}
