package com.vibeforge.botarena

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.Executors

class SettingsActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val (sv, root) = scrollScreen(this)

        root.addView(eyebrow(this, "settings", Th.ALPHA))
        root.gap(dp(this, 8))
        root.addView(heading(this, "OpenRouter"))
        root.gap(dp(this, 10))
        root.addView(
            body(
                this,
                "Your key stays on this phone and is sent only to openrouter.ai. Every turn a model " +
                    "harness plays costs a real call, so start with a cheap model while you tune a harness."
            )
        )
        root.gap(dp(this, 22))

        root.addView(sectionLabel(this, "API key", "Starts with sk-or-"))
        root.gap(dp(this, 8))
        val keyField = input(this, Store.apiKey(this), "sk-or-v1-…")
        root.addView(keyField, LinearLayout.LayoutParams(MATCH, WRAP))
        root.gap(dp(this, 12))

        val r = row(this)
        r.addView(
            button(this, "Save key", true) {
                Store.setApiKey(this, keyField.text.toString())
                status.text = "Key saved."
                status.setTextColor(Th.OK)
            }
        )
        r.addView(
            button(this, "Load model list", false) { fetchModels() },
            lp(WRAP, WRAP, 0, this).also { it.leftMargin = dp(this, 10) }
        )
        root.addView(r)

        root.gap(dp(this, 16))
        val saved = Store.models(this)
        status = body(this, saved.size.toString() + " models available for picking.", Th.MUTED, 13f)
        root.addView(status)

        root.gap(dp(this, 26))
        root.addView(divider(this), LinearLayout.LayoutParams(MATCH, dp(this, 1)))
        root.gap(dp(this, 20))

        root.addView(sectionLabel(this, "Keeping it cheap", null))
        root.gap(dp(this, 10))
        root.addView(
            body(
                this,
                "A per-turn harness calls the model once every turn, for both robots. Queue mode asks " +
                    "for several moves at once and spends a fraction as much. Strategy mode calls the " +
                    "model exactly once per match, then runs its policy locally — free after that first call."
            )
        )
        root.gap(dp(this, 20))
        root.addView(button(this, "Done", false) { finish() })

        setContentView(sv)
    }

    private fun fetchModels() {
        status.text = "Loading models…"
        status.setTextColor(Th.MUTED)
        val key = Store.apiKey(this)
        val main = Handler(Looper.getMainLooper())
        Executors.newSingleThreadExecutor().execute {
            var list: List<String>? = null
            var err: String? = null
            try {
                list = OpenRouter.listModels(key)
            } catch (e: Exception) {
                err = e.message ?: e.toString()
            }
            val l = list
            val e2 = err
            main.post {
                if (l != null) {
                    Store.setModels(this, l)
                    status.text = l.size.toString() + " models loaded."
                    status.setTextColor(Th.OK)
                } else {
                    status.text = "Could not load models: " + e2
                    status.setTextColor(Th.DANGER)
                }
            }
        }
    }
}
