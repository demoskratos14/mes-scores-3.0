package com.aventure.messcores

/*
 * Catalogue des modèles 3D du style « sorcier » (app/src/main/assets/wizard3d/, voir LISEZMOI.txt pour la source et la
 * licence) : quel fichier pour quelle pièce, de quel côté elle regarde, et les deux matières (marbre, obsidienne).
 * Pur Kotlin, donc testable sans Android ; le chargement des fichiers est dans Scene3DView.kt ([WizardModels]).
 */
/**
 * Comment une arme se tient dans la main de sa pièce. Tout est dans le repère du fichier .glb de la pièce (Y vers le haut,
 * le mètre pour unité, avant le [WizardModelCatalog.facingYaw]) :
 *  - [handX], [handY], [handZ] : le centre de la main (du poing, ou des mains jointes) qui tient l'arme ;
 *  - [gripX], [gripY], [gripZ] : le point de l'arme que la main serre, dans le repère de l'arme (l'origine est son bas) ;
 *  - [yaw], [pitch], [roll] : l'orientation de l'arme au repos (degrés, même ordre que [Mat4.pose] : lacet, tangage, roulis).
 * Le point de prise tombe donc exactement sur la main, quelle que soit l'orientation.
 */
internal class WeaponMount(
    val handX: Float, val handY: Float, val handZ: Float,
    val gripX: Float, val gripY: Float, val gripZ: Float,
    val yaw: Float = 0f, val pitch: Float = 0f, val roll: Float = 0f
)

internal object WizardModelCatalog {

    private val PIECES = mapOf(
        'K' to "king", 'Q' to "queen", 'R' to "rook", 'B' to "bishop", 'N' to "knight", 'P' to "pawn"
    )

    // Le pion manie, comme la tour, la courte épée « weapon_rook_sword » : un seul fichier pour les deux.
    private val WEAPONS = mapOf(
        'K' to "weapon_king_sword", 'Q' to "weapon_queen_wand", 'R' to "weapon_rook_sword",
        'B' to "weapon_bishop_staff", 'N' to "weapon_knight_mace", 'P' to "weapon_rook_sword"
    )

    /** Tous les fichiers livrés (sans l'extension .glb) : les six pièces puis les cinq armes (le pion et la tour partagent la leur). */
    val allModels: List<String> = (PIECES.values + WEAPONS.values).distinct()

    /** Le modèle de la pièce [piece] (lettre de l'échiquier, majuscule ou minuscule), ou null si ce n'est pas une pièce. */
    fun pieceModel(piece: Char): String? = PIECES[piece.uppercaseChar()]

    /** L'arme de la pièce [piece], ou null si ce n'est pas une pièce. */
    fun weaponModel(piece: Char): String? = WEAPONS[piece.uppercaseChar()]

    /**
     * Où chaque arme se tient (mesuré sur les modèles livrés). Les armes sont des fichiers à part dont l'origine est le bas :
     * la pointe pour les épées (la poignée est en haut), le bout du manche pour la masse, le pied pour la baguette et la crosse.
     *  - roi : l'épée plantée devant lui, pointe en bas, les deux mains sous le pommeau ;
     *  - reine : la baguette pendant droit devant elle, sans inclinaison : le pommeau (le haut du fichier) est serré dans ses mains
     *    jointes sans les dépasser, la pointe (le pied du fichier) descend vers le socle, comme l'épée du roi ;
     *  - tour : la courte épée au poing, tendue devant elle (le fichier de la tour regarde vers −Z) ;
     *  - pion : la courte épée de la tour, tenue pointe en l'air dans ses mains croisées, devant lui (il est de profil, agenouillé) ;
     *  - fou : la crosse dressée dans son poing droit (côté +X), le pied à hauteur du socle ;
     *  - cavalier : la masse brandie dans le poing du cavalier (côté −X), la tête levée au-dessus de lui.
     */
    private val MOUNTS = mapOf(
        'K' to WeaponMount(0f, 0.0678f, 0.0090f, -0.00004f, 0.0505f, 0.00164f),
        'Q' to WeaponMount(0f, 0.0664f, 0.0120f, 0f, 0.0520f, 0f),
        'R' to WeaponMount(-0.0093f, 0.0606f, -0.0126f, -0.00006f, 0.0186f, 0.0007f, pitch = 110f),
        'B' to WeaponMount(0.0113f, 0.0629f, 0.0050f, 0.00307f, 0.0469f, 0.00003f),
        // Pion : la main est à 0,8 mm du modèle ; tangage 180° : la lame (de la poignée vers l'origine du fichier) monte droit, dans le plan de l'écran.
        'P' to WeaponMount(0f, 0.0175f, -0.0120f, -0.00006f, 0.0186f, 0.0007f, pitch = 180f),
        'N' to WeaponMount(
            -0.0010f, 0.0600f, 0.0070f, 0.00545f, 0.00222f, -0.00217f,
            yaw = -3.2f, pitch = -9.4f, roll = -29.4f
        )
    )

