package com.hourglass.core

/**
 * A line of encouragement to go under "Your day ends in…", fitted to the part of the day.
 *
 * Plain and kind, never clever: this is read several times a day, every day, and a
 * pun is funny once. The line holds still for the whole morning, afternoon or evening
 * — the notification updates every quarter hour, and a new slogan every fifteen
 * minutes would read as noise — and the set rotates from one day to the next.
 */
object DayLines {

    enum class Part { MORNING, AFTERNOON, EVENING }

    fun partOf(minuteOfDay: Int): Part = when {
        minuteOfDay < NOON -> Part.MORNING
        minuteOfDay < FIVE_PM -> Part.AFTERNOON
        else -> Part.EVENING
    }

    /** The line for [minuteOfDay] on [epochDay]; the same all through that part of that day. */
    fun lineFor(minuteOfDay: Int, epochDay: Long): String {
        val part = partOf(minuteOfDay)
        val lines = linesFor(part)
        val index = Math.floorMod(epochDay * DAY_STRIDE + part.ordinal * PART_STRIDE, lines.size.toLong())
        return lines[index.toInt()]
    }

    fun linesFor(part: Part): List<String> = when (part) {
        Part.MORNING -> MORNING
        Part.AFTERNOON -> AFTERNOON
        Part.EVENING -> EVENING
    }

    private val MORNING = listOf(
        "A fresh start. Make the first hour count.",
        "Begin with the thing that matters most.",
        "One focused hour now makes the whole day lighter.",
        "Start small, start now. Momentum will follow.",
        "The day is wide open. Choose what fills it.",
        "Good days are built one task at a time.",
        "Set your intention, then give it your attention.",
        "There is plenty of time to do something good today.",
        "Clear head, clear plan. You've got this.",
        "Every big day starts with a single step."
    )

    private val AFTERNOON = listOf(
        "Keep going, you're doing great.",
        "Steady progress beats a perfect plan.",
        "You're well into the day. Keep the rhythm going.",
        "A short break, then back to it. You're on track.",
        "What you finish this afternoon, tomorrow will thank you for.",
        "Stay with it. The start was the hardest part, and it's behind you.",
        "Small steps are still steps forward.",
        "You've already done more than you think.",
        "Pick the next task and give it your full attention.",
        "The afternoon is yours. Make it count."
    )

    private val EVENING = listOf(
        "Finish strong, then let the day go.",
        "Wrap up what matters and leave the rest for tomorrow.",
        "You've done good work today. Start winding down soon.",
        "One last push, then rest. You've earned it.",
        "Be proud of what you got done today.",
        "Close the loops you can, and let the others wait.",
        "Rest is part of the work. Protect your sleep.",
        "Tomorrow starts with tonight's rest.",
        "Slow down, look back, and notice how far you've come.",
        "Let today settle. You did well."
    )

    private const val NOON = 12 * 60
    private const val FIVE_PM = 17 * 60

    /** Co-prime with ten, so consecutive days walk through every line before repeating. */
    private const val DAY_STRIDE = 3L
    private const val PART_STRIDE = 7L
}
