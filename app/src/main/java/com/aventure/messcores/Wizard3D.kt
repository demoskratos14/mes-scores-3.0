package com.aventure.messcores

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Les pièces 3D du style « sorcier » dans l'animation d'une prise (voir WizardModels.kt pour les modèles, Renderer3D.kt pour
 * le moteur). La scène recouvre l'échiquier : la caméra est placée loin devant, à l'axe, avec un champ si étroit que 1 unité
 * du monde = 1 case de l'écran. Un point (x, y) du plateau, en cases depuis son coin haut-gauche, est donc le point
 * (x, 8 × pas − y) du monde (le pas des rangées : voir WIZARD_ROW_PITCH), à la profondeur 0 : les pièces 3D tombent pile là où les statues 2D seraient dessinées, et suivent le
 * même mouvement (voir [captureMotion]). Seules la pièce qui prend, son arme, sa victime et ses débris (gravats ou poussière, voir
 * Wizard3DDebris.kt) sont en 3D ; le reste de la prise (éclairs,
 * éclats, traînées de l'arme) reste dessiné en 2D par-dessus, accroché aux points de [CaptureAnchors]. Toutes les pièces frappent avec
 * l'arme qu'elles tiennent (le pion avec ses deux courtes épées, qui se referment en ciseaux sur sa victime) ; seule la statue 2D du repli conjure des lames.
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
    /** Le bout de la baguette de la dame (qu'elle balaie pour frapper). */
    val queenTip: Offset = Offset.Zero,
    /** La main du pion, du roi ou du cavalier, centre de rotation de son arme, et la portée de l'arme (en cases). */
    val hand: Offset = Offset.Zero,
    val swingRadius: Float = 0f,
    /** Le bout de l'épée de la tour, d'où part le carreau. */
    val muzzle: Offset = Offset.Zero,
    /** Le sommet de la crosse du fou, d'où part l'éclair. */
    val bishopHead: Offset = Offset.Zero,
    /** Le pion : sa seconde épée (dans son autre poing). Null pour les autres pièces. */
    val scissors: ScissorsHook? = null
)

/**
 * Les deux épées du pion : [hand][CaptureAnchors.hand] et [CaptureAnchors.swingRadius] sont celles de la première, [handB] et
 * [swingRadiusB] celles de la seconde. Les angles sont ceux d'écran (degrés, sens horaire, 0 = vers la droite) : lame levée
 * ([windA], [windB]), lame au moment du coup ([strikeA], [strikeB]) et lame en ce moment ([headA], [headB]).
 */
internal class ScissorsHook(
    val handB: Offset,
    val swingRadiusB: Float,
    val windA: Float,
    val strikeA: Float,
    val headA: Float,
    val windB: Float,
    val strikeB: Float,
    val headB: Float
)

/** Une image de la prise : les matrices des deux pièces (et de l'arme), la victime et les points d'accroche des effets. */
internal class Wizard3DPose(
    val mover: FloatArray,
    val weapon: FloatArray?,
    val victim: FloatArray,
    val victimVisible: Boolean,
    val anchors: CaptureAnchors,
    /** La seconde épée du pion (celle de son autre poing) : les deux lames se referment en ciseaux sur la victime. Null pour les autres pièces. */
    val offWeapon: FloatArray? = null,
    /** Les gravats (ou la poussière) de la victime écroulée, un par débris, dans l'ordre des instances de la scène (voir Wizard3DDebris.kt). */
    val debris: List<DebrisPose> = emptyList()
)

