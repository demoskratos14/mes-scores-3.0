package com.aventure.messcores

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/*
 * Maillage 3D et lecture des fichiers glTF binaires (.glb), pour les pièces 3D du style « sorcier » des échecs.
 * Tout est en Kotlin pur (aucune bibliothèque, aucune classe Android) : le chargement se vérifie donc par de simples
 * tests unitaires. Le dessin est dans Renderer3D.kt, l'écran Compose dans Scene3DView.kt.
 */

/**
 * Un maillage de triangles : [positions] et [normals] (x, y, z de chaque sommet à la suite) et [indices] (trois par
 * triangle, sens trigonométrique vu de l'extérieur, comme en glTF). Y est vers le haut.
 */
internal class Mesh3D(val positions: FloatArray, val normals: FloatArray, val indices: IntArray) {
    val vertexCount: Int get() = positions.size / 3
    val triangleCount: Int get() = indices.size / 3

    // Boîte englobante (mêmes unités que les positions : le mètre pour les modèles livrés).
    val minX: Float
    val minY: Float
    val minZ: Float
    val maxX: Float
    val maxY: Float
    val maxZ: Float

    init {
        require(positions.size % 3 == 0 && normals.size == positions.size) { "positions et normales incohérentes" }
        require(indices.size % 3 == 0) { "le nombre d'indices doit être un multiple de 3" }
        require(indices.all { it in 0 until positions.size / 3 }) { "indice de sommet hors limites" }
        var x0 = Float.MAX_VALUE
        var y0 = Float.MAX_VALUE
        var z0 = Float.MAX_VALUE
        var x1 = -Float.MAX_VALUE
        var y1 = -Float.MAX_VALUE
        var z1 = -Float.MAX_VALUE
        for (i in positions.indices step 3) {
            x0 = minOf(x0, positions[i]); x1 = maxOf(x1, positions[i])
            y0 = minOf(y0, positions[i + 1]); y1 = maxOf(y1, positions[i + 1])
            z0 = minOf(z0, positions[i + 2]); z1 = maxOf(z1, positions[i + 2])
        }
        if (positions.isEmpty()) {
            x0 = 0f; y0 = 0f; z0 = 0f; x1 = 0f; y1 = 0f; z1 = 0f
        }
        minX = x0; minY = y0; minZ = z0
        maxX = x1; maxY = y1; maxZ = z1
    }

    val height: Float get() = maxY - minY
}

/** Lecture d'un fichier .glb (glTF 2.0 binaire) : tous les maillages de la scène sont fusionnés en un seul [Mesh3D]. */
internal object GlbParser {
    private const val MAGIC = 0x46546C67 // « glTF »
    private const val CHUNK_JSON = 0x4E4F534A // « JSON »
    private const val CHUNK_BIN = 0x004E4942 // « BIN\0 »

    private const val FLOAT = 5126
    private const val UBYTE = 5121
    private const val USHORT = 5123
    private const val UINT = 5125

    /** Lit [data] ; lève [IllegalArgumentException] avec un message clair si le fichier n'est pas un .glb exploitable. */
    fun parse(data: ByteArray): Mesh3D {
        require(data.size >= 20) { "fichier trop court pour un .glb" }
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        require(buf.getInt(0) == MAGIC) { "ce n'est pas un fichier .glb" }
        require(buf.getInt(4) == 2) { "seul glTF 2.0 est géré" }
        var json: String? = null
        var binStart = -1
        var binLength = 0
        var offset = 12
        while (offset + 8 <= data.size) {
            val length = buf.getInt(offset)
            val type = buf.getInt(offset + 4)
            val start = offset + 8
            require(length >= 0 && start.toLong() + length <= data.size) { "morceau .glb tronqué" }
            if (type == CHUNK_JSON && json == null) {
                json = String(data, start, length, Charsets.UTF_8)
            } else if (type == CHUNK_BIN && binStart < 0) {
                binStart = start
                binLength = length
            }
            offset = start + length
        }
        requireNotNull(json) { "le .glb n'a pas de partie JSON" }
        require(binStart >= 0) { "le .glb n'a pas de partie binaire" }
        val root = MiniJson.parse(json)
        require(root is Map<*, *>) { "JSON glTF invalide" }
        val bin = ByteBuffer.wrap(data, binStart, binLength).slice().order(ByteOrder.LITTLE_ENDIAN)
        return Builder(root, bin).build()
    }

