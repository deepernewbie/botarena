package com.vibeforge.botarena

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView

class HarnessEditorActivity : Activity() {

    private var index = 0
    private lateinit var list: ArrayList<Harness>
    private lateinit var h: Harness

    private var nameField: EditText? = null
    private var promptField: EditText? = null
    private var tempField: EditText? = null
    private var queueField: EditText? = null
    private var memField: EditText? = null
    private var fogField: EditText? = null
    private val ruleFields = ArrayList<EditText>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        index = intent.getIntExtra("index", 0)
        list = Store.harnesses(this)
        if (index < 0 || index >= list.size) {
            toast(this, "That harness is gone.")
            finish()
            return
        }
        h = list[index]
        render()
    }

    /** Pull every text field into the model before anything rebuilds the screen. */
    private fun harvest() {
        nameField?.let { if (it.text.isNotBlank()) h.name = it.text.toString().trim() }
        promptField?.let { h.systemPrompt = it.text.toString() }
        tempField?.let { h.temperature = it.text.toString().toDoubleOrNull() ?: h.temperature }
        queueField?.let { h.queueSize = clamp(it.text.toString().toIntOrNull() ?: h.queueSize, 1, 8) }
        memField?.let { h.memoryN = clamp(it.text.toString().toIntOrNull() ?: h.memoryN, 1, 20) }
        fogField?.let { h.fog = clamp(it.text.toString().toIntOrNull() ?: h.fog, 0, 40) }
        for (i in ruleFields.indices) {
            if (i < h.rules.size) h.rules[i].text = ruleFields[i].text.toString()
        }
    }

    private fun clamp(v: Int, lo: Int, hi: Int): Int = maxOf(lo, minOf(hi, v))

    private fun rebuild() {
        harvest()
        render()
    }

    private fun save(andFinish: Boolean) {
        harvest()
        list[index] = h
        Store.save(this, list)
        if (andFinish) finish() else toast(this, "Saved")
    }

    private fun render() {
        ruleFields.clear()
        val (sv, root) = scrollScreen(this)

        root.addView(eyebrow(this, "harness", Th.BETA))
        root.gap(dp(this, 8))
        root.addView(heading(this, "Build the pilot"))
        root.gap(dp(this, 22))

        // ---- identity -------------------------------------------------
        root.addView(sectionLabel(this, "Name", null))
        root.gap(dp(this, 8))
        val nf = input(this, h.name, "Brawler")
        nameField = nf
        root.addView(nf, LinearLayout.LayoutParams(MATCH, WRAP))
        root.gap(dp(this, 18))

        root.addView(sectionLabel(this, "Model", "Any OpenRouter model id"))
        root.gap(dp(this, 8))
        val modelRow = row(this)
        val modelText = mono(this, h.model, Th.BETA, 13f)
        modelRow.addView(modelText, LinearLayout.LayoutParams(0, WRAP, 1f))
        modelRow.addView(smallButton(this, "Change") { pickModel(modelText) })
        root.addView(modelRow, LinearLayout.LayoutParams(MATCH, WRAP))
        root.gap(dp(this, 18))

        root.addView(sectionLabel(this, "Temperature", "Higher wanders more. 0.6–0.9 fights best."))
        root.gap(dp(this, 8))
        val tf = input(this, h.temperature.toString(), "0.7")
        tempField = tf
        root.addView(tf, LinearLayout.LayoutParams(MATCH, WRAP))
        root.gap(dp(this, 24))
        root.addView(divider(this), LinearLayout.LayoutParams(MATCH, dp(this, 1)))
        root.gap(dp(this, 20))

        // ---- drive ----------------------------------------------------
        root.addView(sectionLabel(this, "How the model drives", driveHint()))
        root.gap(dp(this, 10))
        root.addView(
            segmented(
                this,
                listOf(DRIVE_TURN, DRIVE_QUEUE, DRIVE_STRATEGY),
                listOf("Per turn", "Queue", "Strategy"),
                h.drive
            ) {
                h.drive = it
                rebuild()
            },
            LinearLayout.LayoutParams(MATCH, WRAP)
        )
        if (h.drive == DRIVE_QUEUE) {
            root.gap(dp(this, 12))
            root.addView(sectionLabel(this, "Actions per call", "They run blind, so more is riskier"))
            root.gap(dp(this, 8))
            val qf = numberInput(this, h.queueSize)
            queueField = qf
            root.addView(qf, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        root.gap(dp(this, 24))
        root.addView(divider(this), LinearLayout.LayoutParams(MATCH, dp(this, 1)))
        root.gap(dp(this, 20))

        // ---- world view -----------------------------------------------
        root.addView(sectionLabel(this, "World view", "How the arena is drawn in the prompt"))
        root.gap(dp(this, 10))
        root.addView(
            segmented(
                this,
                listOf(VIEW_ASCII, VIEW_JSON, VIEW_PROSE),
                listOf("ASCII map", "JSON", "Described"),
                h.view
            ) {
                h.view = it
                rebuild()
            },
            LinearLayout.LayoutParams(MATCH, WRAP)
        )
        root.gap(dp(this, 12))
        root.addView(sectionLabel(this, "Fog", "0 sees everything. Above 0, the enemy vanishes past that many tiles until a SCAN."))
        root.gap(dp(this, 8))
        val ff = numberInput(this, h.fog)
        fogField = ff
        root.addView(ff, LinearLayout.LayoutParams(MATCH, WRAP))
        root.gap(dp(this, 24))
        root.addView(divider(this), LinearLayout.LayoutParams(MATCH, dp(this, 1)))
        root.gap(dp(this, 20))

        // ---- memory ---------------------------------------------------
        root.addView(sectionLabel(this, "Memory", "Each call is otherwise a blank slate"))
        root.gap(dp(this, 10))
        root.addView(
            segmented(
                this,
                listOf(MEM_NONE, MEM_LAST_N, MEM_SCRATCH),
                listOf("None", "Last turns", "Scratchpad"),
                h.memory
            ) {
                h.memory = it
                rebuild()
            },
            LinearLayout.LayoutParams(MATCH, WRAP)
        )
        if (h.memory != MEM_NONE) {
            root.gap(dp(this, 12))
            root.addView(sectionLabel(this, "Turns carried forward", null))
            root.gap(dp(this, 8))
            val mf = numberInput(this, h.memoryN)
            memField = mf
            root.addView(mf, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        root.gap(dp(this, 8))
        root.addView(
            check(this, "Let it write NOTE: lines to itself", h.allowNotes) { h.allowNotes = it }
        )
        if (h.memory == MEM_SCRATCH && !h.allowNotes) {
            root.addView(body(this, "Scratchpad memory does nothing unless NOTE: lines are allowed.", Th.DANGER, 12.5f))
        }
        root.gap(dp(this, 24))
        root.addView(divider(this), LinearLayout.LayoutParams(MATCH, dp(this, 1)))
        root.gap(dp(this, 20))

        // ---- actions --------------------------------------------------
        root.addView(sectionLabel(this, "Action set", "Turn one off and the model is never told it exists"))
        root.gap(dp(this, 6))
        for (a in ALL_ACTIONS) {
            root.addView(
                check(this, actionLabel(a), h.actions.contains(a)) { on ->
                    if (on) {
                        if (!h.actions.contains(a)) h.actions.add(a)
                    } else {
                        h.actions.remove(a)
                        if (h.actions.isEmpty()) {
                            h.actions.add("WAIT")
                            toast(this, "A robot needs at least one action.")
                        }
                    }
                }
            )
        }
        root.gap(dp(this, 24))
        root.addView(divider(this), LinearLayout.LayoutParams(MATCH, dp(this, 1)))
        root.gap(dp(this, 20))

        // ---- flow -----------------------------------------------------
        root.addView(
            sectionLabel(
                this, "Context flow",
                "The prompt is assembled top to bottom. Move a block up to make the model read it first."
            )
        )
        root.gap(dp(this, 10))
        for (i in h.blocks.indices) {
            val blk = h.blocks[i]
            val cardAccent = if (blk.enabled) Th.BETA else 0
            val card = panel(this, cardAccent)
            val top = row(this)

            val num = mono(this, String.format("%02d", i + 1), if (blk.enabled) Th.BETA else Th.MUTED, 12f)
            top.addView(num)

            val titles = column(this)
            val t = body(this, Blk.title(blk.id), if (blk.enabled) Th.TEXT else Th.MUTED, 15f)
            titles.addView(t)
            titles.addView(body(this, Blk.blurb(blk.id), Th.MUTED, 12f))
            val tp = LinearLayout.LayoutParams(0, WRAP, 1f)
            tp.leftMargin = dp(this, 12)
            top.addView(titles, tp)

            top.addView(smallButton(this, "▲") { moveBlock(i, -1) })
            top.addView(smallButton(this, "▼") { moveBlock(i, 1) }, leftMargin(6))
            card.addView(top, LinearLayout.LayoutParams(MATCH, WRAP))
            card.gap(dp(this, 4))
            card.addView(
                check(this, if (blk.enabled) "Included" else "Left out", blk.enabled) { on ->
                    blk.enabled = on
                    rebuild()
                }
            )
            root.addView(card, LinearLayout.LayoutParams(MATCH, WRAP))
            root.gap(dp(this, 8))
        }
        root.gap(dp(this, 8))
        root.addView(button(this, "Preview the prompt", false) { previewPrompt() })
        root.gap(dp(this, 24))
        root.addView(divider(this), LinearLayout.LayoutParams(MATCH, dp(this, 1)))
        root.gap(dp(this, 20))

        // ---- standing orders ------------------------------------------
        root.addView(
            sectionLabel(
                this, "Standing orders",
                "Extra instructions that appear only when their condition is true. This is the closest " +
                    "thing to wiring behaviour without writing the behaviour."
            )
        )
        root.gap(dp(this, 10))
        for (i in h.rules.indices) {
            val rule = h.rules[i]
            val card = panel(this, Th.ALPHA)
            val top = row(this)
            top.addView(eyebrow(this, "when", Th.ALPHA))
            val condBtn = smallButton(this, Cond.label(rule.cond, rule.value)) { editCondition(i) }
            top.addView(condBtn, leftMargin(10))
            top.addView(mono(this, "", Th.MUTED, 12f), LinearLayout.LayoutParams(0, WRAP, 1f))
            top.addView(smallButton(this, "✕") {
                harvest()
                h.rules.removeAt(i)
                render()
            })
            card.addView(top, LinearLayout.LayoutParams(MATCH, WRAP))
            card.gap(dp(this, 10))
            val rf = input(this, rule.text, "Break line of sight and recharge.", true)
            rf.setLines(3)
            ruleFields.add(rf)
            card.addView(rf, LinearLayout.LayoutParams(MATCH, WRAP))
            root.addView(card, LinearLayout.LayoutParams(MATCH, WRAP))
            root.gap(dp(this, 8))
        }
        root.gap(dp(this, 4))
        root.addView(
            button(this, "Add a standing order", false) {
                harvest()
                h.rules.add(Rule())
                render()
            }
        )
        root.gap(dp(this, 24))
        root.addView(divider(this), LinearLayout.LayoutParams(MATCH, dp(this, 1)))
        root.gap(dp(this, 20))

        // ---- system prompt --------------------------------------------
        root.addView(sectionLabel(this, "System prompt", "Its character. The action format is appended for you."))
        root.gap(dp(this, 8))
        val pf = input(this, h.systemPrompt, "You pilot a combat robot…", true)
        promptField = pf
        root.addView(pf, LinearLayout.LayoutParams(MATCH, WRAP))
        root.gap(dp(this, 24))

        val bar = row(this)
        bar.addView(button(this, "Save", true) { save(true) }, LinearLayout.LayoutParams(0, WRAP, 1f))
        bar.addView(button(this, "Discard", false) { finish() }, leftMargin(10))
        root.addView(bar, LinearLayout.LayoutParams(MATCH, WRAP))

        setContentView(sv)
    }

    private fun leftMargin(v: Int): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(WRAP, WRAP)
        p.leftMargin = dp(this, v)
        return p
    }

    private fun actionLabel(a: String): String = when (a) {
        "MOVE" -> "MOVE — step one tile"
        "TURN" -> "TURN — change facing for free"
        "FIRE" -> "FIRE — straight beam, needs cooling"
        "SCAN" -> "SCAN — reveal the enemy through fog"
        "SHIELD" -> "SHIELD — soak one hit"
        else -> "WAIT — recharge faster"
    }

    private fun driveHint(): String = when (h.drive) {
        DRIVE_QUEUE -> "One call returns several moves that run in order. Cheaper, but the enemy moves in between."
        DRIVE_STRATEGY -> "One call before the match writes a policy of IF/THEN lines. It then runs on its own, with no further calls."
        else -> "One call, one move. The sharpest and the most expensive."
    }

    private fun moveBlock(i: Int, delta: Int) {
        val j = i + delta
        if (j < 0 || j >= h.blocks.size) return
        harvest()
        val tmp = h.blocks[i]
        h.blocks[i] = h.blocks[j]
        h.blocks[j] = tmp
        render()
    }

    private fun editCondition(ruleIndex: Int) {
        harvest()
        val rule = h.rules[ruleIndex]
        val labels = ArrayList<String>()
        for (c in Cond.ALL) labels.add(Cond.label(c, rule.value))
        AlertDialog.Builder(this)
            .setTitle("Trigger this order when…")
            .setItems(labels.toTypedArray()) { _, which ->
                rule.cond = Cond.ALL[which]
                if (Cond.takesValue(rule.cond)) askValue(rule) else render()
            }
            .show()
    }

    private fun askValue(rule: Rule) {
        val field = numberInput(this, rule.value)
        val wrap = LinearLayout(this)
        wrap.setPadding(dp(this, 24), dp(this, 8), dp(this, 24), dp(this, 8))
        wrap.addView(field, LinearLayout.LayoutParams(MATCH, WRAP))
        AlertDialog.Builder(this)
            .setTitle("Threshold")
            .setView(wrap)
            .setPositiveButton("Set") { _, _ ->
                rule.value = field.text.toString().toIntOrNull() ?: rule.value
                render()
            }
            .setNegativeButton("Cancel") { _, _ -> render() }
            .show()
    }

    private fun pickModel(target: android.widget.TextView) {
        val all = Store.models(this)
        val wrap = column(this)
        wrap.setPadding(dp(this, 20), dp(this, 12), dp(this, 20), dp(this, 4))
        val filter = input(this, "", "Filter, e.g. haiku")
        wrap.addView(filter, LinearLayout.LayoutParams(MATCH, WRAP))
        val sv = ScrollView(this)
        val listCol = column(this)
        sv.addView(listCol, LinearLayout.LayoutParams(MATCH, WRAP))
        wrap.addView(sv, LinearLayout.LayoutParams(MATCH, dp(this, 320)))

        val dialog = AlertDialog.Builder(this)
            .setTitle("Choose a model")
            .setView(wrap)
            .setNegativeButton("Cancel", null)
            .create()

        fun fill(q: String) {
            listCol.removeAllViews()
            var shown = 0
            for (m in all) {
                if (q.isNotEmpty() && !m.contains(q, true)) continue
                if (shown >= 120) break
                shown++
                val t = mono(this, m, Th.TEXT, 13f)
                t.setPadding(dp(this, 4), dp(this, 12), dp(this, 4), dp(this, 12))
                t.isClickable = true
                t.setOnClickListener {
                    h.model = m
                    target.text = m
                    dialog.dismiss()
                }
                listCol.addView(t, LinearLayout.LayoutParams(MATCH, WRAP))
            }
            if (shown == 0) {
                listCol.addView(body(this, "Nothing matches. Load the full list in Settings.", Th.MUTED, 13f))
            }
        }
        fill("")
        filter.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                fill(s?.toString()?.trim() ?: "")
            }

            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        dialog.show()
    }

    private fun previewPrompt() {
        harvest()
        val demo = World(13, 13, 7L)
        demo.bots[0].hp = 78
        demo.bots[0].energy = 8
        demo.bots[0].x = 4
        demo.bots[0].y = 6
        demo.bots[1].x = 8
        demo.bots[1].y = 6
        demo.turn = 9
        demo.events.add("BETA fires W and hits nothing")
        demo.bots[0].history.add("T8 you did MOVE E; BETA fires W and hits nothing")
        demo.bots[0].notes = "Enemy was heading north along column 8."

        val text = if (h.drive == DRIVE_STRATEGY) {
            "SYSTEM\n" + h.systemPrompt + "\n\n— — —\n\nUSER\n" + strategyRequest(h)
        } else {
            "SYSTEM\n" + h.systemPrompt + "\n\n— — —\n\nUSER\n" + buildUserMessage(h, demo, 0)
        }

        val sv = ScrollView(this)
        val col = column(this)
        col.setPadding(dp(this, 20), dp(this, 12), dp(this, 20), dp(this, 12))
        col.addView(mono(this, text, Th.TEXT, 11f))
        sv.addView(col, LinearLayout.LayoutParams(MATCH, WRAP))

        AlertDialog.Builder(this)
            .setTitle("What the model receives")
            .setView(sv)
            .setPositiveButton("Close", null)
            .setNeutralButton("Copy") { _, _ -> copyToClipboard(this, "prompt", text) }
            .show()
    }

    override fun onPause() {
        super.onPause()
        if (::h.isInitialized && ::list.isInitialized && index < list.size) {
            harvest()
            list[index] = h
            Store.save(this, list)
        }
    }
}
