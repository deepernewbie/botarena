package com.vibeforge.botarena

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Instrument-panel palette: deep ink blue rather than black, amber for ALPHA,
 * violet for BETA. Data is always monospace; headings are light sans with wide
 * tracking, so the two robots never share a visual language with the chrome.
 */
object Th {
    val BG = Color.parseColor("#0F1220")
    val PANEL = Color.parseColor("#171B2E")
    val PANEL2 = Color.parseColor("#1F2540")
    val LINE = Color.parseColor("#2B3252")
    val TEXT = Color.parseColor("#EDEFF7")
    val MUTED = Color.parseColor("#8A90AE")
    val ALPHA = Color.parseColor("#E8A33D")
    val BETA = Color.parseColor("#C77DFF")
    val DANGER = Color.parseColor("#FF5C7A")
    val OK = Color.parseColor("#5AD1A0")
}

fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density).toInt()

fun rounded(color: Int, radiusDp: Float, c: Context, stroke: Int = 0): GradientDrawable {
    val g = GradientDrawable()
    g.setColor(color)
    g.cornerRadius = radiusDp * c.resources.displayMetrics.density
    if (stroke != 0) g.setStroke(dp(c, 1), stroke)
    return g
}

fun lp(w: Int, h: Int, topDp: Int = 0, c: Context? = null): LinearLayout.LayoutParams {
    val p = LinearLayout.LayoutParams(w, h)
    if (c != null && topDp != 0) p.topMargin = dp(c, topDp)
    return p
}

val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

fun column(c: Context): LinearLayout {
    val l = LinearLayout(c)
    l.orientation = LinearLayout.VERTICAL
    return l
}

fun row(c: Context): LinearLayout {
    val l = LinearLayout(c)
    l.orientation = LinearLayout.HORIZONTAL
    l.gravity = Gravity.CENTER_VERTICAL
    return l
}

/** Full-bleed screen with a scrolling body. Returns the body to fill. */
fun scrollScreen(c: Context): Pair<ScrollView, LinearLayout> {
    val sv = ScrollView(c)
    sv.setBackgroundColor(Th.BG)
    sv.isFillViewport = true
    val body = column(c)
    body.setPadding(dp(c, 20), dp(c, 28), dp(c, 20), dp(c, 40))
    sv.addView(body, LinearLayout.LayoutParams(MATCH, WRAP))
    return Pair(sv, body)
}

fun eyebrow(c: Context, s: String, color: Int = Th.MUTED): TextView {
    val t = TextView(c)
    t.text = s.uppercase()
    t.textSize = 11f
    t.setTextColor(color)
    t.letterSpacing = 0.22f
    t.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    return t
}

fun heading(c: Context, s: String, size: Float = 27f): TextView {
    val t = TextView(c)
    t.text = s
    t.textSize = size
    t.setTextColor(Th.TEXT)
    t.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    t.letterSpacing = 0.02f
    return t
}

fun body(c: Context, s: String, color: Int = Th.MUTED, size: Float = 14f): TextView {
    val t = TextView(c)
    t.text = s
    t.textSize = size
    t.setTextColor(color)
    t.setLineSpacing(dp(c, 4).toFloat(), 1f)
    return t
}

fun mono(c: Context, s: String, color: Int = Th.TEXT, size: Float = 12f): TextView {
    val t = TextView(c)
    t.text = s
    t.textSize = size
    t.setTextColor(color)
    t.typeface = Typeface.MONOSPACE
    return t
}

fun panel(c: Context, accent: Int = 0): LinearLayout {
    val l = column(c)
    l.background = rounded(Th.PANEL, 14f, c, if (accent != 0) accent else Th.LINE)
    l.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14))
    return l
}

fun bigButton(c: Context, text: String, sub: String?, accent: Int, onClick: () -> Unit): View {
    val l = column(c)
    l.background = rounded(Th.PANEL, 14f, c, Th.LINE)
    l.setPadding(dp(c, 18), dp(c, 16), dp(c, 18), dp(c, 16))
    l.isClickable = true
    l.setOnClickListener { onClick() }

    val head = row(c)
    val bar = View(c)
    bar.background = rounded(accent, 2f, c)
    head.addView(bar, LinearLayout.LayoutParams(dp(c, 3), dp(c, 18)))
    val t = TextView(c)
    t.text = text
    t.textSize = 17f
    t.setTextColor(Th.TEXT)
    t.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    val tp = LinearLayout.LayoutParams(WRAP, WRAP)
    tp.leftMargin = dp(c, 12)
    head.addView(t, tp)
    l.addView(head)

    if (sub != null) {
        val s = body(c, sub, Th.MUTED, 13f)
        val sp = LinearLayout.LayoutParams(MATCH, WRAP)
        sp.topMargin = dp(c, 6)
        sp.leftMargin = dp(c, 15)
        l.addView(s, sp)
    }
    return l
}

