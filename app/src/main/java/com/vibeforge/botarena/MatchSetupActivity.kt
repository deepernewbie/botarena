package com.vibeforge.botarena

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout

/** A fighter is either a saved harness (index >= 0) or a scripted bot. */
class Fighter(val harnessIndex: Int, val botStyle: String?) {
    fun isBot(): Boolean = botStyle != null
}

class MatchSetupActivity : Activity() {

    private var a = Fighter(0, null)
    private var b = Fighter(-1, "HUNTER")
    private var size = 1
    private var maxTurns = 40
    private var pace = 700

    private val botStyles = listOf("HUNTER", "CAMPER", "SCRAMBLER")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    private fun harnesses(): ArrayList<Harness> = Store.harnesses(this)

    private fun nameOf(f: Fighter): String {
        if (f.isBot()) {
            return when (f.botStyle) {
                "CAMPER" -> "Camper"
                "SCRAMBLER" -> "Scrambler"
                else -> "Hunter"
            }
        }
        val list = harnesses()
        if (f.harnessIndex < 0 || f.harnessIndex >= list.size) return "Missing harness"
        return list[f.harnessIndex].name
    }

    private fun detailOf(f: Fighter): String {
        if (f.isBot()) {
            return when (f.botStyle) {
                "CAMPER" -> "Scripted · holds a lane, shields when hurt · free"
                "SCRAMBLER" -> "Scripted · moves unpredictably, fires on sight · free"
                else -> "Scripted · closes distance and shoots · free"
            }
        }
        val list = harnesses()
        if (f.harnessIndex < 0 || f.harnessIndex >= list.size) return "Pick another fighter"
        val h = list[f.harnessIndex]
        return h.model + " · " + h.driveLabel()
    }

    private fun render() {
        val (sv, root) = scrollScreen(this)

        root.addView(eyebrow(this, "new match", Th.ALPHA))
        root.gap(dp(this, 8))
        root.addView(heading(this, "Set the board"))
        root.gap(dp(this, 22))

        root.addView(fighterCard("Alpha", Th.ALPHA, a) { pickFighter(true) })
        root.gap(dp(this, 10))
        val vs = mono(this, "versus", Th.MUTED, 12f)
        vs.gravity = android.view.Gravity.CENTER
        root.addView(vs, LinearLayout.LayoutParams(MATCH, WRAP))
        root.gap(dp(this, 10))
        root.addView(fighterCard("Beta", Th.BETA, b) { pickFighter(false) })

        root.gap(dp(this, 24))
        root.addView(sectionLabel(this, "Arena", null))
        root.gap(dp(this, 10))
        root.addView(
            segmented(
                this,
                listOf("0", "1", "2"),
                listOf("Tight 11×11", "Standard 15×15", "Open 19×15"),
                size.toString()
            ) { size = it.toInt() },
            LinearLayout.LayoutParams(MATCH, WRAP)
        )

        root.gap(dp(this, 18))
        root.addView(sectionLabel(this, "Turn limit", "Most HP left wins if nobody dies"))
        root.gap(dp(this, 10))
        root.addView(
            segmented(
                this,
                listOf("20", "40", "80"),
                listOf("20", "40", "80"),
                maxTurns.toString()
            ) { maxTurns = it.toInt() },
            LinearLayout.LayoutParams(MATCH, WRAP)
        )

        root.gap(dp(this, 18))
        root.addView(sectionLabel(this, "Pace", "Waiting time between turns. Model calls take as long as they take."))
        root.gap(dp(this, 10))
        root.addView(
            segmented(
                this,
                listOf("200", "700", "1500"),
                listOf("Fast", "Watchable", "Slow"),
                pace.toString()
            ) { pace = it.toInt() },
            LinearLayout.LayoutParams(MATCH, WRAP)
        )

        val needsKey = !a.isBot() || !b.isBot()
        if (needsKey && Store.apiKey(this).isBlank()) {
            root.gap(dp(this, 18))
            val warn = panel(this, Th.DANGER)
            warn.addView(eyebrow(this, "no api key", Th.DANGER))
            warn.gap(dp(this, 6))
            warn.addView(
                body(
                    this,
                    "A model harness is in this match but no OpenRouter key is saved. Add one in " +
                        "Settings, or set both fighters to scripted bots.",
                    Th.MUTED, 13f
                )
            )
            root.addView(warn, LinearLayout.LayoutParams(MATCH, WRAP))
        }

        root.gap(dp(this, 26))
        root.addView(button(this, "Start the match", true) { start() })
        root.gap(dp(this, 12))
        root.addView(button(this, "Back", false) { finish() })

        setContentView(sv)
    }

    private fun fighterCard(slot: String, accent: Int, f: Fighter, onTap: () -> Unit): LinearLayout {
        val card = panel(this, accent)
        card.isClickable = true
        card.setOnClickListener { onTap() }
        val top = row(this)
        top.addView(eyebrow(this, slot, accent))
        card.addView(top)
        card.gap(dp(this, 8))
        card.addView(heading(this, nameOf(f), 20f))
        card.gap(dp(this, 4))
        card.addView(body(this, detailOf(f), Th.MUTED, 12.5f))
        card.gap(dp(this, 10))
        card.addView(body(this, "Tap to change", accent, 12f))
        return card
    }

    private fun pickFighter(isA: Boolean) {
        val list = harnesses()
        val labels = ArrayList<String>()
        val picks = ArrayList<Fighter>()
        for (i in list.indices) {
            labels.add(list[i].name + "  ·  " + list[i].model)
            picks.add(Fighter(i, null))
        }
        for (s in botStyles) {
            val label = when (s) {
                "CAMPER" -> "Camper  ·  scripted"
                "SCRAMBLER" -> "Scrambler  ·  scripted"
                else -> "Hunter  ·  scripted"
            }
            labels.add(label)
            picks.add(Fighter(-1, s))
        }
        AlertDialog.Builder(this)
            .setTitle(if (isA) "Alpha fights as…" else "Beta fights as…")
            .setItems(labels.toTypedArray()) { _, which ->
                if (isA) a = picks[which] else b = picks[which]
                render()
            }
            .show()
    }

    private fun start() {
        val list = harnesses()
        if (!a.isBot() && (a.harnessIndex < 0 || a.harnessIndex >= list.size)) {
            toast(this, "Pick a fighter for Alpha.")
            return
        }
        if (!b.isBot() && (b.harnessIndex < 0 || b.harnessIndex >= list.size)) {
            toast(this, "Pick a fighter for Beta.")
            return
        }
        val i = Intent(this, ArenaActivity::class.java)
        i.putExtra("aIndex", a.harnessIndex)
        i.putExtra("aBot", a.botStyle)
        i.putExtra("bIndex", b.harnessIndex)
        i.putExtra("bBot", b.botStyle)
        i.putExtra("size", size)
        i.putExtra("maxTurns", maxTurns)
        i.putExtra("pace", pace)
        startActivity(i)
    }
}
