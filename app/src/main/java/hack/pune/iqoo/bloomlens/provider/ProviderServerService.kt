package hack.pune.iqoo.bloomlens.provider

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import fi.iki.elonen.NanoHTTPD
import hack.pune.iqoo.bloomlens.BloomLensApp
import hack.pune.iqoo.bloomlens.MainActivity
import hack.pune.iqoo.bloomlens.R

/**
 * Foreground service hosting [ProviderWebServer], so it keeps serving students even while the
 * teacher's screen is off or they're using another app - the whole point of Provider Mode is
 * that this phone can be left running unattended.
 */
class ProviderServerService : Service() {

    private var server: ProviderWebServer? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        if (server == null) {
            val container = (application as BloomLensApp).container
            val newServer = ProviderWebServer(
                textRecognizer = container.textRecognizer,
                llm = container.onDeviceLlm,
                historyRepository = container.historyRepository,
                imageStorage = container.imageStorage,
                port = ProviderStatus.port.value,
            )
            runCatching { newServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }
                .onSuccess {
                    server = newServer
                    ProviderStatus.isRunning.value = true
                }
                .onFailure {
                    ProviderStatus.isRunning.value = false
                    stopSelf()
                }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        ProviderStatus.isRunning.value = false
        ProviderStatus.sessionCount.value = 0
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BloomLens Provider Mode")
            .setContentText("Serving the tutor to nearby devices over Wi-Fi")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Provider Mode", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "provider_mode"
        private const val NOTIFICATION_ID = 4242

        fun start(context: Context) {
            context.startForegroundService(Intent(context, ProviderServerService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ProviderServerService::class.java))
        }
    }
}
