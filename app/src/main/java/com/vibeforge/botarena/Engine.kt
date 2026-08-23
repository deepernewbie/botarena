package com.vibeforge.botarena

import java.util.Random

enum class Dir(val dx: Int, val dy: Int, val code: String) {
    N(0, -1, "N"), E(1, 0, "E"), S(0, 1, "S"), W(-1, 0, "W");

    fun opposite(): Dir = when (this) {
        N -> S
        S -> N
        E -> W
        W -> E
    }

    companion object {
        fun of(raw: String?): Dir? {
            if (raw == null) return null
            return when (raw.trim().uppercase()) {
                "N", "NORTH", "UP" -> N
                "S", "SOUTH", "DOWN" -> S
                "E", "EAST", "RIGHT" -> E
                "W", "WEST", "LEFT" -> W
                else -> null
            }
        }
    }
}

class Act(val kind: String, val dir: Dir? = null) {
    override fun toString(): String = if (dir != null) kind + " " + dir.code else kind
}

class Robot(val name: String, var x: Int, var y: Int, var dir: Dir) {
    var hp = 100
    var energy = 10
    var shield = false
    var cooldown = 0
    var scanTurn = -99
    var notes = ""
    val history = ArrayList<String>()
}

class World(val w: Int, val h: Int, seed: Long) {

    val walls = Array(h) { BooleanArray(w) }
    val bots = ArrayList<Robot>()
    val events = ArrayList<String>()
    val shots = ArrayList<IntArray>()
    var turn = 1

    init {
        val r = Random(seed)
        // Cover comes in short blocks, not confetti — a lone tile is nothing to hide behind.
        val blocks = maxOf(3, (w * h) / 26)
        var placed = 0
        var guard = 0
        while (placed < blocks && guard < 600) {
            guard++
            val x = 1 + r.nextInt(maxOf(1, w - 2))
            val y = 1 + r.nextInt(maxOf(1, h - 2))
            val long = 2 + r.nextInt(3)
            val horizontal = r.nextBoolean()
            for (k in 0 until long) {
                val bx = if (horizontal) x + k else x
                val by = if (horizontal) y else y + k
                if (bx in 0 until w && by in 0 until h) {
                    walls[by][bx] = true
                    walls[h - 1 - by][w - 1 - bx] = true
                }
            }
            placed++
        }
        // Guaranteed loop of open corridor, so neither robot can be sealed in.
        for (y in 1 until h - 1) {
            walls[y][1] = false
            walls[y][w - 2] = false
        }
        for (x in 1 until w - 1) {
            walls[1][x] = false
            walls[h - 2][x] = false
        }
        clearAround(1, 1)
        clearAround(w - 2, h - 2)
        bots.add(Robot("ALPHA", 1, 1, Dir.S))
        bots.add(Robot("BETA", w - 2, h - 2, Dir.N))
    }

    private fun clearAround(cx: Int, cy: Int) {
        for (y in cy - 1..cy + 1) {
            for (x in cx - 1..cx + 1) {
                if (x >= 0 && y >= 0 && x < w && y < h) walls[y][x] = false
            }
        }
    }

    fun free(x: Int, y: Int): Boolean = x >= 0 && y >= 0 && x < w && y < h && !walls[y][x]