    private class Builder(val root: Map<*, *>, val bin: ByteBuffer) {
        val positions = ArrayList<FloatArray>()
        val normals = ArrayList<FloatArray>()
        val indices = ArrayList<IntArray>()
        var vertexBase = 0

        fun build(): Mesh3D {
            val meshes = list(root["meshes"])
            require(meshes.isNotEmpty()) { "le .glb ne contient aucun maillage" }
            val nodes = list(root["nodes"])
            val roots: List<Int> = sceneRoots(nodes.size)
            if (nodes.isEmpty()) {
                for (m in meshes.indices) addMesh(m, Mat4.identity())
            } else {
                for (n in roots) visit(nodes, n, Mat4.identity(), 0)
            }
            require(positions.isNotEmpty()) { "le .glb ne contient aucun triangle" }
            val p = FloatArray(positions.sumOf { it.size })
            val nrm = FloatArray(p.size)
            val idx = IntArray(indices.sumOf { it.size })
            var po = 0
            for (a in positions) { a.copyInto(p, po); po += a.size }
            po = 0
            for (a in normals) { a.copyInto(nrm, po); po += a.size }
            var io = 0
            for (a in indices) { a.copyInto(idx, io); io += a.size }
            return Mesh3D(p, nrm, idx)
        }

        fun sceneRoots(nodeCount: Int): List<Int> {
            val scenes = list(root["scenes"])
            if (scenes.isNotEmpty()) {
                val which = (root["scene"] as? Number)?.toInt() ?: 0
                val scene = scenes.getOrNull(which) as? Map<*, *>
                val ids = list(scene?.get("nodes")).mapNotNull { (it as? Number)?.toInt() }
                if (ids.isNotEmpty()) return ids
            }
            return (0 until nodeCount).toList()
        }

        fun visit(nodes: List<Any?>, index: Int, parent: FloatArray, depth: Int) {
            require(depth < 64) { "hiérarchie de nœuds trop profonde" }
            val node = nodes.getOrNull(index) as? Map<*, *> ?: throw IllegalArgumentException("nœud $index introuvable")
            val world = Mat4.multiply(parent, localMatrix(node))
            (node["mesh"] as? Number)?.let { addMesh(it.toInt(), world) }
            for (child in list(node["children"])) (child as? Number)?.let { visit(nodes, it.toInt(), world, depth + 1) }
        }

        fun localMatrix(node: Map<*, *>): FloatArray {
            val m = list(node["matrix"]).mapNotNull { (it as? Number)?.toFloat() }
            if (m.size == 16) return m.toFloatArray()
            return Mat4.fromTrs(
                floats(node["translation"], 3),
                floats(node["rotation"], 4),
                floats(node["scale"], 3)
            )
        }

        fun floats(value: Any?, size: Int): FloatArray? {
            val l = list(value).mapNotNull { (it as? Number)?.toFloat() }
            return if (l.size == size) l.toFloatArray() else null
        }

        fun addMesh(meshIndex: Int, world: FloatArray) {
            val mesh = list(root["meshes"]).getOrNull(meshIndex) as? Map<*, *>
                ?: throw IllegalArgumentException("maillage $meshIndex introuvable")
            for (primitive in list(mesh["primitives"])) {
                val prim = primitive as? Map<*, *> ?: continue
                val mode = (prim["mode"] as? Number)?.toInt() ?: 4
                require(mode == 4) { "seuls les triangles simples (mode 4) sont gérés" }
                val attrs = prim["attributes"] as? Map<*, *> ?: throw IllegalArgumentException("primitive sans attributs")
                val posAcc = (attrs["POSITION"] as? Number)?.toInt() ?: throw IllegalArgumentException("primitive sans POSITION")
                val pos = readFloats(posAcc, 3)
                val count = pos.size / 3
                val idx = (prim["indices"] as? Number)?.let { readIndices(it.toInt()) } ?: IntArray(count) { it }
                require(idx.size % 3 == 0) { "nombre d'indices invalide" }
                require(idx.all { it in 0 until count }) { "indice de sommet hors limites" }
                val nrm = (attrs["NORMAL"] as? Number)?.let { readFloats(it.toInt(), 3) }
                    ?.takeIf { it.size == pos.size } ?: smoothNormals(pos, idx)
                transform(world, pos, nrm)
                positions.add(pos)
                normals.add(nrm)
                indices.add(IntArray(idx.size) { idx[it] + vertexBase })
                vertexBase += count
            }
        }

