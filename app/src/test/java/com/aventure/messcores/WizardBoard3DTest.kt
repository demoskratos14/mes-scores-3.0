package com.aventure.messcores

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Toutes les pièces en 3D : celles du plateau au repos, et la pièce qui glisse pour un coup sans prise. */
class WizardBoard3DTest {

    private val meshes = HashMap<String, Mesh3D>()

    private fun assetsDir(): File =
        listOf(File("src/main/assets/wizard3d"), File("app/src/main/assets/wizard3d")).first { it.isDirectory }

    private fun mesh(model: String): Mesh3D? =
        meshes.getOrPut(model) { GlbParser.parse(File(assetsDir(), "$model.glb").readBytes()) }

    private fun sq(name: String): Int = Chess.squareOf(name)!!

    // La position de départ : les noirs en haut (rangée 8 d'abord), 32 pièces.
    private val start = "rnbqkbnr" + "pppppppp" + ".".repeat(32) + "PPPPPPPP" + "RNBQKBNR"

    @Test
    fun laPositionDeDepartPlaceLesTrenteDeuxPiecesEtSesSeizeArmes() {
        val board = WizardBoard3D { mesh(it) }
        assertTrue(board.setBoard(start, -1, flipped = false))
        // Le pion n'a pas d'arme : 32 pièces et 16 armes (cavaliers, tours, fous, dames et rois).
        assertEquals(48, board.scene.instances.size)
    }

    @Test
    fun laCaseAnimeeEstLaisseeVide() {
        val board = WizardBoard3D { mesh(it) }
        assertTrue(board.setBoard(start, sq("e2"), flipped = false))
        assertEquals(47, board.scene.instances.size)
        assertTrue(board.setBoard(start, sq("e1"), flipped = false))
        assertEquals(46, board.scene.instances.size)
    }

    @Test
    fun unPlateauVideOuIncompletNAPasDePieces() {
        val board = WizardBoard3D { mesh(it) }
        assertTrue(board.setBoard(".".repeat(64), -1, flipped = false))
        assertEquals(0, board.scene.instances.size)
        assertFalse(board.setBoard("pp", -1, flipped = false))
    }

    @Test
    fun unModeleManquantRameneAuxStatues2D() {
        val board = WizardBoard3D { null }
        assertFalse(board.setBoard(start, -1, flipped = false))
        assertEquals(0, board.scene.instances.size)
        // Seule l'arme manque : on ne montre pas des pièces sans leur arme.
        val noWeapons = WizardBoard3D { if (it.startsWith("weapon")) null else mesh(it) }
        assertFalse(noWeapons.setBoard(start, -1, flipped = false))
    }

    @Test
    fun lePionAuReposTombeAuCentreDeSaCase() {
        val board = WizardBoard3D { mesh(it) }
        // Le pion blanc d'e2 : colonne 4, rangée 6 à l'écran ; retourné, c'est la colonne 3 et la rangée 1.
        val expected = listOf(false to (4.5f to 6.5f), true to (3.5f to 1.5f))
        for ((flipped, centre) in expected) {
            board.setBoard(".".repeat(sq("e2")) + "P" + ".".repeat(63 - sq("e2")), -1, flipped)
            val m = board.scene.instances.single().matrix
            assertEquals(centre.first, m[12], 0.001f)
            assertEquals(Wizard3DCapture.BOARD - (centre.second + Wizard3DCapture.FOOT_OFFSET), m[13], 0.001f)
        }
    }

    @Test
    fun lesPiecesAuReposSontLesMemesQueAuDebutDUnePrise() {
        val fx = MoveFx(sq("e4"), sq("e5"), 'N', 'p', sq("e5"))
        val capture = Wizard3DCapture.create(fx) { mesh(it) }!!
        val rest = Wizard3DCapture.restMatrix('N', 4.5f, 4.5f)
        val begin = capture.pose(0f, false).mover
        for (i in 12..14) assertEquals(rest[i], begin[i], 0.001f)
    }

    @Test
    fun seuleUnPieceSansPriseGlisse() {
        assertNull(WizardSlide3D.create(MoveFx(sq("e4"), sq("e5"), 'K', 'p', sq("e5"))) { mesh(it) })
        assertNotNull(WizardSlide3D.create(MoveFx(sq("e2"), sq("e4"), 'P', '.', sq("e4"))) { mesh(it) })
        assertNull(WizardSlide3D.create(MoveFx(sq("e2"), sq("e4"), 'P', '.', sq("e4"))) { null })
    }

    @Test
    fun laPieceGlisseDeSaCaseDeDepartACelleDArrivee() {
        val slide = WizardSlide3D.create(MoveFx(sq("e2"), sq("e4"), 'P', '.', sq("e4"))) { mesh(it) }!!
        val start = slide.pose(0f, false)
        val end = slide.pose(1f, false)
        assertEquals(4.5f, start[12], 0.001f)
        assertEquals(4.5f, end[12], 0.001f)
        // Deux cases vers le haut de l'écran : le monde monte de 2 (l'axe Y du monde va vers le haut).
        assertEquals(2f, end[13] - start[13], 0.001f)
        // En route, la pièce est entre les deux.
        val middle = slide.pose(0.5f, false)
        assertTrue(middle[13] > start[13] && middle[13] < end[13])
    }
}