    fun step(acts: List<Act>) {
        events.clear()
        shots.clear()
        for (b in bots) b.shield = false

        // Stationary actions first.
        for (i in bots.indices) {
            val b = bots[i]
            val a = acts[i]
            if (b.hp <= 0) continue
            when (a.kind) {
                "TURN" -> {
                    val d = a.dir
                    if (d != null) {
                        b.dir = d
                        events.add(b.name + " faces " + d.code)
                    } else {
                        events.add(b.name + " tried to turn without a direction")
                    }
                }
                "SHIELD" -> {
                    if (spend(b, COST_SHIELD)) {
                        b.shield = true
                        events.add(b.name + " raises a shield")
                    } else {
                        events.add(b.name + " has no energy for a shield")
                    }
                }
                "SCAN" -> {
                    if (spend(b, COST_SCAN)) {
                        b.scanTurn = turn
                        events.add(b.name + " pings a scan")
                    } else {
                        events.add(b.name + " has no energy to scan")
                    }
                }
                "WAIT" -> {
                    b.energy = minOf(MAX_ENERGY, b.energy + 2)
                    events.add(b.name + " holds position and recharges")
                }
            }
        }

        // Movement, resolved simultaneously.
        val nx = IntArray(bots.size)
        val ny = IntArray(bots.size)
        val mv = BooleanArray(bots.size)
        for (i in bots.indices) {
            val b = bots[i]
            val a = acts[i]
            nx[i] = b.x
            ny[i] = b.y
            if (b.hp <= 0 || a.kind != "MOVE") continue
            val d = a.dir ?: b.dir
            b.dir = d
            val tx = b.x + d.dx
            val ty = b.y + d.dy
            if (!free(tx, ty)) {
                events.add(b.name + " bumps a wall going " + d.code)
            } else if (!spend(b, COST_MOVE)) {
                events.add(b.name + " is too drained to move")
            } else {
                nx[i] = tx
                ny[i] = ty
                mv[i] = true
            }
        }
        if (bots.size == 2) {
            if (mv[0] && mv[1]) {
                if (nx[0] == nx[1] && ny[0] == ny[1]) {
                    mv[0] = false
                    mv[1] = false
                    events.add("Both robots reach for the same tile and neither moves")
                } else if (nx[0] == bots[1].x && ny[0] == bots[1].y &&
                    nx[1] == bots[0].x && ny[1] == bots[0].y
                ) {
                    mv[0] = false
                    mv[1] = false
                    events.add("The robots grind against each other and stall")
                }
            }
            for (i in 0..1) {
                val o = bots[1 - i]
                if (mv[i] && !mv[1 - i] && nx[i] == o.x && ny[i] == o.y) {
                    mv[i] = false
                    events.add(bots[i].name + " is blocked by " + o.name)
                }
            }
        }
        for (i in bots.indices) {
            if (mv[i]) {
                bots[i].x = nx[i]
                bots[i].y = ny[i]
            }
        }

        // Firing, after everyone has moved.
        for (i in bots.indices) {
            val b = bots[i]
            val a = acts[i]
            if (b.hp <= 0 || a.kind != "FIRE") continue
            if (b.cooldown > 0) {
                events.add(b.name + "'s cannon is still cooling")
                continue
            }
            if (!spend(b, COST_FIRE)) {
                events.add(b.name + " has no energy to fire")
                continue
            }
            val d = a.dir ?: b.dir
            b.dir = d
            b.cooldown = 2
            var cx = b.x
            var cy = b.y
            var hit = false
            var reach = 0
            while (reach < RANGE) {
                val px = cx + d.dx
                val py = cy + d.dy
                if (!free(px, py)) break
                cx = px
                cy = py
                reach++
                var target: Robot? = null
                for (o in bots) {
                    if (o !== b && o.hp > 0 && o.x == cx && o.y == cy) target = o
                }
                if (target != null) {
                    val dmg = if (target.shield) DMG_SHIELDED else DMG
                    target.hp = maxOf(0, target.hp - dmg)
                    hit = true
                    events.add(
                        b.name + " hits " + target.name + " for " + dmg +
                            (if (target.shield) " through a shield" else "") +
                            " (" + target.name + " at " + target.hp + " HP)"
                    )
                    break
                }
            }
            if (!hit) events.add(b.name + " fires " + d.code + " and hits nothing")
            shots.add(intArrayOf(b.x, b.y, cx, cy, if (hit) 1 else 0))
        }

        for (b in bots) {
            if (b.cooldown > 0) b.cooldown--
            b.energy = minOf(MAX_ENERGY, b.energy + 1)
        }
        turn++
    }

    private fun spend(b: Robot, n: Int): Boolean {
        if (b.energy < n) return false
        b.energy -= n
        return true
    }

    companion object {
        const val MAX_ENERGY = 12
        const val RANGE = 6
        const val DMG = 22
        const val DMG_SHIELDED = 9
        const val COST_MOVE = 1
        const val COST_FIRE = 3
        const val COST_SCAN = 2
        const val COST_SHIELD = 3
    }
}

fun dist(a: Robot, b: Robot): Int = Math.abs(a.x - b.x) + Math.abs(a.y - b.y)

