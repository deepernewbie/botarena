package com.vibeforge.botarena

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object Blk {
    const val LEGEND = "LEGEND"
    const val MAP = "MAP"
    const val SELF = "SELF"
    const val ENEMY = "ENEMY"
    const val ACTIONS = "ACTIONS"
    const val LASTRESULT = "LASTRESULT"
    const val HISTORY = "HISTORY"
    const val NOTES = "NOTES"
    const val TACTICS = "TACTICS"

    val ALL = listOf(LEGEND, MAP, SELF, ENEMY, ACTIONS, LASTRESULT, HISTORY, NOTES, TACTICS)

    fun title(id: String): String = when (id) {
        LEGEND -> "How to read the world"
        MAP -> "The world"
        SELF -> "Your state"
        ENEMY -> "The enemy"
        ACTIONS -> "What you can do"
        LASTRESULT -> "What just happened"
        HISTORY -> "Recent turns"
        NOTES -> "Your own notes"
        TACTICS -> "Standing orders"
        else -> id
    }

    fun blurb(id: String): String = when (id) {
        LEGEND -> "Explains the symbols. Drop it if your prompt already does."
        MAP -> "The arena itself, in your chosen format."
        SELF -> "HP, energy, position, heading, cannon cooldown."
        ENEMY -> "Enemy position and HP, subject to fog."
        ACTIONS -> "The action list with energy costs."
        LASTRESULT -> "Every event from the previous turn."
        HISTORY -> "The last few turns, as far back as memory allows."
        NOTES -> "Whatever the model wrote to itself with NOTE:."
        TACTICS -> "Standing orders whose condition is true right now."
        else -> ""
    }
}

class Block(var id: String, var enabled: Boolean)

object Cond {
    const val ALWAYS = "ALWAYS"
    const val HP_BELOW = "HP_BELOW"
    const val ENERGY_BELOW = "ENERGY_BELOW"
    const val ENEMY_WITHIN = "ENEMY_WITHIN"
    const val ENEMY_BEYOND = "ENEMY_BEYOND"
    const val TURN_AFTER = "TURN_AFTER"
    const val HAS_SHOT = "HAS_SHOT"

    val ALL = listOf(ALWAYS, HP_BELOW, ENERGY_BELOW, ENEMY_WITHIN, ENEMY_BEYOND, TURN_AFTER, HAS_SHOT)

    fun label(c: String, v: Int): String = when (c) {
        ALWAYS -> "Always"
        HP_BELOW -> "My HP below $v"
        ENERGY_BELOW -> "My energy below $v"
        ENEMY_WITHIN -> "Enemy within $v tiles"
        ENEMY_BEYOND -> "Enemy further than $v tiles"
        TURN_AFTER -> "Turn $v or later"
        HAS_SHOT -> "I have a clear shot"
        else -> c
    }

    fun takesValue(c: String): Boolean = c != ALWAYS && c != HAS_SHOT
}

class Rule(var cond: String = Cond.HP_BELOW, var value: Int = 35, var text: String = "")

const val DRIVE_TURN = "PER_TURN"
const val DRIVE_QUEUE = "QUEUE"
const val DRIVE_STRATEGY = "STRATEGY"

const val VIEW_ASCII = "ASCII"
const val VIEW_JSON = "JSON"
const val VIEW_PROSE = "PROSE"

const val THINK_OFF = "OFF"
const val THINK_BRIEF = "BRIEF"
const val THINK_DEFAULT = "DEFAULT"

const val MEM_NONE = "NONE"
const val MEM_LAST_N = "LAST_N"
const val MEM_SCRATCH = "SCRATCHPAD"

val ALL_ACTIONS = listOf("MOVE", "TURN", "FIRE", "SCAN", "SHIELD", "WAIT")

fun defaultBlocks(): ArrayList<Block> {
    val out = ArrayList<Block>()
    for (id in Blk.ALL) out.add(Block(id, id != Blk.NOTES))
    return out
}

class Harness {
    var name = "New harness"
    var model = "openai/gpt-4o-mini"
    var temperature = 0.7
    var thinking = THINK_OFF
    var drive = DRIVE_TURN
    var queueSize = 4
    var view = VIEW_ASCII
    var memory = MEM_LAST_N
    var memoryN = 4
    var allowNotes = true
    var fog = 0
    var systemPrompt = DEFAULT_PROMPT
    var actions = ArrayList<String>(ALL_ACTIONS)
    var blocks = defaultBlocks()
    var rules = ArrayList<Rule>()

