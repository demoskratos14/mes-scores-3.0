package com.aventure.messcores

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/*
 * Petit moteur 3D logiciel (sans OpenGL, sans bibliothèque) pour les pièces du style « sorcier » : une scène de
 * quelques maillages (une ou deux pièces et leurs armes, soit 10 000 à 30 000 triangles), une caméra en perspective,
 * un tampon de profondeur (les armes qui traversent une main sont donc justes) et un éclairage calculé pour chaque
 * pixel (lumière principale, lumière d'appoint, brillance, liseré). Le résultat est une image ARGB à poser sur
 * l'échiquier ; le fond reste transparent. Tout est en Kotlin pur : le rendu se vérifie sans écran, dans les tests.
 *
 * Repère : celui de glTF, main droite, Y vers le haut, le mètre pour unité. Les lumières sont fixées à la caméra
 * (x à droite, y en haut, z devant elle) : l'éclairage ne change donc pas quand on tourne autour de la scène.
 */

/** Matrices 4 × 4 rangées par colonnes (m[colonne * 4 + ligne]), comme en glTF. */
internal object Mat4 {
    fun identity(): FloatArray = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)

    /** Le produit a × b : b s'applique en premier. Le résultat peut être l'une des deux entrées. */
    fun multiply(a: FloatArray, b: FloatArray, out: FloatArray = FloatArray(16)): FloatArray {
        val r = FloatArray(16)
        for (col in 0 until 4) for (row in 0 until 4) {
            var s = 0f
            for (k in 0 until 4) s += a[k * 4 + row] * b[col * 4 + k]
            r[col * 4 + row] = s
        }
        r.copyInto(out)
        return out
    }

    fun translation(x: Float, y: Float, z: Float): FloatArray =
        identity().also { it[12] = x; it[13] = y; it[14] = z }

    fun scaling(sx: Float, sy: Float, sz: Float): FloatArray =
        identity().also { it[0] = sx; it[5] = sy; it[10] = sz }

    fun rotationX(degrees: Float): FloatArray {
        val c = cos(degrees * RAD)
        val s = sin(degrees * RAD)
        return identity().also { it[5] = c; it[6] = s; it[9] = -s; it[10] = c }
    }

    fun rotationY(degrees: Float): FloatArray {
        val c = cos(degrees * RAD)
        val s = sin(degrees * RAD)
        return identity().also { it[0] = c; it[2] = -s; it[8] = s; it[10] = c }
    }

    fun rotationZ(degrees: Float): FloatArray {
        val c = cos(degrees * RAD)
        val s = sin(degrees * RAD)
        return identity().also { it[0] = c; it[1] = s; it[4] = -s; it[5] = c }
    }

    /** Translation × rotation (quaternion x, y, z, w) × échelle, comme un nœud glTF. */
    fun fromTrs(t: FloatArray?, q: FloatArray?, s: FloatArray?): FloatArray {
        val m = identity()
        if (q != null) {
            val len = sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3])
            if (len > 1e-12f) {
                val x = q[0] / len; val y = q[1] / len; val z = q[2] / len; val w = q[3] / len
                m[0] = 1 - 2 * (y * y + z * z); m[1] = 2 * (x * y + z * w); m[2] = 2 * (x * z - y * w)
                m[4] = 2 * (x * y - z * w); m[5] = 1 - 2 * (x * x + z * z); m[6] = 2 * (y * z + x * w)
                m[8] = 2 * (x * z + y * w); m[9] = 2 * (y * z - x * w); m[10] = 1 - 2 * (x * x + y * y)
            }
        }
        if (s != null) {
            for (i in 0 until 3) { m[i] *= s[0]; m[4 + i] *= s[1]; m[8 + i] *= s[2] }
        }
        if (t != null) { m[12] = t[0]; m[13] = t[1]; m[14] = t[2] }
        return m
    }

    /** Pose d'un objet : on le met à l'échelle, puis on le tourne (lacet, tangage, roulis, en degrés), puis on le place. */
    fun pose(x: Float, y: Float, z: Float, yaw: Float = 0f, pitch: Float = 0f, roll: Float = 0f, scale: Float = 1f): FloatArray {
        var m = translation(x, y, z)
        m = multiply(m, rotationY(yaw))
        m = multiply(m, rotationX(pitch))
        m = multiply(m, rotationZ(roll))
        return multiply(m, scaling(scale, scale, scale))
    }

    private const val RAD = (PI / 180.0).toFloat()
}

