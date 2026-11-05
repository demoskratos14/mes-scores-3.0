package com.aventure.messcores

import androidx.compose.ui.geometry.Rect
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Les pièces 3D d'une prise : où elles tombent sur l'échiquier, comment l'arme pivote, et le repli sur les statues 2D. */
class Wizard3DCaptureTest {

    private val meshes = HashMap<String, Mesh3D>()

    private fun assetsDir(): File =
        listOf(File("src/main/assets/wizard3d"), File("app/src/main/assets/wizard3d")).first { it.isDirectory }

    private fun mesh(model: String): Mesh3D? =
        meshes.getOrPut(model) { GlbParser.parse(File(assetsDir(), "$model.glb").readBytes()) }

    private fun sq(name: String): Int = Chess.squareOf(name)!!

    private fun fx(mover: Char, from: String, to: String, captured: Char) =
        MoveFx(sq(from), sq(to), mover, captured, sq(to))

    private fun capture(fx: MoveFx): Wizard3DCapture = Wizard3DCapture.create(fx) { mesh(it) }!!

    // Le centre de la case [square] de l'écran, en cases depuis le coin haut-gauche (le pied de la pièce est plus bas).
    private fun footX(square: Int, flipped: Boolean = false): Float =
        (if (flipped) 7 - square % 8 else square % 8) + 0.5f

    private fun footWorldY(square: Int, flipped: Boolean = false): Float =
        Wizard3DCapture.BOARD_H - (((if (flipped) 7 - square / 8 else square / 8) + 0.5f) * WIZARD_ROW_PITCH + Wizard3DCapture.FOOT_OFFSET)

    private fun transform(m: FloatArray, p: FloatArray) = floatArrayOf(
        m[0] * p[0] + m[4] * p[1] + m[8] * p[2] + m[12],
        m[1] * p[0] + m[5] * p[1] + m[9] * p[2] + m[13],
        m[2] * p[0] + m[6] * p[1] + m[10] * p[2] + m[14]
    )

    @Test
    fun seuleUnePriseADesPieces3D() {
        assertNull(Wizard3DCapture.create(fx('P', "e2", "e4", '.')) { mesh(it) })
        assertNotNull(Wizard3DCapture.create(fx('K', "e4", "e5", 'p')) { mesh(it) })
    }

    @Test
    fun unModeleManquantRamenePrisesAuxStatues2D() {
        assertNull(Wizard3DCapture.create(fx('K', "e4", "e5", 'p')) { null })
        // Seule l'arme manque : la scène n'est pas faite non plus (une pièce sans son arme serait étrange).
        assertNull(Wizard3DCapture.create(fx('K', "e4", "e5", 'p')) { if (it.startsWith("weapon")) null else mesh(it) })
        // Le pion n'a pas d'arme : son modèle seul suffit.
        assertNotNull(Wizard3DCapture.create(fx('P', "e4", "d5", 'n')) { if (it.startsWith("weapon")) null else mesh(it) })
    }

    @Test
    fun lePionNAPasDArmeEtLesAutresEnOnt() {
        assertNull(capture(fx('P', "e4", "d5", 'n')).pose(0.3f, false).weapon)
        for (piece in "KQRBN") {
            assertNotNull("arme de $piece", capture(fx(piece, "b4", "f4", 'p')).pose(0.3f, false).weapon)
        }
    }

    @Test
    fun auDepartLaPieceEtSaVictimeSontPosesSurLeursCases() {
        for (flipped in listOf(false, true)) {
            val f = fx('K', "b4", "f4", 'p')
            val pose = capture(f).pose(0f, flipped)
            assertEquals(footX(f.from, flipped), pose.mover[12], 1e-3f)
            assertEquals(footWorldY(f.from, flipped), pose.mover[13], 1e-3f)
            assertEquals(footX(f.to, flipped), pose.victim[12], 1e-3f)
            assertEquals(footWorldY(f.to, flipped), pose.victim[13], 1e-3f)
            assertTrue(pose.victimVisible)
        }
    }

    @Test
    fun alaFinLaPieceOccupeLaCaseDeSaVictime() {
        val f = fx('N', "b4", "f4", 'p')
        val pose = capture(f).pose(1f, false)
        assertEquals(footX(f.to), pose.mover[12], 1e-2f)
        assertEquals(footWorldY(f.to), pose.mover[13], 1e-2f)
    }

    @Test
    fun laVictimeResteIntacteJusquALImpactPuisDisparaitSelonLeCoup() {
        // Le cavalier l'écrase puis elle éclate (2D) ; le pion la tranche : elle bascule puis s'efface.
        val knight = capture(fx('N', "b4", "f4", 'p'))
        assertTrue(knight.pose(0.40f, false).victimVisible)
        assertTrue(knight.pose(0.50f, false).victimVisible)
        assertFalse(knight.pose(0.90f, false).victimVisible)

        val pawn = capture(fx('P', "e4", "f5", 'n'))
        assertTrue(pawn.pose(0.60f, false).victimVisible)
        assertFalse(pawn.pose(1f, false).victimVisible)
    }

