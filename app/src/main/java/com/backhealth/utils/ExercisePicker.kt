package com.backhealth.utils

import com.backhealth.db.ExerciseDao
import com.backhealth.model.Exercise

object ExercisePicker {

    private var lastPickedEx1Id: String? = null
    private var lastPickedEx2Id: String? = null

    // Default in-memory exercises so picking NEVER fails even if Room DB hasn't finished seeding
    val DEFAULT_SET1 = listOf(
        Exercise(
            id = "EX_L1",
            name = "Slow Standing March",
            setId = "SET_1",
            durationType = "TIME",
            durationValue = 30,
            durationUnit = "seconds",
            coachingCue = "Lift your knees just high enough to engage your hips, but not so high that your lower back rounds. This acts as a gentle, low-impact mechanical pump to clear fluid from the L4-L5 joint.",
            warning = "Do not let lower back round.",
            enabled = true,
            sortOrder = 1
        ),
        Exercise(
            id = "EX_L2",
            name = "Calf Raises",
            setId = "SET_1",
            durationType = "REPS",
            durationValue = 20,
            durationUnit = "reps",
            coachingCue = "Hold your desk for balance. Push straight up onto your toes.",
            warning = "",
            // videoUrl = "https://cdn.jsdelivr.net/gh/namnq/workout-free-videos@v2.0.0/media/video/standing-machine-calf-raise.mp4",
            videoUrl = "",
            enabled = true,
            sortOrder = 2
        ),
        Exercise(
            id = "EX_L3",
            name = "Glute Squeezes",
            setId = "SET_1",
            durationType = "REPS",
            durationValue = 10,
            durationUnit = "reps (3s hold)",
            coachingCue = "Stand tall and consciously squeeze your glutes. This temporarily relieves the lower back muscles from having to stabilize your pelvis.",
            warning = "Hold for 3 seconds per squeeze.",
            enabled = true,
            sortOrder = 3
        ),
        Exercise(
            id = "EX_L4",
            name = "Standing Wall Calf Stretch",
            setId = "SET_1",
            durationType = "TIME",
            durationValue = 15,
            durationUnit = "sec per side",
            coachingCue = "Place hands flat on a wall. Step one foot back, keeping that back leg perfectly straight with the heel pressed into the floor. Keep your spine in a rigid, straight line.",
            warning = "Keep spine in a straight rigid line.",
            enabled = true,
            sortOrder = 4
        ),
        Exercise(
            id = "EX_L5",
            name = "Standing Quad Stretch",
            setId = "SET_1",
            durationType = "TIME",
            durationValue = 15,
            durationUnit = "sec per side",
            coachingCue = "Bend one knee to bring your heel toward your glute. Keep your knees close together and your torso upright. Do not lean forward.",
            warning = "Torso upright; do not lean forward.",
            enabled = true,
            sortOrder = 5
        ),
        Exercise(
            id = "EX_L6",
            name = "Standing Hip Flexor Stretch",
            setId = "SET_1",
            durationType = "TIME",
            durationValue = 30,
            durationUnit = "sec per side",
            coachingCue = "Step one foot back slightly into a mini-lunge. Crucial: You must brace your stomach muscles tightly. If your stomach sags, your lower back will arch and pinch the disc tear.",
            warning = "CRUCIAL: Brace your core tightly so lower back does not arch.",
            enabled = true,
            sortOrder = 6
        ),
        Exercise(
            id = "EX_L7",
            name = "Standing Ankle Circles",
            setId = "SET_1",
            durationType = "REPS",
            durationValue = 10,
            durationUnit = "reps per side",
            coachingCue = "Stand completely upright while holding your desk for balance. Keep the spine fully decompressed while getting the blood flowing.",
            warning = "Stand fully upright with decompressed spine.",
            enabled = true,
            sortOrder = 7
        )
    )

