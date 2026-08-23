package com.vibeforge.botarena

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import java.io.File

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installCrashReporter()
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val (sv, root) = scrollScreen(this)

        root.addView(eyebrow(this, "openrouter × tile combat", Th.ALPHA))
        root.gap(dp(this, 8))
        root.addView(heading(this, "Bot Arena", 34f))
        root.gap(dp(this, 10))
        root.addView(
            body(
                this,
                "You don't program the robot. You build the harness around a model — what it sees, " +
                    "what it remembers, what it's allowed to do — then send it into the arena and " +
                    "watch it work."
            )
        )
        root.gap(dp(this, 26))

        val harnessCount = Store.harnesses(this).size
        val keySet = Store.apiKey(this).isNotBlank()

        root.addView(
            bigButton(this, "New match", "Pick two fighters and start the clock", Th.ALPHA) {
                startActivity(Intent(this, MatchSetupActivity::class.java))
            }
        )
        root.gap(dp(this, 12))
        root.addView(
            bigButton(
                this, "Harnesses",
                harnessCount.toString() + " saved · prompt, world view, memory, flow", Th.BETA
            ) {
                startActivity(Intent(this, HarnessListActivity::class.java))
            }
        )
        root.gap(dp(this, 12))
        root.addView(
            bigButton(
                this, "OpenRouter key",
                if (keySet) "Saved on this device" else "Not set — matches with a model harness will fail",
                if (keySet) Th.OK else Th.DANGER
            ) {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
        )
        root.gap(dp(this, 12))
        root.addView(
            bigButton(this, "How the fight works", "Rules, energy, damage, cooldowns", Th.MUTED) {
                showRules()
            }
        )

        val crash = File(filesDir, "last-crash.txt")
        if (crash.exists()) {
            root.gap(dp(this, 24))
            val p = panel(this, Th.DANGER)
            p.addView(eyebrow(this, "last crash", Th.DANGER))
            p.gap(dp(this, 8))
            val text = try {
                crash.readText()
            } catch (e: Exception) {
                "Could not read the crash file: " + e.message
            }
            p.addView(mono(this, text.take(1400), Th.MUTED, 10f))
            p.gap(dp(this, 12))
            val r = row(this)
            r.addView(smallButton(this, "Copy") { copyToClipboard(this, "crash", text) })
            r.addView(
                smallButton(this, "Dismiss") {
                    crash.delete()
                    render()
                },
                lp(WRAP, WRAP, 0, this).also { it.leftMargin = dp(this, 8) }
            )
            p.addView(r)
            root.addView(p)
        }

        root.gap(dp(this, 28))
        val foot = mono(this, "v1 · built on VibeForge", Th.MUTED, 11f)
        foot.gravity = Gravity.CENTER
        root.addView(foot, LinearLayout.LayoutParams(MATCH, WRAP))

        setContentView(sv)
    }

    private fun showRules() {
        val text =
            "THE ARENA\n" +
                "A tile grid with walls. Two robots, 100 HP each, " + World.MAX_ENERGY + " energy max.\n\n" +
                "EVERY TURN\n" +
                "Both robots choose at the same time. Stationary actions resolve first, then movement, " +
                "then shots — so a robot can shoot the tile someone just walked into.\n\n" +
                "ACTIONS\n" +
                "MOVE  one tile, " + World.COST_MOVE + " energy\n" +
                "TURN  change facing, free\n" +
                "FIRE  straight beam, " + World.RANGE + " tiles, " + World.DMG + " damage, " +
                World.COST_FIRE + " energy, then one turn to cool\n" +
                "SCAN  reveals the enemy for two turns, " + World.COST_SCAN + " energy\n" +
                "SHIELD cuts a hit to " + World.DMG_SHIELDED + " damage, " + World.COST_SHIELD + " energy\n" +
                "WAIT  recharge 2 extra energy\n\n" +
                "ENERGY\n" +
                "Everyone regains 1 per turn, 3 if they waited. Firing dry is how most matches are lost.\n\n" +
                "WINNING\n" +
                "Drop the enemy to 0 HP, or hold more HP when the turn limit runs out.\n\n" +
                "COST\n" +
                "Every turn of a model harness is a real OpenRouter call. A 40-turn duel between two " +
                "per-turn harnesses is 80 calls. Queue mode and strategy mode cost far less."
        AlertDialog.Builder(this)
            .setTitle("How the fight works")
            .setMessage(text)
            .setPositiveButton("Got it", null)
            .show()
    }

    private fun installCrashReporter() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                File(filesDir, "last-crash.txt")
                    .writeText(android.util.Log.getStackTraceString(error))
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
