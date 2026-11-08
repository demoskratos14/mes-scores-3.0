package com.aventure.messcores

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.min

/*
 * Le plateau sorcier en vraie 3D, pour le mode explorer (voir ChessExplore.kt), en marbre (voir Marble3D.kt). Au repos, l'échiquier est dessiné en 2D (les
 * cases) avec les pièces 3D posées par-dessus, vues de face (voir WizardBoard3D.kt) ; une caméra qui tourne autour du plateau
 * ne peut pas se contenter de cela. Ici, tout est dans la scène : les dalles, le cadre et les pièces, sur un plateau horizontal.
 *
 * Repère du monde : 1 unité = 1 case. Le plateau est dans le plan Y = 0 (Y vers le haut) : X va de la colonne de gauche à celle
 * de droite (0 à 8), Z de la rangée du haut de l'écran à celle du bas (0 à 8). La caméra de départ est donc du côté de Z
 * grand, comme le joueur du bas. Les pièces gardent les mêmes échelles et les mêmes regards qu'en 2D (voir
 * [Wizard3DCapture.restYaw]).
 */

/** Les pièces et le plateau, dans une scène que la caméra de [BoardView] regarde de n'importe quel côté. */
internal class ExploreBoard3D(private val meshOf: (String) -> Mesh3D?) {
    val scene = Scene3D().also { it.outlineDepthStep = OUTLINE_STEP }

    /**
     * Construit la scène pour [board] (64 cases, `.` pour une case vide). [lastFrom] et [lastTo] (-1 : aucune) marquent le dernier
     * coup, [checked] (-1 : aucune) la case du roi en échec. [flipped] : les noirs sont en bas. Rend false (et laisse la scène vide)
     * si un modèle manque : l'écran garde alors son plateau 2D.
     */
    fun setBoard(board: String, lastFrom: Int, lastTo: Int, checked: Int, flipped: Boolean): Boolean {
        scene.instances.clear()
        if (board.length < 64) return false
        addBoard(lastFrom, lastTo, checked, flipped)
        for (square in 0 until 64) {
            val piece = board[square]
            if (piece == '.') continue
            val col = if (flipped) 7 - square % 8 else square % 8
            val row = if (flipped) 7 - square / 8 else square / 8
            if (!addPiece(piece, col + 0.5f, row + 0.5f, whiteAtBottom = !flipped)) {
                scene.instances.clear()
                return false
            }
        }
        return true
    }

    /** Place la caméra de la scène d'après [view] (appelé avant chaque image : lit seulement des valeurs). */
    fun aim(view: BoardView) {
        val camera = scene.camera
        camera.near = NEAR
        camera.fovDegrees = verticalFov(view.aspect)
        camera.orbit(view.targetX, TARGET_HEIGHT, view.targetZ, view.distance, view.yaw, view.pitch)
        // Le liseré coûte une passe de plus : on s'en passe pendant le mouvement (image réduite).
        scene.outline = if (view.moving) 0 else OUTLINE_WIDTH
    }

