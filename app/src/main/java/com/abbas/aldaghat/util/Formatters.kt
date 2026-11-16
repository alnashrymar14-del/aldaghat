package com.abbas.aldaghat.util

import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Formatting helpers for an Arabic UI that keeps Western digits (predictable in screenshots,
 * tests and when files are copied to a computer).
 */
object Formatters {
    private val arabicMonths = arrayOf(
        "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
        "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر",
    )

    const val UNKNOWN = "—"

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 بايت"
        if (bytes < 1024L) return "$bytes بايت"
        val kilobytes = bytes / 1024.0
        if (kilobytes < 1024.0) return String.format(Locale.US, "%.0f كيلوبايت", kilobytes)
        val megabytes = kilobytes / 1024.0
        if (megabytes < 1024.0) return String.format(Locale.US, "%.1f ميجابايت", megabytes)
        val gigabytes = megabytes / 1024.0
        return String.format(Locale.US, "%.2f جيجابايت", gigabytes)
    }

    fun formatMegabytes(megabytes: Double): String {
        if (megabytes.isNaN() || megabytes <= 0.0) return "0 ميجابايت"
        if (megabytes < 1024.0) return String.format(Locale.US, "%.1f ميجابايت", megabytes)
        return String.format(Locale.US, "%.2f جيجابايت", megabytes / 1024.0)
    }

    fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0L) return "00:00"
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMs)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun formatElapsed(elapsedMs: Long): String = formatDuration(elapsedMs)

    fun formatPercent(percent: Double, decimals: Int = 0): String {
        val safe = if (percent.isFinite()) percent.coerceIn(0.0, 100.0) else 0.0
        return String.format(Locale.US, "%.${decimals}f%%", safe)
    }

    fun formatBitrate(kbps: Int): String =
        if (kbps <= 0) UNKNOWN else "$kbps كيلوبت/ث"

    fun formatResolution(width: Int, height: Int): String =
        if (width <= 0 || height <= 0) UNKNOWN else "${width}×${height}"

    fun formatFrameRate(fps: Float): String {
        if (!fps.isFinite() || fps <= 0f) return UNKNOWN
        return String.format(Locale.US, "%.0f إطار/ث", fps)
    }

    fun formatDate(epochMillis: Long): String {
        if (epochMillis <= 0L) return UNKNOWN
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = epochMillis
        val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
        val month = arabicMonths[calendar.get(java.util.Calendar.MONTH)]
        val year = calendar.get(java.util.Calendar.YEAR)
        var hour = calendar.get(java.util.Calendar.HOUR)
        if (hour == 0) hour = 12
        val minute = calendar.get(java.util.Calendar.MINUTE)
        val suffix = if (calendar.get(java.util.Calendar.AM_PM) == java.util.Calendar.AM) "ص" else "م"
        return String.format(Locale.US, "%d %s %d، %d:%02d %s", day, month, year, hour, minute, suffix)
    }

    fun formatEta(etaMs: Long?): String =
        if (etaMs == null || etaMs <= 0L) UNKNOWN else formatDuration(etaMs)

    fun formatMb(megabytes: Double): String = formatMegabytes(megabytes)
}
