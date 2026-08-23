package com.vibeforge.botarena

import org.json.JSONArray
import org.json.JSONObject

fun enemyVisible(wo: World, me: Robot, en: Robot, fog: Int): Boolean {
    if (fog <= 0) return true
    if (dist(me, en) <= fog) return true
    return me.scanTurn >= wo.turn - 2
}

fun asciiMap(wo: World, me: Robot, en: Robot, vis: Boolean): String {
    val sb = StringBuilder()
    sb.append("    ")
    for (x in 0 until wo.w) sb.append(x % 10)
    sb.append("\n")
    for (y in 0 until wo.h) {
        sb.append(String.format("%3d ", y))
        for (x in 0 until wo.w) {
            val c = when {
                me.x == x && me.y == y -> 'Y'
                vis && en.x == x && en.y == y && en.hp > 0 -> 'E'
                wo.walls[y][x] -> '#'
                else -> '.'
            }
            sb.append(c)
        }
        sb.append("\n")
    }
    return sb.toString()
}

fun jsonMap(wo: World, me: Robot, en: Robot, vis: Boolean): String {
    val o = JSONObject()
    o.put("grid", JSONObject().put("width", wo.w).put("height", wo.h))
    o.put("you", JSONObject().put("x", me.x).put("y", me.y).put("facing", me.dir.code))
    if (vis && en.hp > 0) {
        o.put("enemy", JSONObject().put("x", en.x).put("y", en.y).put("facing", en.dir.code))
    } else {
        o.put("enemy", "unknown")
    }
    val walls = JSONArray()
    for (y in 0 until wo.h) {
        for (x in 0 until wo.w) {
            if (wo.walls[y][x] && Math.abs(x - me.x) + Math.abs(y - me.y) <= 8) {
                walls.put(JSONArray().put(x).put(y))
            }
        }
    }
    o.put("walls_near_you", walls)
    o.put("axes", "x grows east, y grows south")
    return o.toString(1)
}

fun proseMap(wo: World, me: Robot, en: Robot, vis: Boolean): String {
    val sb = StringBuilder()
    sb.append("The arena is ").append(wo.w).append(" tiles wide and ").append(wo.h)
        .append(" tall. You stand at column ").append(me.x).append(", row ").append(me.y)
        .append(", facing ").append(dirWord(me.dir)).append(".\n")
    val open = ArrayList<String>()
    val blocked = ArrayList<String>()
    for (d in listOf(Dir.N, Dir.E, Dir.S, Dir.W)) {
        if (wo.free(me.x + d.dx, me.y + d.dy)) open.add(dirWord(d)) else blocked.add(dirWord(d))
    }
    if (open.isNotEmpty()) sb.append("Open ground lies to the ").append(open.joinToString(", ")).append(".\n")
    if (blocked.isNotEmpty()) sb.append("Walls block the ").append(blocked.joinToString(", ")).append(".\n")
    if (vis && en.hp > 0) {
        val dx = en.x - me.x
        val dy = en.y - me.y
        val parts = ArrayList<String>()
        if (dy < 0) parts.add((-dy).toString() + " north") else if (dy > 0) parts.add(dy.toString() + " south")
        if (dx < 0) parts.add((-dx).toString() + " west") else if (dx > 0) parts.add(dx.toString() + " east")
        if (parts.isEmpty()) parts.add("on your own tile")
        sb.append("The enemy is ").append(parts.joinToString(" and ")).append(" of you")
        val lane = clearLine(wo, me, en)
        if (lane != null) sb.append(", with a clear lane ").append(dirWord(lane)).append(" — you can hit them")
        sb.append(".\n")
    } else {
        sb.append("You cannot see the enemy from here.\n")
    }
    return sb.toString()
}

fun dirWord(d: Dir): String = when (d) {
    Dir.N -> "north"
    Dir.S -> "south"
    Dir.E -> "east"
    Dir.W -> "west"
}

fun actionMenu(h: Harness): String {
    val sb = StringBuilder()
    for (a in h.actions) {
        when (a) {
            "MOVE" -> sb.append("MOVE <N|S|E|W>  — one tile, ").append(World.COST_MOVE).append(" energy\n")
            "TURN" -> sb.append("TURN <N|S|E|W>  — change facing, free\n")
            "FIRE" -> sb.append("FIRE <N|S|E|W>  — straight beam up to ").append(World.RANGE)
                .append(" tiles, ").append(World.DMG).append(" damage, ")
                .append(World.COST_FIRE).append(" energy, one turn to cool\n")
            "SCAN" -> sb.append("SCAN            — reveals the enemy for two turns, ").append(World.COST_SCAN).append(" energy\n")
            "SHIELD" -> sb.append("SHIELD          — cuts incoming damage to ").append(World.DMG_SHIELDED)
                .append(" this turn, ").append(World.COST_SHIELD).append(" energy\n")
            "WAIT" -> sb.append("WAIT            — recharge 2 extra energy\n")
        }
    }
    return sb.toString()
}

fun activeTactics(h: Harness, wo: World, me: Robot, en: Robot): List<String> {
    val out = ArrayList<String>()
    for (r in h.rules) {
        if (r.text.isBlank()) continue
        val d = dist(me, en)
        val on = when (r.cond) {
            Cond.ALWAYS -> true
            Cond.HP_BELOW -> me.hp < r.value
            Cond.ENERGY_BELOW -> me.energy < r.value
            Cond.ENEMY_WITHIN -> d <= r.value
            Cond.ENEMY_BEYOND -> d > r.value
            Cond.TURN_AFTER -> wo.turn >= r.value
            Cond.HAS_SHOT -> clearLine(wo, me, en) != null
            else -> false
        }
        if (on) out.add(r.text)
    }
    return out
}