    /** Le montage de l'arme de la pièce [piece], ou null si ce n'est pas une pièce. */
    fun weaponMount(piece: Char): WeaponMount? = MOUNTS[piece.uppercaseChar()]

    /** Le point de la main qui tient l'arme, dans le repère du fichier de la pièce (x, y, z), ou null si ce n'est pas une pièce. */
    fun handPoint(piece: Char): FloatArray? = weaponMount(piece)?.let { floatArrayOf(it.handX, it.handY, it.handZ) }

    /**
     * La matrice qui place l'arme de [piece] dans la main de la pièce, dans le repère de la pièce (ou null si ce n'est pas une pièce).
     * Pour dessiner l'arme avec la pièce : `weapon.matrix = Mat4.multiply(pièce.matrix, WizardModelCatalog.weaponLocal(p)!!)`
     * (l'arme suit alors la pièce quand elle avance, tourne ou grandit).
     *
     * [swingYaw], [swingPitch], [swingRoll] (degrés, dans le repère de la pièce) font pivoter l'arme autour de la main :
     * c'est ce qui lève puis abat l'arme pendant l'animation de la prise. La main ne bouge pas, l'arme reste donc tenue.
     */
    fun weaponLocal(piece: Char, swingYaw: Float = 0f, swingPitch: Float = 0f, swingRoll: Float = 0f): FloatArray? {
        val m = weaponMount(piece) ?: return null
        var out = Mat4.translation(m.handX, m.handY, m.handZ)
        out = Mat4.multiply(out, Mat4.rotationY(swingYaw))
        out = Mat4.multiply(out, Mat4.rotationX(swingPitch))
        out = Mat4.multiply(out, Mat4.rotationZ(swingRoll))
        out = Mat4.multiply(out, Mat4.rotationY(m.yaw))
        out = Mat4.multiply(out, Mat4.rotationX(m.pitch))
        out = Mat4.multiply(out, Mat4.rotationZ(m.roll))
        return Mat4.multiply(out, Mat4.translation(-m.gripX, -m.gripY, -m.gripZ))
    }

    /** Chemin du fichier dans les assets de l'appli. */
    fun assetPath(model: String): String = "wizard3d/$model.glb"

    /**
     * Le lacet (en degrés, autour de Y) à ajouter pour que la pièce regarde vers +Z, comme le roi, la reine et le fou le
     * font déjà dans leur fichier. Le cavalier est dessiné de profil, tourné vers +X (−90°) ; la tour et le pion
     * regardent vers −Z dans leur fichier (bras tendu et visage de ce côté, bouclier dans le dos) : 180°. Pour viser une
     * direction, on ajoute ce décalage au lacet voulu : `Mat4.pose(…, yaw = cap + facingYaw(pièce))`. Le montage des
     * armes ([weaponLocal]) est dans le repère du fichier : il ne dépend pas de ce décalage.
     */
    fun facingYaw(piece: Char): Float = when (piece.uppercaseChar()) {
        'N' -> -90f
        'R', 'P' -> 180f
        else -> 0f
    }

    /** Marbre blanc, un peu brillant. */
    val MARBLE = Material3D(0.93f, 0.92f, 0.88f, specular = 0.35f, shininess = 48f)

    /** Obsidienne : sombre et brillante, mais assez claire (gris bleuté) pour qu'on voie les détails sculptés. */
    val OBSIDIAN = Material3D(0.27f, 0.28f, 0.36f, specular = 0.85f, shininess = 70f)

    /** La matière du camp de [piece] (majuscule = blanc, comme dans [Chess]) : marbre pour les blancs, obsidienne pour les noirs. */
    fun materialFor(piece: Char): Material3D = if (Chess.isWhite(piece)) MARBLE else OBSIDIAN
}