        // Applique la matrice du nœud aux positions et (par la matrice des cofacteurs) aux normales.
        fun transform(m: FloatArray, pos: FloatArray, nrm: FloatArray) {
            val c = cofactor3(m)
            for (i in pos.indices step 3) {
                val x = pos[i]
                val y = pos[i + 1]
                val z = pos[i + 2]
                pos[i] = m[0] * x + m[4] * y + m[8] * z + m[12]
                pos[i + 1] = m[1] * x + m[5] * y + m[9] * z + m[13]
                pos[i + 2] = m[2] * x + m[6] * y + m[10] * z + m[14]
                val nx = nrm[i]
                val ny = nrm[i + 1]
                val nz = nrm[i + 2]
                var ox = c[0] * nx + c[1] * ny + c[2] * nz
                var oy = c[3] * nx + c[4] * ny + c[5] * nz
                var oz = c[6] * nx + c[7] * ny + c[8] * nz
                val len = sqrt(ox * ox + oy * oy + oz * oz)
                if (len > 1e-12f) { ox /= len; oy /= len; oz /= len }
                nrm[i] = ox; nrm[i + 1] = oy; nrm[i + 2] = oz
            }
        }

        // Cofacteurs de la partie 3 × 3 (rangées dans l'ordre ligne par ligne) : transformation correcte des normales.
        fun cofactor3(m: FloatArray): FloatArray {
            val a = m[0]; val b = m[4]; val c = m[8]
            val d = m[1]; val e = m[5]; val f = m[9]
            val g = m[2]; val h = m[6]; val i = m[10]
            return floatArrayOf(
                e * i - f * h, f * g - d * i, d * h - e * g,
                c * h - b * i, a * i - c * g, b * g - a * h,
                b * f - c * e, c * d - a * f, a * e - b * d
            )
        }

        fun smoothNormals(pos: FloatArray, idx: IntArray): FloatArray {
            val n = FloatArray(pos.size)
            for (t in idx.indices step 3) {
                val a = idx[t] * 3
                val b = idx[t + 1] * 3
                val c = idx[t + 2] * 3
                val ux = pos[b] - pos[a]; val uy = pos[b + 1] - pos[a + 1]; val uz = pos[b + 2] - pos[a + 2]
                val vx = pos[c] - pos[a]; val vy = pos[c + 1] - pos[a + 1]; val vz = pos[c + 2] - pos[a + 2]
                val nx = uy * vz - uz * vy
                val ny = uz * vx - ux * vz
                val nz = ux * vy - uy * vx
                for (v in intArrayOf(a, b, c)) { n[v] += nx; n[v + 1] += ny; n[v + 2] += nz }
            }
            for (i in n.indices step 3) {
                val len = sqrt(n[i] * n[i] + n[i + 1] * n[i + 1] + n[i + 2] * n[i + 2])
                if (len > 1e-12f) { n[i] /= len; n[i + 1] /= len; n[i + 2] /= len } else n[i + 1] = 1f
            }
            return n
        }

        class Access(val offset: Int, val stride: Int, val count: Int, val componentType: Int)

        fun access(accessorIndex: Int, components: Int, allowed: Set<Int>): Access {
            val acc = list(root["accessors"]).getOrNull(accessorIndex) as? Map<*, *>
                ?: throw IllegalArgumentException("accessor $accessorIndex introuvable")
            require(acc["sparse"] == null) { "les accessors creux ne sont pas gérés" }
            val type = acc["componentType"].let { (it as? Number)?.toInt() ?: -1 }
            require(type in allowed) { "type de composante $type non géré" }
            val count = (acc["count"] as? Number)?.toInt() ?: throw IllegalArgumentException("accessor sans count")
            val viewIndex = (acc["bufferView"] as? Number)?.toInt() ?: throw IllegalArgumentException("accessor sans bufferView")
            val view = list(root["bufferViews"]).getOrNull(viewIndex) as? Map<*, *>
                ?: throw IllegalArgumentException("bufferView $viewIndex introuvable")
            require(((view["buffer"] as? Number)?.toInt() ?: 0) == 0) { "un seul tampon (celui du .glb) est géré" }
            val viewOffset = (view["byteOffset"] as? Number)?.toInt() ?: 0
            val accOffset = (acc["byteOffset"] as? Number)?.toInt() ?: 0
            val size = components * when (type) { UBYTE -> 1; USHORT -> 2; else -> 4 }
            val stride = (view["byteStride"] as? Number)?.toInt()?.takeIf { it > 0 } ?: size
            val start = viewOffset + accOffset
            val end = start.toLong() + (if (count > 0) (count - 1).toLong() * stride + size else 0L)
            require(start >= 0 && end <= bin.limit()) { "accessor hors du tampon" }
            return Access(start, stride, count, type)
        }

