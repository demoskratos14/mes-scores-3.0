package com.aventure.messcores

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** La 3D du style « sorcier » : lecture des .glb, maths, rendu logiciel (sans écran) et catalogue des modèles. */
class Scene3DTest {

    // ---------- Fabrication d'un petit .glb de test ----------

    private fun le(vararg values: Number, bytes: Int = 4): ByteArray {
        val buf = ByteBuffer.allocate(values.size * bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (v in values) when (bytes) {
            1 -> buf.put(v.toByte())
            2 -> buf.putShort(v.toShort())
            else -> if (v is Float) buf.putFloat(v) else buf.putInt(v.toInt())
        }
        return buf.array()
    }

    private fun pad4(data: ByteArray, filler: Byte = 0): ByteArray {
        val extra = (4 - data.size % 4) % 4
        return data + ByteArray(extra) { filler }
    }

    private fun glb(json: String, bin: ByteArray): ByteArray {
        val j = pad4(json.toByteArray(), ' '.code.toByte())
        val b = pad4(bin)
        val out = ByteArrayOutputStream()
        out.write(le(0x46546C67, 2, 12 + 8 + j.size + 8 + b.size))
        out.write(le(j.size, 0x4E4F534A)); out.write(j)
        out.write(le(b.size, 0x004E4942)); out.write(b)
        return out.toByteArray()
    }

    // Un triangle (0,0,0) (1,0,0) (0,1,0), regardant vers +Z, avec ses indices dans le type demandé.
    private fun triangleGlb(
        indexType: Int = 5123, withNormals: Boolean = true, node: String = """{"mesh":0}"""
    ): ByteArray {
        val positions = le(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f)
        val normals = le(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f)
        val indices = when (indexType) {
            5121 -> le(0, 1, 2, bytes = 1)
            5123 -> le(0, 1, 2, bytes = 2)
            else -> le(0, 1, 2)
        }
        val bin = pad4(positions) + pad4(normals) + indices
        val attrs = if (withNormals) """{"POSITION":0,"NORMAL":1}""" else """{"POSITION":0}"""
        val json = """{"asset":{"version":"2.0"},"scene":0,"scenes":[{"nodes":[0]}],"nodes":[$node],
            "meshes":[{"primitives":[{"attributes":$attrs,"indices":2}]}],
            "accessors":[{"bufferView":0,"componentType":5126,"count":3,"type":"VEC3"},
              {"bufferView":1,"componentType":5126,"count":3,"type":"VEC3"},
              {"bufferView":2,"componentType":$indexType,"count":3,"type":"SCALAR"}],
            "bufferViews":[{"buffer":0,"byteOffset":0,"byteLength":36},
              {"buffer":0,"byteOffset":36,"byteLength":36},
              {"buffer":0,"byteOffset":72,"byteLength":${bin.size - 72}}],
            "buffers":[{"byteLength":${bin.size}}]}"""
        return glb(json, bin)
    }

    private fun assetsDir(): File =
        listOf(File("src/main/assets/wizard3d"), File("app/src/main/assets/wizard3d")).first { it.isDirectory }

    private fun loadAsset(model: String): Mesh3D =
        GlbParser.parse(File(assetsDir(), "$model.glb").readBytes())

    // ---------- Lecture des .glb ----------

    @Test
    fun unTriangleSeLitQuelQueSoitLeTypeDIndices() {
        for (type in listOf(5121, 5123, 5125)) {
            val mesh = GlbParser.parse(triangleGlb(indexType = type))
            assertEquals(3, mesh.vertexCount)
            assertEquals(1, mesh.triangleCount)
            assertEquals(listOf(0, 1, 2), mesh.indices.toList())
            assertEquals(1f, mesh.maxX, 1e-6f)
            assertEquals(1f, mesh.maxY, 1e-6f)
            assertEquals(1f, mesh.normals[2], 1e-6f)
        }
    }

    @Test
    fun laTransformationDuNoeudEstAppliqueAuxPositionsEtAuxNormales() {
        // Le nœud monte de 2 en Y, double l'échelle et tourne de 90° autour de X (quaternion 0,7071,0,0,7071).
        val node = """{"mesh":0,"translation":[0,2,0],"scale":[2,2,2],"rotation":[0.70710678,0,0,0.70710678]}"""
        val mesh = GlbParser.parse(triangleGlb(node = node))
        // (1,0,0) -> reste (1,0,0) -> x2 -> (2,0,0), puis +2 en Y.
        assertEquals(2f, mesh.positions[3], 1e-5f)
        assertEquals(2f, mesh.positions[4], 1e-5f)
        // (0,1,0) -> tourne vers +Z -> (0,0,1) -> x2 -> (0,0,2), puis +2 en Y.
        assertEquals(2f, mesh.positions[8], 1e-5f)
        assertEquals(2f, mesh.positions[7], 1e-5f)
        // La normale +Z devient -Y après la même rotation, et reste de longueur 1.
        assertEquals(-1f, mesh.normals[1], 1e-5f)
        assertEquals(0f, mesh.normals[2], 1e-5f)
    }

    @Test
    fun sansNormalesElleSontCalculees() {
        val mesh = GlbParser.parse(triangleGlb(withNormals = false))
        for (v in 0 until 3) {
            assertEquals(0f, mesh.normals[v * 3], 1e-6f)
            assertEquals(0f, mesh.normals[v * 3 + 1], 1e-6f)
            assertEquals(1f, mesh.normals[v * 3 + 2], 1e-6f)
        }
    }

    @Test
    fun unFichierInvalideEstRefuseAvecUneErreurClaire() {
        val good = triangleGlb()
        val badMagic = good.copyOf().also { it[0] = 'X'.code.toByte() }
        for (data in listOf(ByteArray(0), ByteArray(10), badMagic, good.copyOf(good.size - 20))) {
            try {
                GlbParser.parse(data)
                fail("le fichier aurait dû être refusé")
            } catch (_: IllegalArgumentException) {
                // attendu
            }
        }
    }

    @Test
    fun unIndiceHorsLimitesEstRefuse() {
        val data = triangleGlb(indexType = 5121)
        // Dernier octet utile des indices : 2 -> 9 (le tampon est : 36 + 36 octets de sommets, puis 3 indices).
        val json = String(data, 20, ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getInt(12))
        val binStart = 20 + json.length + 8
        data[binStart + 72 + 2] = 9
        try {
            GlbParser.parse(data)
            fail("l'indice 9 aurait dû être refusé")
        } catch (_: IllegalArgumentException) {
            // attendu
        }
    }

    @Test
    fun leLecteurJsonComprendLesTypesUtilises() {
        val v = MiniJson.parse("""{"a":[1,-2.5e1,true,false,null],"b":"x\"é\n","c":{}}""") as Map<*, *>
        assertEquals(listOf(1.0, -25.0, true, false, null), v["a"])
        assertEquals("x\"é\n", v["b"])
        assertEquals(emptyMap<String, Any?>(), v["c"])
        try {
            MiniJson.parse("""{"a":[1,2}""")
            fail("un JSON cassé aurait dû être refusé")
        } catch (_: IllegalArgumentException) {
            // attendu
        }
    }

    // ---------- Les onze modèles livrés ----------

    @Test
    fun lesOnzeModelesDesAssetsSeLisent() {
        for (model in WizardModelCatalog.allModels) {
            val mesh = loadAsset(model)
            assertTrue("$model : triangles", mesh.triangleCount in 500..12000)
            // Posé sur Y = 0 (à un millimètre près), centré sur l'origine, de taille « pièce d'échecs » (moins de 12 cm).
            assertEquals("$model : base", 0f, mesh.minY, 0.001f)
            assertTrue("$model : taille", mesh.height in 0.01f..0.12f)
            assertTrue("$model : centrage", mesh.minX < 0.002f && mesh.maxX > -0.002f)
            // Normales de longueur 1.
            for (i in mesh.normals.indices step 3) {
                val len = Math.sqrt(
                    (mesh.normals[i] * mesh.normals[i] + mesh.normals[i + 1] * mesh.normals[i + 1] +
                        mesh.normals[i + 2] * mesh.normals[i + 2]).toDouble()
                )
                assertEquals("$model : normale $i", 1.0, len, 1e-3)
            }
        }
    }

    @Test
    fun leRoiMesureQuatreVingtHuitMillimetres() {
        assertEquals(0.088f, loadAsset("king").height, 0.001f)
    }

    @Test
    fun chaqueFichierDuCatalogueExisteEtInversement() {
        val onDisk = assetsDir().listFiles { f -> f.name.endsWith(".glb") }!!.map { it.name.removeSuffix(".glb") }.toSet()
        assertEquals(onDisk, WizardModelCatalog.allModels.toSet())
        assertEquals(11, WizardModelCatalog.allModels.size)
    }

    // ---------- Les armes dans les mains ----------

    // Applique la matrice [m] (par colonnes, comme en glTF) au point (x, y, z).
    private fun transform(m: FloatArray, x: Float, y: Float, z: Float): FloatArray = floatArrayOf(
        m[0] * x + m[4] * y + m[8] * z + m[12],
        m[1] * x + m[5] * y + m[9] * z + m[13],
        m[2] * x + m[6] * y + m[10] * z + m[14]
    )

    // L'arme de [piece] placée dans sa main, puis ses sommets dans le repère de la pièce (x, y, z à la suite).
    private fun heldWeaponVertices(piece: Char, swing: Float = 0f): FloatArray {
        val mesh = loadAsset(WizardModelCatalog.weaponModel(piece)!!)
        val m = WizardModelCatalog.weaponLocal(piece, swingPitch = swing)!!
        val out = FloatArray(mesh.positions.size)
        for (i in out.indices step 3) {
            val p = transform(m, mesh.positions[i], mesh.positions[i + 1], mesh.positions[i + 2])
            out[i] = p[0]; out[i + 1] = p[1]; out[i + 2] = p[2]
        }
        return out
    }

    @Test
    fun seuleLesPiecesArmeesOntUnMontage() {
        for (p in "KQRBNPkqrbnp") {
            assertEquals("$p", WizardModelCatalog.weaponModel(p) != null, WizardModelCatalog.weaponMount(p) != null)
            assertEquals("$p", WizardModelCatalog.weaponModel(p) != null, WizardModelCatalog.weaponLocal(p) != null)
            assertEquals("$p", WizardModelCatalog.weaponModel(p) != null, WizardModelCatalog.handPoint(p) != null)
        }
    }

    @Test
    fun laPoigneeDeChaqueArmeTombeSurLaMainDeSaPiece() {
        for (p in "KQRBNP") {
            val mount = WizardModelCatalog.weaponMount(p)!!
            val hand = WizardModelCatalog.handPoint(p)!!
            // Au repos comme quand l'arme pivote autour de la main (levée, abattue…), le point de prise ne quitte pas la main.
            for (swing in listOf(0f, 40f, -75f, 160f)) {
                val m = WizardModelCatalog.weaponLocal(p, swingYaw = swing / 2, swingPitch = swing, swingRoll = -swing / 3)!!
                val g = transform(m, mount.gripX, mount.gripY, mount.gripZ)
                assertEquals("$p x ($swing°)", hand[0], g[0], 1e-5f)
                assertEquals("$p y ($swing°)", hand[1], g[1], 1e-5f)
                assertEquals("$p z ($swing°)", hand[2], g[2], 1e-5f)
            }
        }
    }

    @Test
    fun laMainEstSurLeCorpsDeLaPiece() {
        // La main (le poing) est à moins de 3 mm d'un sommet du modèle de la pièce : l'arme n'est pas dans le vide.
        for (p in "KQRBNP") {
            val piece = loadAsset(WizardModelCatalog.pieceModel(p)!!)
            val h = WizardModelCatalog.handPoint(p)!!
            var best = Float.MAX_VALUE
            for (i in piece.positions.indices step 3) {
                val dx = piece.positions[i] - h[0]
                val dy = piece.positions[i + 1] - h[1]
                val dz = piece.positions[i + 2] - h[2]
                best = minOf(best, Math.sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat())
            }
            assertTrue("$p : la main est à $best m du corps", best < 0.003f)
        }
    }

    @Test
    fun chaqueArmePointeDuBonCoteAuRepos() {
        fun lowest(v: FloatArray, axis: Int) = (axis until v.size step 3).minOf { v[it] }
        fun highest(v: FloatArray, axis: Int) = (axis until v.size step 3).maxOf { v[it] }
        fun hand(p: Char) = WizardModelCatalog.handPoint(p)!!
        // Roi : épée plantée, la pointe 5 cm sous les mains.
        assertTrue(lowest(heldWeaponVertices('K'), 1) < hand('K')[1] - 0.045f)
        // Reine : la baguette pend droit devant elle, sans inclinaison : la pointe descend ~5 cm sous les mains jointes,
        // et le pommeau est serré dans les mains sans les dépasser (ni vers le haut, ni vers l'avant).
        assertTrue(lowest(heldWeaponVertices('Q'), 1) < hand('Q')[1] - 0.04f)
        assertTrue(highest(heldWeaponVertices('Q'), 1) < hand('Q')[1] + 0.01f)
        assertTrue(highest(heldWeaponVertices('Q'), 2) < hand('Q')[2] + 0.01f)
        // Tour : l'épée est tenue en travers du corps, la pointe vers le bouclier (+Z), à plus de 1,2 cm de la main.
        assertTrue(highest(heldWeaponVertices('R'), 2) > hand('R')[2] + 0.012f)
        // Fou : la crosse est dressée, sa volute au-dessus du poing.
        assertTrue(highest(heldWeaponVertices('B'), 1) > hand('B')[1] + 0.015f)
        // Cavalier : la tête de la masse est levée au-dessus du poing.
        assertTrue(highest(heldWeaponVertices('N'), 1) > hand('N')[1] + 0.01f)
        // Pion : la courte épée est tenue pointe en l'air devant lui (la pointe monte d'au moins 1,5 cm au-dessus des mains, sans les dépasser vers l'avant).
        assertTrue(highest(heldWeaponVertices('P'), 1) > hand('P')[1] + 0.015f)
        assertTrue(lowest(heldWeaponVertices('P'), 2) > hand('P')[2] - 0.004f)
    }

    // ---------- Catalogue ----------

    @Test
    fun leCatalogueAssocieChaquePieceAUnModele() {
        assertEquals("king", WizardModelCatalog.pieceModel('K'))
        assertEquals("king", WizardModelCatalog.pieceModel('k'))
        assertEquals("knight", WizardModelCatalog.pieceModel('n'))
        assertNull(WizardModelCatalog.pieceModel('.'))
        assertEquals("weapon_queen_wand", WizardModelCatalog.weaponModel('q'))
        assertEquals("weapon_rook_sword", WizardModelCatalog.weaponModel('R'))
        // Le pion manie la même courte épée que la tour (un seul fichier pour les deux).
        assertEquals("weapon_rook_sword", WizardModelCatalog.weaponModel('P'))
        assertEquals("weapon_rook_sword", WizardModelCatalog.weaponModel('p'))
        assertEquals("wizard3d/pawn.glb", WizardModelCatalog.assetPath("pawn"))
        // Les six pièces de l'échiquier ont toutes un modèle.
        for (p in "KQRBNPkqrbnp") assertNotNull(WizardModelCatalog.pieceModel(p))
    }

    @Test
    fun lesPiecesRegardentDansLeSensDeLeurFichier() {
        for (p in "NnRrPp") {
            val expected = when (p.uppercaseChar()) { 'N' -> -90f; 'R' -> 90f; else -> 180f }
            assertEquals("$p", expected, WizardModelCatalog.facingYaw(p), 0f)
        }
        for (p in "KQBkqb") assertEquals(0f, WizardModelCatalog.facingYaw(p), 0f)
    }

    @Test
    fun marbrePourLesBlancsEtObsidiennePourLesNoirs() {
        assertTrue(WizardModelCatalog.materialFor('K') === WizardModelCatalog.MARBLE)
        assertTrue(WizardModelCatalog.materialFor('k') === WizardModelCatalog.OBSIDIAN)
        assertTrue(WizardModelCatalog.MARBLE.r > WizardModelCatalog.OBSIDIAN.r)
    }

    // ---------- Matrices ----------

    @Test
    fun laRotationAutourDeYEnvoieXVersMoinsZ() {
        val m = Mat4.rotationY(90f)
        // Colonne 0 = image de (1, 0, 0).
        assertEquals(0f, m[0], 1e-6f)
        assertEquals(-1f, m[2], 1e-6f)
    }

    @Test
    fun laPoseMetAEchelleTournePuisPlace() {
        val m = Mat4.pose(1f, 2f, 3f, yaw = 90f, scale = 2f)
        // Le point (1, 0, 0) : x2 -> (2, 0, 0), tourne -> (0, 0, -2), placé -> (1, 2, 1).
        val x = m[0] * 1f + m[12]
        val y = m[1] * 1f + m[13]
        val z = m[2] * 1f + m[14]
        assertEquals(1f, x, 1e-5f)
        assertEquals(2f, y, 1e-5f)
        assertEquals(1f, z, 1e-5f)
    }

    @Test
    fun leProduitAppliqueLeSecondFacteurEnPremier() {
        val m = Mat4.multiply(Mat4.translation(5f, 0f, 0f), Mat4.scaling(2f, 2f, 2f))
        // (1, 0, 0) -> x2 -> +5 = 7 (et non (1 + 5) x 2 = 12).
        assertEquals(7f, m[0] * 1f + m[12], 1e-6f)
    }

    // ---------- Rendu ----------

    private val white = Material3D(1f, 1f, 1f, specular = 0f, shininess = 8f)
    private val red = Material3D(1f, 0f, 0f, specular = 0f, shininess = 8f)

    // Un grand triangle plat dans le plan Z = [z], regardant vers +Z (donc vers la caméra par défaut), de couleur [material].
    private fun flatTriangle(z: Float, material: Material3D, facing: Float = 1f): Instance3D {
        val positions = floatArrayOf(-1f, -1f, z, 1f, -1f, z, 0f, 1f, z)
        val normals = floatArrayOf(0f, 0f, facing, 0f, 0f, facing, 0f, 0f, facing)
        val indices = if (facing > 0f) intArrayOf(0, 1, 2) else intArrayOf(0, 2, 1)
        return Instance3D(Mesh3D(positions, normals, indices), material)
    }

    private fun frontScene(vararg instances: Instance3D): Scene3D {
        val scene = Scene3D()
        scene.instances.addAll(instances)
        scene.camera = Camera3D(0f, 0f, 5f, 0f, 0f, 0f, fovDegrees = 40f)
        return scene
    }

    private fun render(scene: Scene3D, size: Int = 64, ss: Int = 1): Frame3D {
        val frame = Frame3D(size, size, ss)
        Renderer3D().render(scene, frame)
        return frame
    }

    private fun Frame3D.alphaAt(x: Int, y: Int): Int = pixels[y * width + x] ushr 24

    @Test
    fun uneSceneVideResteTransparente() {
        val frame = render(Scene3D())
        assertTrue(frame.pixels.all { it == 0 })
    }

    @Test
    fun unTriangleDeFaceEstDessineAuCentreEtPasDansLesCoins() {
        val frame = render(frontScene(flatTriangle(0f, white)))
        assertEquals(255, frame.alphaAt(32, 40))
        assertEquals(0, frame.alphaAt(1, 1))
        assertEquals(0, frame.alphaAt(62, 1))
        // Le haut du triangle est étroit, le bas est large.
        assertEquals(0, frame.alphaAt(10, 30))
        assertEquals(255, frame.alphaAt(32, 20))
    }

    @Test
    fun uneFaceTourneeVersLArriereNEstPasDessinee() {
        val frame = render(frontScene(flatTriangle(0f, white, facing = -1f)))
        assertTrue(frame.pixels.all { it == 0 })
    }

    @Test
    fun leTamponDeProfondeurGardeLeTriangleLePlusProcheQuelQueSoitLOrdre() {
        val far = flatTriangle(-1f, white)
        val near = flatTriangle(1f, red)
        for (order in listOf(listOf(far, near), listOf(near, far))) {
            val frame = render(frontScene(*order.toTypedArray()))
            val pixel = frame.pixels[40 * frame.width + 32]
            // Le rouge est devant : vert et bleu restent bas, le rouge domine.
            assertTrue("rouge attendu, obtenu ${Integer.toHexString(pixel)}", ((pixel shr 16) and 0xFF) > 100 && (pixel and 0xFF) < 20)
        }
    }

    @Test
    fun unObjetCacheOuDerriereLaCameraNEstPasDessine() {
        val hidden = flatTriangle(0f, white).also { it.visible = false }
        assertTrue(render(frontScene(hidden)).pixels.all { it == 0 })
        val behind = flatTriangle(10f, white) // derrière la caméra (placée en Z = 5)
        assertTrue(render(frontScene(behind)).pixels.all { it == 0 })
    }

    @Test
    fun deplacerLaMatriceDeplaceLObjet() {
        val inst = flatTriangle(0f, white)
        inst.setPose(0f, 0f, 0f, scale = 0.3f)
        val scene = frontScene(inst)
        val centered = render(scene)
        assertEquals(255, centered.alphaAt(32, 34))
        inst.setPose(1.5f, 0f, 0f, scale = 0.3f)
        val moved = render(scene)
        assertEquals(0, moved.alphaAt(32, 34))
        assertTrue((0 until 64).any { moved.alphaAt(it, 34) == 255 && it > 40 })
    }

    @Test
    fun leSurechantillonnageAdoucitLesBords() {
        val frame = render(frontScene(flatTriangle(0f, white)), size = 64, ss = 3)
        val partial = frame.pixels.count { val a = it ushr 24; a in 1..254 }
        assertTrue("des pixels de bord à moitié transparents", partial > 10)
        // Sans suréchantillonnage, un pixel est soit couvert soit vide.
        val hard = render(frontScene(flatTriangle(0f, white)), size = 64, ss = 1)
        assertEquals(0, hard.pixels.count { val a = it ushr 24; a in 1..254 })
    }

    @Test
    fun unModeleReelEstDessineEntierEtCentre() {
        val inst = Instance3D(loadAsset("king"), WizardModelCatalog.MARBLE)
        val scene = Scene3D()
        scene.instances.add(inst)
        scene.camera.fovDegrees = 30f
        scene.camera.orbit(0f, 0.044f, 0f, 0.3f, 0f, 10f)
        val frame = render(scene, size = 128, ss = 1)
        val covered = frame.pixels.count { (it ushr 24) == 255 }
        assertTrue("le roi couvre une part raisonnable de l'image ($covered)", covered in 600..8000)
        // Rien sur les bords : la pièce est entière dans le cadre.
        for (i in 0 until 128) {
            assertEquals(0, frame.alphaAt(i, 0)); assertEquals(0, frame.alphaAt(i, 127))
            assertEquals(0, frame.alphaAt(0, i)); assertEquals(0, frame.alphaAt(127, i))
        }
        // Ni noir ni blanc uni : la pièce est modelée par la lumière.
        val shades = frame.pixels.filter { (it ushr 24) == 255 }.map { it and 0xFF }.toSet()
        assertTrue("plusieurs niveaux de gris", shades.size > 30)
        assertFalse(frame.pixels.all { it == 0 })
    }

    @Test
    fun lObsidienneEstPlusSombreQueLeMarbre() {
        fun meanLuma(material: Material3D): Double {
            val scene = Scene3D()
            scene.instances.add(Instance3D(loadAsset("pawn"), material))
            scene.camera.fovDegrees = 30f
            scene.camera.orbit(0f, 0.016f, 0f, 0.2f, 0f, 10f)
            val px = render(scene, size = 96, ss = 1).pixels.filter { (it ushr 24) == 255 }
            return px.map { (((it shr 16) and 0xFF) + ((it shr 8) and 0xFF) + (it and 0xFF)) / 3.0 }.average()
        }
        assertTrue(meanLuma(WizardModelCatalog.MARBLE) > meanLuma(WizardModelCatalog.OBSIDIAN) + 60)
    }
    // Le pixel du centre de l'image, en gris (0 à 255).
    private fun Frame3D.centerLuma(): Int {
        val c = pixels[(height / 2) * width + width / 2]
        return (((c shr 16) and 0xFF) + ((c shr 8) and 0xFF) + (c and 0xFF)) / 3
    }

    // Deux triangles l'un derrière l'autre : un noir devant (z = 0) et un blanc 0,3 derrière lui.
    private fun frontBackScene(whiteBias: Float): Scene3D {
        val mesh = GlbParser.parse(triangleGlb())
        val scene = Scene3D()
        scene.instances.add(Instance3D(mesh, WizardModelCatalog.OBSIDIAN, Mat4.translation(0f, 0f, 0f)))
        scene.instances.add(
            Instance3D(mesh, WizardModelCatalog.MARBLE, Mat4.translation(0f, 0f, -0.3f)).also { it.depthBias = whiteBias }
        )
        scene.camera = Camera3D(eyeX = 0.3f, eyeY = 0.3f, eyeZ = 2f, targetX = 0.3f, targetY = 0.3f, targetZ = 0f, fovDegrees = 30f)
        return scene
    }

    @Test
    fun sansBiaisLObjetLePlusProcheMasqueCeluiDeDerriere() {
        assertTrue(render(frontBackScene(0f)).centerLuma() < 100)
    }

    @Test
    fun leBiaisDeProfondeurPasseUnObjetDevantUnAutreSansChangerSonImage() {
        // Le blanc, plus loin, mais rapproché de 1 pour le rangement : il masque le noir (la tête d'une pièce de la rangée du
        // dessous passe devant le pied de la pièce du dessus), et il garde sa taille : la scène ne bouge pas, seul l'ordre change.
        val biased = render(frontBackScene(1f))
        assertTrue(biased.centerLuma() > 150)
        val plain = render(frontBackScene(0f))
        assertEquals(plain.pixels.count { (it ushr 24) != 0 }, biased.pixels.count { (it ushr 24) != 0 })
    }

    @Test
    fun lePlusBasDesDeuxPiecesDuPlateauAuraitUnBiaisPlusGrand() {
        val high = Wizard3DCapture.restMatrix('P', 3.5f, 1.5f * WIZARD_ROW_PITCH)
        val low = Wizard3DCapture.restMatrix('p', 3.5f, 2.5f * WIZARD_ROW_PITCH)
        // Une rangée plus bas : plus près de la caméra, d'au moins une épaisseur de pièce (1,5 en unités du monde).
        assertTrue(Wizard3DCapture.depthBias(low) - Wizard3DCapture.depthBias(high) > 1.5f)
    }
    @Test
    fun lesPiecesDuCampQuiJoueTournentLeDosEtLesAutresFontFace() {
        for (p in "KQBNRPkqbnrp") {
            val front = Wizard3DCapture.restYaw(p, whiteToMove = !Chess.isWhite(p)) // son camp attend
            val away = Wizard3DCapture.restYaw(p, whiteToMove = Chess.isWhite(p)) // son camp joue
            assertEquals("$p sans consigne", front, Wizard3DCapture.restYaw(p), 0f)
            // Le regard (direction du lacet) du camp qui joue est l'opposé de celui du camp qui attend, en miroir de l'écran.
            val f = Math.toRadians(front.toDouble())
            val a = Math.toRadians(away.toDouble())
            assertEquals("$p : droite/gauche gardée", Math.sin(f), Math.sin(a), 1e-4)
            assertEquals("$p : devant/derrière inversé", Math.cos(f), -Math.cos(a), 1e-4)
        }
    }
}
