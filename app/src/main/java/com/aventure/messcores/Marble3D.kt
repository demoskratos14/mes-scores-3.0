package com.aventure.messcores

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/*
 * Le marbre du plateau sorcier (mode explorer, voir WizardExplore3D.kt). Le moteur ([Renderer3D]) n'a ni texture ni couleur
 * par sommet : une [Material3D] qui porte un [MarbleStyle] reçoit donc, pour chaque pixel, un motif de veines calculé d'après
 * la position du point sur l'objet (le maillage doit être posé sans transformation, comme le plateau).
 *
 * Le motif : chaque dalle (le plateau en compte 8 × 8, et le motif se répète au-delà) a sa propre coupe de pierre : un sens, un
 * décalage et un ton à elle. Les veines sont des lignes fines tracées dans un champ de déformation lisse (un bruit à plusieurs
 * échelles, calculé une seule fois), si bien qu'elles serpentent. Une veine large et pâle (la « brume ») et un léger nuage de
 * ton s'y ajoutent. Tout est pur Kotlin : le rendu se vérifie sans écran, dans les tests.
 */

/** Les teintes du marbre d'une matière : la couleur de base est celle de la [Material3D], ici la veine et la force de chaque effet. */
internal class MarbleStyle(
    /** Couleur des veines (0 à 1). */
    val veinR: Float,
    val veinG: Float,
    val veinB: Float,
    /** Part de la couleur de veine au cœur d'une veine fine (0 à 1). */
    val veinStrength: Float = 0.6f,
    /** Part de la couleur de veine dans les veines larges et pâles (0 à 1). */
    val haze: Float = 0.12f,
    /** Variation de ton (± part de la couleur de base) d'un nuage à l'autre et d'une dalle à l'autre. */
    val cloud: Float = 0.08f
)

internal object MarbleField {
    /** Nombre de dalles (par côté) au bout duquel les coupes de pierre de dalle et le champ de déformation se répètent (le plateau en a 8). */
    const val PERIOD = 8

    private const val RES = 256
    private const val MASK = RES - 1
    private const val PROFILE_SIZE = 1024
    private const val TILES = PERIOD * PERIOD

    // Champ de déformation lisse, de -1 à 1, périodique : RES points pour PERIOD dalles.
    private val warp: FloatArray by lazy { bakeWarp() }

    // Profil d'une veine selon la phase (0 à 1, le cœur est en 0) : fin, très fin, large.
    private val thin = FloatArray(PROFILE_SIZE) { profile(it, 9) }
    private val sharp = FloatArray(PROFILE_SIZE) { profile(it, 28) }
    private val wide = FloatArray(PROFILE_SIZE) { profile(it, 2) }

    // Pour chaque dalle : sens, décalage, phases et ton.
    private val cosA = FloatArray(TILES)
    private val sinA = FloatArray(TILES)
    private val offU = FloatArray(TILES)
    private val offV = FloatArray(TILES)
    private val phase1 = FloatArray(TILES)
    private val phase2 = FloatArray(TILES)
    private val tileTone = FloatArray(TILES)

    init {
        for (t in 0 until TILES) {
            val angle = unit(t, 1) * PI.toFloat()
            cosA[t] = kotlin.math.cos(angle)
            sinA[t] = sin(angle)
            offU[t] = unit(t, 2) * PERIOD
            offV[t] = unit(t, 3) * PERIOD
            phase1[t] = unit(t, 4)
            phase2[t] = unit(t, 5)
            tileTone[t] = unit(t, 6) * 2f - 1f
        }
    }