fun button(c: Context, text: String, primary: Boolean, onClick: () -> Unit): Button {
    val b = Button(c)
    b.text = text
    b.isAllCaps = false
    b.textSize = 15f
    b.setTextColor(if (primary) Th.BG else Th.TEXT)
    b.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    b.background = rounded(if (primary) Th.ALPHA else Th.PANEL2, 10f, c, if (primary) 0 else Th.LINE)
    b.setPadding(dp(c, 18), dp(c, 12), dp(c, 18), dp(c, 12))
    b.stateListAnimator = null
    b.setOnClickListener { onClick() }
    return b
}

fun smallButton(c: Context, text: String, onClick: () -> Unit): Button {
    val b = Button(c)
    b.text = text
    b.isAllCaps = false
    b.textSize = 13f
    b.setTextColor(Th.TEXT)
    b.background = rounded(Th.PANEL2, 8f, c, Th.LINE)
    b.minWidth = dp(c, 44)
    b.minimumWidth = dp(c, 44)
    b.setPadding(dp(c, 10), dp(c, 6), dp(c, 10), dp(c, 6))
    b.stateListAnimator = null
    b.setOnClickListener { onClick() }
    return b
}

fun input(c: Context, value: String, hint: String, multiline: Boolean = false): EditText {
    val e = EditText(c)
    e.setText(value)
    e.hint = hint
    e.setHintTextColor(Th.MUTED)
    e.setTextColor(Th.TEXT)
    e.textSize = 14f
    e.background = rounded(Th.PANEL2, 10f, c, Th.LINE)
    e.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12))
    if (multiline) {
        e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        e.gravity = Gravity.TOP or Gravity.START
        e.setLines(7)
        e.maxLines = 14
        e.isVerticalScrollBarEnabled = true
    } else {
        e.inputType = InputType.TYPE_CLASS_TEXT
        e.maxLines = 1
    }
    return e
}

fun numberInput(c: Context, value: Int): EditText {
    val e = input(c, value.toString(), "0")
    e.inputType = InputType.TYPE_CLASS_NUMBER
    e.typeface = Typeface.MONOSPACE
    return e
}

fun check(c: Context, text: String, on: Boolean, onChange: (Boolean) -> Unit): CheckBox {
    val cb = CheckBox(c)
    cb.text = text
    cb.isChecked = on
    cb.textSize = 14f
    cb.setTextColor(Th.TEXT)
    cb.setOnCheckedChangeListener { _, v -> onChange(v) }
    return cb
}

fun divider(c: Context): View {
    val v = View(c)
    v.setBackgroundColor(Th.LINE)
    return v
}

/** Segmented control. Rebuilds its own styling on selection. */
fun segmented(
    c: Context,
    options: List<String>,
    labels: List<String>,
    selected: String,
    onSelect: (String) -> Unit
): LinearLayout {
    val holder = row(c)
    holder.background = rounded(Th.PANEL2, 10f, c, Th.LINE)
    holder.setPadding(dp(c, 4), dp(c, 4), dp(c, 4), dp(c, 4))
    var current = selected
    val buttons = ArrayList<TextView>()

    fun restyle() {
        for (i in options.indices) {
            val on = options[i] == current
            val t = buttons[i]
            t.background = if (on) rounded(Th.ALPHA, 7f, c) else null
            t.setTextColor(if (on) Th.BG else Th.MUTED)
        }
    }

    for (i in options.indices) {
        val t = TextView(c)
        t.text = labels[i]
        t.textSize = 13f
        t.gravity = Gravity.CENTER
        t.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        t.setPadding(dp(c, 8), dp(c, 9), dp(c, 8), dp(c, 9))
        t.isClickable = true
        t.setOnClickListener {
            current = options[i]
            restyle()
            onSelect(current)
        }
        buttons.add(t)
        val p = LinearLayout.LayoutParams(0, WRAP, 1f)
        holder.addView(t, p)
    }
    restyle()
    return holder
}

fun sectionLabel(c: Context, title: String, hint: String?): LinearLayout {
    val l = column(c)
    l.addView(eyebrow(c, title))
    if (hint != null) {
        val h = body(c, hint, Th.MUTED, 12.5f)
        val p = LinearLayout.LayoutParams(MATCH, WRAP)
        p.topMargin = dp(c, 4)
        l.addView(h, p)
    }
    return l
}

fun copyToClipboard(c: Context, label: String, text: String) {
    val cm = c.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    cm.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
    toast(c, "Copied")
}

fun toast(c: Context, msg: String) {
    android.widget.Toast.makeText(c, msg, android.widget.Toast.LENGTH_SHORT).show()
}

fun LinearLayout.gap(px: Int) {
    val v = View(context)
    addView(v, LinearLayout.LayoutParams(MATCH, px))
}
