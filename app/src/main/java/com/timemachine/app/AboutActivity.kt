package com.timemachine.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class AboutActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var bubble: TextView
    private lateinit var caught: TextView
    private var step = 0
    private var msgIndex = 0

    private val messages = arrayOf(
        "hey... you up? \uD83D\uDC40",
        "I miss you \uD83E\uDD7A",
        "ok forget it, ignore me \uD83D\uDE48",
        "who ate my biryani?? \uD83D\uDE24"
    )

    private fun dp(v: Int): Int = Ui.dp(this, v)

    private val loop = object : Runnable {
        override fun run() {
            when (step) {
                0 -> {
                    caught.alpha = 0f
                    bubble.text = messages[msgIndex % messages.size]
                    msgIndex++
                    bubble.setTextColor(Color.WHITE)
                    bubble.background = Ui.pill(this@AboutActivity, "#3A4BFF")
                    bubble.animate().alpha(1f).setDuration(400).start()
                    handler.postDelayed(this, 2200)
                }
                1 -> {
                    bubble.animate().alpha(0f).setDuration(200).withEndAction {
                        bubble.text = "\uD83D\uDEAB  This message was unsent"
                        bubble.setTextColor(Color.parseColor("#8A8FA8"))
                        bubble.background = Ui.pill(this@AboutActivity, "#2A2E45")
                        bubble.animate().alpha(1f).setDuration(300).start()
                    }.start()
                    handler.postDelayed(this, 1800)
                }
                else -> {
                    caught.animate().alpha(1f).setDuration(400).start()
                    handler.postDelayed(this, 2800)
                }
            }
            step = (step + 1) % 3
        }
    }

    private fun wrapParams(top: Int): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        p.topMargin = top
        return p
    }

    private fun label(s: String, size: Float, color: String, face: Typeface, gravity: Int): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = size
        t.setTextColor(Color.parseColor(color))
        t.typeface = face
        t.gravity = gravity
        return t
    }

    private fun newCard(): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.background = Ui.card(this)
        c.setPadding(dp(20), dp(18), dp(20), dp(20))
        return c
    }

    private fun tag(s: String): TextView {
        val t = label(s, 12f, "#9B8CFF", Typeface.create("sans-serif-condensed", Typeface.BOLD), Gravity.START)
        t.letterSpacing = 0.25f
        return t
    }

    private fun body(s: String): TextView {
        val t = label(s, 16f, "#DDE0EE", Typeface.create("sans-serif", Typeface.NORMAL), Gravity.START)
        t.setLineSpacing(dp(4).toFloat(), 1f)
        t.layoutParams = wrapParams(dp(12))
        return t
    }

    private fun headline(s: String): TextView {
        val t = label(s, 25f, "#FFFFFF", Typeface.create("serif", Typeface.ITALIC), Gravity.START)
        t.layoutParams = wrapParams(dp(8))
        return t
    }

    private fun addStep(card: LinearLayout, title: String, desc: String) {
        val t = label(title, 17f, "#FFFFFF", Typeface.create("sans-serif-medium", Typeface.NORMAL), Gravity.START)
        t.layoutParams = wrapParams(dp(14))
        val d = label(desc, 14f, "#A9AEC7", Typeface.create("sans-serif", Typeface.NORMAL), Gravity.START)
        card.addView(t)
        card.addView(d)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#0B0F1E")
        window.navigationBarColor = Color.parseColor("#2A1458")

        val scroll = ScrollView(this)
        scroll.background = Ui.background()
        scroll.isFillViewport = true

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(20), dp(32), dp(20), dp(40))
        scroll.addView(col)

        // ---------------- hero
        val clockParams = LinearLayout.LayoutParams(dp(210), dp(210))
        clockParams.gravity = Gravity.CENTER_HORIZONTAL
        col.addView(ClockView(this), clockParams)

        val title = label(
            "TIME MACHINE", 34f, "#FFFFFF",
            Typeface.create("sans-serif-black", Typeface.NORMAL), Gravity.CENTER
        )
        title.letterSpacing = 0.12f
        col.addView(title, wrapParams(dp(16)))

        col.addView(
            label(
                "Some messages shouldn't vanish in a blink.", 17f, "#C9B8FF",
                Typeface.create("serif", Typeface.ITALIC), Gravity.CENTER
            ),
            wrapParams(dp(6))
        )

        // ---------------- animated "unsent" demo
        val chat = newCard()
        chat.addView(tag("MESSENGER  \u00B7  3:07 AM"))
        bubble = TextView(this)
        bubble.textSize = 17f
        bubble.setPadding(dp(18), dp(12), dp(18), dp(12))
        bubble.alpha = 0f
        val bubbleParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        bubbleParams.topMargin = dp(14)
        chat.addView(bubble, bubbleParams)
        caught = label(
            "\u23EA  Time Machine caught it!", 15f, "#5CFFB0",
            Typeface.create("sans-serif-medium", Typeface.BOLD), Gravity.START
        )
        caught.alpha = 0f
        val caughtParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        caughtParams.topMargin = dp(14)
        chat.addView(caught, caughtParams)
        col.addView(chat, wrapParams(dp(22)))

        // ---------------- the hook
        val hook = newCard()
        hook.addView(tag("THE HOOK"))
        hook.addView(headline("Sent. Unsent. Gone."))
        hook.addView(
            body(
                "It's 3 AM. You're fast asleep. Meanwhile, somebody types a message... hits send... " +
                    "panics... and unsends it. \uD83D\uDE05\n\n" +
                    "Girls do it. Guys do it. Honestly, everybody does it. (No judgment. We've all been there.)\n\n" +
                    "Then you wake up and switch your data on. Messenger flashes the message for a tiny fraction " +
                    "of a second... and poof. Gone.\n\n" +
                    "You stare at your screen. \"Did I just see that?\" \uD83E\uDD14 \"What did it SAY?!\"\n\n" +
                    "You'll never know.\n\nUntil now."
            )
        )
        col.addView(hook, wrapParams(dp(16)))

        // ---------------- the vision
        val vision = newCard()
        vision.addView(tag("THE DREAM"))
        vision.addView(headline("Older than my first line of code."))
        vision.addView(
            body(
                "When I was a kid, I imagined an app.\n\n" +
                    "I had no coding knowledge. No tools. Just a phone and a picture in my head: an app that " +
                    "never looks away. A time machine for my screen, quietly remembering the last few minutes " +
                    "so that when something slips past me, I could simply rewind and see it.\n\n" +
                    "Years went by, but the idea never left me. I searched the Play Store. I searched the " +
                    "internet. Again and again. Nobody had made it.\n\n" +
                    "So one day I stopped waiting for someone else to build my dream...\n\n" +
                    "and I built it myself."
            )
        )
        col.addView(vision, wrapParams(dp(16)))

        // ---------------- how it works
        val how = newCard()
        how.addView(tag("HOW IT WORKS"))
        addStep(how, "\u23FA  Always watching", "Records your screen in tiny 30-second clips.")
        addStep(how, "\uD83D\uDDD1  Always forgetting", "Keeps only the last 5 minutes and deletes the rest.")
        addStep(how, "\u23EA  Rewind anytime", "Open the app, tap a clip, and watch what you missed.")
        col.addView(how, wrapParams(dp(16)))

        // ---------------- promise
        val promise = newCard()
        promise.addView(tag("MY PROMISE"))
        promise.addView(headline("No ads. Not now. Not ever."))
        promise.addView(
            body(
                "Time Machine doesn't run ads, and it never will. No pop-ups, no trackers, no selling " +
                    "your attention.\n\n" +
                    "\uD83D\uDD12  Your clips never leave your phone. The app doesn't even have permission " +
                    "to use the internet."
            )
        )
        col.addView(promise, wrapParams(dp(16)))

        // ---------------- support
        val support = newCard()
        support.addView(tag("KEEP THE DREAM AD-FREE"))
        support.addView(headline("Want to support it?"))
        support.addView(
            body(
                "Keeping an app free of ads takes time, coffee and a lot of love. If Time Machine ever " +
                    "saved you from a \"wait, what did they say?\" moment, any donation is welcome and " +
                    "means the world. \uD83D\uDC9C"
            )
        )
        support.addView(
            label(
                "bKash", 13f, "#E2136E",
                Typeface.create("sans-serif-condensed", Typeface.BOLD), Gravity.CENTER
            ),
            wrapParams(dp(18))
        )
        val number = label(
            "+8801905-903264", 26f, "#FFD27A",
            Typeface.create("sans-serif-medium", Typeface.NORMAL), Gravity.CENTER
        )
        number.letterSpacing = 0.05f
        number.setTextIsSelectable(true)
        support.addView(number, wrapParams(dp(2)))

        val copy = Ui.button(this, "Copy bKash number", "#E2136E")
        copy.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("bKash", "+8801905903264"))
            Toast.makeText(this, "Number copied. Thank you! \uD83D\uDC9C", Toast.LENGTH_LONG).show()
        }
        val copyParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50))
        copyParams.topMargin = dp(14)
        support.addView(copy, copyParams)
        col.addView(support, wrapParams(dp(16)))

        // ---------------- footer
        col.addView(
            label(
                "A childhood idea, finally real.", 16f, "#A9AEC7",
                Typeface.create("serif", Typeface.ITALIC), Gravity.CENTER
            ),
            wrapParams(dp(34))
        )
        col.addView(
            label(
                "Made with \u2665 by", 13f, "#8A8FA8",
                Typeface.create("sans-serif-condensed", Typeface.NORMAL), Gravity.CENTER
            ),
            wrapParams(dp(4))
        )
        col.addView(
            label(
                "Mohsin", 44f, "#FFD27A",
                Typeface.create("cursive", Typeface.NORMAL), Gravity.CENTER
            ),
            wrapParams(0)
        )
        col.addView(
            label(
                "Built with Claude", 12f, "#6F7490",
                Typeface.create("sans-serif-light", Typeface.NORMAL), Gravity.CENTER
            ),
            wrapParams(dp(2))
        )

        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        step = 0
        handler.post(loop)
    }

    override fun onPause() {
        handler.removeCallbacks(loop)
        bubble.animate().cancel()
        caught.animate().cancel()
        super.onPause()
    }
}