    private fun addBoard(lastFrom: Int, lastTo: Int, checked: Int, flipped: Boolean) {
        val light = MeshBuilder()
        val dark = MeshBuilder()
        val last = MeshBuilder()
        val check = MeshBuilder()
        for (square in 0 until 64) {
            val col = if (flipped) 7 - square % 8 else square % 8
            val row = if (flipped) 7 - square / 8 else square / 8
            // La couleur de la case ne dépend pas du retournement : a1 reste une case sombre.
            val isDark = (square / 8 + square % 8) % 2 != 0
            val target = when {
                square == checked -> check
                square == lastFrom || square == lastTo -> last
                isDark -> dark
                else -> light
            }
            target.box(col.toFloat(), -TILE_THICKNESS, row.toFloat(), col + 1f, 0f, row + 1f)
        }
        val base = MeshBuilder().box(-FRAME, -BASE_DEPTH, -FRAME, 8f + FRAME, -TILE_THICKNESS, 8f + FRAME)
        // Le liseré d'argent : quatre barres sur le bord extérieur du cadre.
        val trim = MeshBuilder()
        val lo = -FRAME
        val hi = 8f + FRAME
        trim.box(lo, -TILE_THICKNESS, lo, hi, TRIM_HEIGHT, lo + TRIM_WIDTH)
        trim.box(lo, -TILE_THICKNESS, hi - TRIM_WIDTH, hi, TRIM_HEIGHT, hi)
        trim.box(lo, -TILE_THICKNESS, lo + TRIM_WIDTH, lo + TRIM_WIDTH, TRIM_HEIGHT, hi - TRIM_WIDTH)
        trim.box(hi - TRIM_WIDTH, -TILE_THICKNESS, lo + TRIM_WIDTH, hi, TRIM_HEIGHT, hi - TRIM_WIDTH)
        for ((builder, material) in listOf(
            base to FRAME_MATERIAL,
            trim to TRIM_MATERIAL,
            light to LIGHT_TILE_MATERIAL,
            dark to DARK_TILE_MATERIAL,
            last to LAST_MOVE_MATERIAL,
            check to CHECK_MATERIAL
        )) {
            builder.build()?.let { scene.instances.add(Instance3D(it, material)) }
        }
    }

    // Ajoute la pièce [piece] (et son arme, dans sa main) debout au centre de la case ([x], [z]) ; false si un modèle manque.
    private fun addPiece(piece: Char, x: Float, z: Float, whiteAtBottom: Boolean): Boolean {
        val mesh = WizardModelCatalog.pieceModel(piece)?.let(meshOf) ?: return false
        val material = WizardModelCatalog.materialFor(piece)
        val yaw = Wizard3DCapture.restYaw(piece, whiteAtBottom) + WizardModelCatalog.facingYaw(piece)
        val scale = if (piece.uppercaseChar() == 'P') Wizard3DCapture.SCALE * PAWN_BOOST else Wizard3DCapture.SCALE
        val matrix = Mat4.pose(x, 0f, z, yaw = yaw, scale = scale)
        scene.instances.add(Instance3D(mesh, material, matrix))
        val weaponModel = WizardModelCatalog.weaponModel(piece) ?: return true
        val weaponMesh = meshOf(weaponModel) ?: return false
        val local = WizardModelCatalog.weaponLocal(piece.uppercaseChar()) ?: return false
        scene.instances.add(Instance3D(weaponMesh, material, Mat4.multiply(matrix, local)))
        return true
    }

    companion object {
        /** Le champ vertical (en degrés) qui garde la même largeur de vue quelle que soit la forme de l'écran ([aspect] = largeur / hauteur). */
        fun verticalFov(aspect: Float): Float {
            val a = min(aspect, 1f).coerceAtLeast(0.2f)
            return (2.0 * atan(BoardView.TAN_HALF_WIDE / a) * 180.0 / PI).toFloat()
        }

        /** Le point regardé, au-dessus du plateau : les pièces sont hautes, on les centre à peu près. */
        const val TARGET_HEIGHT = 0.4f

        private const val NEAR = 0.3f
        private const val OUTLINE_WIDTH = 2
        private const val OUTLINE_STEP = 0.02f
        private const val PAWN_BOOST = 1.3f

        // Le cadre : 0,55 case de large, sur une base épaisse.
        private const val FRAME = 0.55f
        private const val BASE_DEPTH = 0.5f
        private const val TILE_THICKNESS = 0.04f
        private const val TRIM_HEIGHT = 0.06f
        private const val TRIM_WIDTH = 0.12f

        // Le marbre : des dalles blanches (type Carrare, veines grises) et noires (type Nero Marquina, veines argentées), un cadre
        // de marbre bleu nuit, et des dalles de dernier coup et d'échec teintées dans leur couleur. Polies : brillance nette.
        private val LIGHT_TILE_MATERIAL = Material3D(
            0.92f, 0.91f, 0.89f, specular = 0.45f, shininess = 70f,
            marble = MarbleStyle(0.50f, 0.52f, 0.58f, veinStrength = 0.60f, haze = 0.22f, cloud = 0.06f)
        )
        private val DARK_TILE_MATERIAL = Material3D(
            0.10f, 0.12f, 0.17f, specular = 0.65f, shininess = 80f,
            marble = MarbleStyle(0.86f, 0.88f, 0.95f, veinStrength = 0.62f, haze = 0.20f, cloud = 0.25f)
        )
        private val LAST_MOVE_MATERIAL = Material3D(
            0.56f, 0.60f, 0.26f, specular = 0.45f, shininess = 70f,
            marble = MarbleStyle(0.86f, 0.90f, 0.52f, veinStrength = 0.55f, haze = 0.14f, cloud = 0.08f)
        )
        private val CHECK_MATERIAL = Material3D(
            0.80f, 0.26f, 0.26f, specular = 0.45f, shininess = 70f,
            marble = MarbleStyle(0.98f, 0.66f, 0.62f, veinStrength = 0.55f, haze = 0.14f, cloud = 0.08f)
        )
        private val FRAME_MATERIAL = Material3D(
            0.13f, 0.15f, 0.24f, specular = 0.70f, shininess = 80f,
            marble = MarbleStyle(0.62f, 0.68f, 0.88f, veinStrength = 0.50f, haze = 0.10f, cloud = 0.20f)
        )
        private val TRIM_MATERIAL = Material3D(0.74f, 0.77f, 0.84f, specular = 0.90f, shininess = 70f)
    }
}

