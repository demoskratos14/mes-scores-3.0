package com.aventure.messcores

import androidx.annotation.StringRes

/**
 * Une ligne d'une feuille de catégories (ex : « Full » au Yams).
 *
 * @param options scores proposés en boutons dans la fenêtre de saisie (0 sert à « barrer » la case) ;
 *   null si le score est libre (somme des dés), saisi au clavier.
 * @param upper vrai pour la partie haute, dont le total donne droit au bonus.
 * @param hint texte d'aide affiché sous les boutons de saisie (ex : table croix → points), ou null.
 * @param zeroCrossesOut vrai si le bouton 0 s'affiche « Barrer » (Yams), faux s'il s'affiche « 0 ».
 */
data class SheetRow(
    val id: String,
    @StringRes val label: Int,
    val options: List<Int>?,
    val upper: Boolean = false,
    @StringRes val hint: Int? = null,
    val zeroCrossesOut: Boolean = true
)

/**
 * Feuilles de catégories : jeux où chaque joueur remplit chaque ligne une seule fois. Elles
 * s'appuient sur le mode TABLE : une « manche » du tableau est ici une catégorie (voir [GameRules.sheet]).
 */
object ScoreSheets {
    const val YAMS = "yams"
    const val QWIXX = "qwixx"
    const val BOWLING = "bowling"

    /** Total de la partie haute à atteindre pour avoir le bonus. */
    const val UPPER_BONUS_THRESHOLD = 63
    const val UPPER_BONUS = 35

    private val yamsRows: List<SheetRow> = listOf(
        SheetRow("aces", R.string.sheet_yams_aces, (0..5).map { it * 1 }, upper = true),
        SheetRow("twos", R.string.sheet_yams_twos, (0..5).map { it * 2 }, upper = true),
        SheetRow("threes", R.string.sheet_yams_threes, (0..5).map { it * 3 }, upper = true),
        SheetRow("fours", R.string.sheet_yams_fours, (0..5).map { it * 4 }, upper = true),
        SheetRow("fives", R.string.sheet_yams_fives, (0..5).map { it * 5 }, upper = true),
        SheetRow("sixes", R.string.sheet_yams_sixes, (0..5).map { it * 6 }, upper = true),
        SheetRow("three_kind", R.string.sheet_yams_three_kind, null),
        SheetRow("four_kind", R.string.sheet_yams_four_kind, null),
        SheetRow("full_house", R.string.sheet_yams_full_house, listOf(0, 25)),
        SheetRow("small_straight", R.string.sheet_yams_small_straight, listOf(0, 30)),
        SheetRow("large_straight", R.string.sheet_yams_large_straight, listOf(0, 40)),
        SheetRow("yams", R.string.sheet_yams_yams, listOf(0, 50)),
        SheetRow("chance", R.string.sheet_yams_chance, null)
    )

    // Qwixx : points d'une couleur = 1 + 2 + … + n pour n croix (le cadenas compte comme une croix), jusqu'à 12.
    private val qwixxPoints = (0..12).map { it * (it + 1) / 2 }
    private val qwixxRows: List<SheetRow> = listOf(
        SheetRow("red", R.string.sheet_qwixx_red, qwixxPoints, hint = R.string.sheet_qwixx_hint, zeroCrossesOut = false),
        SheetRow("yellow", R.string.sheet_qwixx_yellow, qwixxPoints, hint = R.string.sheet_qwixx_hint, zeroCrossesOut = false),
        SheetRow("green", R.string.sheet_qwixx_green, qwixxPoints, hint = R.string.sheet_qwixx_hint, zeroCrossesOut = false),
        SheetRow("blue", R.string.sheet_qwixx_blue, qwixxPoints, hint = R.string.sheet_qwixx_hint, zeroCrossesOut = false),
        SheetRow(
            "penalties", R.string.sheet_qwixx_penalties, listOf(0, -5, -10, -15, -20),
            hint = R.string.sheet_qwixx_penalty_hint, zeroCrossesOut = false
        )
    )

    // Bowling : une ligne par frame. Le contenu d'une case est géré par BowlingScoring.
    private val bowlingRows: List<SheetRow> = listOf(
        R.string.sheet_bowling_1, R.string.sheet_bowling_2, R.string.sheet_bowling_3, R.string.sheet_bowling_4,
        R.string.sheet_bowling_5, R.string.sheet_bowling_6, R.string.sheet_bowling_7, R.string.sheet_bowling_8,
        R.string.sheet_bowling_9, R.string.sheet_bowling_10
    ).mapIndexed { i, label -> SheetRow("frame_${i + 1}", label, null) }

    /** Lignes de la feuille [sheetId], ou null si ce n'est pas une feuille de catégories. */
    fun rows(sheetId: String?): List<SheetRow>? = when (sheetId) {
        YAMS -> yamsRows
        QWIXX -> qwixxRows
        BOWLING -> bowlingRows
        else -> null
    }

    /** Vrai si la feuille a une partie haute donnant un bonus (Yams, pas Qwixx). */
    fun hasBonus(sheetId: String?): Boolean = rows(sheetId)?.any { it.upper } == true

    /** Total de la partie haute d'un joueur ; [rowValues] donne le score de chaque ligne (null = vide). */
    fun upperTotal(sheetId: String?, rowValues: List<Int?>): Int {
        val rows = rows(sheetId) ?: return 0
        return rows.indices.sumOf { i -> if (rows[i].upper) rowValues.getOrNull(i) ?: 0 else 0 }
    }

    /** Bonus de la partie haute (35 points dès 63), ou 0. */
    fun bonus(sheetId: String?, rowValues: List<Int?>): Int =
        if (hasBonus(sheetId) && upperTotal(sheetId, rowValues) >= UPPER_BONUS_THRESHOLD) UPPER_BONUS else 0
}