/** Matière : couleur de base (0 à 1), force et finesse de la brillance, et lueur propre (0 = aucune). */
internal class Material3D(
    val r: Float,
    val g: Float,
    val b: Float,
    val specular: Float = 0.25f,
    val shininess: Float = 40f,
    val emissive: Float = 0f
) {
    // Brillance précalculée : (n·h)^finesse pour n·h de 0 à 1 par pas de 1/127.
    internal val specularTable = FloatArray(128) { (it / 127f).toDouble().pow(shininess.toDouble()).toFloat() }
}

/** Un maillage placé dans la scène : [matrix] (de l'objet vers le monde) se change à chaque image pour l'animer. */
internal class Instance3D(val mesh: Mesh3D, var material: Material3D, var matrix: FloatArray = Mat4.identity()) {
    var visible: Boolean = true

    fun setPose(x: Float, y: Float, z: Float, yaw: Float = 0f, pitch: Float = 0f, roll: Float = 0f, scale: Float = 1f) {
        matrix = Mat4.pose(x, y, z, yaw, pitch, roll, scale)
    }
}

/** Caméra en perspective qui regarde de [eyeX], [eyeY], [eyeZ] vers [targetX], [targetY], [targetZ] (Y est le haut). */
internal class Camera3D(
    var eyeX: Float = 0f,
    var eyeY: Float = 0.1f,
    var eyeZ: Float = 0.3f,
    var targetX: Float = 0f,
    var targetY: Float = 0.03f,
    var targetZ: Float = 0f,
    var fovDegrees: Float = 35f,
    var near: Float = 0.005f
) {
    /** Se place à [distance] de la cible, tournée de [yawDegrees] autour d'elle et relevée de [pitchDegrees]. */
    fun orbit(tx: Float, ty: Float, tz: Float, distance: Float, yawDegrees: Float, pitchDegrees: Float) {
        val yaw = yawDegrees * (PI / 180.0).toFloat()
        val pitch = pitchDegrees * (PI / 180.0).toFloat()
        targetX = tx; targetY = ty; targetZ = tz
        eyeX = tx + distance * cos(pitch) * sin(yaw)
        eyeY = ty + distance * sin(pitch)
        eyeZ = tz + distance * cos(pitch) * cos(yaw)
    }
}

/** Éclairage : une lumière principale, une d'appoint, une lumière ambiante (plus claire vers le haut) et un liseré. */
internal class Lighting3D(
    var ambient: Float = 0.34f,
    var key: Float = 0.82f,
    var fill: Float = 0.22f,
    var rim: Float = 0.30f
)

internal class Scene3D {
    val instances = ArrayList<Instance3D>()
    var camera = Camera3D()
    var lighting = Lighting3D()

    /** Largeur, en pixels de l'image, du liseré qui détache chaque pièce de ce qui est derrière elle (0 : aucun). */
    var outline: Int = 0
}

/** L'image rendue : [pixels] (ARGB non prémultiplié, ligne par ligne) est prêt à être copié dans un Bitmap. */
internal class Frame3D(val width: Int, val height: Int, val supersampling: Int = 2) {
    init {
        require(width >= 1 && height >= 1) { "image vide" }
        require(supersampling in 1..4) { "suréchantillonnage de 1 à 4" }
    }

    val pixels = IntArray(width * height)
    internal val hiWidth = width * supersampling
    internal val hiHeight = height * supersampling
    internal val hiColor = IntArray(hiWidth * hiHeight)
    internal val hiDepth = FloatArray(hiWidth * hiHeight)
}

internal class Renderer3D {
    private var sx = FloatArray(0)
    private var sy = FloatArray(0)
    private var siz = FloatArray(0)
    private var snx = FloatArray(0)
    private var sny = FloatArray(0)
    private var snz = FloatArray(0)

    private fun ensure(vertices: Int) {
        if (sx.size >= vertices) return
        sx = FloatArray(vertices); sy = FloatArray(vertices); siz = FloatArray(vertices)
        snx = FloatArray(vertices); sny = FloatArray(vertices); snz = FloatArray(vertices)
    }

