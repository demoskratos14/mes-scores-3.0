package com.aventure.messcores

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le mode explorer : tout l'échiquier en vraie 3D, et la caméra qui tourne autour. */
class ExploreBoard3DTest {

    private val meshes = HashMap<String, Mesh3D>()

    private fun assetsDir(): File =
        listOf(File("src/main/assets/wizard3d"), File("app/src/main/assets/wizard3d")).first { it.isDirectory }

    private fun mesh(model: String): Mesh3D? =
        meshes.getOrPut(model) { GlbParser.parse(File(assetsDir(), "$model.glb").readBytes()) }

    private fun sq(name: String): Int = Chess.squareOf(name)!!

    private val start = "rnbqkbnr" + "pppppppp" + ".".repeat(32) + "PPPPPPPP" + "RNBQKBNR"
    private val empty = ".".repeat(64)

    // Le cadre, le liseré, les dalles claires et les dalles sombres : quatre maillages, avant les pièces.
    private val boardMeshes = 4

    @Test
    fun laPositionDeDepartPlaceLePlateauEtLesSoixanteQuatrePiecesEtArmes() {
        val board = ExploreBoard3D { mesh(it) }
        assertTrue(board.setBoard(start, -1, -1, -1, flipped = false))
        // 32 pièces ; 16 pions à deux épées, 16 autres pièces à une arme : 32 + 16 × 2 + 16.
        assertEquals(boardMeshes + 80, board.scene.instances.size)
    }

    @Test
    fun leDernierCoupEtLEchecAjoutentLeurCouleurDeDalle() {
        val board = ExploreBoard3D { mesh(it) }
        assertTrue(board.setBoard(start, sq("e2"), sq("e4"), sq("e1"), flipped = false))
        assertEquals(boardMeshes + 2 + 80, board.scene.instances.size)
    }

    @Test
    fun unModeleManquantLaisseLaSceneVide() {
        val board = ExploreBoard3D { null }
        assertFalse(board.setBoard(start, -1, -1, -1, flipped = false))
        assertEquals(0, board.scene.instances.size)
        assertFalse(board.setBoard("pp", -1, -1, -1, flipped = false))
        assertEquals(0, board.scene.instances.size)
    }

    @Test
    fun lePionTientDeboutAuCentreDeSaCase() {
        val board = ExploreBoard3D { mesh(it) }
        // Le pion blanc d'e2 : colonne 4, rangée 6 depuis le haut ; retourné, colonne 3 et rangée 1.
        val expected = listOf(false to (4.5f to 6.5f), true to (3.5f to 1.5f))
        for ((flipped, centre) in expected) {
            board.setBoard(".".repeat(sq("e2")) + "P" + ".".repeat(63 - sq("e2")), -1, -1, -1, flipped)
            // Après le plateau : le pion, puis ses deux épées.
            assertEquals(boardMeshes + 3, board.scene.instances.size)
            val pawn = board.scene.instances[boardMeshes].matrix
            assertEquals(centre.first, pawn[12], 1e-4f)
            assertEquals(0f, pawn[13], 1e-4f)
            assertEquals(centre.second, pawn[14], 1e-4f)
        }
    }

    @Test
    fun lePionTientSesDeuxEpeesLeLongDesFlancsLameVersLArriere() {
        val board = ExploreBoard3D { mesh(it) }
        // Blanc en bas (il regarde vers le haut de l'écran, Z décroissant) puis noir en haut (il regarde vers le bas) : dans les deux cas,
        // la lame couchée sur son flanc part vers l'arrière, presque à l'horizontale. La lame va de la pointe (origine du fichier) vers le haut du fichier.
        for ((piece, backZ) in listOf('P' to 1f, 'p' to -1f)) {
            val square = sq(if (piece == 'P') "e2" else "e7")
            board.setBoard(".".repeat(square) + piece + ".".repeat(63 - square), -1, -1, -1, flipped = false)
            for (sword in 1..2) {
                val m = board.scene.instances[boardMeshes + sword].matrix
                val len = Math.sqrt((m[4] * m[4] + m[5] * m[5] + m[6] * m[6]).toDouble()).toFloat()
                val dirY = -m[5] / len
                val dirZ = -m[6] / len
                assertTrue("$piece épée $sword : lame vers l'arrière (z=$dirZ)", dirZ * backZ > 0.95f)
                assertTrue("$piece épée $sword : lame presque horizontale (y=$dirY)", dirY in 0f..0.2f)
            }
        }
    }

