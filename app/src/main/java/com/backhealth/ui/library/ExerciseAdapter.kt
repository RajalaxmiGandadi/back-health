package com.backhealth.ui.library

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.backhealth.databinding.ItemExerciseBinding
import com.backhealth.model.Exercise

class ExerciseAdapter(
    private val onToggleEnabled: (Exercise, Boolean) -> Unit,
    private val onWatch3D: ((Exercise) -> Unit)? = null
) : ListAdapter<Exercise, ExerciseAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(val binding: ItemExerciseBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemExerciseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val exercise = getItem(position)
        with(holder.binding) {
            tvItemName.text = exercise.name
            tvItemDuration.text = "${exercise.durationValue} ${exercise.durationUnit}"
            tvItemCue.text = exercise.coachingCue
            tvItemSetBadge.text = if (exercise.setId == "SET_1") "Set 1: Lower Body" else "Set 2: Posture Reset"

            if (exercise.warning.isNotEmpty()) {
                tvItemWarning.visibility = View.VISIBLE
                tvItemWarning.text = "⚠️ ${exercise.warning}"
            } else {
                tvItemWarning.visibility = View.GONE
            }

            switchEnabled.setOnCheckedChangeListener(null)
            switchEnabled.isChecked = exercise.enabled
            switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                onToggleEnabled(exercise, isChecked)
            }

            btnWatch3D.setOnClickListener {
                onWatch3D?.invoke(exercise)
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Exercise>() {
        override fun areItemsTheSame(oldItem: Exercise, newItem: Exercise): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Exercise, newItem: Exercise): Boolean = oldItem == newItem
    }
}