    /** Dessine [scene] dans [frame] (fond transparent). */
    fun render(scene: Scene3D, frame: Frame3D) {
        frame.hiColor.fill(0)
        frame.hiDepth.fill(0f)
        val cam = scene.camera
        var fx = cam.targetX - cam.eyeX
        var fy = cam.targetY - cam.eyeY
        var fz = cam.targetZ - cam.eyeZ
        val fl = sqrt(fx * fx + fy * fy + fz * fz)
        if (fl < 1e-9f) return
        fx /= fl; fy /= fl; fz /= fl
        // Droite = avant × haut(0, 1, 0) ; si on regarde pile vers le haut ou le bas, on prend l'axe X.
        var rx = fy * 0f - fz * 1f
        var ry = fz * 0f - fx * 0f
        var rz = fx * 1f - fy * 0f
        var rl = sqrt(rx * rx + ry * ry + rz * rz)
        if (rl < 1e-6f) { rx = 1f; ry = 0f; rz = 0f; rl = 1f }
        rx /= rl; ry /= rl; rz /= rl
        val ux = ry * fz - rz * fy
        val uy = rz * fx - rx * fz
        val uz = rx * fy - ry * fx

        val w = frame.hiWidth
        val h = frame.hiHeight
        val cx = w / 2f
        val cy = h / 2f
        val focal = (h / 2f) / tan(cam.fovDegrees * (PI / 180.0).toFloat() / 2f)

        for (inst in scene.instances) {
            if (!inst.visible || inst.mesh.triangleCount == 0) continue
            projectVertices(inst, cam, rx, ry, rz, ux, uy, uz, fx, fy, fz, cx, cy, focal)
            rasterize(inst, scene.lighting, frame, w, h)
        }
        if (scene.outline > 0) outline(frame, scene.outline * frame.supersampling)
        downsample(frame)
    }

    // Un liseré là où un pixel est nettement devant ce qui l'entoure (le vide, ou une pièce plus loin) : clair sur ce qui est
    // sombre, sombre sur ce qui est clair. Les pièces se détachent ainsi les unes des autres et du plateau, même noir sur noir.
    private fun outline(frame: Frame3D, width: Int) {
        val w = frame.hiWidth
        val h = frame.hiHeight
        val color = frame.hiColor
        val depth = frame.hiDepth
        for (y in 0 until h) {
            for (x in 0 until w) {
                val o = y * w + x
                val iz = depth[o]
                if (iz <= 0f) continue
                val limit = iz * (1f - OUTLINE_DEPTH_STEP)
                val edge = (x >= width && depth[o - width] < limit) ||
                    (x + width < w && depth[o + width] < limit) ||
                    (y >= width && depth[o - width * w] < limit) ||
                    (y + width < h && depth[o + width * w] < limit)
                if (!edge) continue
                val c = color[o]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val dark = (r * 30 + g * 59 + b * 11) / 100 < OUTLINE_SPLIT
                val nr = if (dark) r + ((255 - r) * OUTLINE_LIGHTEN).toInt() else (r * OUTLINE_DARKEN).toInt()
                val ng = if (dark) g + ((255 - g) * OUTLINE_LIGHTEN).toInt() else (g * OUTLINE_DARKEN).toInt()
                val nb = if (dark) b + ((255 - b) * OUTLINE_LIGHTEN).toInt() else (b * OUTLINE_DARKEN).toInt()
                color[o] = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
            }
        }
    }