/**
 * Assemble des boîtes en un seul maillage. Chaque face est écrite dans le sens trigonométrique vu de l'extérieur (c'est la face
 * avant pour le moteur, voir Renderer3D.kt), avec sa normale. La face du dessous n'est jamais vue : on ne la crée pas.
 */
internal class MeshBuilder {
    private val positions = ArrayList<Float>()
    private val normals = ArrayList<Float>()
    private val indices = ArrayList<Int>()

    /** Ajoute la boîte de coins ([x0], [y0], [z0]) et ([x1], [y1], [z1]) (dessus et quatre côtés). Rend le constructeur. */
    fun box(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float): MeshBuilder {
        val dx = x1 - x0
        val dy = y1 - y0
        val dz = z1 - z0
        // dessus (+Y), puis +X, −X, +Z, −Z : chaque coin et ses deux côtés u et v vérifient u × v = normale.
        face(0f, 1f, 0f, x0, y1, z1, dx, 0f, 0f, 0f, 0f, -dz)
        face(1f, 0f, 0f, x1, y0, z0, 0f, dy, 0f, 0f, 0f, dz)
        face(-1f, 0f, 0f, x0, y0, z0, 0f, 0f, dz, 0f, dy, 0f)
        face(0f, 0f, 1f, x0, y0, z1, dx, 0f, 0f, 0f, dy, 0f)
        face(0f, 0f, -1f, x0, y0, z0, 0f, dy, 0f, dx, 0f, 0f)
        return this
    }

    private fun face(
        nx: Float, ny: Float, nz: Float,
        cx: Float, cy: Float, cz: Float,
        ux: Float, uy: Float, uz: Float,
        vx: Float, vy: Float, vz: Float
    ) {
        val first = positions.size / 3
        val corners = listOf(
            Triple(cx, cy, cz),
            Triple(cx + ux, cy + uy, cz + uz),
            Triple(cx + ux + vx, cy + uy + vy, cz + uz + vz),
            Triple(cx + vx, cy + vy, cz + vz)
        )
        for ((x, y, z) in corners) {
            positions.add(x); positions.add(y); positions.add(z)
            normals.add(nx); normals.add(ny); normals.add(nz)
        }
        indices.addAll(listOf(first, first + 1, first + 2, first, first + 2, first + 3))
    }

    /** Le maillage, ou null si aucune boîte n'a été ajoutée. */
    fun build(): Mesh3D? {
        if (indices.isEmpty()) return null
        return Mesh3D(positions.toFloatArray(), normals.toFloatArray(), indices.toIntArray())
    }
}
