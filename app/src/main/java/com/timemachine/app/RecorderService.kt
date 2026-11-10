package com.timemachine.app

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.DisplayMetrics
import android.view.Display
import android.widget.Toast
import java.io.File

/**
 * Records the screen in ~30 second clips, forever, and deletes clips older than 5 minutes.
 * Clips are swapped without a gap (new recorder starts before the old one stops).
 */
class RecorderService : Service() {

    companion object {
        const val ACTION_STOP = "com.timemachine.app.STOP"
        const val ACTION_FLUSH = "com.timemachine.app.FLUSH"
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        const val KEEP_MS = 5 * 60_000L

        private const val CHANNEL_ID = "timemachine"
        private const val NOTIF_ID = 1
        private const val SEGMENT_MS = 30_000L
        private const val MAX_FILES = 60

        @Volatile
        var running = false

        fun segmentDir(ctx: Context): File {
            val d = File(ctx.filesDir, "segments")
            if (!d.exists()) d.mkdirs()
            return d
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var display: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var curW = 0
    private var curH = 0
    private var dpi = 320
    private var started = false
    private var cleaned = false
    private var receiverRegistered = false
    private var noOverlap = false
    private var failures = 0

    private val rotateTask = Runnable { startSegment() }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (cleaned) return
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> finishCurrent()
                Intent.ACTION_SCREEN_ON -> startSegment()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopEverything()
                return START_NOT_STICKY
            }
            ACTION_FLUSH -> {
                if (started && !cleaned) startSegment() else stopSelf()
                return START_NOT_STICKY
            }
        }

        // Must be called right away, before anything else.
        startForeground(
            NOTIF_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        )
        if (started) return START_NOT_STICKY