    private fun projectVertices(
        inst: Instance3D, cam: Camera3D,
        rx: Float, ry: Float, rz: Float, ux: Float, uy: Float, uz: Float, fx: Float, fy: Float, fz: Float,
        cx: Float, cy: Float, focal: Float
    ) {
        val mesh = inst.mesh
        val m = inst.matrix
        ensure(mesh.vertexCount)
        // Matrice des cofacteurs de la partie 3 × 3 : transforme correctement les normales (même avec une échelle).
        val a = m[0]; val b = m[4]; val c = m[8]
        val d = m[1]; val e = m[5]; val f = m[9]
        val g = m[2]; val hh = m[6]; val i = m[10]
        val c00 = e * i - f * hh; val c01 = f * g - d * i; val c02 = d * hh - e * g
        val c10 = c * hh - b * i; val c11 = a * i - c * g; val c12 = b * g - a * hh
        val c20 = b * f - c * e; val c21 = c * d - a * f; val c22 = a * e - b * d
        val p = mesh.positions
        val n = mesh.normals
        for (v in 0 until mesh.vertexCount) {
            val o = v * 3
            val x = p[o]; val y = p[o + 1]; val z = p[o + 2]
            val wx = m[0] * x + m[4] * y + m[8] * z + m[12] - cam.eyeX
            val wy = m[1] * x + m[5] * y + m[9] * z + m[13] - cam.eyeY
            val wz = m[2] * x + m[6] * y + m[10] * z + m[14] - cam.eyeZ
            val xc = wx * rx + wy * ry + wz * rz
            val yc = wx * ux + wy * uy + wz * uz
            val zc = wx * fx + wy * fy + wz * fz
            if (zc <= cam.near) {
                siz[v] = 0f // derrière la caméra : les triangles qui l'utilisent sont ignorés
                continue
            }
            val iz = 1f / zc
            sx[v] = cx + xc * iz * focal
            sy[v] = cy - yc * iz * focal
            siz[v] = iz
            val nx = n[o]; val ny = n[o + 1]; val nz = n[o + 2]
            val wnx = c00 * nx + c01 * ny + c02 * nz
            val wny = c10 * nx + c11 * ny + c12 * nz
            val wnz = c20 * nx + c21 * ny + c22 * nz
            var vx = wnx * rx + wny * ry + wnz * rz
            var vy = wnx * ux + wny * uy + wnz * uz
            var vz = wnx * fx + wny * fy + wnz * fz
            val len = sqrt(vx * vx + vy * vy + vz * vz)
            if (len > 1e-12f) { vx /= len; vy /= len; vz /= len }
            // On garde normale × (1 / profondeur), pour l'interpoler sans déformation de perspective.
            snx[v] = vx * iz; sny[v] = vy * iz; snz[v] = vz * iz
        }
    }

    private fun rasterize(inst: Instance3D, light: Lighting3D, frame: Frame3D, w: Int, h: Int) {
        val mesh = inst.mesh
        val mat = inst.material
        val idx = mesh.indices
        val color = frame.hiColor
        val depth = frame.hiDepth
        val table = mat.specularTable

        // Directions des lumières (vers la lumière) dans le repère de la caméra, et vecteur « demi » de la principale.
        val kx = -0.45f; val ky = 0.75f; val kz = -0.50f
        val kl = sqrt(kx * kx + ky * ky + kz * kz)
        val lkx = kx / kl; val lky = ky / kl; val lkz = kz / kl
        val ffx = 0.62f; val ffy = 0.08f; val ffz = -0.50f
        val fl = sqrt(ffx * ffx + ffy * ffy + ffz * ffz)
        val lfx = ffx / fl; val lfy = ffy / fl; val lfz = ffz / fl
        var hx = lkx; var hy = lky; var hz = lkz - 1f // + direction vers la caméra (0, 0, -1)
        val hl = sqrt(hx * hx + hy * hy + hz * hz)
        hx /= hl; hy /= hl; hz /= hl

        for (t in idx.indices step 3) {
            val i0 = idx[t]; val i1 = idx[t + 1]; val i2 = idx[t + 2]
            val iz0 = siz[i0]; val iz1 = siz[i1]; val iz2 = siz[i2]
            if (iz0 <= 0f || iz1 <= 0f || iz2 <= 0f) continue
            val x0 = sx[i0]; val y0 = sy[i0]
            val x1 = sx[i1]; val y1 = sy[i1]
            val x2 = sx[i2]; val y2 = sy[i2]
            val area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
            // Face avant = sens trigonométrique vu de la caméra, donc aire négative (l'axe Y de l'écran descend).
            if (area >= -1e-6f) continue
            val minX = max(0, floorInt(min(x0, min(x1, x2))))
            val maxX = min(w - 1, floorInt(max(x0, max(x1, x2))))
            val minY = max(0, floorInt(min(y0, min(y1, y2))))
            val maxY = min(h - 1, floorInt(max(y0, max(y1, y2))))
            if (minX > maxX || minY > maxY) continue
            val invArea = 1f / area
            for (py in minY..maxY) {
                val fy = py + 0.5f
                val row = py * w
                for (px in minX..maxX) {
                    val fx = px + 0.5f
                    val w0 = ((x1 - fx) * (y2 - fy) - (x2 - fx) * (y1 - fy)) * invArea
                    val w1 = ((x2 - fx) * (y0 - fy) - (x0 - fx) * (y2 - fy)) * invArea
                    val w2 = 1f - w0 - w1
                    if (w0 < -1e-6f || w1 < -1e-6f || w2 < -1e-6f) continue
                    val iz = w0 * iz0 + w1 * iz1 + w2 * iz2
                    val o = row + px
                    if (iz <= depth[o]) continue
                    depth[o] = iz
                    var nx = (w0 * snx[i0] + w1 * snx[i1] + w2 * snx[i2]) / iz
                    var ny = (w0 * sny[i0] + w1 * sny[i1] + w2 * sny[i2]) / iz
                    var nz = (w0 * snz[i0] + w1 * snz[i1] + w2 * snz[i2]) / iz
                    val nl = sqrt(nx * nx + ny * ny + nz * nz)
                    if (nl > 1e-12f) { nx /= nl; ny /= nl; nz /= nl }

                    val dk = max(0f, nx * lkx + ny * lky + nz * lkz)
                    val df = max(0f, nx * lfx + ny * lfy + nz * lfz)
                    val facing = max(0f, -nz)
                    val edge = 1f - facing
                    val rimTerm = edge * edge * edge * light.rim
                    val lit = light.ambient * (0.75f + 0.25f * ny) + light.key * dk + light.fill * df + mat.emissive
                    var spec = 0f
                    if (dk > 0f) {
                        val nh = max(0f, nx * hx + ny * hy + nz * hz)
                        spec = table[(nh * 127f).toInt()] * mat.specular * light.key
                    }
                    val r = mat.r * lit + spec + rimTerm * 0.70f
                    val g = mat.g * lit + spec + rimTerm * 0.80f
                    val b = mat.b * lit + spec + rimTerm * 1.00f
                    color[o] = (0xFF shl 24) or (to255(r) shl 16) or (to255(g) shl 8) or to255(b)
                }
            }
        }
    }

