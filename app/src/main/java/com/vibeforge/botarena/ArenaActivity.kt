package com.vibeforge.botarena

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class ArenaActivity : Activity() {

    private lateinit var world: World
    private val brains = ArrayList<Brain>()
    private val handler = Handler(Looper.getMainLooper())

    private var maxTurns = 40
    private var pace = 700
    private var running = false
    private var finished = false
    private var waiting = false
    private var failA = 0
    private var failB = 0
    private var pendingA: Decision? = null
    private var pendingB: Decision? = null

    private lateinit var arena: ArenaView
    private lateinit var transcript: TextView
    private lateinit var scroll: ScrollView
    private lateinit var status: TextView
    private lateinit var playButton: Button
    private lateinit var turnLabel: TextView
    private lateinit var hpFillA: View
    private lateinit var hpRestA: View
    private lateinit var hpFillB: View
    private lateinit var hpRestB: View
    private lateinit var banner: LinearLayout

    private val fullLog = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        maxTurns = intent.getIntExtra("maxTurns", 40)
        pace = intent.getIntExtra("pace", 700)
        val sizeIdx = intent.getIntExtra("size", 1)
        val w = when (sizeIdx) {
            0 -> 11
            2 -> 19
            else -> 15
        }
        val h = when (sizeIdx) {
            0 -> 11
            2 -> 15
            else -> 15
        }
        world = World(w, h, System.currentTimeMillis())

        val key = Store.apiKey(this)
        val list = Store.harnesses(this)
        brains.add(makeBrain(intent.getStringExtra("aBot"), intent.getIntExtra("aIndex", -1), list, key))
        brains.add(makeBrain(intent.getStringExtra("bBot"), intent.getIntExtra("bIndex", -1), list, key))

        buildUi()
        line("Turn limit " + maxTurns + ". " + brains[0].label + " takes Alpha, " + brains[1].label + " takes Beta.", Th.MUTED, false)
        line("", Th.MUTED, false)
        refresh()
        setRunning(true)
    }

    private fun makeBrain(bot: String?, index: Int, list: ArrayList<Harness>, key: String): Brain {
        if (bot != null) return ScriptedBrain(bot)
        if (index < 0 || index >= list.size) return ScriptedBrain("HUNTER")
        return LlmBrain(list[index], key)
    }

    /* ------------------------------------------------------------- layout */

    private fun buildUi() {
        val root = column(this)
        root.setBackgroundColor(Th.BG)
        root.setPadding(dp(this, 14), dp(this, 18), dp(this, 14), dp(this, 14))

        // scoreboard
        val board = row(this)
        board.addView(fighterColumn(0), LinearLayout.LayoutParams(0, WRAP, 1f))
        turnLabel = mono(this, "T1", Th.MUTED, 13f)
        turnLabel.gravity = Gravity.CENTER
        val tp = LinearLayout.LayoutParams(dp(this, 54), WRAP)
        board.addView(turnLabel, tp)
        board.addView(fighterColumn(1), LinearLayout.LayoutParams(0, WRAP, 1f))
        root.addView(board, LinearLayout.LayoutParams(MATCH, WRAP))

        root.gap(dp(this, 10))

        arena = ArenaView(this)
        arena.world = world
        root.addView(arena, LinearLayout.LayoutParams(MATCH, 0, 3.2f))

        root.gap(dp(this, 8))
        status = mono(this, "", Th.MUTED, 12f)
        root.addView(status, LinearLayout.LayoutParams(MATCH, WRAP))
        root.gap(dp(this, 8))

        banner = panel(this, Th.OK)
        banner.visibility = View.GONE
        root.addView(banner, LinearLayout.LayoutParams(MATCH, WRAP))

        scroll = ScrollView(this)
        scroll.background = rounded(Th.PANEL, 12f, this, Th.LINE)
        transcript = TextView(this)
        transcript.setTextColor(Th.TEXT)
        transcript.textSize = 11.5f
        transcript.typeface = Typeface.MONOSPACE
        transcript.setPadding(dp(this, 12), dp(this, 12), dp(this, 12), dp(this, 12))
        transcript.setTextIsSelectable(true)
        scroll.addView(transcript, LinearLayout.LayoutParams(MATCH, WRAP))
        root.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 2.4f))

        root.gap(dp(this, 10))
        val controls = row(this)
        playButton = button(this, "Pause", true) { setRunning(!running) }
        controls.addView(playButton, LinearLayout.LayoutParams(0, WRAP, 1f))
        controls.addView(
            smallButton(this, "Step") {
                setRunning(false)
                tick()
            },
            leftMargin(8)
        )
        controls.addView(smallButton(this, "Copy") { copyToClipboard(this, "match", fullLog.toString()) }, leftMargin(8))
        controls.addView(smallButton(this, "Exit") { finish() }, leftMargin(8))
        root.addView(controls, LinearLayout.LayoutParams(MATCH, WRAP))

        setContentView(root)
    }

    private fun leftMargin(v: Int): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(WRAP, WRAP)
        p.leftMargin = dp(this, v)
        return p
    }

    private fun fighterColumn(i: Int): LinearLayout {
        val accent = if (i == 0) Th.ALPHA else Th.BETA
        val col = column(this)
        col.addView(eyebrow(this, if (i == 0) "alpha" else "beta", accent))
        col.gap(dp(this, 4))
        val name = body(this, brains[i].label, Th.TEXT, 14f)
        name.maxLines = 1
        col.addView(name)
        col.addView(body(this, brains[i].detail, Th.MUTED, 11f))
        col.gap(dp(this, 8))

        val bar = row(this)
        bar.background = rounded(Th.PANEL2, 3f, this)
        val fill = View(this)
        fill.background = rounded(accent, 3f, this)
        val rest = View(this)
        bar.addView(fill, LinearLayout.LayoutParams(0, dp(this, 6), 100f))
        bar.addView(rest, LinearLayout.LayoutParams(0, dp(this, 6), 0f))
        col.addView(bar, LinearLayout.LayoutParams(MATCH, dp(this, 6)))

        if (i == 0) {
            hpFillA = fill
            hpRestA = rest
        } else {
            hpFillB = fill
            hpRestB = rest
        }
        return col
    }

    /* --------------------------------------------------------- match loop */

    private fun setRunning(on: Boolean) {
        if (finished) return
        running = on
        playButton.text = if (on) "Pause" else "Play"
        if (on) {
            banner.visibility = View.GONE
            tick()
        }
    }

    private fun tick() {
        if (finished || waiting) return
        waiting = true
        pendingA = null
        pendingB = null
        val remoteA = brains[0].isRemote
        val remoteB = brains[1].isRemote
        status.text = if (remoteA || remoteB) "waiting on the model…" else "resolving…"
        status.setTextColor(Th.MUTED)
        brains[0].decide(world, 0) { d ->
            pendingA = d
            ready()
        }
        brains[1].decide(world, 1) { d ->
            pendingB = d
            ready()
        }
    }

    private fun ready() {
        if (!waiting) return
        val a = pendingA ?: return
        val b = pendingB ?: return
        waiting = false
        apply(a, b)
    }

    private fun apply(a: Decision, b: Decision) {
        val t = world.turn
        line("── turn " + t + " ─────────────", Th.LINE, false)
        report(0, a)
        report(1, b)

        val actA = a.acts.firstOrNull() ?: Act("WAIT")
        val actB = b.acts.firstOrNull() ?: Act("WAIT")
        world.step(listOf(actA, actB))

        for (e in world.events) line("   " + e, Th.MUTED, false)
        line("", Th.MUTED, false)

        remember(0, t, actA)
        remember(1, t, actB)

        refresh()

        val down0 = world.bots[0].hp <= 0
        val down1 = world.bots[1].hp <= 0
        if (down0 && down1) {
            end("Mutual destruction", "Both robots took their last hit on the same turn.")
            return
        }
        if (down0 || down1) {
            val wi = if (down0) 1 else 0
            end(
                world.bots[wi].name + " wins",
                brains[wi].label + " leaves the arena on " + world.bots[wi].hp + " HP."
            )
            return
        }
        if (world.turn > maxTurns) {
            val h0 = world.bots[0].hp
            val h1 = world.bots[1].hp
            if (h0 == h1) {
                end("Draw", "The turn limit ran out with both robots on " + h0 + " HP.")
            } else {
                val wi = if (h0 > h1) 0 else 1
                end(
                    world.bots[wi].name + " wins on damage",
                    brains[wi].label + " finished ahead, " + maxOf(h0, h1) + " HP to " + minOf(h0, h1) + "."
                )
            }
            return
        }

        failA = if (a.error != null) failA + 1 else 0
        failB = if (b.error != null) failB + 1 else 0
        if (failA >= 3 || failB >= 3) {
            val alpha = failA >= 3
            val who = if (alpha) "Alpha" else "Beta"
            val why = (if (alpha) a.error else b.error) ?: "The model kept returning nothing usable."
            failA = 0
            failB = 0
            line("── paused ─────────────", Th.DANGER, true)
            halt(
                who + " isn't sending usable moves",
                why + "\n\nThat's three turns in a row, so the match stopped rather than keep spending " +
                    "calls. Check the model id in the harness editor — its Preview shows exactly what " +
                    "gets sent — or try a different model. Press Play to carry on anyway."
            )
            return
        }

        status.text = ""
        if (running) handler.postDelayed({ tick() }, pace.toLong())
    }

    private fun report(i: Int, d: Decision) {
        val accent = if (i == 0) Th.ALPHA else Th.BETA
        val who = if (i == 0) "ALPHA" else "BETA"
        val act = d.acts.firstOrNull()?.toString() ?: "WAIT"
        line(who + " ▸ " + act, accent, true)
        if (d.error != null) {
            line("   ! " + d.error, Th.DANGER, false)
        }
        if (d.log.isNotBlank()) {
            val trimmed = d.log.trim()
            val shown = if (trimmed.length > 600) trimmed.substring(0, 600) + " …" else trimmed
            for (l in shown.split("\n")) {
                if (l.isNotBlank()) line("   " + l.trim(), Th.MUTED, false)
            }
        }
        if (d.note != null) line("   note: " + d.note, Th.BETA, false)
    }

    private fun remember(i: Int, t: Int, act: Act) {
        val r = world.bots[i]
        val summary = "T" + t + " you did " + act + "; " + world.events.joinToString("; ")
        r.history.add(summary)
        while (r.history.size > 40) r.history.removeAt(0)
    }

    private fun refresh() {
        arena.invalidate()
        turnLabel.text = "T" + world.turn
        setHp(hpFillA, hpRestA, world.bots[0].hp)
        setHp(hpFillB, hpRestB, world.bots[1].hp)
    }

    private fun setHp(fill: View, rest: View, hp: Int) {
        val v = maxOf(0, minOf(100, hp)).toFloat()
        val pf = fill.layoutParams as LinearLayout.LayoutParams
        pf.weight = v
        fill.layoutParams = pf
        val pr = rest.layoutParams as LinearLayout.LayoutParams
        pr.weight = 100f - v
        rest.layoutParams = pr
    }

    private fun halt(title: String, detail: String) {
        running = false
        waiting = false
        status.text = ""
        playButton.text = "Play"

        banner.removeAllViews()
        banner.background = rounded(Th.PANEL, 14f, this, Th.DANGER)
        banner.visibility = View.VISIBLE
        banner.addView(eyebrow(this, "paused", Th.DANGER))
        banner.gap(dp(this, 6))
        banner.addView(heading(this, title, 18f))
        banner.gap(dp(this, 4))
        banner.addView(body(this, detail, Th.MUTED, 13f))
        banner.gap(dp(this, 12))
        val r = row(this)
        r.addView(smallButton(this, "Copy match log") { copyToClipboard(this, "match", fullLog.toString()) })
        r.addView(smallButton(this, "Exit") { finish() }, leftMargin(8))
        banner.addView(r)
    }

    private fun end(title: String, detail: String) {
        finished = true
        running = false
        waiting = false
        status.text = ""
        playButton.text = "Match over"
        line("── " + title.uppercase() + " ─────────────", Th.OK, true)

        banner.removeAllViews()
        banner.background = rounded(Th.PANEL, 14f, this, Th.OK)
        banner.visibility = View.VISIBLE
        banner.addView(eyebrow(this, "result", Th.OK))
        banner.gap(dp(this, 6))
        banner.addView(heading(this, title, 20f))
        banner.gap(dp(this, 4))
        banner.addView(body(this, detail, Th.MUTED, 13f))
        banner.gap(dp(this, 12))
        val r = row(this)
        r.addView(smallButton(this, "Rematch") { rematch() })
        r.addView(smallButton(this, "Copy match log") { copyToClipboard(this, "match", fullLog.toString()) }, leftMargin(8))
        banner.addView(r)
    }

    private fun rematch() {
        val i = intent
        finish()
        startActivity(i)
    }

    private fun line(text: String, color: Int, bold: Boolean) {
        fullLog.append(text).append("\n")
        val sp = SpannableString(text + "\n")
        sp.setSpan(ForegroundColorSpan(color), 0, sp.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (bold) sp.setSpan(StyleSpan(Typeface.BOLD), 0, sp.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        transcript.append(sp)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        for (b in brains) b.shutdown()
    }

    override fun onPause() {
        super.onPause()
        running = false
        if (::playButton.isInitialized && !finished) playButton.text = "Play"
    }
}