        fun readFloats(accessorIndex: Int, components: Int): FloatArray {
            val a = access(accessorIndex, components, setOf(FLOAT))
            val out = FloatArray(a.count * components)
            for (i in 0 until a.count) for (c in 0 until components) {
                out[i * components + c] = bin.getFloat(a.offset + i * a.stride + c * 4)
            }
            return out
        }

        fun readIndices(accessorIndex: Int): IntArray {
            val a = access(accessorIndex, 1, setOf(UBYTE, USHORT, UINT))
            return IntArray(a.count) { i ->
                val o = a.offset + i * a.stride
                when (a.componentType) {
                    UBYTE -> bin.get(o).toInt() and 0xFF
                    USHORT -> bin.getShort(o).toInt() and 0xFFFF
                    else -> bin.getInt(o)
                }
            }
        }

        fun list(value: Any?): List<Any?> = (value as? List<*>) ?: emptyList()
    }
}

/** Lecteur JSON minimal (objets, tableaux, nombres, texte, booléens, null) : le JSON d'un .glb est petit et simple. */
internal object MiniJson {
    fun parse(text: String): Any? {
        val p = Parser(text)
        val value = p.value()
        p.skipSpaces()
        require(p.atEnd()) { "JSON : texte en trop à la fin" }
        return value
    }

    private class Parser(val s: String) {
        var i = 0

        fun atEnd() = i >= s.length

        fun skipSpaces() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun value(depth: Int = 0): Any? {
            require(depth < 100) { "JSON trop imbriqué" }
            skipSpaces()
            require(i < s.length) { "JSON : fin inattendue" }
            return when (val c = s[i]) {
                '{' -> obj(depth)
                '[' -> arr(depth)
                '"' -> str()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) number() else throw IllegalArgumentException("JSON : caractère inattendu « $c »")
            }
        }

        fun literal(word: String, result: Any?): Any? {
            require(s.startsWith(word, i)) { "JSON : mot inattendu" }
            i += word.length
            return result
        }

        fun number(): Double {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            return s.substring(start, i).toDoubleOrNull() ?: throw IllegalArgumentException("JSON : nombre invalide")
        }

        fun str(): String {
            i++ // guillemet ouvrant
            val sb = StringBuilder()
            while (true) {
                require(i < s.length) { "JSON : texte non terminé" }
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        require(i < s.length) { "JSON : échappement incomplet" }
                        when (val e = s[i++]) {
                            'n' -> sb.append('\n')
                            't' -> sb.append('\t')
                            'r' -> sb.append('\r')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'u' -> {
                                require(i + 4 <= s.length) { "JSON : échappement \\u incomplet" }
                                sb.append(s.substring(i, i + 4).toInt(16).toChar())
                                i += 4
                            }
                            else -> sb.append(e)
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        fun arr(depth: Int): List<Any?> {
            i++ // [
            val out = ArrayList<Any?>()
            skipSpaces()
            if (i < s.length && s[i] == ']') { i++; return out }
            while (true) {
                out.add(value(depth + 1))
                skipSpaces()
                require(i < s.length) { "JSON : tableau non terminé" }
                when (s[i++]) {
                    ',' -> Unit
                    ']' -> return out
                    else -> throw IllegalArgumentException("JSON : « , » ou « ] » attendu")
                }
            }
        }

        fun obj(depth: Int): Map<String, Any?> {
            i++ // {
            val out = LinkedHashMap<String, Any?>()
            skipSpaces()
            if (i < s.length && s[i] == '}') { i++; return out }
            while (true) {
                skipSpaces()
                require(i < s.length && s[i] == '"') { "JSON : clé attendue" }
                val key = str()
                skipSpaces()
                require(i < s.length && s[i++] == ':') { "JSON : « : » attendu" }
                out[key] = value(depth + 1)
                skipSpaces()
                require(i < s.length) { "JSON : objet non terminé" }
                when (s[i++]) {
                    ',' -> Unit
                    '}' -> return out
                    else -> throw IllegalArgumentException("JSON : « , » ou « } » attendu")
                }
            }
        }
    }
}
