package com.vibeforge.botarena

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

class ArenaView(c: Context) : View(c) {

    var world: World? = null

    private val wall = Paint(Paint.ANTI_ALIAS_FLAG)
    private val floor = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val notch = Paint(Paint.ANTI_ALIAS_FLAG)
    private val beam = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        wall.color = Th.PANEL2
        floor.color = Th.LINE
        beam.strokeCap = Paint.Cap.ROUND
        ring.style = Paint.Style.STROKE
        setBackgroundColor(Th.BG)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val wo = world ?: return
        val cell = minOf(width.toFloat() / wo.w, height.toFloat() / wo.h)
        if (cell <= 0f) return
        val ox = (width - cell * wo.w) / 2f
        val oy = (height - cell * wo.h) / 2f
        val pad = cell * 0.08f

        // floor dots and walls
        for (y in 0 until wo.h) {
            for (x in 0 until wo.w) {
                val left = ox + x * cell
                val top = oy + y * cell
                if (wo.walls[y][x]) {
                    val r = RectF(left + pad, top + pad, left + cell - pad, top + cell - pad)
                    canvas.drawRoundRect(r, cell * 0.18f, cell * 0.18f, wall)
                } else {
                    canvas.drawCircle(left + cell / 2f, top + cell / 2f, cell * 0.045f, floor)
                }
            }
        }

        // shot traces from the turn that just resolved
        for (s in wo.shots) {
            val hit = s[4] == 1
            beam.color = if (hit) Th.DANGER else Th.MUTED
            beam.strokeWidth = if (hit) cell * 0.16f else cell * 0.08f
            beam.alpha = if (hit) 230 else 130
            canvas.drawLine(
                ox + s[0] * cell + cell / 2f,
                oy + s[1] * cell + cell / 2f,
                ox + s[2] * cell + cell / 2f,
                oy + s[3] * cell + cell / 2f,
                beam
            )
        }

        // robots
        for (i in wo.bots.indices) {
            val b = wo.bots[i]
            if (b.hp <= 0) continue
            val color = if (i == 0) Th.ALPHA else Th.BETA
            val left = ox + b.x * cell
            val top = oy + b.y * cell
            val inset = cell * 0.16f
            val r = RectF(left + inset, top + inset, left + cell - inset, top + cell - inset)
            bot.color = color
            canvas.drawRoundRect(r, cell * 0.24f, cell * 0.24f, bot)

            // heading notch
            notch.color = Th.BG
            val cx = left + cell / 2f
            val cy = top + cell / 2f
            val reach = cell * 0.30f
            val wide = cell * 0.15f
            val p = Path()
            when (b.dir) {
                Dir.N -> {
                    p.moveTo(cx, cy - reach); p.lineTo(cx - wide, cy - reach * 0.25f); p.lineTo(cx + wide, cy - reach * 0.25f)
                }
                Dir.S -> {
                    p.moveTo(cx, cy + reach); p.lineTo(cx - wide, cy + reach * 0.25f); p.lineTo(cx + wide, cy + reach * 0.25f)
                }
                Dir.E -> {
                    p.moveTo(cx + reach, cy); p.lineTo(cx + reach * 0.25f, cy - wide); p.lineTo(cx + reach * 0.25f, cy + wide)
                }
                Dir.W -> {
                    p.moveTo(cx - reach, cy); p.lineTo(cx - reach * 0.25f, cy - wide); p.lineTo(cx - reach * 0.25f, cy + wide)
                }
            }
            p.close()
            canvas.drawPath(p, notch)

            if (b.shield) {
                ring.color = color
                ring.strokeWidth = cell * 0.07f
                canvas.drawCircle(cx, cy, cell * 0.46f, ring)
            }
        }
    }
}