    private fun floorInt(v: Float): Int = if (v >= 0f) v.toInt() else v.toInt() - 1

    private fun to255(v: Float): Int = if (v <= 0f) 0 else if (v >= 1f) 255 else (v * 255f + 0.5f).toInt()

    // Réduit l'image de travail (s fois plus grande) en moyennant les pixels : adoucit les bords des pièces.
    private fun downsample(frame: Frame3D) {
        val s = frame.supersampling
        val hi = frame.hiColor
        val out = frame.pixels
        val hw = frame.hiWidth
        val n = s * s
        for (y in 0 until frame.height) for (x in 0 until frame.width) {
            var a = 0
            var r = 0
            var g = 0
            var b = 0
            for (dy in 0 until s) {
                val rowStart = (y * s + dy) * hw + x * s
                for (dx in 0 until s) {
                    val c = hi[rowStart + dx]
                    val ca = c ushr 24
                    if (ca != 0) {
                        a += ca
                        r += (c shr 16) and 0xFF
                        g += (c shr 8) and 0xFF
                        b += c and 0xFF
                    }
                }
            }
            out[y * frame.width + x] = if (a == 0) {
                0
            } else {
                val covered = a / 255 // nombre de sous-pixels couverts (chacun est opaque ou transparent)
                val alpha = (a + n / 2) / n
                ((alpha shl 24) or ((r / covered) shl 16) or ((g / covered) shl 8) or (b / covered))
            }
        }
    }

    private companion object {
        /** Écart de profondeur (part de la distance à la caméra) à partir duquel un pixel est « devant » son voisin : environ 0,1 case. */
        const val OUTLINE_DEPTH_STEP = 0.0015f

        /** Au-dessous de cette luminosité (0 à 255), le pixel est sombre : son liseré est clair ; sinon il est foncé. */
        const val OUTLINE_SPLIT = 110

        /** Part de blanc ajoutée au liseré d'un pixel sombre, et part de couleur gardée pour celui d'un pixel clair. */
        const val OUTLINE_LIGHTEN = 0.5f
        const val OUTLINE_DARKEN = 0.5f
    }
}
