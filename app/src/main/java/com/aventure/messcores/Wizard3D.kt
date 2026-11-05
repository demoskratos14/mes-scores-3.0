package com.aventure.messcores

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Les pièces 3D du style « sorcier » dans l'animation d'une prise (voir WizardModels.kt pour les modèles, Renderer3D.kt pour
 * le moteur). La scène recouvre l'échiquier : la caméra est placée loin devant, à l'axe, avec un champ si étroit que 1 unité
 * du monde = 1 case de l'écran. Un point (x, y) du plateau, en cases depuis son coin haut-gauche, est donc le point
 * (x, 8 × pas − y) du monde (le pas des rangées : voir WIZARD_ROW_PITCH), à la profondeur 0 : les pièces 3D tombent pile là où les statues 2D seraient dessinées, et suivent le
 * même mouvement (voir [captureMotion]). Seules la pièce qui prend et sa victime sont en 3D ; le reste de la prise (éclairs,
 * éclats, traînées de l'arme, lames conjurées) reste dessiné en 2D par-dessus, accroché aux points de [CaptureAnchors].
 */

/**
 * La résolution de calcul de la scène 3D, en part des pixels de l'écran (le rendu est logiciel : 0,75 garde un dessin net tout en
 * restant fluide sur un téléphone modeste ; en gros plan, la scène ne calcule que la zone regardée par la caméra de la prise,
 * donc ces pixels servent à une zone bien plus petite et l'image reste nette malgré le zoom).
 */
internal const val WIZARD_3D_RENDER_SCALE = 0.9f

/**
 * La résolution de calcul de l'image fixe de toutes les pièces au repos (voir WizardBoard3D.kt). Elle n'est calculée qu'une fois par
 * coup, donc on peut la prendre plus fine que celle de l'animation : les pièces restent nettes quand la caméra d'une prise
 * agrandit le plateau (1,25 fois les pixels de l'écran ; la mémoire de travail grandit avec le carré de ce nombre).
 */
internal const val WIZARD_3D_BOARD_RENDER_SCALE = 1.25f

/** Largeur (en pixels de l'image calculée) du liseré qui détache les pièces : clair sur les pièces sombres, sombre sur les claires. 0 : aucun. */
internal const val WIZARD_3D_OUTLINE = 2

/** Les points où les effets 2D s'accrochent aux pièces 3D, en cases du plateau (1 = une case, origine : coin haut-gauche). */
internal class CaptureAnchors(
    /** Les deux mains du pion, d'où surgissent ses épées conjurées. */
    val pawnHandA: Offset = Offset.Zero,
    val pawnHandB: Offset = Offset.Zero,
    /** Le bout de la baguette de la dame, d'où surgit sa lame. */
    val queenTip: Offset = Offset.Zero,
    /** La main du roi ou du cavalier, centre de rotation de son arme, et la portée de l'arme (en cases). */
    val hand: Offset = Offset.Zero,
    val swingRadius: Float = 0f,
    /** Le bout de l'épée de la tour, d'où part le carreau. */
    val muzzle: Offset = Offset.Zero,
    /** Le sommet de la crosse du fou, d'où part l'éclair. */
    val bishopHead: Offset = Offset.Zero
)

/** Une image de la prise : les matrices des deux pièces (et de l'arme), la victime et les points d'accroche des effets. */
internal class Wizard3DPose(
    val mover: FloatArray,
    val weapon: FloatArray?,
    val victim: FloatArray,
    val victimVisible: Boolean,
    val anchors: CaptureAnchors
)

internal class Wizard3DCapture private constructor(
    val fx: MoveFx,
    moverMesh: Mesh3D,
    private val weaponMesh: Mesh3D?,
    victimMesh: Mesh3D
) {
    private val mover = Instance3D(moverMesh, WizardModelCatalog.materialFor(fx.mover))
    private val weapon = weaponMesh?.let { Instance3D(it, WizardModelCatalog.materialFor(fx.mover)) }
    private val victim = Instance3D(victimMesh, WizardModelCatalog.materialFor(fx.captured))

    private val camera = boardCamera()

    /** La scène à donner à `Scene3DView`, dont `update` doit appeler [update]. */
    val scene = Scene3D().also { s3d ->
        s3d.instances.add(victim)
        s3d.instances.add(mover)
        weapon?.let { s3d.instances.add(it) }
        s3d.camera = camera
        s3d.outline = WIZARD_3D_OUTLINE
    }

    /**
     * La zone du plateau que la scène calcule en ce moment, en parts du plateau (0 à 1, origine : coin haut-gauche) : tout le
     * plateau au repos, une petite zone autour de l'action quand la caméra de la prise zoome. `Scene3DView` dessine l'image
     * calculée exactement dans ce rectangle (voir son paramètre `region`).
     */
    var region: Rect = FULL_REGION
        private set

    /** Place les pièces pour l'avancement [progress] (de 0 à 1) de la prise, et la fenêtre de la scène sur ce que regarde la caméra. */
    fun update(progress: Float, flipped: Boolean) {
        frameCamera(progress, flipped)
        val pose = pose(progress, flipped)
        mover.matrix = pose.mover
        weapon?.let { it.matrix = pose.weapon ?: it.matrix }
        victim.matrix = pose.victim
        victim.visible = pose.victimVisible
    }

    // La fenêtre de la scène suit la caméra de la prise (voir wizardCamera) : même centre, et une largeur un peu plus grande que
    // la zone vue (marge pour l'inclinaison et la secousse), pour ne calculer que ce qu'on voit et garder des pièces nettes.
    private fun frameCamera(progress: Float, flipped: Boolean) {
        val cam = wizardCamera(fx, progress, flipped)
        val span = if (cam == null) 1f else (WINDOW_MARGIN / cam.zoom).coerceAtMost(1f)
        val cx = cam?.viewX ?: 0.5f
        val cy = cam?.viewY ?: 0.5f
        // La fenêtre dépasse un peu en haut : les pièces hautes de la rangée du fond dépassent du plateau.
        region = Rect(cx - span / 2f, cy - span / 2f - TOP_FRACTION, cx + span / 2f, cy + span / 2f)
        aim(camera, region)
    }

    /** Les points d'accroche des effets 2D à l'avancement [progress]. */
    fun anchors(progress: Float, flipped: Boolean): CaptureAnchors = pose(progress, flipped).anchors

    /** Où sont les pièces à l'avancement [progress] ([flipped] : les noirs sont en bas) : pas d'effet de bord, donc testable. */
    fun pose(progress: Float, flipped: Boolean): Wizard3DPose {
        val cell = 100f
        val m = captureMotionFor(fx, progress, cell, flipped)
        val kind = m.kind
        // Le sens de l'attaque (vers la droite si la victime est à droite de la case de départ) ne change pas pendant la prise.
        val attackDx = m.targetCenter.x - (wizardSquareOrigin(fx.from, flipped, cell).x + cell / 2f)

        // ----- La pièce qui prend -----
        val moverX = m.center.x / cell
        val moverRow = m.center.y / cell + FOOT_OFFSET
        val heading = moverYaw(attackDx, progress, cell)
        val moverMatrix = Mat4.pose(
            moverX, BOARD_H - moverRow, depth(moverRow), yaw = heading, scale = scaleOf(fx.mover) * m.bounce
        )

        // L'arme suit la pièce, et pivote autour de la main (dans le plan de l'écran) selon le geste de l'animation 2D :
        // un angle d'écran positif tourne dans le sens des aiguilles d'une montre, donc un roulis négatif autour de Z.
        var weaponMatrix: FloatArray? = null
        var hooks = CaptureAnchors()
        val local = WizardModelCatalog.weaponLocal(kind)
        val hand = WizardModelCatalog.handPoint(kind)
        if (local != null && hand != null) {
            val rest = Mat4.multiply(moverMatrix, local)
            val h = transform(moverMatrix, hand[0], hand[1], hand[2])
            var w = rest
            if (m.weaponDeg != 0f || m.weaponScale != 1f) {
                val s = m.weaponScale
                var swing = Mat4.translation(h[0], h[1], h[2])
                swing = Mat4.multiply(swing, Mat4.rotationZ(-m.weaponDeg))
                swing = Mat4.multiply(swing, Mat4.scaling(s, s, s))
                swing = Mat4.multiply(swing, Mat4.translation(-h[0], -h[1], -h[2]))
                w = Mat4.multiply(swing, rest)
            }
            weaponMatrix = w
            // Le bout de l'arme : la pointe (origine du fichier) pour les épées et la baguette (tenue pointe en bas), le haut pour la crosse et la masse.
            val tipLocalY = if (kind == 'K' || kind == 'R' || kind == 'Q') 0f else (weaponMesh?.maxY ?: 0f)
            val tip = transform(w, 0f, tipLocalY, 0f)
            val reach = sqrt((tip[0] - h[0]) * (tip[0] - h[0]) + (tip[1] - h[1]) * (tip[1] - h[1]))
            hooks = CaptureAnchors(
                queenTip = toBoard(tip),
                hand = toBoard(h),
                swingRadius = (reach * 0.9f).coerceAtLeast(MIN_SWING_RADIUS),
                muzzle = toBoard(tip),
                bishopHead = toBoard(tip)
            )
        } else if (kind == 'P') {
            // Le pion n'a pas d'arme : ses épées surgissent de ses poings, à peu près à mi-hauteur.
            val grow = scaleOf(fx.mover) / REFERENCE_SCALE
            hooks = CaptureAnchors(
                pawnHandA = Offset(moverX - 0.07f * grow, moverRow - 0.20f * grow),
                pawnHandB = Offset(moverX + 0.07f * grow, moverRow - 0.16f * grow)
            )
        }

        // ----- La victime : intacte jusqu'à l'impact, puis elle subit le coup comme la statue 2D -----
        val victimX = m.targetCenter.x / cell
        val victimRow = m.targetCenter.y / cell + FOOT_OFFSET
        var roll = 0f
        var shiftX = 0f
        var rise = 0f
        var squashX = 1f
        var squashY = 1f
        var shrink = 1f
        var visible = true
        val after = m.after
        when (kind) {
            'P', 'Q', 'K' -> {
                // Tranchée : elle bascule en s'écartant du coup, puis s'efface.
                val ease = 1f - (1f - after) * (1f - after)
                roll = m.side * 80f * ease
                shiftX = -m.side * 0.3f * ease
                shrink = 1f - smooth(((after - 0.45f) / 0.55f).coerceIn(0f, 1f))
            }
            'N' -> {
                // Écrasée : elle s'aplatit sous la masse, puis ne reste que l'onde de choc et les éclats 2D.
                if (after < CRUSH_END) {
                    val t = smooth(after / CRUSH_END)
                    squashX = 1f + 0.18f * t
                    squashY = 1f - 0.5f * t
                } else {
                    visible = false
                }
            }
            'R' -> {
                // Percée : elle tressaille, puis les fissures 2D la font éclater.
                if (after < PIERCE_END) shiftX = sin(after * 140f) * 0.03f else visible = false
            }
            else -> {
                // Dissoute en lumière : elle rétrécit en s'élevant.
                if (after < DISSOLVE_END) {
                    val t = after / DISSOLVE_END
                    shrink = 1f - smooth(t)
                    rise = 0.3f * t
                } else {
                    visible = false
                }
            }
        }
        if (shrink < 0.02f) visible = false
        var victimMatrix = Mat4.translation(victimX + shiftX, BOARD_H - victimRow + rise, depth(victimRow))
        victimMatrix = Mat4.multiply(victimMatrix, Mat4.rotationZ(roll))
        victimMatrix = Mat4.multiply(victimMatrix, Mat4.scaling(squashX, squashY, squashX))
        victimMatrix = Mat4.multiply(victimMatrix, Mat4.rotationY(victimYaw(attackDx, progress, cell)))
        val s = scaleOf(fx.captured) * shrink
        victimMatrix = Mat4.multiply(victimMatrix, Mat4.scaling(s, s, s))

        return Wizard3DPose(moverMatrix, weaponMatrix, victimMatrix, visible, hooks)
    }

    // Le lacet de la pièce qui prend : son regard habituel (les blanches regardent à gauche, les noires à droite, pour celles dessinées
    // de profil), puis elle se tourne vers sa victime pour frapper et reprend son regard habituel en avançant.
    private fun moverYaw(attackDx: Float, progress: Float, cell: Float): Float {
        val profile = fx.mover.uppercaseChar() in PROFILE
        val own = if (Chess.isWhite(fx.mover)) -1f else 1f
        val sideways = abs(attackDx) > cell * 0.15f
        val sign = if (sideways) (if (attackDx > 0f) 1f else -1f) else own
        val defaultYaw = if (profile) own * PROFILE_YAW else 0f
        val attackYaw = when {
            profile -> sign * PROFILE_YAW
            sideways -> sign * FRONT_YAW
            else -> 0f
        }
        val turn = smooth((progress / ATTACK_START).coerceIn(0f, 1f)) *
            (1f - smooth(((progress - FOLLOW_END) / (STEP_END - FOLLOW_END)).coerceIn(0f, 1f)))
        return defaultYaw + (attackYaw - defaultYaw) * turn + facingOffset(fx.mover)
    }

    // La victime, elle, se tourne vers celui qui l'attaque (si le coup vient de côté).
    private fun victimYaw(attackDx: Float, progress: Float, cell: Float): Float {
        val profile = fx.captured.uppercaseChar() in PROFILE
        val own = if (Chess.isWhite(fx.captured)) -1f else 1f
        val sideways = abs(attackDx) > cell * 0.15f
        val toward = if (attackDx > 0f) -1f else 1f
        val defaultYaw = if (profile) own * PROFILE_YAW else 0f
        val attackYaw = when {
            !sideways -> defaultYaw
            profile -> toward * PROFILE_YAW
            else -> toward * FRONT_YAW
        }
        val t = smooth((progress / ATTACK_START).coerceIn(0f, 1f))
        return defaultYaw + (attackYaw - defaultYaw) * t + facingOffset(fx.captured)
    }

    // Le décalage de lacet qui fait regarder la pièce vers +Z (pour les pièces de profil du catalogue, voir WizardModelCatalog).
    private fun facingOffset(piece: Char): Float = WizardModelCatalog.facingYaw(piece)

    companion object {
        /** Les huit cases du plateau, et donc la hauteur du monde visible : 1 unité = 1 case. */
        const val BOARD = 8f

        /** La hauteur du plateau en unités du monde (cases de largeur) : les rangées sont plus écartées que les colonnes ([WIZARD_ROW_PITCH]). */
        const val BOARD_H = BOARD * WIZARD_ROW_PITCH

        /** Loin devant l'échiquier : la perspective y est presque nulle, les pièces gardent la taille de leur case. */
        const val CAMERA_DISTANCE = 60f

        /** Cases par mètre : le roi (88 mm) fait environ 1,75 case de haut, le pion (32 mm) 0,8. À régler au goût. */
        const val SCALE = 20f

        /** Le pion est le plus petit : on le grossit en plus, pour qu'il ne paraisse pas minuscule à côté des autres pièces. */
        private const val PAWN_BOOST = 1.3f

        /** L'échelle d'origine (avant l'agrandissement), à laquelle sont réglés les points d'accroche fixes du pion. */
        private const val REFERENCE_SCALE = 14f

        private fun scaleOf(piece: Char): Float = if (piece.uppercaseChar() == 'P') SCALE * PAWN_BOOST else SCALE

        /** Le pied de la pièce, sous le centre de sa case (en cases) : 0,88 depuis le haut de la case, comme les statues 2D. */
        const val FOOT_OFFSET = 0.45f

        /** Part de la zone vue qu'on calcule en plus (la caméra de la prise incline le plateau : on voit un peu plus loin en haut). */
        private const val WINDOW_MARGIN = 1.4f

        /** Ce que la scène voit au-dessus du plateau, en cases : les pièces hautes (le roi fait près de 1,8 case) de la rangée du fond y dépassent. */
        const val TOP_EXTRA = 1f

        private const val TOP_FRACTION = TOP_EXTRA / BOARD_H

        /** Tout l'échiquier et le bandeau au-dessus, en parts du plateau (0 à 1, origine : coin haut-gauche) : la zone vue au repos. */
        val FULL_REGION = Rect(0f, -TOP_FRACTION, 1f, 1f)

        /** Dirige [camera] (de face, à l'axe) sur [region] (en parts du plateau) : 1 unité du monde = 1 case de l'écran. */
        fun aim(camera: Camera3D, region: Rect) {
            val x = (region.left + region.right) / 2f * BOARD
            val y = BOARD_H - (region.top + region.bottom) / 2f * BOARD_H
            camera.eyeX = x
            camera.targetX = x
            camera.eyeY = y
            camera.targetY = y
            camera.eyeZ = CAMERA_DISTANCE
            camera.targetZ = 0f
            camera.fovDegrees = fovFor(region.height * BOARD_H)
        }

        /** La caméra qui voit tout l'échiquier ([FULL_REGION]), de face (voir le commentaire du fichier). */
        fun boardCamera(): Camera3D = Camera3D(near = 1f).also { aim(it, FULL_REGION) }

        /**
         * La matrice d'une pièce [piece] debout au centre ([centerX], [centerY]) d'une case, en cases depuis le coin haut-gauche
         * de l'écran, avec le regard qu'elle a au repos (c'est aussi celui qu'elle a au début d'une prise) ; [bounce] la gonfle
         * un peu quand elle se déplace.
         */
        fun restMatrix(piece: Char, centerX: Float, centerY: Float, bounce: Float = 1f): FloatArray {
            val footRow = centerY + FOOT_OFFSET
            val profile = piece.uppercaseChar() in PROFILE
            val own = if (Chess.isWhite(piece)) -1f else 1f
            val yaw = (if (profile) own * PROFILE_YAW else 0f) + WizardModelCatalog.facingYaw(piece)
            return Mat4.pose(centerX, BOARD_H - footRow, depth(footRow), yaw = yaw, scale = scaleOf(piece) * bounce)
        }

        /** Le champ (en degrés) pour voir [height] cases de haut à la distance de la caméra. */
        private fun fovFor(height: Float): Float =
            (2.0 * atan((height / 2f) / CAMERA_DISTANCE.toDouble()) * 180.0 / PI).toFloat()

        private const val DEPTH_PER_ROW = 0.12f
        private const val PROFILE_YAW = 65f
        private const val FRONT_YAW = 40f
        private const val CRUSH_END = 0.2f
        private const val PIERCE_END = 0.14f
        private const val DISSOLVE_END = 0.3f
        private const val MIN_SWING_RADIUS = 0.3f

        // Les pièces de profil dans leur dessin 2D.
        private val PROFILE = setOf('P', 'N', 'R')

        /** Plus une pièce est basse à l'écran, plus elle est près de la caméra : elle passe devant celles du dessus. */
        private fun depth(row: Float): Float = (row - BOARD_H / 2f) * DEPTH_PER_ROW

        private fun toBoard(p: FloatArray): Offset = Offset(p[0], BOARD_H - p[1])

        private fun transform(m: FloatArray, x: Float, y: Float, z: Float): FloatArray = floatArrayOf(
            m[0] * x + m[4] * y + m[8] * z + m[12],
            m[1] * x + m[5] * y + m[9] * z + m[13],
            m[2] * x + m[6] * y + m[10] * z + m[14]
        )

        /**
         * La scène d'une prise [fx], ou null si un modèle manque ou est illisible : l'écran garde alors ses statues 2D.
         * [meshOf] donne le maillage d'un modèle du catalogue (voir [WizardModels.mesh]).
         */
        fun create(fx: MoveFx, meshOf: (String) -> Mesh3D?): Wizard3DCapture? {
            if (!fx.isCapture) return null
            val moverMesh = WizardModelCatalog.pieceModel(fx.mover)?.let(meshOf) ?: return null
            val victimMesh = WizardModelCatalog.pieceModel(fx.captured)?.let(meshOf) ?: return null
            val weaponModel = WizardModelCatalog.weaponModel(fx.mover)
            val weaponMesh = weaponModel?.let(meshOf)
            if (weaponModel != null && weaponMesh == null) return null
            return Wizard3DCapture(fx, moverMesh, weaponMesh, victimMesh)
        }
    }
}
