package com.vibeforge.botarena

import android.os.Handler
import android.os.Looper
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.regex.Pattern

class Decision(
    val acts: List<Act>,
    val log: String,
    val error: String? = null,
    val note: String? = null
)

interface Brain {
    val label: String
    val detail: String
    val isRemote: Boolean
    fun decide(wo: World, meIdx: Int, cb: (Decision) -> Unit)
    fun shutdown()
}

/* ---------------------------------------------------------------- parsing */

private val ACT_PATTERN: Pattern = Pattern.compile(
    "\\b(MOVE|TURN|FIRE|SCAN|SHIELD|WAIT)\\b" +
        "(?:[^A-Za-z0-9]{0,3}(NORTH|SOUTH|EAST|WEST|UP|DOWN|LEFT|RIGHT|N|S|E|W)\\b)?",
    Pattern.CASE_INSENSITIVE
)

private fun scan(text: String, allowed: List<String>, max: Int): List<Act> {
    val out = ArrayList<Act>()
    val m = ACT_PATTERN.matcher(text)
    while (m.find() && out.size < max) {
        val kind = m.group(1)?.uppercase(Locale.US) ?: continue
        if (!allowed.contains(kind)) continue
        val dir = Dir.of(m.group(2))
        if ((kind == "MOVE" || kind == "TURN" || kind == "FIRE") && dir == null) continue
        out.add(Act(kind, dir))
    }
    return out
}

/**
 * Models are told to put their actions on the last lines, so read the tail first
 * and only fall back to the whole reply if the tail holds nothing usable.
 */
fun parseActs(reply: String, allowed: List<String>, max: Int): List<Act> {
    val lines = reply.trim().split("\n")
    val tailFrom = maxOf(0, lines.size - (max + 3))
    val tail = lines.subList(tailFrom, lines.size).joinToString("\n")
    val fromTail = scan(tail, allowed, max)
    if (fromTail.isNotEmpty()) return fromTail
    return scan(reply, allowed, max)
}

fun parseNote(reply: String): String? {
    for (raw in reply.split("\n")) {
        val line = raw.trim()
        if (line.uppercase(Locale.US).startsWith("NOTE:")) {
            val body = line.substring(5).trim()
            if (body.isNotEmpty()) return body
        }
    }
    return null
}

/* ------------------------------------------------------------ scripted AI */

class ScriptedBrain(private val style: String) : Brain {

    override val label: String = when (style) {
        "CAMPER" -> "Camper"
        "SCRAMBLER" -> "Scrambler"
        else -> "Hunter"
    }

    override val detail: String = when (style) {
        "CAMPER" -> "holds a lane, shields when hurt"
        "SCRAMBLER" -> "moves unpredictably, fires on sight"
        else -> "closes distance and shoots"
    }

    override val isRemote = false

    private val rnd = java.util.Random()

    override fun decide(wo: World, meIdx: Int, cb: (Decision) -> Unit) {
        val me = wo.bots[meIdx]
        val en = wo.bots[1 - meIdx]
        val lane = clearLine(wo, me, en)
        val d = dist(me, en)

        val act: Act = when (style) {
            "CAMPER" -> {
                if (me.hp < 45 && me.energy >= World.COST_SHIELD && lane == null && d <= 3) Act("SHIELD")
                else if (lane != null && me.cooldown == 0 && me.energy >= World.COST_FIRE) Act("FIRE", lane)
                else if (me.energy < 6) Act("WAIT")
                else if (d > 8) Act("MOVE", stepToward(wo, me, en, false))
                else {
                    val al = alignStep(wo, me, en, rnd.nextBoolean())
                    if (al != null) Act("MOVE", al) else Act("TURN", facing(me, en))
                }
            }
            "SCRAMBLER" -> {
                if (lane != null && me.cooldown == 0 && me.energy >= World.COST_FIRE) Act("FIRE", lane)
                else if (me.energy < 3) Act("WAIT")
                else if (d > 6 || rnd.nextInt(100) < 45) {
                    val al = if (d <= 7) alignStep(wo, me, en, rnd.nextBoolean()) else null
                    Act("MOVE", al ?: stepToward(wo, me, en, false))
                } else {
                    val dirs = listOf(Dir.N, Dir.E, Dir.S, Dir.W).shuffled(rnd)
                    var pick = stepToward(wo, me, en, false)
                    for (c in dirs) if (wo.free(me.x + c.dx, me.y + c.dy)) {
                        pick = c
                        break
                    }
                    Act("MOVE", pick)
                }
            }
            else -> {
                if (lane != null && me.cooldown == 0 && me.energy >= World.COST_FIRE) Act("FIRE", lane)
                else if (me.energy < 2) Act("WAIT")
                else {
                    val al = if (d <= 7) alignStep(wo, me, en, rnd.nextBoolean()) else null
                    Act("MOVE", al ?: stepToward(wo, me, en, false))
                }
            }
        }
        // A small stumble. Two identical bots on a symmetric map otherwise mirror
        // each other into an unbreakable standoff.
        val final = if (act.kind == "MOVE" && rnd.nextInt(100) < 15) {
            var pick = act.dir
            for (c in listOf(Dir.N, Dir.E, Dir.S, Dir.W).shuffled(rnd)) {
                if (wo.free(me.x + c.dx, me.y + c.dy)) {
                    pick = c
                    break
                }
            }
            Act("MOVE", pick)
        } else {
            act
        }
        cb(Decision(listOf(final), label + " → " + final))
    }

    override fun shutdown() {}
}

/* ------------------------------------------------- one-shot policy runtime */

class Policy(val lines: List<Pair<String, String>>) {

