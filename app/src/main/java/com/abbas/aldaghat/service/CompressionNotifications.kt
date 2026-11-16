package com.abbas.aldaghat.service

import android.app.Notification
import android.app.NotificationChannel
import android.os.Build
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.abbas.aldaghat.MainActivity
import com.abbas.aldaghat.R
import com.abbas.aldaghat.domain.CompressionJobSpec
import com.abbas.aldaghat.domain.CompressionResult
import com.abbas.aldaghat.util.Formatters

/** Builds the progress and result notifications, including the in-notification cancel action. */
class CompressionNotifications(private val context: Context) {

    fun ensureChannels() {
        // NotificationChannel only exists on API 26+; the compat builder works without channels below.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val progressChannel = NotificationChannel(
            CHANNEL_PROGRESS,
            context.getString(R.string.notification_channel_progress),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_progress_desc)
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        val resultChannel = NotificationChannel(
            CHANNEL_RESULT,
            context.getString(R.string.notification_channel_result),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_result_desc)
        }
        manager.createNotificationChannel(progressChannel)
        manager.createNotificationChannel(resultChannel)
    }

    fun progressNotification(
        spec: CompressionJobSpec,
        percent: Int,
        etaMs: Long?,
        elapsedMs: Long,
        saving: Boolean = false,
    ): Notification {
        val contentIntent = activityIntent()
        val cancelIntent = PendingIntent.getService(
            context,
            REQUEST_CANCEL,
            CompressionService.cancelIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val text = when {
            saving -> context.getString(R.string.job_saving)
            percent <= 0 -> context.getString(R.string.job_preparing)
            else -> context.getString(
                R.string.notification_progress_format,
                percent,
                context.getString(R.string.job_remaining) + ": " + Formatters.formatEta(etaMs),
            )
        }

        return NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(spec.displayName)
            .setContentText(text)
            .setSubText(
                context.getString(R.string.job_elapsed) + ": " + Formatters.formatElapsed(elapsedMs),
            )
            .setProgress(100, percent.coerceIn(0, 100), saving || percent <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .addAction(
                R.drawable.ic_notification,
                context.getString(R.string.notification_action_cancel),
                cancelIntent,
            )
            .build()
    }

    fun successNotification(result: CompressionResult): Notification {
        val savedText = context.getString(
            R.string.notification_saved_text,
            result.outputName,
            Formatters.formatBytes(result.outputBytes),
        )
        return NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_saved_title))
            .setContentText(savedText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(savedText))
            .setAutoCancel(true)
            .setContentIntent(activityIntent())
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
    }

    fun failureNotification(inputName: String, message: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_failed_title))
            .setContentText("$inputName • $message")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$inputName • $message"))
            .setAutoCancel(true)
            .setContentIntent(activityIntent())
            .build()

    fun cancelledNotification(inputName: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_cancelled_title))
            .setContentText(context.getString(R.string.notification_cancelled_text, inputName))
            .setAutoCancel(true)
            .setContentIntent(activityIntent())
            .build()

    fun show(notificationId: Int, notification: Notification) {
        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }

    fun cancel(notificationId: Int) {
        runCatching { NotificationManagerCompat.from(context).cancel(notificationId) }
    }

    private fun activityIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_OPEN_APP,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_PROGRESS = "aldaghat_progress"
        const val CHANNEL_RESULT = "aldaghat_result"
        const val PROGRESS_NOTIFICATION_ID = 4101
        const val RESULT_NOTIFICATION_ID = 4102
        private const val REQUEST_CANCEL = 21
        private const val REQUEST_OPEN_APP = 22
    }
}
