package com.aventure.messcores

import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Ce qu'il reste d'une pièce prise, dans la scène 3D de la prise (voir Wizard3D.kt) : quand sa statue s'écroule, elle devient un tas de
 * gravats (des blocs de pierre qui jaillissent, retombent et restent là), ou un nuage de poussière quand c'est le fou qui l'a frappée.
 * Tout reste en place jusqu'à ce que la pièce qui prend arrive sur la case : alors les débris s'effacent sous ses pieds.
 *
 * Les débris sont des maillages minuscules fabriqués ici (aucun fichier) et leur position ne dépend que de l'avancement de la prise :
 * [Wizard3DDebris.poses] n'a pas d'effet de bord, donc se teste comme le reste.
 */

/** La matrice d'un débris et s'il est à dessiner (il ne l'est qu'entre l'écroulement et l'arrivée de la pièce qui prend). */
internal class DebrisPose(val matrix: FloatArray, val visible: Boolean)

/** Les maillages des débris : des blocs irréguliers (icosaèdres déformés) et un grain de poussière (octaèdre). Rayon moyen : 1. */
internal object DebrisMeshes {
    val rocks: List<Mesh3D> by lazy { List(3) { rock(it + 1) } }
    val speck: Mesh3D by lazy { flat(OCTA_VERTS.map { it.copyOf() }, OCTA_FACES) }

    private val T = ((1.0 + sqrt(5.0)) / 2.0).toFloat()

    private val ICO_VERTS = listOf(
        floatArrayOf(-1f, T, 0f), floatArrayOf(1f, T, 0f), floatArrayOf(-1f, -T, 0f), floatArrayOf(1f, -T, 0f),
        floatArrayOf(0f, -1f, T), floatArrayOf(0f, 1f, T), floatArrayOf(0f, -1f, -T), floatArrayOf(0f, 1f, -T),
        floatArrayOf(T, 0f, -1f), floatArrayOf(T, 0f, 1f), floatArrayOf(-T, 0f, -1f), floatArrayOf(-T, 0f, 1f)
    )

    private val ICO_FACES = listOf(
        intArrayOf(0, 11, 5), intArrayOf(0, 5, 1), intArrayOf(0, 1, 7), intArrayOf(0, 7, 10), intArrayOf(0, 10, 11),
        intArrayOf(1, 5, 9), intArrayOf(5, 11, 4), intArrayOf(11, 10, 2), intArrayOf(10, 7, 6), intArrayOf(7, 1, 8),
        intArrayOf(3, 9, 4), intArrayOf(3, 4, 2), intArrayOf(3, 2, 6), intArrayOf(3, 6, 8), intArrayOf(3, 8, 9),
        intArrayOf(4, 9, 5), intArrayOf(2, 4, 11), intArrayOf(6, 2, 10), intArrayOf(8, 6, 7), intArrayOf(9, 8, 1)
    )

    private val OCTA_VERTS = listOf(
        floatArrayOf(1f, 0f, 0f), floatArrayOf(-1f, 0f, 0f), floatArrayOf(0f, 1f, 0f),
        floatArrayOf(0f, -1f, 0f), floatArrayOf(0f, 0f, 1f), floatArrayOf(0f, 0f, -1f)
    )

    private val OCTA_FACES = listOf(
        intArrayOf(0, 2, 4), intArrayOf(4, 2, 1), intArrayOf(1, 2, 5), intArrayOf(5, 2, 0),
        intArrayOf(4, 3, 0), intArrayOf(1, 3, 4), intArrayOf(5, 3, 1), intArrayOf(0, 3, 5)
    )

    // Un bloc : les sommets d'un icosaèdre ramenés à une sphère, puis tirés ou rentrés au hasard (le hasard est fixé par [seed]).
    private fun rock(seed: Int): Mesh3D {
        val random = Random(seed.toLong() * 7919L)
        val sx = 0.8f + 0.4f * random.nextFloat()
        val sy = 0.7f + 0.4f * random.nextFloat()
        val sz = 0.8f + 0.4f * random.nextFloat()
        val verts = ICO_VERTS.map { v ->
            val len = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
            val r = 0.75f + 0.5f * random.nextFloat()
            floatArrayOf(v[0] / len * r * sx, v[1] / len * r * sy, v[2] / len * r * sz)
        }
        return flat(verts, ICO_FACES)
    }

