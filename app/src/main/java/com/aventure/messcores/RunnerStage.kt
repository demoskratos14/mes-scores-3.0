package com.aventure.messcores

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/*
 * Le décor 3D de la Course : une [Scene3D] reconstruite à chaque image d'après l'état de [RunnerGame] (voie, obstacles, bonus,
 * décor de gare qui défile), et la caméra. Tout est en Kotlin pur (les maillages arrivent par une fonction), donc le rendu
 * se vérifie sans écran. Repère : le coureur est toujours au même endroit de la vue ; le monde tourne autour de lui aux virages.
 */

/** La caméra de la Course et la projection d'un point du monde sur la vue (pour poser le sprite du coureur). */
internal object RunnerView {
    private const val EYE_Y = 2.5f
    private const val EYE_Z = 5.8f
    private const val LOOK_Y = 0.85f
    private const val LOOK_Z = -6.5f
    const val FOV = 52f

    /** Part du déplacement latéral du coureur que la caméra suit (elle glisse un peu avec lui). */
    private const val FOLLOW = 0.5f

    /** Distance, devant la caméra, au-delà de laquelle un sommet est ignoré par le moteur (voir [Camera3D.near]). */
    const val NEAR = 0.3f

    /** Tout ce qui dépasse cette coordonnée z (vers la caméra) a des sommets trop près : on ne le dessine pas entier. */
    const val NEAR_Z = EYE_Z - NEAR - 0.1f

    fun camera(laneX: Float, camera: Camera3D = Camera3D()): Camera3D {
        camera.eyeX = laneX * FOLLOW
        camera.eyeY = EYE_Y
        camera.eyeZ = EYE_Z
        camera.targetX = laneX * FOLLOW
        camera.targetY = LOOK_Y
        camera.targetZ = LOOK_Z
        camera.fovDegrees = FOV
        camera.near = NEAR
        return camera
    }

    /**
     * Où le point (wx, wy, wz) du monde tombe sur une vue de [width] × [height] pixels : [x, y, pixelsParMètre], ou null s'il est
     * derrière la caméra. C'est le même calcul que le moteur (voir Renderer3D).
     */
    fun project(cam: Camera3D, wx: Float, wy: Float, wz: Float, width: Float, height: Float): FloatArray? {
        var fx = cam.targetX - cam.eyeX
        var fy = cam.targetY - cam.eyeY
        var fz = cam.targetZ - cam.eyeZ
        val fl = kotlin.math.sqrt(fx * fx + fy * fy + fz * fz)
        fx /= fl; fy /= fl; fz /= fl
        var rx = -fz
        var rz = fx
        val rl = kotlin.math.sqrt(rx * rx + rz * rz)
        rx /= rl; rz /= rl
        val ux = 0f * fz - rz * fy
        val uy = rz * fx - rx * fz
        val uz = rx * fy - 0f * fx
        val dx = wx - cam.eyeX
        val dy = wy - cam.eyeY
        val dz = wz - cam.eyeZ
        val zc = dx * fx + dy * fy + dz * fz
        if (zc <= cam.near) return null
        val xc = dx * rx + dz * rz
        val yc = dx * ux + dy * uy + dz * uz
        val focal = (height / 2f) / tan(cam.fovDegrees * (PI.toFloat() / 180f) / 2f)
        return floatArrayOf(width / 2f + xc / zc * focal, height / 2f - yc / zc * focal, focal / zc)
    }

    /** La hauteur de l'horizon sur la vue, en part de la hauteur (0 en haut, 1 en bas). */
    fun horizon(cam: Camera3D): Float {
        val dy = cam.targetY - cam.eyeY
        val dz = cam.targetZ - cam.eyeZ
        val pitch = kotlin.math.atan2(-dy, -dz) // angle sous l'horizontale
        val half = FOV * (PI.toFloat() / 180f) / 2f
        return 0.5f - 0.5f * tan(pitch) / tan(half)
    }
}

internal class RunnerStage(private val meshOf: (String) -> Mesh3D?) {
    val scene = Scene3D().apply {
        lighting = Lighting3D(ambient = 0.62f, key = 0.62f, fill = 0.18f, rim = 0f)
    }

    // Les objets sont réutilisés d'une image à l'autre : un petit réservoir par modèle.
    private val pools = HashMap<String, ArrayList<Instance3D>>()
    private val used = HashMap<String, Int>()
    private val matte = Material3D(1f, 1f, 1f, specular = 0.03f, shininess = 12f)
    private val shiny = Material3D(1f, 1f, 1f, specular = 0.55f, shininess = 28f, emissive = 0.16f)
    private val arrowRight = arrowMesh(1f)
    private val arrowLeft = arrowMesh(-1f)