    @Test
    fun laVictimeTranchéeBasculeLoinDuCoup() {
        // Le roi vient de la gauche : la victime tombe vers la droite (roulis négatif = sens des aiguilles d'une montre).
        val f = fx('K', "b4", "f4", 'p')
        val before = capture(f).pose(0.46f, false).victim
        val later = capture(f).pose(0.75f, false).victim
        assertTrue("elle se déplace vers la droite", later[12] > before[12])
        // Colonne X de la matrice : son Y (indice 1) devient négatif quand la pièce se couche vers la droite.
        assertTrue("elle se couche", abs(later[1]) > abs(before[1]) + 0.05f)
    }

    @Test
    fun laMainResteSurLArmeQuandElleSeLeve() {
        for (piece in "KNRB") {
            val f = fx(piece, "b4", "f4", 'p')
            val scene = capture(f)
            val mount = WizardModelCatalog.weaponMount(piece)!!
            for (progress in listOf(0.1f, 0.3f, 0.44f, 0.5f, 0.6f)) {
                val pose = scene.pose(progress, false)
                val hand = transform(pose.mover, WizardModelCatalog.handPoint(piece)!!)
                val grip = transform(pose.weapon!!, floatArrayOf(mount.gripX, mount.gripY, mount.gripZ))
                for (i in 0..2) assertEquals("$piece à $progress, axe $i", hand[i], grip[i], 1e-3f)
            }
        }
    }

    @Test
    fun leRoiLeveEtAbatSonEpee() {
        val scene = capture(fx('K', "b4", "f4", 'p'))
        val rest = scene.pose(0.1f, false).weapon!!
        val strike = scene.pose(0.44f, false).weapon!!
        var different = false
        for (i in 0 until 16) if (abs(rest[i] - strike[i]) > 1e-3f) different = true
        assertTrue("l'arme a bougé", different)
    }

    @Test
    fun lesPointsDAccrocheRestentPresDeLaPiece() {
        for (piece in "KQRBNP") {
            val f = fx(piece, "b4", "f4", 'p')
            val scene = capture(f)
            for (progress in listOf(0.3f, 0.44f, 0.5f)) {
                val pose = scene.pose(progress, false)
                val a = pose.anchors
                val x = pose.mover[12]
                val y = Wizard3DCapture.BOARD_H - pose.mover[13]
                val points = when (piece) {
                    'K', 'N' -> listOf(a.hand)
                    'Q' -> listOf(a.queenTip)
                    'R' -> listOf(a.muzzle)
                    'B' -> listOf(a.bishopHead)
                    else -> listOf(a.pawnHandA, a.pawnHandB)
                }
                for (p in points) {
                    val dx = p.x - x
                    val dy = p.y - y
                    assertTrue("$piece à $progress : point à ${sqrt(dx * dx + dy * dy)} cases", sqrt(dx * dx + dy * dy) < 3f)
                }
            }
        }
    }

    @Test
    fun laScenePeintLesDeuxPiecesChacuneDansSaCase() {
        val f = fx('K', "b4", "f4", 'p')
        val scene = capture(f)
        scene.update(0f, false)
        // L'image du test est carrée et ne couvre que le plateau (la scène voit aussi un bandeau au-dessus) : on la dirige sur le plateau.
        Wizard3DCapture.aim(scene.scene.camera, Rect(0f, 0f, 1f, 1f))
        // Les rangées sont plus écartées que les colonnes : l'image est plus haute que large dans la même proportion.
        val size = 800
        val height = (size * WIZARD_ROW_PITCH).toInt()
        val frame = Frame3D(size, height, 1)
        Renderer3D().render(scene.scene, frame)
        fun opaqueInColumn(col: Int): Int {
            var count = 0
            for (y in 0 until height) for (x in col * 100 until (col + 1) * 100) {
                if (frame.pixels[y * size + x] ushr 24 != 0) count++
            }
            return count
        }
        assertTrue("le roi est dans sa case", opaqueInColumn(f.from % 8) > 300)
        assertTrue("la victime est dans sa case", opaqueInColumn(f.to % 8) > 100)
        assertEquals("rien entre les deux", 0, opaqueInColumn(3) + opaqueInColumn(4) + opaqueInColumn(0) + opaqueInColumn(7))
    }

    @Test
    fun lePlateauRetourneMetLesPiecesDeLAutreCote() {
        val f = fx('K', "b4", "f4", 'p')
        val normal = capture(f).pose(0f, false)
        val flipped = capture(f).pose(0f, true)
        // Retourné, la colonne b (1) se retrouve en 6 : la pièce passe de la gauche à la droite de l'écran.
        assertTrue(normal.mover[12] < normal.victim[12])
        assertTrue(flipped.mover[12] > flipped.victim[12])
        assertEquals(8f - normal.mover[12], flipped.mover[12], 1e-3f)
    }

    @Test
    fun lesCasesDeLEcranSuiventLeSensDuPlateau() {
        val topLeft = wizardSquareOrigin(0, false, 100f)
        assertEquals(0f, topLeft.x, 1e-3f)
        assertEquals(0f, topLeft.y, 1e-3f)
        // Retourné, la case a8 passe en bas à droite.
        val flipped = wizardSquareOrigin(0, true, 100f)
        assertEquals(700f, flipped.x, 1e-3f)
        assertEquals(700f * WIZARD_ROW_PITCH, flipped.y, 1e-3f)
        // La case 17 est à la rangée 2, colonne 1.
        val other = wizardSquareOrigin(17, false, 100f)
        assertEquals(100f, other.x, 1e-3f)
        assertEquals(200f * WIZARD_ROW_PITCH, other.y, 1e-3f)
    }
}