    @Test
    fun vuDuDessusLePlateauAfficheDesDallesClairesEtSombres() {
        val board = ExploreBoard3D { mesh(it) }
        assertTrue(board.setBoard(empty, -1, -1, -1, flipped = false))
        val view = BoardView().also { it.pitch = 88f }
        val pixels = render(board, view, 160, 200)
        val opaque = pixels.filter { (it ushr 24) != 0 }
        assertTrue("le plateau doit remplir une bonne part de l'image", opaque.size > 160 * 200 / 3)
        val light = opaque.count { luminance(it) > 120 }
        val dark = opaque.count { luminance(it) < 90 }
        assertTrue("dalles claires : $light sur ${opaque.size}", light > opaque.size / 12)
        assertTrue("dalles sombres : $dark sur ${opaque.size}", dark > opaque.size / 12)
    }

    @Test
    fun deTousLesCotesLeCentreDuPlateauEstDessine() {
        val board = ExploreBoard3D { mesh(it) }
        assertTrue(board.setBoard(start, -1, -1, -1, flipped = false))
        for (yaw in listOf(0f, 90f, 180f, 270f)) {
            for (pitch in listOf(15f, 50f, 88f)) {
                val view = BoardView().also { it.yaw = yaw; it.pitch = pitch }
                val pixels = render(board, view, 120, 150)
                assertTrue("centre vide (yaw $yaw, hauteur $pitch)", (pixels[75 * 120 + 60] ushr 24) != 0)
                assertEquals("le coin haut-gauche doit rester transparent", 0, pixels[0] ushr 24)
            }
        }
    }

    @Test
    fun laCameraTourneAutourDuPlateauEtSeRapprocheSansSortirDeSesLimites() {
        val view = BoardView()
        view.rotateBy(0f, 100000f)
        assertEquals(BoardView.MAX_PITCH, view.pitch, 1e-4f)
        view.rotateBy(0f, -100000f)
        assertEquals(BoardView.MIN_PITCH, view.pitch, 1e-4f)
        view.rotateBy(-100f, 0f)
        assertEquals(30f, view.yaw, 1e-3f)
        repeat(50) { view.zoomBy(2f) }
        assertEquals(BoardView.MIN_DISTANCE, view.distance, 1e-4f)
        repeat(50) { view.zoomBy(0.5f) }
        assertEquals(BoardView.MAX_DISTANCE, view.distance, 1e-4f)
        view.reset()
        assertEquals(BoardView.DEFAULT_DISTANCE, view.distance, 1e-4f)
        assertEquals(BoardView.DEFAULT_PITCH, view.pitch, 1e-4f)
        assertEquals(BoardView.DEFAULT_YAW, view.yaw, 1e-4f)
    }

    @Test
    fun unDoigtFaitGlisserLePlateauAvecLui() {
        val view = BoardView()
        view.panBy(100f, 0f, 1000f)
        assertTrue("le plateau suit le doigt vers la droite", view.targetX < BoardView.CENTER)
        view.reset()
        view.panBy(0f, 100f, 1000f)
        assertTrue("le plateau suit le doigt vers le bas", view.targetZ < BoardView.CENTER)
        // Vu depuis le côté (la caméra a tourné d'un quart de tour), la droite de l'écran est un autre côté du plateau.
        view.reset()
        view.yaw = 90f
        view.panBy(100f, 0f, 1000f)
        assertTrue(view.targetZ > BoardView.CENTER)
        // Jamais bien loin du plateau.
        repeat(1000) { view.panBy(500f, 500f, 100f) }
        assertTrue(view.targetX >= -BoardView.MARGIN - 1e-3f && view.targetX <= 8f + BoardView.MARGIN + 1e-3f)
        assertTrue(view.targetZ >= -BoardView.MARGIN - 1e-3f && view.targetZ <= 8f + BoardView.MARGIN + 1e-3f)
    }