    // L'image en cours : le tracé, la position du coureur dans le plan et la rotation de la vue.
    private var path = RunnerPath()
    private var px = 0.0
    private var pz = 0.0
    private var playerX = 0f
    private var theta = 0f
    private var cosT = 1.0
    private var sinT = 0.0
    private val tmp = DoubleArray(2)
    private var vx = 0f
    private var vz = 0f

    /** Calcule dans (vx, vz) où tombe, dans la vue, le point à la distance [s] du tronçon [seg], décalé de [lat] vers la droite. */
    private fun toView(seg: Int, s: Float, lat: Float) {
        path.position(seg, s, lat, tmp)
        val dx = tmp[0] - px
        val dz = tmp[1] - pz
        vx = (cosT * dx + sinT * dz).toFloat() + playerX
        vz = (-sinT * dx + cosT * dz).toFloat()
    }

    private fun behind(seg: Int, s: Float, lat: Float): Boolean {
        toView(seg, s, lat)
        return vz <= RunnerView.NEAR_Z
    }

    /** Vrai si tout le rectangle (de [s0] à [s1] sur [seg], à [lat] ± [hw]) est assez loin devant la caméra pour être dessiné. */
    private fun fits(seg: Int, s0: Float, s1: Float, lat: Float, hw: Float): Boolean =
        behind(seg, s0, lat - hw) && behind(seg, s0, lat + hw) && behind(seg, s1, lat - hw) && behind(seg, s1, lat + hw)

    /**
     * Pose [mesh] en (x, y, z) de la vue, tourné de [yaw] degrés et mis à l'échelle [scale]. [center] : 0 garde l'origine du
     * modèle, 1 le centre en x et z, 2 le centre aussi en hauteur.
     */
    private fun place(key: String, mesh: Mesh3D, x: Float, y: Float, z: Float, yaw: Float, scale: Float, center: Int, glossy: Boolean) {
        val list = pools.getOrPut(key) { ArrayList() }
        val n = used[key] ?: 0
        val inst = if (n < list.size) list[n] else Instance3D(mesh, if (glossy) shiny else matte).also { list.add(it) }
        used[key] = n + 1
        var m = Mat4.pose(x, y, z, yaw, 0f, 0f, scale)
        if (center != 0) {
            val cx = (mesh.minX + mesh.maxX) / 2f
            val cy = if (center == 2) (mesh.minY + mesh.maxY) / 2f else 0f
            val cz = (mesh.minZ + mesh.maxZ) / 2f
            m = Mat4.multiply(m, Mat4.translation(-cx, -cy, -cz))
        }
        inst.matrix = m
        scene.instances.add(inst)
    }

    /**
     * Pose un modèle au point (distance [s], décalage [lat]) du tronçon [seg]. [yaw] est le lacet par rapport au tracé (0 : comme
     * le modèle est fait, face au coureur) ; on ne dessine pas ce qui est plus près de la caméra que [maxZ].
     */
    private fun put(
        model: String, seg: Int, s: Float, lat: Float, y: Float, yaw: Float, scale: Float,
        center: Int = 0, glossy: Boolean = false, maxZ: Float = Float.MAX_VALUE, mesh: Mesh3D? = meshOf(model)
    ) {
        if (mesh == null) return
        toView(seg, s, lat)
        if (vz > maxZ) return
        place(model, mesh, vx, y, vz, yaw - 90f * path.quarter(seg) + theta, scale, center, glossy)
    }

    /** Remet la scène à l'état de [game] : caméra, voie, virages, décor, obstacles, bonus. */
    fun update(game: RunnerGame) {
        scene.instances.clear()
        used.clear()
        scene.camera = RunnerView.camera(game.x, scene.camera)
        path = game.path
        val d = game.dist
        nowDist = d
        val seg = game.seg
        theta = 90f * path.quarter(seg) + game.turnVisual
        val rad = theta.toDouble() * (PI / 180.0)
        cosT = cos(rad)
        sinT = sin(rad)
        playerX = game.x
        path.position(seg, d, game.x, tmp)
        px = tmp[0]
        pz = tmp[1]

        // On dessine d'abord ce qui est près de la caméra : le moteur écarte alors sans calcul ce qui est caché derrière.
        val spin = game.clock * 190f
        for (t in game.things) {
            val rel = t.z - d
            if (rel > VIEW_M || rel < -6f) continue
            val isPickup = t.kind.isPickup
            if (t.taken && isPickup) continue
            if (t.smashed) continue
            val s = path.segmentAt(t.z)
            val lat = t.x
            if (isPickup) {
                val bob = 0.07f * sin((game.clock * 3f + t.z).toDouble()).toFloat()
                put(t.model, s, t.z, lat, t.y + bob, spin + t.z * 40f, t.scale, center = 2, glossy = true)
            } else {
                put(t.model, s, t.z, lat, t.y, t.yaw, t.scale, center = 1, maxZ = RunnerView.NEAR_Z - 1.5f)
            }
        }

        for (i in 0 until path.size) {
            val a = path.start(i) + Runner.JUNCTION_HALF
            val end = if (i + 1 < path.size) path.start(i + 1) + Runner.JUNCTION_HALF else Float.MAX_VALUE
            if (end < d - BACK_M || a > d + VIEW_M) continue
            val from = max(a, d - BACK_M)
            val to = min(end, d + VIEW_M)
            track(i, a, end, from, to)
            platform(i, a, end, from, to)
            if (i > 0) wall(i)
        }
    }