    val DEFAULT_SET2 = listOf(
        Exercise(
            id = "EX_U1",
            name = "Standing Chin Tucks",
            setId = "SET_2",
            durationType = "REPS",
            durationValue = 10,
            durationUnit = "reps",
            coachingCue = "Stand perfectly tall. Pull your chin straight back horizontally (like making a double chin) to align your cervical spine and release the front throat muscles.",
            warning = "Pull straight back horizontally, do not tilt down.",
            enabled = true,
            sortOrder = 1
        ),
        Exercise(
            id = "EX_U2",
            name = "Exaggerated Backward Shoulder Rolls",
            setId = "SET_2",
            durationType = "REPS",
            durationValue = 5,
            durationUnit = "reps",
            coachingCue = "Shrug your shoulders straight up to your ears, roll them as far back as they will go, and pull them firmly down to un-pinch the brachial plexus nerves.",
            warning = "Roll all the way back and pull firmly down.",
            enabled = true,
            sortOrder = 2
        ),
        Exercise(
            id = "EX_U3",
            name = "Standing Shoulder Blade Squeezes",
            setId = "SET_2",
            durationType = "REPS",
            durationValue = 10,
            durationUnit = "reps (3s hold)",
            coachingCue = "Pull your shoulder blades together as if trying to hold a pencil between them.",
            warning = "Hold for 3 seconds per repetition.",
            enabled = true,
            sortOrder = 3
        ),
        Exercise(
            id = "EX_U4",
            name = "Standing Upper Trap Stretch",
            setId = "SET_2",
            durationType = "TIME",
            durationValue = 15,
            durationUnit = "sec per side",
            coachingCue = "Gently tilt your left ear toward your left shoulder until you feel a stretch on the right side of your neck. Repeat on the other side.",
            warning = "Tilt gently, avoid aggressive pulling.",
            enabled = true,
            sortOrder = 4
        ),
        Exercise(
            id = "EX_U5",
            name = "Doorway Chest Opener",
            setId = "SET_2",
            durationType = "TIME",
            durationValue = 15,
            durationUnit = "sec per side",
            coachingCue = "Place one forearm on a doorframe with the elbow bent at 90 degrees. Gently turn your chest away from the doorframe. Keep your abdominal muscles tight so your lower back does not arch.",
            warning = "Keep abdominal muscles tight so lower back does not arch.",
            enabled = true,
            sortOrder = 5
        ),
        Exercise(
            id = "EX_U6",
            name = "Vertical Overhead Reach",
            setId = "SET_2",
            durationType = "TIME",
            durationValue = 10,
            durationUnit = "seconds",
            coachingCue = "Interlock your fingers and reach both hands straight up toward the ceiling. Elongate your entire body vertically. Push strictly upward; do not lean backward.",
            warning = "Push strictly upward; do not lean backward.",
            // videoUrl = "https://cdn.jsdelivr.net/gh/namnq/workout-free-videos@v2.0.0/media/video/alternating-lateral-lunge-with-overhead-reach.mp4",
            videoUrl = "",
            enabled = true,
            sortOrder = 6
        ),
        Exercise(
            id = "EX_U7",
            name = "Wall Angels",
            setId = "SET_2",
            durationType = "REPS",
            durationValue = 10,
            durationUnit = "reps",
            coachingCue = "Stand with your back against a wall and slide your arms up and down like making a snow angel. Keep a natural, relaxed stance—do not forcefully flatten your lower back against the wall, but do not let it aggressively arch away from it either.",
            warning = "Natural relaxed spine, do not forcefully flatten or arch.",
            enabled = true,
            sortOrder = 7
        )
    )

    suspend fun pickExercisesForMicroBreak(exerciseDao: ExerciseDao? = null): Pair<Exercise, Exercise> {
        val dbSet1 = try {
            exerciseDao?.getEnabledBySet("SET_1")?.ifEmpty { null }
        } catch (e: Exception) {
            null
        } ?: DEFAULT_SET1

        val dbSet2 = try {
            exerciseDao?.getEnabledBySet("SET_2")?.ifEmpty { null }
        } catch (e: Exception) {
            null
        } ?: DEFAULT_SET2

        // Avoid repeating the exact exercise from the previous workout if more than 1 is available
        val candidates1 = if (dbSet1.size > 1 && lastPickedEx1Id != null) {
            dbSet1.filter { it.id != lastPickedEx1Id }
        } else {
            dbSet1
        }

        val candidates2 = if (dbSet2.size > 1 && lastPickedEx2Id != null) {
            dbSet2.filter { it.id != lastPickedEx2Id }
        } else {
            dbSet2
        }

        val ex1 = candidates1.random()
        val ex2 = candidates2.random()

        lastPickedEx1Id = ex1.id
        lastPickedEx2Id = ex2.id

        return Pair(ex1, ex2)
    }

    fun getExerciseById(id: String): Exercise? {
        return (DEFAULT_SET1 + DEFAULT_SET2).find { it.id == id }
    }
}