    // Un maillage à facettes (une normale par triangle). Le sens de chaque face est corrigé pour regarder vers l'extérieur
    // (les formes sont centrées sur l'origine et étoilées).
    private fun flat(verts: List<FloatArray>, faces: List<IntArray>): Mesh3D {
        val positions = FloatArray(faces.size * 9)
        val normals = FloatArray(faces.size * 9)
        val indices = IntArray(faces.size * 3)
        for ((f, face) in faces.withIndex()) {
            val a = verts[face[0]]
            var b = verts[face[1]]
            var c = verts[face[2]]
            var nx = (b[1] - a[1]) * (c[2] - a[2]) - (b[2] - a[2]) * (c[1] - a[1])
            var ny = (b[2] - a[2]) * (c[0] - a[0]) - (b[0] - a[0]) * (c[2] - a[2])
            var nz = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])
            val cx = a[0] + b[0] + c[0]
            val cy = a[1] + b[1] + c[1]
            val cz = a[2] + b[2] + c[2]
            if (nx * cx + ny * cy + nz * cz < 0f) {
                val t = b; b = c; c = t
                nx = -nx; ny = -ny; nz = -nz
            }
            val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-9f)
            val corners = arrayOf(a, b, c)
            for (k in 0 until 3) {
                val o = f * 9 + k * 3
                positions[o] = corners[k][0]; positions[o + 1] = corners[k][1]; positions[o + 2] = corners[k][2]
                normals[o] = nx / len; normals[o + 1] = ny / len; normals[o + 2] = nz / len
                indices[f * 3 + k] = f * 3 + k
            }
        }
        return Mesh3D(positions, normals, indices)
    }
}

/**
 * Les débris d'une pièce prise : [dust] pour un nuage de poussière (prise par le fou), sinon des gravats (des blocs qui jaillissent et
 * retombent, et un petit tas au sol). [bodyHeight] est la hauteur de la statue (en cases) ; [crumbleAfter] le moment où elle s'écroule
 * (part de la fin du coup, comme `after` dans [captureMotion]) ; [base] la matière de la pièce, dont les débris reprennent la couleur.
 */
