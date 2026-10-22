package gr.misha.xypna

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

class AlarmService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var savedVolume = -1
    private val handler = Handler(Looper.getMainLooper())

    /** Μετά τα 2 λεπτά σιγής: ξαναχτυπάει. */
    private val resume = Runnable {
        silencedUntil = 0L
        startSound()
        startVibration()
        try { startActivity(fullIntent(currentId)) } catch (_: Exception) { }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SILENCE) {
            stopSoundAndVibration()
            handler.removeCallbacks(resume)
            handler.postDelayed(resume, SOLVE_MS)
            return START_STICKY
        }

        val id = intent?.getIntExtra(EXTRA_ID, currentId) ?: currentId
        currentId = id

        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            val ch = NotificationChannel(CHANNEL, "Ξυπνητήρια", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)          // τον ήχο τον παίζουμε εμείς
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(ch)
        }

        val full = fullIntent(id)
        val pi = PendingIntent.getActivity(
            this, 1, full, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val label = AlarmStore.get(this, id)?.label?.takeIf { it.isNotBlank() } ?: "Ξυπνητήρι"
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(label)
            .setContentText("Λύσε τις πράξεις ή κάνε αναβολή")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .build()

        ServiceCompat.startForeground(
            this, NOTIF_ID, n,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        )
        isRinging = true
        silencedUntil = 0L
        handler.removeCallbacks(resume)

        if (player == null) startSound()
        if (vibrator == null) startVibration()

        try { startActivity(full) } catch (_: Exception) { }

        return START_STICKY
    }

    private fun fullIntent(id: Int) = Intent(this, AlarmActivity::class.java)
        .putExtra(EXTRA_ID, id)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)

    private fun startSound() {
        if (player != null) return
        val am = getSystemService(AudioManager::class.java)
        try {
            if (savedVolume < 0) savedVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
            am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        } catch (_: Exception) { }

        val candidates = listOfNotNull(
            RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        )
        for (uri in candidates) {
            if (tryPlay(uri)) return
        }
    }

    private fun tryPlay(uri: Uri): Boolean = try {
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setDataSource(this@AlarmService, uri)
            isLooping = true
            prepare()
            start()
        }
        true
    } catch (e: Exception) {
        player?.release()
        player = null
        false
    }

    private fun startVibration() {
        if (vibrator != null) return
        val v: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator = v
        try {
            v?.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
            )
        } catch (_: Exception) { }
    }

    private fun stopSoundAndVibration() {
        try { player?.stop() } catch (_: Exception) { }
        player?.release()
        player = null
        vibrator?.cancel()
        vibrator = null
    }

    override fun onDestroy() {
        handler.removeCallbacks(resume)
        stopSoundAndVibration()
        if (savedVolume >= 0) {
            try {
                getSystemService(AudioManager::class.java)
                    .setStreamVolume(AudioManager.STREAM_ALARM, savedVolume, 0)
            } catch (_: Exception) { }
        }
        isRinging = false
        silencedUntil = 0L
        super.onDestroy()
    }

    companion object {
        const val CHANNEL = "alarm_ring"
        const val NOTIF_ID = 42
        const val ACTION_SILENCE = "gr.misha.xypna.SILENCE"
        const val SOLVE_MS = 2 * 60 * 1000L

        @Volatile var isRinging = false
        @Volatile var currentId = -1
        /** Πότε τελειώνει η σιγή των 2 λεπτών (0 = χτυπάει τώρα). */
        @Volatile var silencedUntil = 0L

        /** «Λύση προβλήματος»: σιγή για 2 λεπτά, μετά ξαναχτυπάει. */
        fun silence(ctx: Context) {
            silencedUntil = System.currentTimeMillis() + SOLVE_MS
            ctx.startService(Intent(ctx, AlarmService::class.java).setAction(ACTION_SILENCE))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, AlarmService::class.java))
        }
    }
}
