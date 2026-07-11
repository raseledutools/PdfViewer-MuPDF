package com.rasel.RasFocus.selfcontrol.familybrowser.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.rasel.RasFocus.selfcontrol.familybrowser.FamilyBrowserActivity
import java.net.URL
import java.util.concurrent.Executors

/**
 * BackgroundAudioService.kt — Fixed & Enhanced
 * ✅ Thumbnail দেখাবে (hqdefault)
 * ✅ Play/Pause, Prev, Next, +10s, -10s controls
 * ✅ WebView-এ JS broadcast করে real playback control
 * ✅ MediaSession metadata (title + artwork)
 * ✅ Lock screen-এও controls দেখাবে
 */
class BackgroundAudioService : Service() {

    companion object {
        const val CHANNEL_ID      = "bg_audio_channel"
        const val NOTIFICATION_ID = 9001
        const val EXTRA_TITLE     = "video_title"
        const val EXTRA_URL       = "video_url"

        // Intent actions — YoutubeActivity থেকে পাঠানো হবে
        const val ACTION_PLAY_PAUSE    = "com.familybrowser.PLAY_PAUSE"
        const val ACTION_STOP          = "com.familybrowser.STOP_BG_AUDIO"
        const val ACTION_UPDATE_TITLE  = "com.familybrowser.UPDATE_TITLE"
        const val ACTION_PREV          = "com.familybrowser.PREV"
        const val ACTION_NEXT          = "com.familybrowser.NEXT"
        const val ACTION_REWIND        = "com.familybrowser.REWIND"    // -10s
        const val ACTION_FORWARD       = "com.familybrowser.FORWARD"   // +10s

        // YoutubeActivity এই broadcast শুনবে এবং WebView-এ JS inject করবে
        const val BROADCAST_PLAYBACK_ACTION = "com.familybrowser.PLAYBACK_CONTROL"
        const val EXTRA_PLAYBACK_CMD        = "playback_cmd"
    }

    private var audioManager: AudioManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var mediaSession: MediaSessionCompat
    private var isPlaying: Boolean = true
    private var currentTitle: String = "YouTube — Playing"
    private var currentThumbUrl: String? = null
    private var currentThumbnail: Bitmap? = null

    private val bgExecutor = Executors.newSingleThreadExecutor()