    /**
     * Le motif au point ([u], [v]) (en dalles ; sur le dessus, u = x et v = z). Écrit dans [out] : [out]\[0] la veine fine (0 à
     * ~1,4), [out]\[1] la veine large (0 à ~1), [out]\[2] le ton (de -1 à 1 environ : nuage plus ton de la dalle).
     */
    fun sample(u: Float, v: Float, out: FloatArray) {
        val tile = ((floor(u).toInt() and (PERIOD - 1)) * PERIOD) + (floor(v).toInt() and (PERIOD - 1))
        val lu = u + offU[tile]
        val lv = v + offV[tile]
        val w = warpAt(lu, lv)
        val s = lu * cosA[tile] + lv * sinA[tile]
        val main = s * 0.75f + w * 0.95f + phase1[tile]
        val side = s * 2.1f - w * 0.9f + phase2[tile]
        val broad = s * 0.45f + w * 0.7f + phase1[tile] * 0.5f
        out[0] = thin[index(main)] + 0.28f * sharp[index(side)]
        out[1] = wide[index(broad)]
        out[2] = (w * 0.6f + tileTone[tile] * 0.4f)
    }

    private fun index(phase: Float): Int = ((phase - floor(phase)) * PROFILE_SIZE).toInt().coerceAtMost(PROFILE_SIZE - 1)

    private fun profile(i: Int, power: Int): Float {
        val t = i / PROFILE_SIZE.toFloat()
        var base = 1f - abs(sin(PI * t)).toFloat()
        var r = 1f
        var p = power
        while (p > 0) {
            if (p and 1 == 1) r *= base
            base *= base
            p = p shr 1
        }
        return r
    }

    // Interpolation bilinéaire du champ, avec reprise au bord (le motif se répète tous les PERIOD).
    private fun warpAt(u: Float, v: Float): Float {
        val x = u * (RES / PERIOD.toFloat())
        val y = v * (RES / PERIOD.toFloat())
        val fx = floor(x)
        val fy = floor(y)
        val tx = x - fx
        val ty = y - fy
        val x0 = fx.toInt() and MASK
        val y0 = fy.toInt() and MASK
        val x1 = (x0 + 1) and MASK
        val y1 = (y0 + 1) and MASK
        val a = warp[y0 * RES + x0]
        val b = warp[y0 * RES + x1]
        val c = warp[y1 * RES + x0]
        val d = warp[y1 * RES + x1]
        val top = a + (b - a) * tx
        val bottom = c + (d - c) * tx
        return top + (bottom - top) * ty
    }

    // Bruit de valeurs à quatre échelles (4, 8, 16 et 32 mailles pour la période), périodique.
    private fun bakeWarp(): FloatArray {
        val out = FloatArray(RES * RES)
        var amplitude = 1f
        var total = 0f
        var cells = 4
        for (octave in 0 until 4) {
            val step = RES / cells
            for (y in 0 until RES) {
                val gy = y / step
                val ty = smooth((y % step) / step.toFloat())
                for (x in 0 until RES) {
                    val gx = x / step
                    val tx = smooth((x % step) / step.toFloat())
                    val a = lattice(gx, gy, cells, octave)
                    val b = lattice(gx + 1, gy, cells, octave)
                    val c = lattice(gx, gy + 1, cells, octave)
                    val d = lattice(gx + 1, gy + 1, cells, octave)
                    val top = a + (b - a) * tx
                    val bottom = c + (d - c) * tx
                    out[y * RES + x] += amplitude * (top + (bottom - top) * ty)
                }
            }
            total += amplitude
            amplitude *= 0.5f
            cells *= 2
        }
        for (i in out.indices) out[i] /= total
        return out
    }

    private fun smooth(t: Float): Float = t * t * (3f - 2f * t)

    // Valeur du nœud (x, y) de la grille de [cells] mailles (qui se referme sur elle-même), de -1 à 1.
    private fun lattice(x: Int, y: Int, cells: Int, octave: Int): Float =
        hash((x % cells) * 7349 + (y % cells) * 1597 + octave * 131 + 17) * 2f - 1f

    // Graine d'une dalle, de 0 à 1.
    private fun unit(tile: Int, salt: Int): Float = hash(tile * 9973 + salt * 6329 + 5)

    // Mélange d'entiers (de type « murmur ») ramené à [0, 1).
    private fun hash(seed: Int): Float {
        var h = seed * -0x61c88647
        h = h xor (h ushr 15)
        h *= -0x7a143595
        h = h xor (h ushr 13)
        h *= -0x3d4d51cb
        h = h xor (h ushr 16)
        return (h ushr 8) / 16777216f
    }
}