internal class Wizard3DDebris(
    seed: Int,
    private val dust: Boolean,
    bodyHeight: Float,
    private val crumbleAfter: Float,
    base: Material3D
) {
    private class Bit(
        val mesh: Mesh3D,
        val heap: Boolean,
        val x0: Float, val y0: Float, val z0: Float,
        val vx: Float, val vy: Float, val vz: Float,
        val size: Float,
        val flatten: Float,
        val spin: Float,
        val yaw0: Float,
        val tilt0: Float,
        val phase: Float,
        val pushGain: Float
    )

    private val material: Material3D = if (dust) {
        Material3D(0.45f + base.r * 0.4f, 0.43f + base.g * 0.4f, 0.40f + base.b * 0.4f, specular = 0.03f, shininess = 6f)
    } else {
        Material3D(base.r * 0.9f, base.g * 0.9f, base.b * 0.9f, specular = 0.12f, shininess = 20f)
    }

    private val bits: List<Bit> = run {
        val random = Random(seed.toLong() * 104729L + 13L)
        val unit = max(bodyHeight * 0.08f, 0.07f)
        val list = ArrayList<Bit>()
        if (dust) {
            repeat(DUST_COUNT) {
                val angle = random.nextFloat() * 2f * PI.toFloat()
                val speed = 2f + 4f * random.nextFloat()
                list.add(
                    Bit(
                        DebrisMeshes.speck, false,
                        (random.nextFloat() - 0.5f) * 0.35f, 0.05f + random.nextFloat() * bodyHeight * 0.85f, (random.nextFloat() - 0.5f) * 0.2f,
                        cos(angle) * speed, 0.8f + 2.4f * random.nextFloat(), sin(angle) * speed * 0.3f,
                        unit * (0.5f + 0.7f * random.nextFloat()), 1f, 0f, random.nextFloat() * 360f, 0f,
                        random.nextFloat() * 2f * PI.toFloat(), 1.5f * random.nextFloat()
                    )
                )
            }
        } else {
            // Le tas : quelques blocs larges et plats, posés au centre de la case, qui grossissent d'un coup à l'écroulement.
            repeat(HEAP_COUNT) {
                list.add(
                    Bit(
                        DebrisMeshes.rocks[it % DebrisMeshes.rocks.size], true,
                        (random.nextFloat() - 0.5f) * 0.3f, 0f, (random.nextFloat() - 0.5f) * 0.12f,
                        0f, 0f, 0f,
                        unit * (2.2f + 0.8f * random.nextFloat()), 0.5f, 0f, random.nextFloat() * 360f, 0f, 0f, 0f
                    )
                )
            }
            repeat(ROCK_COUNT) {
                val angle = random.nextFloat() * 2f * PI.toFloat()
                val speed = 1.5f + 2.5f * random.nextFloat()
                list.add(
                    Bit(
                        DebrisMeshes.rocks[it % DebrisMeshes.rocks.size], false,
                        (random.nextFloat() - 0.5f) * 0.35f, 0.1f + random.nextFloat() * bodyHeight * 0.8f, (random.nextFloat() - 0.5f) * 0.12f,
                        cos(angle) * speed, 1.5f + 3.5f * random.nextFloat(), sin(angle) * speed * 0.15f,
                        unit * (0.6f + 0.9f * random.nextFloat()), 0.8f, (random.nextFloat() - 0.5f) * 1800f,
                        random.nextFloat() * 360f, random.nextFloat() * 360f, 0f, 1.5f * random.nextFloat()
                    )
                )
            }
        }
        list
    }

    /** Les instances à ajouter à la scène, dans l'ordre de [poses] (cachées tant que la pièce n'est pas écroulée). */
    fun instances(): List<Instance3D> = bits.map { Instance3D(it.mesh, material).also { inst -> inst.visible = false } }

    /**
     * Les matrices des débris à l'avancement [progress] de la prise, autour du pied de la case de la victime ([footX], [footY], [footZ]
     * dans le monde). [push] (−1, 0 ou 1) est le côté vers lequel le coup les pousse : l'opposé de celui d'où vient l'attaquant.
     */
    fun poses(progress: Float, footX: Float, footY: Float, footZ: Float, push: Float): List<DebrisPose> {
        val tau = progress - (IMPACT + crumbleAfter * (1f - IMPACT))
        // Quand la pièce qui prend arrive sur la case, les débris s'effacent sous ses pieds.
        val out = 1f - smooth(((progress - OUT_START) / (STEP_END - OUT_START)).coerceIn(0f, 1f))
        return bits.map { b ->
            if (tau < 0f || out <= 0.01f) HIDDEN else DebrisPose(matrixOf(b, tau, out, footX, footY, footZ, push), true)
        }
    }

    private fun matrixOf(b: Bit, tau: Float, out: Float, footX: Float, footY: Float, footZ: Float, push: Float): FloatArray {
        if (b.heap) {
            val grow = smooth((tau / HEAP_GROW).coerceIn(0f, 1f)) * out
            return place(
                footX + b.x0, footY + b.size * 0.2f * grow, footZ + b.z0, b.yaw0, 0f,
                b.size * grow, b.size * b.flatten * grow, b.size * grow
            )
        }
        if (dust) {
            // Un grain de poussière : il gonfle d'un coup, monte et s'écarte en ralentissant (frottement de l'air), tourbillonne, puis s'amenuise.
            val drift = (1f - exp(-DUST_DRAG * tau)) / DUST_DRAG
            val puff = smooth((tau / DUST_PUFF).coerceIn(0f, 1f))
            val fade = 1f - 0.55f * smooth((tau / DUST_FADE).coerceIn(0f, 1f))
            val s = b.size * puff * fade * out
            val swirl = 0.04f * sin(40f * tau + b.phase)
            return place(
                footX + b.x0 + (b.vx + push * b.pushGain) * drift + swirl,
                footY + b.y0 + b.vy * drift,
                footZ + b.z0 + b.vz * drift,
                b.yaw0, 0f, s, s, s
            )
        }
        // Un bloc : lancé du corps de la statue, il retombe (pesanteur), tourne en l'air, puis reste au sol là où il s'est posé.
        val floorY = b.size * 0.55f
        val land = (b.vy + sqrt(b.vy * b.vy + 2f * GRAVITY * max(b.y0 - floorY, 0f))) / GRAVITY
        val t = min(tau, land)
        val y = max(floorY, b.y0 + b.vy * t - 0.5f * GRAVITY * t * t) - (1f - out) * floorY
        val s = b.size * out
        return place(
            footX + b.x0 + (b.vx + push * b.pushGain) * t, footY + y, footZ + b.z0 + b.vz * t,
            b.yaw0 + b.spin * t * 0.6f, b.tilt0 + b.spin * t, s, s * b.flatten, s
        )
    }

    private fun place(x: Float, y: Float, z: Float, yaw: Float, pitch: Float, sx: Float, sy: Float, sz: Float): FloatArray {
        var m = Mat4.translation(x, y, z)
        m = Mat4.multiply(m, Mat4.rotationY(yaw))
        m = Mat4.multiply(m, Mat4.rotationX(pitch))
        return Mat4.multiply(m, Mat4.scaling(sx, sy, sz))
    }

    companion object {
        private const val ROCK_COUNT = 12
        private const val HEAP_COUNT = 3
        private const val DUST_COUNT = 36

        // Pesanteur (en cases par unité d'avancement au carré), volée des grains de poussière et durées (en avancement).
        private const val GRAVITY = 80f
        private const val DUST_DRAG = 10f
        private const val DUST_PUFF = 0.03f
        private const val DUST_FADE = 0.35f
        private const val HEAP_GROW = 0.05f

        /** Les débris s'effacent entre ce moment (la moitié du pas de la pièce qui prend) et son arrivée sur la case. */
        const val OUT_START = FOLLOW_END + 0.5f * (STEP_END - FOLLOW_END)

        private val HIDDEN = DebrisPose(Mat4.identity(), false)
    }
}
