package com.timemachine.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.MediaPlayer
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.MediaController
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    companion object {
        private const val REQ_CAPTURE = 1001
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var toggle: Button
    private lateinit var video: VideoView
    private lateinit var list: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private var shown: List<File> = emptyList() // newest first
    private var playingName: String? = null

    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 3000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        val pad = (16 * density).toInt()

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(pad, pad, pad, pad)

        status = TextView(this)
        status.textSize = 16f
        status.setTextColor(Color.WHITE)
        status.setPadding(0, 0, 0, pad / 2)

        toggle = Button(this)
        toggle.setOnClickListener { onToggle() }

        val replay = Button(this)
        replay.text = "Replay last 5 minutes"
        replay.setOnClickListener {
            flush()
            handler.postDelayed({
                refresh()
                val oldest = listClips().firstOrNull()
                if (oldest != null) playFile(oldest)
                else Toast.makeText(this, "No clips yet", Toast.LENGTH_SHORT).show()
            }, 1500)
        }

        video = VideoView(this)
        val mc = MediaController(this)
        mc.setAnchorView(video)
        video.setMediaController(mc)
        video.setOnCompletionListener { playNext() }
        video.setOnErrorListener(MediaPlayer.OnErrorListener { _, _, _ ->
            playNext()
            true
        })

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList<String>())
        list = ListView(this)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ ->
            val f = shown.getOrNull(pos)
            if (f != null) playFile(f)
        }

        root.addView(status)
        root.addView(toggle)
        root.addView(replay)
        root.addView(
            video,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        root.addView(
            list,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (200 * density).toInt())
        )
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        flush() // finish the clip in progress so the newest seconds are playable
        handler.post(ticker)
        handler.postDelayed({ refresh() }, 1500)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        video.pause()
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        video.stopPlayback()
        super.onDestroy()
    }

    private fun onToggle() {
        if (RecorderService.running) {
            startService(Intent(this, RecorderService::class.java).setAction(RecorderService.ACTION_STOP))
            handler.postDelayed({ refresh() }, 700)
        } else {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_CAPTURE) return
        if (resultCode == RESULT_OK && data != null) {
            val i = Intent(this, RecorderService::class.java)
            i.putExtra(RecorderService.EXTRA_CODE, resultCode)
            i.putExtra(RecorderService.EXTRA_DATA, data)
            startForegroundService(i)
            handler.postDelayed({ refresh() }, 1000)
        } else {
            Toast.makeText(this, "Permission was not given", Toast.LENGTH_LONG).show()
        }
    }

    private fun flush() {
        if (RecorderService.running) {
            startService(Intent(this, RecorderService::class.java).setAction(RecorderService.ACTION_FLUSH))
        }
    }

    private fun listClips(): List<File> {
        return RecorderService.segmentDir(this).listFiles()
            ?.filter { it.name.endsWith(".mp4") }
            ?.sortedBy { it.name } ?: emptyList()
    }

    private fun playFile(f: File) {
        playingName = f.name
        video.setVideoPath(f.absolutePath)
        video.start()
    }

    private fun playNext() {
        val cur = playingName ?: return
        val next = listClips().firstOrNull { it.name > cur }
        if (next != null) {
            playFile(next)
        } else {
            Toast.makeText(this, "You have reached the present", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refresh() {
        val on = RecorderService.running
        toggle.text = if (on) "Stop recording" else "Start recording"
        val clips = listClips()
        shown = clips.reversed()

        val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val now = System.currentTimeMillis()
        val labels = shown.map { f ->
            val t = f.name.removePrefix("seg_").removeSuffix(".mp4").toLongOrNull() ?: f.lastModified()
            fmt.format(Date(t)) + "   (" + ago((now - t) / 1000) + ")"
        }
        adapter.clear()
        adapter.addAll(labels)
        adapter.notifyDataSetChanged()

        val head = if (on) "Recording. Keeping the last 5 minutes." else "Not recording."
        status.text = if (clips.isEmpty()) {
            head + "\nNo clips yet. The first appears within 30 seconds."
        } else {
            head + "\nTap a clip to play it and everything after it."
        }
    }

    private fun ago(sec: Long): String {
        val s = if (sec < 0) 0 else sec
        return if (s < 60) "${s}s ago" else "${s / 60}m ${s % 60}s ago"
    }
}