    @Test
    fun laCameraVoitUnePieceMemeDeTresPres() {
        val board = ExploreBoard3D { mesh(it) }
        assertTrue(board.setBoard(start, -1, -1, -1, flipped = false))
        val view = BoardView().also {
            it.distance = BoardView.MIN_DISTANCE
            it.pitch = 20f
            it.targetX = 4.5f
            it.targetZ = 7.5f
        }
        val pixels = render(board, view, 120, 150)
        assertTrue(pixels.any { (it ushr 24) != 0 })
    }

    @Test
    fun lesDallesSontVeineesCommeDuMarbre() {
        val board = ExploreBoard3D { mesh(it) }
        assertTrue(board.setBoard(empty, -1, -1, -1, flipped = false))
        val view = BoardView().also { it.pitch = 88f; it.distance = 7f }
        val pixels = render(board, view, 240, 300)
        // Au centre de l'image : une dalle claire unie ne donnerait que quelques valeurs ; le marbre en donne beaucoup, dont des veines grises.
        val centre = pixels.filter { (it ushr 24) != 0 && luminance(it) > 60 }.map { luminance(it) }
        assertTrue("assez de tons différents : ${centre.toSet().size}", centre.toSet().size > 40)
        val veins = centre.count { it in 90..170 }
        assertTrue("des veines (tons moyens) : $veins", veins > 100)
    }

    @Test
    fun leMotifDeMarbreEstStableEtBorne() {
        val a = FloatArray(3)
        val b = FloatArray(3)
        for (i in 0 until 200) {
            val u = i * 0.37f - 3f
            val v = i * 0.53f + 1f
            MarbleField.sample(u, v, a)
            MarbleField.sample(u, v, b)
            assertEquals(a[0], b[0], 0f)
            assertTrue(a[0] in 0f..1.5f && a[1] in 0f..1f && a[2] in -1.2f..1.2f)
        }
    }

    @Test
    fun laTourPresenteSonBouclierAL_Ennemi() {
        val board = ExploreBoard3D { mesh(it) }
        // Le bouclier est du côté +Z dans le fichier de la tour : son image dans le monde (colonne Z de la matrice) doit viser
        // l'ennemi, vers le haut de l'écran (Z décroissant) pour les blancs du bas, vers le bas (Z croissant) pour les noirs du haut.
        for (flipped in listOf(false, true)) {
            for ((piece, square) in listOf('R' to sq("a1"), 'r' to sq("a8"))) {
                board.setBoard(".".repeat(square) + piece + ".".repeat(63 - square), -1, -1, -1, flipped)
                val shieldZ = board.scene.instances[boardMeshes].matrix[10]
                val whiteAtBottom = !flipped
                val enemyUp = Chess.isWhite(piece) == whiteAtBottom
                assertTrue("$piece flipped=$flipped : bouclier en z=$shieldZ", if (enemyUp) shieldZ < 0f else shieldZ > 0f)
            }
        }
    }

    private fun render(board: ExploreBoard3D, view: BoardView, width: Int, height: Int): IntArray {
        view.aspect = width.toFloat() / height
        board.aim(view)
        val frame = Frame3D(width, height, 1)
        Renderer3D().render(board.scene, frame)
        return frame.pixels
    }

    private fun luminance(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (r * 30 + g * 59 + b * 11) / 100
    }
}