    fun choose(wo: World, meIdx: Int): Pair<Act, String> {
        val me = wo.bots[meIdx]
        val en = wo.bots[1 - meIdx]
        for (p in lines) {
            if (test(p.first, wo, me, en)) return Pair(resolve(p.second, wo, me, en), p.first + " → " + p.second)
        }
        return Pair(Act("WAIT"), "no rule matched → WAIT")
    }

    private fun test(cond: String, wo: World, me: Robot, en: Robot): Boolean {
        val parts = cond.split(" AND ")
        for (raw in parts) {
            val c = raw.trim().uppercase(Locale.US)
            val ok = when {
                c == "ALWAYS" || c.isEmpty() -> true
                c == "ALIGNED" -> clearLine(wo, me, en) != null
                c == "READY" -> me.cooldown == 0
                c.startsWith("DIST<") -> dist(me, en) < num(c, 5)
                c.startsWith("DIST>") -> dist(me, en) > num(c, 5)
                c.startsWith("HP<") -> me.hp < num(c, 3)
                c.startsWith("ENERGY<") -> me.energy < num(c, 7)
                else -> false
            }
            if (!ok) return false
        }
        return true
    }

    private fun num(c: String, from: Int): Int =
        c.substring(from).filter { it.isDigit() }.toIntOrNull() ?: 0

    private fun resolve(action: String, wo: World, me: Robot, en: Robot): Act {
        val a = action.trim().uppercase(Locale.US)
        return when {
            a.startsWith("FIRE") -> Act("FIRE", clearLine(wo, me, en) ?: facing(me, en))
            a.startsWith("TOWARD") -> Act("MOVE", stepToward(wo, me, en, false))
            a.startsWith("AWAY") -> Act("MOVE", stepToward(wo, me, en, true))
            a.startsWith("FACE") -> Act("TURN", facing(me, en))
            a.startsWith("MOVE") -> Act("MOVE", Dir.of(a.removePrefix("MOVE").trim()) ?: stepToward(wo, me, en, false))
            a.startsWith("SHIELD") -> Act("SHIELD")
            a.startsWith("SCAN") -> Act("SCAN")
            else -> Act("WAIT")
        }
    }

    companion object {
        fun parse(text: String): Policy {
            val out = ArrayList<Pair<String, String>>()
            for (raw in text.split("\n")) {
                val line = raw.trim().trim('-', '*', '`', '.').trim()
                val up = line.uppercase(Locale.US)
                if (!up.startsWith("IF ") || !up.contains(" THEN ")) continue
                val idx = up.indexOf(" THEN ")
                val cond = up.substring(3, idx).trim()
                val act = up.substring(idx + 6).trim()
                if (cond.isNotEmpty() && act.isNotEmpty()) out.add(Pair(cond, act))
                if (out.size >= 12) break
            }
            if (out.isEmpty()) {
                out.add(Pair("ALIGNED AND READY", "FIRE"))
                out.add(Pair("ENERGY<3", "WAIT"))
                out.add(Pair("ALWAYS", "TOWARD"))
            }
            return Policy(out)
        }
    }
}

/* ------------------------------------------------------------- LLM harness */

class LlmBrain(private val h: Harness, private val apiKey: String) : Brain {

    override val label: String = h.name
    override val detail: String = h.model + " · " + h.driveLabel()
    override val isRemote = true

    private val exec: ExecutorService = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val queue = ArrayList<Act>()
    private var policy: Policy? = null

    override fun decide(wo: World, meIdx: Int, cb: (Decision) -> Unit) {
        val me = wo.bots[meIdx]

        if (h.drive == DRIVE_STRATEGY) {
            val p = policy
            if (p != null) {
                val picked = p.choose(wo, meIdx)
                cb(Decision(listOf(picked.first), "policy: " + picked.second))
                return
            }
            call(strategyRequest(h), h.systemPrompt) { reply, err ->
                if (err != null) {
                    cb(Decision(listOf(Act("WAIT")), "", err))
                } else {
                    val parsed = Policy.parse(reply)
                    policy = parsed
                    val shown = StringBuilder("policy written:\n")
                    for (l in parsed.lines) shown.append("  IF ").append(l.first).append(" THEN ").append(l.second).append("\n")
                    cb(Decision(listOf(Act("WAIT")), shown.toString().trimEnd()))
                }
            }
            return
        }

        if (queue.isNotEmpty()) {
            val next = queue.removeAt(0)
            cb(Decision(listOf(next), "from the queued plan → " + next))
            return
        }

        val user = buildUserMessage(h, wo, meIdx)
        call(user, h.systemPrompt) { reply, err ->
            if (err != null) {
                cb(Decision(listOf(Act("WAIT")), "", err))
                return@call
            }
            val max = if (h.drive == DRIVE_QUEUE) maxOf(1, h.queueSize) else 1
            val acts = parseActs(reply, h.actions, max)
            val note = if (h.allowNotes) parseNote(reply) else null
            if (note != null && h.memory == MEM_SCRATCH) me.notes = note
            if (acts.isEmpty()) {
                cb(Decision(listOf(Act("WAIT")), reply, "No usable action found in the reply — holding position.", note))
                return@call
            }
            for (i in 1 until acts.size) queue.add(acts[i])
            cb(Decision(listOf(acts[0]), reply, null, note))
        }
    }

    private fun call(user: String, system: String, done: (String, String?) -> Unit) {
        exec.execute {
            var reply = ""
            var error: String? = null
            try {
                reply = OpenRouter.chat(apiKey, h.model, h.temperature, system, user)
            } catch (e: Exception) {
                error = e.message ?: e.toString()
            }
            val r = reply
            val er = error
            main.post { done(r, er) }
        }
    }

    override fun shutdown() {
        exec.shutdownNow()
    }
}