    // La voie du tronçon [i] (de [a] à [end]) vue de [from] à [to] : des tronçons longs ; ceux qui touchent la caméra sont
    // remplacés par des tronçons courts, qui ne la dépassent pas.
    private fun track(i: Int, a: Float, end: Float, from: Float, to: Float) {
        val units = if (end == Float.MAX_VALUE) Int.MAX_VALUE else Math.round((end - a) / Runner.UNIT)
        var k = floor((from - a) / TRACK_LEN).toInt()
        while (a + k * TRACK_LEN < to) {
            val s0 = a + k * TRACK_LEN
            val first = 4 * k
            if (first + 4 <= units && fits(i, s0, s0 + TRACK_LEN, 0f, TRACK_HALF_W)) {
                put("rail_track_straight", i, s0, 0f, 0f, 90f, TRACK_SCALE)
            } else {
                for (u in first until min(first + 4, units)) {
                    val su = a + u * SHORT_LEN
                    if (su + SHORT_LEN >= from && fits(i, su, su + SHORT_LEN, 0f, TRACK_HALF_W)) {
                        put("rail_track_short", i, su, 0f, 0f, 90f, TRACK_SCALE)
                    }
                }
            }
            decor(i, k, s0 + TRACK_LEN / 2f)
            k++
        }
    }

    // Le quai, à droite, loin des virages.
    private fun platform(i: Int, a: Float, end: Float, from: Float, to: Float) {
        val pl = PLATFORM_LEN
        var j = floor((from - a) / pl).toInt() - 1
        while (a + j * pl < to) {
            val sc = a + j * pl + pl / 2f
            val ok = j >= 0 && sc + pl / 2f <= end - CORNER_GAP && (i == 0 || sc - pl / 2f >= a - Runner.JUNCTION_HALF + CORNER_GAP) &&
                fits(i, sc - pl / 2f, sc + pl / 2f, PLATFORM_X, PLATFORM_HALF_W)
            if (ok) put("rail_platform_straight", i, sc, PLATFORM_X, 0f, 90f, PLATFORM_SCALE, center = 0)
            j++
        }
    }

    // Au bout de la voie [i - 1], le mur d'une gare avec une grande flèche qui montre où tourner.
    private fun wall(i: Int) {
        val c = path.start(i)
        val prev = i - 1
        if (c - (nowDist) > VIEW_M || nowDist - c > 12f) return
        val front = c + Runner.JUNCTION_HALF
        val mesh = meshOf("rail_station_building")
        if (mesh != null && fits(prev, front, front + WALL_DEPTH, 0f, WALL_HALF_W)) {
            put("rail_station_building", prev, front + 0.2f + WALL_DEPTH / 2f, 0f, 0f, 0f, WALL_SCALE, center = 1, mesh = mesh)
        }
        val right = path.turn(i) > 0
        if (fits(prev, front, front + 0.1f, 0f, 1f)) {
            put(if (right) "arrow_r" else "arrow_l", prev, front + 0.05f, 0f, ARROW_Y, 0f, 1f, glossy = true, mesh = if (right) arrowRight else arrowLeft)
        }
    }

