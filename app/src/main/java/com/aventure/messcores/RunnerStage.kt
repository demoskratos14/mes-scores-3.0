package com.aventure.messcores

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/*
 * Le décor 3D de la Course : une [Scene3D] reconstruite à chaque image d'après l'état de [RunnerGame] (voie, obstacles, bonus,
 * décor de gare qui défile), et la caméra. Tout est en Kotlin pur (les maillages arrivent par une fonction), donc le rendu
 * se vérifie sans écran. Repère : le coureur est toujours en z = 0 ; ce qui est devant lui est en z négatif.
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

    /**
     * Pose le modèle [model] en (x, y, z), tourné de [yaw] degrés et mis à l'échelle [scale]. [center] : 0 garde l'origine du
     * modèle, 1 le centre en x et z, 2 le centre aussi en hauteur.
     */
    private fun place(model: String, x: Float, y: Float, z: Float, yaw: Float = 0f, scale: Float = 1f, center: Int = 0, glossy: Boolean = false) {
        val mesh = meshOf(model) ?: return
        val list = pools.getOrPut(model) { ArrayList() }
        val n = used[model] ?: 0
        val inst = if (n < list.size) list[n] else Instance3D(mesh, if (glossy) shiny else matte).also { list.add(it) }
        used[model] = n + 1
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

    /** Remet la scène à l'état de [game] : caméra, voie, décor, obstacles, bonus. */
    fun update(game: RunnerGame) {
        scene.instances.clear()
        used.clear()
        scene.camera = RunnerView.camera(game.x, scene.camera)
        val d = game.dist

        // On dessine d'abord ce qui est près de la caméra : le moteur écarte alors sans calcul ce qui est caché derrière.
        val spin = game.clock * 190f
        for (t in game.things) {
            val rel = t.z - d
            if (rel > VIEW_M || rel < -6f) continue
            val isPickup = t.kind.ordinal <= RunnerKind.SHIELD.ordinal
            if (t.taken && isPickup) continue
            val wz = -rel
            val wx = t.lane * Runner.LANE_W
            if (isPickup) {
                val bob = 0.07f * sin((game.clock * 3f + t.z).toDouble()).toFloat()
                place(t.model, wx, t.y + bob, wz, spin + t.z * 40f, t.scale, center = 2, glossy = true)
            } else if (wz <= RunnerView.NEAR_Z - 1.5f) {
                place(t.model, wx, t.y, wz, t.yaw, t.scale, center = 1)
            }
        }

        // La voie : des tronçons longs ; ceux qui touchent la caméra sont remplacés par des tronçons courts, qui ne la dépassent pas.
        val len = TRACK_LEN
        var k = ceil((d - NEAR_BACK) / len).toInt() - 1
        while (k * len < d + VIEW_M) {
            val zw = d - k * len
            if (zw <= RunnerView.NEAR_Z) {
                place("rail_track_straight", 0f, 0f, zw, 90f, TRACK_SCALE)
            } else {
                var i = 0
                while (i < 4) {
                    val zs = zw - i * SHORT_LEN
                    if (zs <= RunnerView.NEAR_Z) place("rail_track_short", 0f, 0f, zs, 90f, TRACK_SCALE)
                    i++
                }
            }
            decor(k, zw)
            k++
        }

        // Le quai, à droite.
        val pl = PLATFORM_LEN
        var j = floor((d - NEAR_BACK) / pl).toInt() - 1
        while (j * pl < d + VIEW_M) {
            val zc = d - j * pl - pl / 2f
            if (zc + pl / 2f <= RunnerView.NEAR_Z) place("rail_platform_straight", PLATFORM_X, 0f, zc, 90f, PLATFORM_SCALE)
            j++
        }
    }

    // Le décor de la gare le long du tronçon [k] dont le bout le plus proche est en [zw] : arbres, poteaux et buissons à gauche,
    // lampadaires et bâtiment sur le quai à droite.
    private fun decor(k: Int, zw: Float) {
        val mid = zw - TRACK_LEN / 2f
        when (((k % 4) + 4) % 4) {
            0 -> place("rail_tree_round", -5.0f, 0f, mid, 0f, 1.4f, center = 1)
            1 -> {
                place("rail_telegraph_pole", -3.6f, 0f, mid, 0f, 1.1f, center = 1)
                place("rail_bush", -5.2f, 0f, mid + 1.4f, 0f, 1.3f, center = 1)
            }
            2 -> {
                place("rail_bush", -4.1f, 0f, mid + 1.5f, 0f, 1.3f, center = 1)
                place("rail_bush", -5.4f, 0f, mid - 1.6f, 0f, 1.1f, center = 1)
            }
            else -> {
                place("rail_tree_round", -6.2f, 0f, mid, 90f, 1.2f, center = 1)
                place("rail_bush", -4.0f, 0f, mid, 0f, 1.1f, center = 1)
            }
        }
        if (k % 2 == 0) place("rail_station_lamp", PLATFORM_X + 0.6f, PLATFORM_H, mid, 0f, 1.1f, center = 1)
        if (((k % 6) + 6) % 6 == 3) {
            place("rail_station_building", PLATFORM_X + 3.6f, 0f, mid, -90f, 1.3f, center = 1)
        } else if (k % 3 == 0) {
            place("rail_tree_round", PLATFORM_X + 5.5f, 0f, mid, 0f, 1.3f, center = 1)
        }
    }

    private companion object {
        const val TRACK_SCALE = 1.6f
        const val TRACK_LEN = 4f * TRACK_SCALE
        const val SHORT_LEN = 1f * TRACK_SCALE

        const val PLATFORM_SCALE = 1.2f
        const val PLATFORM_LEN = 3f * PLATFORM_SCALE
        const val PLATFORM_X = 2.95f
        const val PLATFORM_H = 0.55f * PLATFORM_SCALE

        /** Distance dessinée devant le coureur, et marge derrière lui. */
        const val VIEW_M = 60f
        const val NEAR_BACK = 4f
    }
}
