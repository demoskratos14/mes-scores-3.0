package com.aventure.messcores

import kotlin.math.PI
import kotlin.math.sin

/*
 * Toutes les pièces du style « sorcier » en 3D, même celles qui ne bougent pas (voir Wizard3D.kt pour la prise elle-même).
 *  - [WizardBoard3D] : les pièces au repos du plateau, dans une seule scène, calculée en une image fixe à chaque coup
 *    (voir `StaticScene3DView`) : le plateau au repos ne coûte donc rien image par image ;
 *  - [WizardSlide3D] : la pièce qui glisse d'une case à l'autre pour un coup sans prise.
 * Les poses viennent de [Wizard3DCapture.restMatrix], les mêmes que celles du début d'une prise : une pièce ne change donc
 * pas d'aspect quand elle passe du repos à l'animation.
 */

// Ajoute la pièce [piece] (et son arme, tenue dans sa main) à [scene] ; false si un modèle manque.
private fun addPiece(scene: Scene3D, piece: Char, matrix: FloatArray, meshOf: (String) -> Mesh3D?): Boolean {
    val mesh = WizardModelCatalog.pieceModel(piece)?.let(meshOf) ?: return false
    val material = WizardModelCatalog.materialFor(piece)
    scene.instances.add(Instance3D(mesh, material, matrix))
    val weaponModel = WizardModelCatalog.weaponModel(piece) ?: return true
    val weaponMesh = meshOf(weaponModel) ?: return false
    val local = WizardModelCatalog.weaponLocal(piece.uppercaseChar()) ?: return false
    scene.instances.add(Instance3D(weaponMesh, material, Mat4.multiply(matrix, local)))
    return true
}

/** Les pièces au repos de l'échiquier, dans une scène qui voit tout le plateau (voir [Wizard3DCapture.boardCamera]). */
internal class WizardBoard3D(private val meshOf: (String) -> Mesh3D?) {
    val scene = Scene3D().also { it.camera = Wizard3DCapture.boardCamera() }

    /**
     * Place les pièces de [board] (les 64 cases, `.` pour une case vide) sauf celle de la case [skip] (-1 : aucune), que
     * l'animation dessine à part. [flipped] : les noirs sont en bas. Rend false (et laisse la scène vide) si un modèle manque :
     * l'écran garde alors ses statues 2D.
     */
    fun setBoard(board: String, skip: Int, flipped: Boolean): Boolean {
        scene.instances.clear()
        if (board.length < 64) return false
        for (square in 0 until 64) {
            val piece = board[square]
            if (piece == '.' || square == skip) continue
            val col = if (flipped) 7 - square % 8 else square % 8
            val row = if (flipped) 7 - square / 8 else square / 8
            val matrix = Wizard3DCapture.restMatrix(piece, col + 0.5f, row + 0.5f)
            if (!addPiece(scene, piece, matrix, meshOf)) {
                scene.instances.clear()
                return false
            }
        }
        return true
    }
}

/** La pièce d'un coup sans prise, qui glisse de sa case de départ à sa case d'arrivée (comme la statue 2D, avec un petit rebond). */
internal class WizardSlide3D private constructor(val fx: MoveFx, private val mover: Instance3D, private val weapon: Instance3D?) {
    private val weaponLocal = WizardModelCatalog.weaponLocal(fx.mover.uppercaseChar())

    /** La scène à donner à `Scene3DView`, dont `update` doit appeler [update]. */
    val scene = Scene3D().also { s3d ->
        s3d.camera = Wizard3DCapture.boardCamera()
        s3d.instances.add(mover)
        weapon?.let { s3d.instances.add(it) }
    }

    /** Place la pièce pour l'avancement [progress] (de 0 à 1) du coup. */
    fun update(progress: Float, flipped: Boolean) {
        val matrix = pose(progress, flipped)
        mover.matrix = matrix
        val local = weaponLocal
        if (weapon != null && local != null) weapon.matrix = Mat4.multiply(matrix, local)
    }

    /** La matrice de la pièce à l'avancement [progress] : pas d'effet de bord, donc testable. */
    fun pose(progress: Float, flipped: Boolean): FloatArray {
        fun column(square: Int): Float = (if (flipped) 7 - square % 8 else square % 8) + 0.5f
        fun row(square: Int): Float = (if (flipped) 7 - square / 8 else square / 8) + 0.5f
        val slide = smooth(progress.coerceIn(0f, 1f))
        val x = column(fx.from) + (column(fx.to) - column(fx.from)) * slide
        val y = row(fx.from) + (row(fx.to) - row(fx.from)) * slide
        val bounce = 1f + 0.1f * sin(PI.toFloat() * slide)
        return Wizard3DCapture.restMatrix(fx.mover, x, y, bounce)
    }

    companion object {
        /** La scène du coup [fx], ou null pour une prise (voir [Wizard3DCapture]) ou si un modèle manque. */
        fun create(fx: MoveFx, meshOf: (String) -> Mesh3D?): WizardSlide3D? {
            if (fx.isCapture) return null
            val mesh = WizardModelCatalog.pieceModel(fx.mover)?.let(meshOf) ?: return null
            val material = WizardModelCatalog.materialFor(fx.mover)
            val weaponModel = WizardModelCatalog.weaponModel(fx.mover)
            val weaponMesh = weaponModel?.let(meshOf)
            if (weaponModel != null && weaponMesh == null) return null
            return WizardSlide3D(fx, Instance3D(mesh, material), weaponMesh?.let { Instance3D(it, material) })
        }
    }
}
