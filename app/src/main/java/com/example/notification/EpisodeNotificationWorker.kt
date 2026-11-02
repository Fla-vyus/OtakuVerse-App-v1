package com.example.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.data.local.LibraryDatabase
import com.example.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import java.util.Calendar

class EpisodeNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "EpisodeWorker"
    }

    override suspend fun doWork(): Result {
        val context = applicationContext
        val prefs = UserPreferencesRepository(context)

        val enabled = prefs.notificationsEnabled.first()
        if (!enabled) {
            Log.d(TAG, "Notificações desativadas nas preferências.")
            return Result.success()
        }

        if (!NotificationHelper.hasNotificationPermission(context)) {
            Log.d(TAG, "Permissão POST_NOTIFICATIONS não concedida.")
            return Result.success()
        }

        val customToken = prefs.customToken.first()
        val tursoClient = com.example.data.turso.TursoClient { customToken.takeIf { it.isNotBlank() } }
        if (tursoClient.getActiveToken().isBlank()) {
            Log.d(TAG, "Nenhum token do Turso configurado. Ignorando checagem de episódios.")
            return Result.success()
        }

        // Check quiet hours (e.g. 22h to 08h)
        val (quietStart, quietEnd) = prefs.quietHours.first()
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val isQuietTime = if (quietStart > quietEnd) {
            currentHour >= quietStart || currentHour < quietEnd
        } else {
            currentHour in quietStart until quietEnd
        }

        if (isQuietTime) {
            Log.d(TAG, "Horário de silêncio ativo ($currentHour h). Notificação adiada.")
            return Result.success()
        }

        try {
            val db = LibraryDatabase.getDatabase(context)
            val libraryWorks = db.libraryDao().getAllSnapshot()

            // Filter works in READING or PLANNING that have upcoming nextEpisodeAt
            val monitored = libraryWorks.filter {
                (it.status == "READING" || it.status == "PLANNING") && !it.nextEpisodeAt.isNullOrBlank()
            }

            if (monitored.isNotEmpty()) {
                val sampleWork = monitored.first()
                val notificationIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    notificationIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val builder = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("Lembrete de Lançamento")
                    .setContentText("${sampleWork.title} possui novidades na agenda!")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)

                with(NotificationManagerCompat.from(context)) {
                    notify(sampleWork.id.hashCode(), builder.build())
                }
            }

            return Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Falha no Worker de Notificações: ${e.message}")
            return Result.retry()
        }
    }
}