/** The direction that gives a clear shot at [b], or null if there is no lane. */
fun clearLine(wo: World, a: Robot, b: Robot): Dir? {
    if (a.x == b.x && a.y != b.y) {
        val d = if (b.y < a.y) Dir.N else Dir.S
        var y = a.y
        var steps = 0
        while (steps < World.RANGE) {
            y += d.dy
            steps++
            if (!wo.free(a.x, y)) return null
            if (y == b.y) return d
        }
        return null
    }
    if (a.y == b.y && a.x != b.x) {
        val d = if (b.x < a.x) Dir.W else Dir.E
        var x = a.x
        var steps = 0
        while (steps < World.RANGE) {
            x += d.dx
            steps++
            if (!wo.free(x, a.y)) return null
            if (x == b.x) return d
        }
        return null
    }
    return null
}

private val COMPASS = listOf(Dir.N, Dir.E, Dir.S, Dir.W)

/**
 * First step of a shortest path from [a] to [b], or null if there is no route.
 * Greedy chasing walks into a wall pocket and oscillates forever; this does not.
 */
fun bfsStep(wo: World, a: Robot, b: Robot): Dir? {
    if (a.x == b.x && a.y == b.y) return null
    val cameFrom = Array(wo.h) { IntArray(wo.w) { -1 } }
    val seen = Array(wo.h) { BooleanArray(wo.w) }
    val queue = ArrayDeque<Int>()
    seen[a.y][a.x] = true
    queue.addLast(a.y * wo.w + a.x)
    var found = false
    while (queue.isNotEmpty()) {
        val cur = queue.removeFirst()
        val cx = cur % wo.w
        val cy = cur / wo.w
        if (cx == b.x && cy == b.y) {
            found = true
            break
        }
        for (i in COMPASS.indices) {
            val d = COMPASS[i]
            val nx = cx + d.dx
            val ny = cy + d.dy
            if (nx < 0 || ny < 0 || nx >= wo.w || ny >= wo.h) continue
            if (seen[ny][nx]) continue
            val isGoal = nx == b.x && ny == b.y
            if (wo.walls[ny][nx] && !isGoal) continue
            seen[ny][nx] = true
            cameFrom[ny][nx] = i
            queue.addLast(ny * wo.w + nx)
        }
    }
    if (!found) return null
    var x = b.x
    var y = b.y
    var guard = 0
    while (guard < wo.w * wo.h) {
        guard++
        val i = cameFrom[y][x]
        if (i < 0) return null
        val d = COMPASS[i]
        val px = x - d.dx
        val py = y - d.dy
        if (px == a.x && py == a.y) return d
        x = px
        y = py
    }
    return null
}

/** A walkable step toward (or away from) [b]. */
fun stepToward(wo: World, a: Robot, b: Robot, away: Boolean): Dir {
    if (!away) {
        val routed = bfsStep(wo, a, b)
        if (routed != null && wo.free(a.x + routed.dx, a.y + routed.dy)) return routed
    }
    // Retreating, or boxed in: take the free neighbour that best serves the goal.
    var best: Dir? = null
    var bestScore = Int.MIN_VALUE
    for (d in COMPASS) {
        val nx = a.x + d.dx
        val ny = a.y + d.dy
        if (!wo.free(nx, ny)) continue
        val gap = Math.abs(nx - b.x) + Math.abs(ny - b.y)
        val score = if (away) gap else -gap
        if (score > bestScore) {
            bestScore = score
            best = d
        }
    }
    return best ?: a.dir
}

/**
 * A step that closes the smaller axis gap, which is what puts two robots on the
 * same row or column. Without it, two chasers mirror each other down a diagonal
 * and never line up a shot.
 */
fun alignStep(wo: World, a: Robot, b: Robot, flip: Boolean = false): Dir? {
    val dx = b.x - a.x
    val dy = b.y - a.y
    if (dx == 0 || dy == 0) return null
    val horiz = if (dx > 0) Dir.E else Dir.W
    val vert = if (dy > 0) Dir.S else Dir.N
    val closeX = Math.abs(dx) <= Math.abs(dy)
    // Two robots running the same logic on a mirrored map will both close the same
    // axis on the same turn and simply trade places, forever. The flip breaks it.
    val order = if (closeX != flip) listOf(horiz, vert) else listOf(vert, horiz)
    for (d in order) if (wo.free(a.x + d.dx, a.y + d.dy)) return d
    return null
}

fun facing(a: Robot, b: Robot): Dir {
    val dx = b.x - a.x
    val dy = b.y - a.y
    return if (Math.abs(dx) >= Math.abs(dy)) {
        if (dx >= 0) Dir.E else Dir.W
    } else {
        if (dy >= 0) Dir.S else Dir.N
    }
}