fun blockText(id: String, h: Harness, wo: World, meIdx: Int): String? {
    val me = wo.bots[meIdx]
    val en = wo.bots[1 - meIdx]
    val vis = enemyVisible(wo, me, en, h.fog)
    return when (id) {
        Blk.LEGEND -> when (h.view) {
            VIEW_ASCII -> "The map below uses # for walls, . for open floor, Y for you and E for the " +
                "enemy. Column numbers run across the top, row numbers down the left. x grows east, y grows south."
            VIEW_JSON -> "Coordinates are [x, y]. x grows east, y grows south. Walls are listed only near you."
            else -> "Everything is described in plain language relative to where you stand."
        }
        Blk.MAP -> when (h.view) {
            VIEW_JSON -> jsonMap(wo, me, en, vis)
            VIEW_PROSE -> proseMap(wo, me, en, vis)
            else -> asciiMap(wo, me, en, vis)
        }
        Blk.SELF -> {
            val lane = clearLine(wo, me, en)
            "Turn " + wo.turn + ". HP " + me.hp + "/100. Energy " + me.energy + "/" + World.MAX_ENERGY +
                ". Facing " + me.dir.code + ". Position (" + me.x + ", " + me.y + ")." +
                (if (me.cooldown > 0) " Your cannon is cooling and cannot fire this turn." else " Your cannon is ready.") +
                (if (lane != null && vis) " You currently have a clear shot to the " + dirWord(lane) + "." else "")
        }
        Blk.ENEMY -> if (!vis) {
            "Enemy position unknown. Your last scan has gone stale."
        } else {
            "Enemy at (" + en.x + ", " + en.y + "), HP " + en.hp + "/100, facing " + en.dir.code +
                ", " + dist(me, en) + " tiles away by walking distance."
        }
        Blk.ACTIONS -> actionMenu(h)
        Blk.LASTRESULT -> if (wo.events.isEmpty()) "Nothing yet — this is the opening turn." else wo.events.joinToString("\n")
        Blk.HISTORY -> {
            if (h.memory == MEM_NONE || me.history.isEmpty()) return null
            val n = maxOf(1, h.memoryN)
            val from = maxOf(0, me.history.size - n)
            me.history.subList(from, me.history.size).joinToString("\n")
        }
        Blk.NOTES -> if (me.notes.isBlank()) null else me.notes
        Blk.TACTICS -> {
            val t = activeTactics(h, wo, me, en)
            if (t.isEmpty()) null else t.joinToString("\n")
        }
        else -> null
    }
}

fun buildUserMessage(h: Harness, wo: World, meIdx: Int): String {
    val sb = StringBuilder()
    for (b in h.blocks) {
        if (!b.enabled) continue
        if (b.id == Blk.NOTES && h.memory != MEM_SCRATCH) continue
        if (b.id == Blk.HISTORY && h.memory == MEM_NONE) continue
        val body = blockText(b.id, h, wo, meIdx) ?: continue
        sb.append("## ").append(Blk.title(b.id)).append("\n").append(body.trimEnd()).append("\n\n")
    }
    sb.append("## Your move\n")
    sb.append(outputContract(h))
    return sb.toString()
}

fun outputContract(h: Harness): String {
    val verbs = h.actions.joinToString(", ")
    val sb = StringBuilder()
    if (h.drive == DRIVE_QUEUE) {
        sb.append("Decide up to ").append(h.queueSize).append(" actions, in the order they should run. ")
            .append("They execute blind — the enemy moves in between — so keep the later ones safe.\n")
    } else {
        sb.append("Decide exactly one action.\n")
    }
    sb.append("Write each one on its own line, at the very end of your reply, in this exact form:\n")
    sb.append("ACTION: MOVE N\n")
    sb.append("Valid verbs: ").append(verbs).append(". MOVE, TURN and FIRE take a direction: N, S, E or W.\n")
    if (h.allowNotes) {
        sb.append("You may add one line starting with NOTE: to leave a memo for your future self.\n")
    }
    sb.append("Keep any thinking to a sentence or two. A reply with no ACTION: line wastes the turn.")
    return sb.toString()
}

fun strategyRequest(h: Harness): String {
    return "Write your battle policy now, before the fight starts. You get one chance — it will run " +
        "unattended for the whole match with no further calls to you.\n\n" +
        "Give up to 8 lines, each exactly:\n" +
        "IF <condition> THEN <action>\n\n" +
        "Conditions: ALWAYS, ALIGNED (a clear shot exists), READY (cannon cool), DIST<n, DIST>n, HP<n, ENERGY<n\n" +
        "Actions: FIRE, SHIELD, SCAN, WAIT, TOWARD (step at the enemy), AWAY (step back), FACE (turn to the enemy), MOVE N|S|E|W\n\n" +
        "You may join two conditions with AND. The first line whose condition holds is the one that " +
        "runs, so put your sharpest reactions at the top and a fallback at the bottom.\n\n" +
        "Energy costs: fire " + World.COST_FIRE + ", shield " + World.COST_SHIELD + ", scan " +
        World.COST_SCAN + ", move " + World.COST_MOVE + ". You regain 1 per turn, 3 if you wait. " +
        "The cannon needs a turn to cool after firing.\n\n" +
        "Output only the policy lines, nothing else."
}