    fun copyOf(): Harness {
        val h = Harness()
        h.name = name + " copy"
        h.model = model
        h.temperature = temperature
        h.thinking = thinking
        h.drive = drive
        h.queueSize = queueSize
        h.view = view
        h.memory = memory
        h.memoryN = memoryN
        h.allowNotes = allowNotes
        h.fog = fog
        h.systemPrompt = systemPrompt
        h.actions = ArrayList(actions)
        h.blocks = ArrayList<Block>()
        for (b in blocks) h.blocks.add(Block(b.id, b.enabled))
        h.rules = ArrayList<Rule>()
        for (r in rules) h.rules.add(Rule(r.cond, r.value, r.text))
        return h
    }

    fun driveLabel(): String = when (drive) {
        DRIVE_QUEUE -> "queue of " + queueSize
        DRIVE_STRATEGY -> "one-shot strategy"
        else -> "one call per turn"
    }
}

const val DEFAULT_PROMPT =
    "You pilot a combat robot in a top-down tile arena. Your opponent is another robot piloted " +
        "by a rival model.\n\n" +
        "Play to win. Shots travel in straight lines only, so line up on the enemy's row or column " +
        "before firing, and use walls as cover while your cannon cools. Energy is the real " +
        "constraint: moving is cheap, firing is not, and waiting refills you faster than drifting " +
        "does.\n\n" +
        "Think briefly, then commit."

object Store {

    private const val PREFS = "botarena"