    // Le décor de la gare le long du tronçon [i], pour la tuile [k] dont le milieu est à la distance [mid] : arbres, poteaux et
    // buissons à gauche, lampadaires et bâtiment sur le quai à droite. Rien près des virages.
    private fun decor(i: Int, k: Int, mid: Float) {
        if (k < 0) return
        if (i > 0 && mid < path.start(i) + DECOR_GAP) return
        if (i + 1 < path.size && mid > path.start(i + 1) - DECOR_GAP) return
        // dz : décalage dans le sens du monde d'origine (positif vers le coureur), donc opposé à la distance.
        fun at(model: String, lat: Float, dz: Float, yaw: Float, scale: Float, y: Float = 0f) =
            put(model, i, mid - dz, lat, y, yaw, scale, center = 1, maxZ = RunnerView.NEAR_Z)
        when (k % 4) {
            0 -> at("rail_tree_round", -5.0f, 0f, 0f, 1.4f)
            1 -> {
                at("rail_telegraph_pole", -3.6f, 0f, 0f, 1.1f)
                at("rail_bush", -5.2f, 1.4f, 0f, 1.3f)
            }
            2 -> {
                at("rail_bush", -4.1f, 1.5f, 0f, 1.3f)
                at("rail_bush", -5.4f, -1.6f, 0f, 1.1f)
            }
            else -> {
                at("rail_tree_round", -6.2f, 0f, 90f, 1.2f)
                at("rail_bush", -4.0f, 0f, 0f, 1.1f)
            }
        }
        if (k % 2 == 0) at("rail_station_lamp", PLATFORM_X + 0.6f, 0f, 0f, 1.1f, PLATFORM_H)
        if (k % 6 == 3) {
            at("rail_station_building", PLATFORM_X + 3.6f, 0f, -90f, 1.3f)
        } else if (k % 3 == 0) {
            at("rail_tree_round", PLATFORM_X + 5.5f, 0f, 0f, 1.3f)
        }
    }

    private var nowDist = 0f

    private companion object {
        const val TRACK_SCALE = 1.6f
        const val TRACK_LEN = 4f * TRACK_SCALE
        const val SHORT_LEN = 1f * TRACK_SCALE
        const val TRACK_HALF_W = 1.6f

        const val PLATFORM_SCALE = 1.2f
        const val PLATFORM_LEN = 3f * PLATFORM_SCALE
        const val PLATFORM_X = 2.95f
        const val PLATFORM_H = 0.55f * PLATFORM_SCALE
        const val PLATFORM_HALF_W = 1.5f

        /** Distance dessinée devant le coureur, et marge derrière lui. */
        const val VIEW_M = 60f
        const val BACK_M = 14f

        /** Distance à garder entre un virage et le quai, ou le décor. */
        const val CORNER_GAP = 12f
        const val DECOR_GAP = 14f

        const val WALL_SCALE = 1.3f
        const val WALL_DEPTH = 3.2f * WALL_SCALE
        const val WALL_HALF_W = 1.55f * WALL_SCALE
        const val ARROW_Y = 2.1f

        /** Une grande flèche debout (dans le plan x, y, face au coureur) qui montre à droite si [sx] vaut 1, à gauche si -1. */
        fun arrowMesh(sx: Float): Mesh3D {
            val shaft = floatArrayOf(-0.9f, -0.25f, 0.2f, -0.25f, 0.2f, 0.25f, -0.9f, 0.25f)
            val head = floatArrayOf(0.2f, -0.75f, 1.1f, 0f, 0.2f, 0.75f)
            val flat = shaft + head
            val n = flat.size / 2
            val pos = FloatArray(n * 2 * 3)
            val nor = FloatArray(n * 2 * 3)
            val col = FloatArray(n * 2 * 3)
            for (side in 0..1) for (v in 0 until n) {
                val o = (side * n + v) * 3
                pos[o] = flat[v * 2] * sx
                pos[o + 1] = flat[v * 2 + 1]
                pos[o + 2] = if (side == 0) 0f else -0.02f
                nor[o + 2] = if (side == 0) 1f else -1f
                col[o] = 1f; col[o + 1] = 0.82f; col[o + 2] = 0.1f
            }
            // L'envers est un second jeu de sommets, tournés dans l'autre sens, pour qu'il se voie aussi.
            val front = intArrayOf(0, 1, 2, 0, 2, 3, 4, 5, 6)
            val idx = IntArray(front.size * 2)
            for (t in 0 until front.size / 3) {
                val a = front[t * 3]; val b = front[t * 3 + 1]; val c = front[t * 3 + 2]
                if (sx > 0f) {
                    idx[t * 3] = a; idx[t * 3 + 1] = b; idx[t * 3 + 2] = c
                    idx[front.size + t * 3] = n + a; idx[front.size + t * 3 + 1] = n + c; idx[front.size + t * 3 + 2] = n + b
                } else {
                    idx[t * 3] = a; idx[t * 3 + 1] = c; idx[t * 3 + 2] = b
                    idx[front.size + t * 3] = n + a; idx[front.size + t * 3 + 1] = n + b; idx[front.size + t * 3 + 2] = n + c
                }
            }
            return Mesh3D(pos, nor, idx, col)
        }
    }
}