    // ── Audio Focus ────────────────────────────────────────────────────────────
    private val audioFocusRequest by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAcceptsDelayedFocusGain(true)
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener { /* audio focus হারালেও চালু থাকবে */ }
                .build()
        } else null
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────
    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
        acquireWakeLock()
        initMediaSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {

            ACTION_STOP -> {
                sendPlaybackBroadcast("stop")
                stopSelfClean()
                return START_NOT_STICKY
            }

            ACTION_PLAY_PAUSE -> {
                isPlaying = !isPlaying
                updatePlaybackState()
                refreshNotification()
                sendPlaybackBroadcast(if (isPlaying) "play" else "pause")
                return START_STICKY
            }

            ACTION_PREV -> {
                sendPlaybackBroadcast("prev")
                return START_STICKY
            }

            ACTION_NEXT -> {
                sendPlaybackBroadcast("next")
                return START_STICKY
            }

            ACTION_REWIND -> {
                sendPlaybackBroadcast("rewind")
                return START_STICKY
            }

            ACTION_FORWARD -> {
                sendPlaybackBroadcast("forward")
                return START_STICKY
            }

            ACTION_UPDATE_TITLE -> {
                val newTitle = intent.getStringExtra(EXTRA_TITLE) ?: return START_STICKY
                currentTitle = newTitle
                updateMediaMetadata()
                refreshNotification()
                return START_STICKY
            }
        }

        // নতুন start — title, thumb, videoId নাও
        val title    = intent?.getStringExtra(EXTRA_TITLE)     ?: "YouTube — Playing"
        val thumbUrl = intent?.getStringExtra("extra_thumb_url")
        val videoId  = intent?.getStringExtra("extra_video_id")

        currentTitle    = title
        currentThumbUrl = thumbUrl
        isPlaying       = true

        updatePlaybackState()
        requestAudioFocus()

        // প্রথমে thumbnail ছাড়াই notification দেখাও (যাতে দেরি না হয়)
        val initialNotification = buildNotification(title, null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, initialNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        // Background-এ thumbnail download করো
        if (thumbUrl != null) {
            bgExecutor.execute {
                val bmp = downloadThumbnail(thumbUrl)
                if (bmp != null) {
                    currentThumbnail = bmp
                    updateMediaMetadata()
                    val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(NOTIFICATION_ID, buildNotification(currentTitle, bmp))
                }
            }
        }

        updateMediaMetadata()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseWakeLock()
        releaseAudioFocus()
        bgExecutor.shutdownNow()
        if (::mediaSession.isInitialized) {
            mediaSession.isActive = false
            mediaSession.release()
        }
        currentThumbnail?.recycle()
        currentThumbnail = null
        super.onDestroy()
    }

    // ── MediaSession ───────────────────────────────────────────────────────────
    private fun initMediaSession() {
        mediaSession = MediaSessionCompat(this, "RasFocusMediaSession").apply {
            setPlaybackState(buildPlaybackState())
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay()  { isPlaying = true;  updatePlaybackState(); refreshNotification(); sendPlaybackBroadcast("play") }
                override fun onPause() { isPlaying = false; updatePlaybackState(); refreshNotification(); sendPlaybackBroadcast("pause") }
                override fun onStop()  { stopSelfClean() }
                override fun onSkipToNext()     { sendPlaybackBroadcast("next") }
                override fun onSkipToPrevious() { sendPlaybackBroadcast("prev") }
                override fun onFastForward()    { sendPlaybackBroadcast("forward") }
                override fun onRewind()         { sendPlaybackBroadcast("rewind") }
            })
            isActive = true
        }
    }

    private fun buildPlaybackState(): PlaybackStateCompat =
        PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_PLAY       or
                PlaybackStateCompat.ACTION_PAUSE      or
                PlaybackStateCompat.ACTION_STOP       or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT     or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_FAST_FORWARD     or
                PlaybackStateCompat.ACTION_REWIND
            )
            .setState(
                if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f
            )
            .build()

    private fun updatePlaybackState() {
        mediaSession.setPlaybackState(buildPlaybackState())
    }

    private fun updateMediaMetadata() {
        val meta = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE,  currentTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "YouTube")
            .apply {
                currentThumbnail?.let {
                    putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
                    putBitmap(MediaMetadataCompat.METADATA_KEY_ART, it)
                }
            }
            .build()
        mediaSession.setMetadata(meta)
    }

    private fun refreshNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(currentTitle, currentThumbnail))
    }

    // ── Notification ───────────────────────────────────────────────────────────
    private fun buildNotification(title: String, thumbnail: Bitmap?): Notification {

        // App খোলার intent
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, FamilyBrowserActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        fun svcIntent(action: String) = PendingIntent.getService(
            this,
            action.hashCode(),
            Intent(this, BackgroundAudioService::class.java).apply { this.action = action },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon  = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseLabel = if (isPlaying) "Pause" else "Play"

        // ── 5টি action button ──
        // Index:  0=Prev   1=Rewind   2=Play/Pause   3=Forward   4=Stop
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText("YouTube • Background Play")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setShowWhen(false)
            // Action 0 — Previous
            .addAction(android.R.drawable.ic_media_previous, "Prev",    svcIntent(ACTION_PREV))
            // Action 1 — Rewind 10s
            .addAction(android.R.drawable.ic_media_rew,      "-10s",    svcIntent(ACTION_REWIND))
            // Action 2 — Play/Pause
            .addAction(playPauseIcon,                         playPauseLabel, svcIntent(ACTION_PLAY_PAUSE))
            // Action 3 — Forward 10s
            .addAction(android.R.drawable.ic_media_ff,       "+10s",    svcIntent(ACTION_FORWARD))
            // Action 4 — Stop
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", svcIntent(ACTION_STOP))
            // MediaStyle — compact view-এ Play/Pause, Prev, Next দেখাবে
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 2, 4) // Prev, Play/Pause, Stop
            )

        // Thumbnail সেট করো
        if (thumbnail != null) {
            builder.setLargeIcon(thumbnail)
        }

        return builder.build()
    }

    // ── Broadcast to YoutubeActivity ───────────────────────────────────────────
    /**
     * YoutubeActivity-তে এই broadcast শুনতে হবে এবং WebView-এ JS inject করতে হবে।
     * cmd values: "play", "pause", "stop", "next", "prev", "forward", "rewind"
     */
    private fun sendPlaybackBroadcast(cmd: String) {
        val broadcastIntent = Intent(BROADCAST_PLAYBACK_ACTION).apply {
            putExtra(EXTRA_PLAYBACK_CMD, cmd)
            setPackage(packageName)
        }
        sendBroadcast(broadcastIntent)
    }

    // ── Thumbnail Download ─────────────────────────────────────────────────────
    private fun downloadThumbnail(url: String): Bitmap? {
        return try {
            val connection = URL(url).openConnection().apply {
                connectTimeout = 5000
                readTimeout    = 5000
            }
            connection.getInputStream().use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (_: Exception) { null }
    }

    // ── WakeLock ───────────────────────────────────────────────────────────────
    private fun acquireWakeLock() {
        if (wakeLock != null) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "RasFocus:AudioPlayback"
        ).also {
            it.setReferenceCounted(false)
            it.acquire(3 * 60 * 60 * 1000L) // 3 ঘণ্টা max
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    // ── Audio Focus ────────────────────────────────────────────────────────────
    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.requestAudioFocus(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun releaseAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(null)
        }
    }

    private fun stopSelfClean() {
        releaseWakeLock()
        releaseAudioFocus()
        if (::mediaSession.isInitialized) {
            mediaSession.isActive = false
            mediaSession.release()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ── Notification Channel ───────────────────────────────────────────────────
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Background Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps audio playing when app is in background"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }
}