    private fun p(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun apiKey(c: Context): String = p(c).getString("key", "") ?: ""

    fun setApiKey(c: Context, v: String) {
        p(c).edit().putString("key", v.trim()).apply()
    }

    fun models(c: Context): List<String> {
        val raw = p(c).getString("models", "") ?: ""
        if (raw.isEmpty()) return FALLBACK_MODELS
        val out = ArrayList<String>()
        for (s in raw.split("\n")) if (s.isNotBlank()) out.add(s.trim())
        return if (out.isEmpty()) FALLBACK_MODELS else out
    }

    fun setModels(c: Context, list: List<String>) {
        p(c).edit().putString("models", list.joinToString("\n")).apply()
    }

    fun harnesses(c: Context): ArrayList<Harness> {
        val raw = p(c).getString("harnesses", "") ?: ""
        if (raw.isEmpty()) {
            val seeded = seed()
            save(c, seeded)
            return seeded
        }
        val out = ArrayList<Harness>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) out.add(fromJson(arr.getJSONObject(i)))
        } catch (e: Exception) {
            return seed()
        }
        if (out.isEmpty()) return seed()
        return out
    }

    fun save(c: Context, list: List<Harness>) {
        val arr = JSONArray()
        for (h in list) arr.put(toJson(h))
        p(c).edit().putString("harnesses", arr.toString()).apply()
    }

    private fun toJson(h: Harness): JSONObject {
        val o = JSONObject()
        o.put("name", h.name)
        o.put("model", h.model)
        o.put("temperature", h.temperature)
        o.put("thinking", h.thinking)
        o.put("drive", h.drive)
        o.put("queueSize", h.queueSize)
        o.put("view", h.view)
        o.put("memory", h.memory)
        o.put("memoryN", h.memoryN)
        o.put("allowNotes", h.allowNotes)
        o.put("fog", h.fog)
        o.put("systemPrompt", h.systemPrompt)
        val a = JSONArray()
        for (s in h.actions) a.put(s)
        o.put("actions", a)
        val b = JSONArray()
        for (blk in h.blocks) {
            val bo = JSONObject()
            bo.put("id", blk.id)
            bo.put("on", blk.enabled)
            b.put(bo)
        }
        o.put("blocks", b)
        val r = JSONArray()
        for (rule in h.rules) {
            val ro = JSONObject()
            ro.put("cond", rule.cond)
            ro.put("value", rule.value)
            ro.put("text", rule.text)
            r.put(ro)
        }
        o.put("rules", r)
        return o
    }

    private fun fromJson(o: JSONObject): Harness {
        val h = Harness()
        h.name = o.optString("name", "Harness")
        h.model = o.optString("model", "openai/gpt-4o-mini")
        h.temperature = o.optDouble("temperature", 0.7)
        h.thinking = o.optString("thinking", THINK_OFF)
        h.drive = o.optString("drive", DRIVE_TURN)
        h.queueSize = o.optInt("queueSize", 4)
        h.view = o.optString("view", VIEW_ASCII)
        h.memory = o.optString("memory", MEM_LAST_N)
        h.memoryN = o.optInt("memoryN", 4)
        h.allowNotes = o.optBoolean("allowNotes", true)
        h.fog = o.optInt("fog", 0)
        h.systemPrompt = o.optString("systemPrompt", DEFAULT_PROMPT)
        val a = o.optJSONArray("actions")
        if (a != null) {
            val list = ArrayList<String>()
            for (i in 0 until a.length()) list.add(a.optString(i))
            if (list.isNotEmpty()) h.actions = list
        }
        val b = o.optJSONArray("blocks")
        if (b != null && b.length() > 0) {
            val list = ArrayList<Block>()
            for (i in 0 until b.length()) {
                val bo = b.optJSONObject(i) ?: continue
                list.add(Block(bo.optString("id"), bo.optBoolean("on", true)))
            }
            for (id in Blk.ALL) {
                var found = false
                for (blk in list) if (blk.id == id) found = true
                if (!found) list.add(Block(id, false))
            }
            h.blocks = list
        }
        val r = o.optJSONArray("rules")
        if (r != null) {
            val list = ArrayList<Rule>()
            for (i in 0 until r.length()) {
                val ro = r.optJSONObject(i) ?: continue
                list.add(Rule(ro.optString("cond", Cond.ALWAYS), ro.optInt("value", 0), ro.optString("text", "")))
            }
            h.rules = list
        }
        return h
    }

    private fun seed(): ArrayList<Harness> {
        val out = ArrayList<Harness>()

        val a = Harness()
        a.name = "Brawler"
        a.drive = DRIVE_TURN
        a.view = VIEW_ASCII
        a.memory = MEM_LAST_N
        a.memoryN = 4
        a.systemPrompt = "You pilot a combat robot in a top-down tile arena, against a robot flown " +
            "by a rival model.\n\nYou are an aggressor. Close the distance, get onto the enemy's " +
            "row or column, and keep the pressure on. Your cannon needs a turn to cool between " +
            "shots — spend that turn repositioning, not standing still.\n\nBe decisive. One short " +
            "line of reasoning, then your action."
        a.rules.add(Rule(Cond.HP_BELOW, 30, "You are badly hurt. Break line of sight behind a wall and recharge before re-engaging."))
        out.add(a)

        val b = Harness()
        b.name = "Sniper"
        b.model = "anthropic/claude-3.5-haiku"
        b.drive = DRIVE_QUEUE
        b.queueSize = 3
        b.view = VIEW_JSON
        b.memory = MEM_SCRATCH
        b.memoryN = 6
        b.fog = 7
        b.systemPrompt = "You pilot a combat robot in a top-down tile arena, against a robot flown " +
            "by a rival model.\n\nYou fight at range. Hold a lane with a long clear line, keep " +
            "energy above 6, and fire the moment the enemy crosses your row or column. You cannot " +
            "always see them — scan when the picture goes stale.\n\nWrite yourself a NOTE: each " +
            "turn recording where you last saw the enemy and which way they were heading."
        b.rules.add(Rule(Cond.ENEMY_WITHIN, 2, "The enemy is on top of you. Back away to restore firing distance."))
        b.rules.add(Rule(Cond.ENERGY_BELOW, 5, "Energy is low. Wait to recharge rather than firing dry."))
        out.add(b)

        return out
    }

    val FALLBACK_MODELS = listOf(
        "anthropic/claude-3.5-haiku",
        "anthropic/claude-3.7-sonnet",
        "openai/gpt-4o-mini",
        "openai/gpt-4.1-mini",
        "google/gemini-2.0-flash-001",
        "meta-llama/llama-3.3-70b-instruct",
        "mistralai/mistral-small",
        "deepseek/deepseek-chat",
        "qwen/qwen-2.5-72b-instruct"
    )
}