internal class Wizard3DCapture private constructor(
    val fx: MoveFx,
    moverMesh: Mesh3D,
    private val weaponMesh: Mesh3D?,
    victimMesh: Mesh3D,
    private val whiteAtBottom: Boolean? = null
) {
    private val mover = Instance3D(moverMesh, WizardModelCatalog.materialFor(fx.mover))
    private val weapon = weaponMesh?.let { Instance3D(it, WizardModelCatalog.materialFor(fx.mover)) }
    private val victim = Instance3D(victimMesh, WizardModelCatalog.materialFor(fx.captured))
    // Le pion tient une épée dans chaque poing (la même courte épée) et cisaille sa victime avec les deux.
    private val offWeapon = if (weaponMesh != null && WizardModelCatalog.offHandMount(fx.mover) != null) {
        Instance3D(weaponMesh, WizardModelCatalog.materialFor(fx.mover))
    } else null

    // Ce qu'il reste de la victime : des gravats, ou de la poussière quand c'est le fou qui frappe (voir Wizard3DDebris.kt).
    private val debris = Wizard3DDebris(
        seed = fx.capturedSquare * 31 + fx.captured.code,
        dust = fx.mover.uppercaseChar() == 'B',
        bodyHeight = victimMesh.maxY * scaleOf(fx.captured),
        crumbleAfter = crumbleAfter(fx.mover),
        base = WizardModelCatalog.materialFor(fx.captured)
    )
    private val debrisInstances = debris.instances()

    private val camera = boardCamera()

    /** La scène à donner à `Scene3DView`, dont `update` doit appeler [update]. */
    val scene = Scene3D().also { s3d ->
        s3d.instances.add(victim)
        s3d.instances.addAll(debrisInstances)
        s3d.instances.add(mover)
        weapon?.let { s3d.instances.add(it) }
        offWeapon?.let { s3d.instances.add(it) }
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
        mover.depthBias = depthBias(pose.mover)
        weapon?.let {
            it.matrix = pose.weapon ?: it.matrix
            it.depthBias = mover.depthBias
        }
        offWeapon?.let {
            it.matrix = pose.offWeapon ?: it.matrix
            it.depthBias = mover.depthBias
        }
        victim.matrix = pose.victim
        victim.depthBias = depthBias(pose.victim)
        victim.visible = pose.victimVisible
        for ((i, d) in pose.debris.withIndex()) {
            val instance = debrisInstances[i]
            instance.matrix = d.matrix
            instance.visible = d.visible
            instance.depthBias = victim.depthBias
        }
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
        val ms = moverState(progress, flipped)
        val m = ms.motion
        val kind = m.kind
        // Le sens de l'attaque (vers la droite si la victime est à droite de la case de départ) ne change pas pendant la prise.
        val attackDx = ms.attackDx
        val moverMatrix = ms.matrix
        val weaponMatrix = ms.weapon
        var hooks = CaptureAnchors()
        val h = ms.hand
        if (weaponMatrix != null && h != null) {
            // Le bout de l'arme : la pointe (origine du fichier) pour les épées et la baguette, le haut pour la crosse et la masse.
            val tipLocalY = if (kind == 'K' || kind == 'R' || kind == 'Q' || kind == 'P') 0f else (weaponMesh?.maxY ?: 0f)
            val tip = transform(weaponMatrix, 0f, tipLocalY, 0f)
            val reach = sqrt((tip[0] - h[0]) * (tip[0] - h[0]) + (tip[1] - h[1]) * (tip[1] - h[1]))
            var scissors: ScissorsHook? = null
            val offMatrix = ms.offWeapon
            val hB = ms.offHand
            val angles = ms.scissors
            if (offMatrix != null && hB != null && angles != null) {
                val tipB = transform(offMatrix, 0f, 0f, 0f)
                val reachB = sqrt((tipB[0] - hB[0]) * (tipB[0] - hB[0]) + (tipB[1] - hB[1]) * (tipB[1] - hB[1]))
                scissors = ScissorsHook(
                    handB = toBoard(hB), swingRadiusB = (reachB * 0.9f).coerceAtLeast(MIN_SWING_RADIUS),
                    windA = angles[0], strikeA = angles[1], headA = angles[2],
                    windB = angles[3], strikeB = angles[4], headB = angles[5]
                )
            }
            hooks = CaptureAnchors(
                queenTip = toBoard(tip),
                hand = toBoard(h),
                swingRadius = (reach * 0.9f).coerceAtLeast(MIN_SWING_RADIUS),
                muzzle = toBoard(tip),
                bishopHead = toBoard(tip),
                scissors = scissors
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
                // Tranchée : elle bascule en s'écartant du coup, puis s'écroule en gravats (le corps s'amenuise pendant que le tas se forme).
                val ease = 1f - (1f - after) * (1f - after)
                roll = m.side * 80f * ease
                shiftX = -m.side * 0.3f * ease
                shrink = 1f - smooth(((after - CRUMBLE_SLICED) / 0.45f).coerceIn(0f, 1f))
            }
            'N' -> {
                // Écrasée : elle s'aplatit sous la masse, puis s'écroule en gravats (et les éclats 2D).
                if (after < CRUSH_END) {
                    val t = smooth(after / CRUSH_END)
                    squashX = 1f + 0.18f * t
                    squashY = 1f - 0.5f * t
                } else {
                    visible = false
                }
            }
            'R' -> {
                // Percée : elle tressaille, puis éclate en gravats (avec les fissures 2D).
                if (after < PIERCE_END) shiftX = sin(after * 140f) * 0.03f else visible = false
            }
            else -> {
                // Foudroyée par le fou : elle rétrécit en s'élevant, et part en poussière.
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

        // Les débris tombent là où la victime se tenait, poussés à l'opposé de l'attaquant.
        val push = when {
            attackDx > cell * 0.15f -> 1f
            attackDx < -cell * 0.15f -> -1f
            else -> 0f
        }
        val debrisPoses = debris.poses(progress, victimX, BOARD_H - victimRow, depth(victimRow), push)

        return Wizard3DPose(moverMatrix, weaponMatrix, victimMatrix, visible, hooks, ms.offWeapon, debrisPoses)
    }

    private class MoverState(
        val motion: CaptureMotion,
        val attackDx: Float,
        val matrix: FloatArray,
        val weapon: FloatArray?,
        val hand: FloatArray?,
        // Le pion : sa seconde épée, son poing, et les angles d'écran (degrés, sens horaire) de ses deux lames.
        val offWeapon: FloatArray? = null,
        val offHand: FloatArray? = null,
        val scissors: FloatArray? = null
    )

    private fun moverMatrixFor(m: CaptureMotion, attackDx: Float, progress: Float): FloatArray {
        val cell = 100f
        val moverX = m.center.x / cell
        val moverRow = m.center.y / cell + FOOT_OFFSET
        val heading = moverYaw(attackDx, progress, cell)
        return Mat4.pose(moverX, BOARD_H - moverRow, depth(moverRow), yaw = heading, scale = scaleOf(fx.mover) * m.bounce)
    }

    // Une arme tenue au poing [hand] (repère du monde) : elle suit la pièce, et pivote autour de la main (dans le plan de l'écran) :
    // un angle d'écran positif tourne dans le sens des aiguilles d'une montre, donc un roulis négatif autour de Z.
    private fun swungWeapon(matrix: FloatArray, local: FloatArray, hand: FloatArray, deg: Float, scale: Float): FloatArray {
        val rest = Mat4.multiply(matrix, local)
        if (deg == 0f && scale == 1f) return rest
        var swing = Mat4.translation(hand[0], hand[1], hand[2])
        swing = Mat4.multiply(swing, Mat4.rotationZ(-deg))
        swing = Mat4.multiply(swing, Mat4.scaling(scale, scale, scale))
        swing = Mat4.multiply(swing, Mat4.translation(-hand[0], -hand[1], -hand[2]))
        return Mat4.multiply(swing, rest)
    }

    // La pièce qui prend et son (ses) arme(s) à l'avancement [progress].
    private fun moverState(progress: Float, flipped: Boolean): MoverState {
        val cell = 100f
        val m = captureMotionFor(fx, progress, cell, flipped)
        val attackDx = m.targetCenter.x - (wizardSquareOrigin(fx.from, flipped, cell).x + cell / 2f)
        val matrix = moverMatrixFor(m, attackDx, progress)
        val local = WizardModelCatalog.weaponLocal(m.kind)
        val hand = WizardModelCatalog.handPoint(m.kind)
        if (local == null || hand == null) return MoverState(m, attackDx, matrix, null, null)
        val h = transform(matrix, hand[0], hand[1], hand[2])

        val offLocal = WizardModelCatalog.offHandLocal(m.kind)
        val offPoint = WizardModelCatalog.offHandPoint(m.kind)
        if (offLocal == null || offPoint == null) {
            return MoverState(m, attackDx, matrix, swungWeapon(matrix, local, h, m.weaponDeg, m.weaponScale), h)
        }
        // Le pion : une épée dans chaque poing. Au repos, les lames montent côte à côte ; pour frapper, elles s'ouvrent (levées de
        // part et d'autre) puis se referment en ciseaux, chacune visant le même point devant la victime : les lames se croisent.
        val hB = transform(matrix, offPoint[0], offPoint[1], offPoint[2])
        val strikes = scissorStrikes(flipped)
        val rest = m.restAngle
        val (angleA, scaleA) = swingPose(progress, rest, strikes[0] - SCISSOR_OPEN_DEG, strikes[0], SCISSOR_WIND_SCALE, SCISSOR_STRIKE_SCALE)
        val (angleB, scaleB) = swingPose(progress, rest, strikes[1] + SCISSOR_OPEN_DEG, strikes[1], SCISSOR_WIND_SCALE, SCISSOR_STRIKE_SCALE)
        return MoverState(
            m, attackDx, matrix,
            swungWeapon(matrix, local, h, angleA - rest, scaleA), h,
            swungWeapon(matrix, offLocal, hB, angleB - rest, scaleB), hB,
            floatArrayOf(strikes[0] - SCISSOR_OPEN_DEG, strikes[0], angleA, strikes[1] + SCISSOR_OPEN_DEG, strikes[1], angleB)
        )
    }

    // Les angles d'écran (degrés, sens horaire, 0 = vers la droite) des deux lames du pion à l'impact : chaque lame vise, depuis son
    // poing, le même point, entre le pion et le corps de sa victime. Elles s'y croisent, et leurs pointes dépassent vers la victime.
    private fun scissorStrikes(flipped: Boolean): FloatArray {
        val cell = 100f
        val m = captureMotionFor(fx, IMPACT, cell, flipped)
        val attackDx = m.targetCenter.x - (wizardSquareOrigin(fx.from, flipped, cell).x + cell / 2f)
        val matrix = moverMatrixFor(m, attackDx, IMPACT)
        val a = WizardModelCatalog.handPoint(m.kind)!!
        val b = WizardModelCatalog.offHandPoint(m.kind)!!
        val hA = transform(matrix, a[0], a[1], a[2])
        val hB = transform(matrix, b[0], b[1], b[2])
        val targetX = m.targetCenter.x / cell
        val targetY = BOARD_H - (m.targetCenter.y / cell + FOOT_OFFSET) + SCISSOR_AIM_HEIGHT
        val midX = (hA[0] + hB[0]) / 2f
        val midY = (hA[1] + hB[1]) / 2f
        val aimX = midX + (targetX - midX) * SCISSOR_AIM_FRACTION
        val aimY = midY + (targetY - midY) * SCISSOR_AIM_FRACTION
        fun angleFrom(h: FloatArray): Float = Math.toDegrees(atan2((h[1] - aimY).toDouble(), (aimX - h[0]).toDouble())).toFloat()
        return floatArrayOf(angleFrom(hA), angleFrom(hB))
    }

    // Le lacet de la pièce qui prend : son regard habituel (les blanches regardent à gauche, les noires à droite, pour celles dessinées
    // de profil), puis elle se tourne vers sa victime pour frapper et reprend son regard habituel en avançant.
    private fun moverYaw(attackDx: Float, progress: Float, cell: Float): Float {
        val profile = fx.mover.uppercaseChar() in PROFILE
        val own = if (Chess.isWhite(fx.mover)) -1f else 1f
        val sideways = abs(attackDx) > cell * 0.15f
        val sign = if (sideways) (if (attackDx > 0f) 1f else -1f) else own
        val defaultYaw = restYaw(fx.mover, whiteAtBottom)
        val attackYaw = when {
            profile -> awayMirror(fx.mover, sign * PROFILE_YAW)
            sideways -> awayMirror(fx.mover, sign * FRONT_YAW)
            else -> defaultYaw
        }
        val turn = smooth((progress / ATTACK_START).coerceIn(0f, 1f)) *
            (1f - smooth(((progress - FOLLOW_END) / (STEP_END - FOLLOW_END)).coerceIn(0f, 1f)))
        return defaultYaw + (attackYaw - defaultYaw) * turn + facingOffset(fx.mover)
    }

    // Une pièce du bas de l'écran tourne le dos à l'écran : son lacet est le miroir (180° − lacet) de celui d'une pièce du haut.
    private fun awayMirror(piece: Char, yaw: Float): Float =
        if (whiteAtBottom != null && Chess.isWhite(piece) == whiteAtBottom) 180f - yaw else yaw

    // La victime, elle, se tourne vers celui qui l'attaque (si le coup vient de côté).
    private fun victimYaw(attackDx: Float, progress: Float, cell: Float): Float {
        val profile = fx.captured.uppercaseChar() in PROFILE
        val sideways = abs(attackDx) > cell * 0.15f
        val toward = if (attackDx > 0f) -1f else 1f
        val defaultYaw = restYaw(fx.captured, whiteAtBottom)
        val attackYaw = when {
            !sideways -> defaultYaw
            profile -> awayMirror(fx.captured, toward * PROFILE_YAW)
            else -> awayMirror(fx.captured, toward * FRONT_YAW)
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

        private fun crumbleAfter(mover: Char): Float = when (mover.uppercaseChar()) {
            'N' -> CRUSH_END - 0.03f
            'R' -> PIERCE_END
            'B' -> 0.05f
            else -> CRUMBLE_SLICED
        }

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
        fun restMatrix(piece: Char, centerX: Float, centerY: Float, bounce: Float = 1f, whiteAtBottom: Boolean? = null): FloatArray {
            val footRow = centerY + FOOT_OFFSET
            val yaw = restYaw(piece, whiteAtBottom) + WizardModelCatalog.facingYaw(piece)
            return Mat4.pose(centerX, BOARD_H - footRow, depth(footRow), yaw = yaw, scale = scaleOf(piece) * bounce)
        }

        /** Le champ (en degrés) pour voir [height] cases de haut à la distance de la caméra. */
        private fun fovFor(height: Float): Float =
            (2.0 * atan((height / 2f) / CAMERA_DISTANCE.toDouble()) * 180.0 / PI).toFloat()

        private const val DEPTH_PER_ROW = 0.12f
        private const val PROFILE_YAW = 65f

        // Les ciseaux du pion : les lames s'ouvrent de 75° de part et d'autre de leur angle de frappe, grandissent un peu, puis se
        // referment sur un point situé aux 45 % du chemin vers la victime, à mi-hauteur de son corps (en cases).
        private const val SCISSOR_OPEN_DEG = 75f
        private const val SCISSOR_WIND_SCALE = 1.35f
        private const val SCISSOR_STRIKE_SCALE = 1.9f
        private const val SCISSOR_AIM_FRACTION = 0.45f
        private const val SCISSOR_AIM_HEIGHT = 0.5f
        private const val FRONT_YAW = 40f
        // Le moment où la statue s'écroule en débris (part de la fin du coup) : tranchée, un peu après avoir basculé ; écrasée et percée,
        // dès que le coup a fini son œuvre ; foudroyée par le fou, presque tout de suite (le nuage de poussière se forme pendant qu'elle se dissout).
        private const val CRUMBLE_SLICED = 0.3f
        private const val CRUSH_END = 0.2f
        private const val PIERCE_END = 0.14f
        private const val DISSOLVE_END = 0.3f
        private const val MIN_SWING_RADIUS = 0.3f

        // Les pièces de profil dans leur dessin 2D (le pion, lui, regarde droit vers l'ennemi).
        private val PROFILE = setOf('N', 'R')

        /**
         * Le lacet de repos d'une pièce, avant [WizardModelCatalog.facingYaw]. Les pièces du bas de l'écran
         * ([whiteAtBottom] : les blancs sont en bas, sinon ce sont les noirs ; null : on ne s'en soucie pas) tournent le dos à l'écran,
         * face à l'ennemi, et celles du haut font face à l'écran. Ce sens ne change donc que lorsqu'on retourne l'échiquier. Les pièces de profil gardent leur
         * inclinaison (65°) du même côté de l'écran, vues de face ou de dos.
         */
        fun restYaw(piece: Char, whiteAtBottom: Boolean? = null): Float {
            val profile = piece.uppercaseChar() in PROFILE
            val own = if (Chess.isWhite(piece)) -1f else 1f
            val front = if (profile) own * PROFILE_YAW else 0f
            val away = whiteAtBottom != null && Chess.isWhite(piece) == whiteAtBottom
            return if (!away) front else 180f - front
        }

        /** Plus une pièce est basse à l'écran, plus elle est près de la caméra : elle passe devant celles du dessus. */
        private fun depth(row: Float): Float = (row - BOARD_H / 2f) * DEPTH_PER_ROW

        /**
         * Le rapprochement de la caméra (pour le seul tampon de profondeur, voir `Instance3D.depthBias`) d'une pièce posée par
         * [restMatrix] : il grandit avec la profondeur de la pièce, à raison de 1,7 case par rangée, plus que l'épaisseur d'une
         * pièce. Une pièce d'une rangée plus basse passe ainsi entièrement devant celle de la rangée du dessus, tête comprise
         * (avec la seule profondeur du modèle, 0,14 par rangée, sa tête passait derrière le pied de la pièce de devant).
         */
        fun depthBias(matrix: FloatArray): Float = matrix[14] * (DEPTH_BIAS_PER_ROW / DEPTH_PER_ROW / WIZARD_ROW_PITCH)

        private const val DEPTH_BIAS_PER_ROW = 1.7f

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
        fun create(fx: MoveFx, whiteAtBottom: Boolean? = null, meshOf: (String) -> Mesh3D?): Wizard3DCapture? {
            if (!fx.isCapture) return null
            val moverMesh = WizardModelCatalog.pieceModel(fx.mover)?.let(meshOf) ?: return null
            val victimMesh = WizardModelCatalog.pieceModel(fx.captured)?.let(meshOf) ?: return null
            val weaponModel = WizardModelCatalog.weaponModel(fx.mover)
            val weaponMesh = weaponModel?.let(meshOf)
            if (weaponModel != null && weaponMesh == null) return null
            return Wizard3DCapture(fx, moverMesh, weaponMesh, victimMesh, whiteAtBottom)
        }
    }
}