        @Suppress("DEPRECATION")
        val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA)
        val code = intent?.getIntExtra(EXTRA_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        if (data == null || code != Activity.RESULT_OK) {
            stopEverything()
            return START_NOT_STICKY
        }

        try {
            cleaned = false
            noOverlap = false
            failures = 0
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val p = mpm.getMediaProjection(code, data)
            projection = p
            val cb = object : MediaProjection.Callback() {
                override fun onStop() {
                    handler.post { stopEverything() }
                }
            }
            projectionCallback = cb
            p.registerCallback(cb, handler)

            val filter = IntentFilter()
            filter.addAction(Intent.ACTION_SCREEN_OFF)
            filter.addAction(Intent.ACTION_SCREEN_ON)
            registerReceiver(screenReceiver, filter)
            receiverRegistered = true

            segmentDir(this).listFiles()?.forEach {
                if (it.name.endsWith(".tmp")) it.delete()
            }
            started = true
            running = true
            startSegment()
        } catch (e: Exception) {
            toast("Could not start recording")
            stopEverything()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- recording

    private fun startSegment() {
        handler.removeCallbacks(rotateTask)
        if (cleaned) return
        val proj = projection ?: return
        if (!isScreenOn()) {
            finishCurrent()
            return
        }

        val size = targetSize()
        val w = size.first
        val h = size.second

        var old = recorder
        var oldFile = currentFile
        val sizeChanged = display != null && (w != curW || h != curH)
        if (old != null && (noOverlap || sizeChanged)) {
            finishCurrent()
            old = null
            oldFile = null
        }

        val file = File(segmentDir(this), "seg_" + System.currentTimeMillis() + ".tmp")
        var created: MediaRecorder? = null
        var createdDisplay = false
        val newRec: MediaRecorder
        try {
            val r = buildRecorder(w, h, file)
            created = r
            val surface = r.surface
            val d = display
            if (d == null) {
                val nd = proj.createVirtualDisplay(
                    "TimeMachine", w, h, dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    surface, null, handler
                )
                if (nd == null) throw IllegalStateException("no virtual display")
                display = nd
                createdDisplay = true
                r.start()
            } else {
                if (w != curW || h != curH) d.resize(w, h, dpi)
                r.start()
                d.setSurface(surface)
            }
            newRec = r
        } catch (e: Exception) {
            safe { created?.release() }
            file.delete()
            if (createdDisplay) {
                safe { display?.release() }
                display = null
            }
            if (old != null) {
                // Probably this phone cannot run two encoders at once: switch to stop-then-start.
                noOverlap = true
                startSegment()
            } else {
                failures++
                if (failures >= 5) {
                    toast("Time Machine could not record on this phone")
                    stopEverything()
                } else {
                    handler.postDelayed(rotateTask, 3000)
                }
            }
            return
        }

        curW = w
        curH = h
        recorder = newRec
        currentFile = file
        failures = 0
        if (old != null && oldFile != null) finalizeRecorder(old, oldFile)
        handler.postDelayed(rotateTask, SEGMENT_MS)
    }

    private fun buildRecorder(w: Int, h: Int, file: File): MediaRecorder {
        @Suppress("DEPRECATION")
        val r = MediaRecorder()
        try {
            r.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            r.setVideoSize(w, h)
            r.setVideoFrameRate(15)
            r.setVideoEncodingBitRate(1_500_000)
            r.setOutputFile(file.absolutePath)
            r.prepare()
        } catch (e: Exception) {
            safe { r.release() }
            throw e
        }
        return r
    }

    /** Ends the current clip (if any) and detaches the display from it. */
    private fun finishCurrent() {
        handler.removeCallbacks(rotateTask)
        val r = recorder
        val f = currentFile
        recorder = null
        currentFile = null
        safe { display?.setSurface(null) }
        if (r != null && f != null) finalizeRecorder(r, f)
    }

    private fun finalizeRecorder(rec: MediaRecorder, file: File) {
        var ok = true
        try {
            rec.stop()
        } catch (e: Exception) {
            ok = false // happens if the screen never changed during the clip
        }
        safe { rec.release() }
        if (ok && file.length() > 2048) {
            val dest = File(file.path.removeSuffix(".tmp") + ".mp4")
            if (!file.renameTo(dest)) file.delete()
        } else {
            file.delete()
        }
        prune()
    }

    private fun prune() {
        val now = System.currentTimeMillis()
        val files = segmentDir(this).listFiles()
            ?.filter { it.name.endsWith(".mp4") }
            ?.sortedByDescending { it.name } ?: return
        files.forEachIndexed { i, f ->
            if (i >= MAX_FILES || now - f.lastModified() > KEEP_MS) f.delete()
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun targetSize(): Pair<Int, Int> {
        val dm = DisplayMetrics()
        val dmgr = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        @Suppress("DEPRECATION")
        dmgr.getDisplay(Display.DEFAULT_DISPLAY)?.getRealMetrics(dm)
        if (dm.widthPixels <= 0 || dm.heightPixels <= 0) dm.setTo(resources.displayMetrics)
        dpi = if (dm.densityDpi > 0) dm.densityDpi else 320
        val shorter = minOf(dm.widthPixels, dm.heightPixels).toFloat()
        val scale = if (shorter > 720f) 720f / shorter else 1f
        return Pair(align16(dm.widthPixels * scale), align16(dm.heightPixels * scale))
    }

    private fun align16(v: Float): Int {
        val a = ((v + 8f).toInt() / 16) * 16
        return if (a < 16) 16 else a
    }

    private fun isScreenOn(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isInteractive
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Time Machine", NotificationManager.IMPORTANCE_LOW)
        )
        val stopPi = PendingIntent.getService(
            this, 1,
            Intent(this, RecorderService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val openPi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE
        )
        @Suppress("DEPRECATION")
        val action = Notification.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPi
        ).build()
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Time Machine is recording")
            .setContentText("Keeping the last 5 minutes of your screen. Tap to replay.")
            .setContentIntent(openPi)
            .addAction(action)
            .setOngoing(true)
            .build()
    }

    private fun toast(msg: String) {
        handler.post { Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show() }
    }

    private fun safe(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun cleanup() {
        if (cleaned) return
        cleaned = true
        handler.removeCallbacksAndMessages(null)
        if (receiverRegistered) {
            safe { unregisterReceiver(screenReceiver) }
            receiverRegistered = false
        }
        finishCurrent()
        safe { display?.release() }
        display = null
        val p = projection
        val cb = projectionCallback
        projection = null
        projectionCallback = null
        if (p != null) {
            safe { if (cb != null) p.unregisterCallback(cb) }
            safe { p.stop() }
        }
        running = false
        started = false
    }

    private fun stopEverything() {
        cleanup()
        stopForeground(true)
        stopSelf()
    }
}
