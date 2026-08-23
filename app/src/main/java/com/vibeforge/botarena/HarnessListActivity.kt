package com.vibeforge.botarena

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout

class HarnessListActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val (sv, root) = scrollScreen(this)
        val list = Store.harnesses(this)

        root.addView(eyebrow(this, "harnesses", Th.BETA))
        root.gap(dp(this, 8))
        root.addView(heading(this, "Your pilots"))
        root.gap(dp(this, 10))
        root.addView(
            body(
                this,
                "A harness is everything around the model: the prompt, the way the world is drawn for " +
                    "it, what it remembers, and the order the context arrives in."
            )
        )
        root.gap(dp(this, 22))

        for (i in list.indices) {
            val h = list[i]
            val card = panel(this)
            card.addView(heading(this, h.name, 19f))
            card.gap(dp(this, 6))
            card.addView(mono(this, h.model, Th.BETA, 12f))
            card.gap(dp(this, 8))

            val bits = ArrayList<String>()
            bits.add(h.driveLabel())
            bits.add(
                when (h.view) {
                    VIEW_JSON -> "JSON world"
                    VIEW_PROSE -> "described world"
                    else -> "ASCII world"
                }
            )
            bits.add(
                when (h.memory) {
                    MEM_NONE -> "no memory"
                    MEM_SCRATCH -> "scratchpad"
                    else -> "last " + h.memoryN + " turns"
                }
            )
            if (h.fog > 0) bits.add("fog " + h.fog)
            if (h.rules.isNotEmpty()) bits.add(h.rules.size.toString() + " standing orders")
            card.addView(body(this, bits.joinToString(" · "), Th.MUTED, 12.5f))
            card.gap(dp(this, 14))

            val r = row(this)
            r.addView(smallButton(this, "Edit") { open(i) })
            r.addView(
                smallButton(this, "Duplicate") {
                    list.add(h.copyOf())
                    Store.save(this, list)
                    render()
                },
                marginLeft(8)
            )
            r.addView(
                smallButton(this, "Delete") { confirmDelete(list, i) },
                marginLeft(8)
            )
            card.addView(r)

            root.addView(card, LinearLayout.LayoutParams(MATCH, WRAP))
            root.gap(dp(this, 12))
        }

        root.gap(dp(this, 10))
        root.addView(
            button(this, "New harness", true) {
                val fresh = Harness()
                fresh.name = "Harness " + (list.size + 1)
                list.add(fresh)
                Store.save(this, list)
                open(list.size - 1)
            }
        )
        root.gap(dp(this, 12))
        root.addView(button(this, "Back", false) { finish() })

        setContentView(sv)
    }

    private fun marginLeft(v: Int): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(WRAP, WRAP)
        p.leftMargin = dp(this, v)
        return p
    }

    private fun open(index: Int) {
        val i = Intent(this, HarnessEditorActivity::class.java)
        i.putExtra("index", index)
        startActivity(i)
    }

    private fun confirmDelete(list: ArrayList<Harness>, index: Int) {
        if (list.size <= 1) {
            toast(this, "Keep at least one harness.")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Delete " + list[index].name + "?")
            .setMessage("This removes the harness and everything configured in it.")
            .setPositiveButton("Delete") { _, _ ->
                list.removeAt(index)
                Store.save(this, list)
                render()
            }
            .setNegativeButton("Keep", null)
            .show()
    }
}
