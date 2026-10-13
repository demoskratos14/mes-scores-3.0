package com.aventure.messcores

/**
 * Mécanique de saisie et de calcul que l'on peut donner à un jeu personnalisé. Chacune reprend
 * une mécanique déjà utilisée par un jeu prédéfini (voir [GameRepository.builtInGames]).
 */
enum class GameVariant {
    /** Aucune mécanique particulière : manches libres (TABLE) ou équipes choisies à la main. */
    NONE,
    /** Feuille de catégories : Yams, Qwixx, Bowling, ou catégories écrites par l'utilisateur (mode TABLE). */
    SHEET,
    /** Annonce et plis réalisés : Wizard, Oh Hell (mode TABLE). */
    BID,
    /** Score à atteindre pile, avec repli si on le dépasse : Mölkky (mode TABLE). */
    EXACT_TARGET,
    /** Décompte depuis un score de départ jusqu'à 0 pile : fléchettes (mode TABLE). */
    COUNTDOWN,
    /** Calcul automatique d'une manche de Tarot (mode VARIABLE_TEAMS, 3 à 5 joueurs). */
    TAROT
}

/** Mécaniques proposées pour un mode de score donné (NONE est toujours la première). */
fun variantsFor(mode: ScoreMode): List<GameVariant> = when (mode) {
    ScoreMode.TABLE -> listOf(GameVariant.NONE, GameVariant.SHEET, GameVariant.BID, GameVariant.EXACT_TARGET, GameVariant.COUNTDOWN)
    ScoreMode.VARIABLE_TEAMS -> listOf(GameVariant.NONE, GameVariant.TAROT)
    ScoreMode.COUNTER -> listOf(GameVariant.NONE)
}

/** Mécanique d'un jeu existant (pour préremplir le formulaire de modification). */
fun GameRules.mechanic(): GameVariant = when {
    tarotScoring -> GameVariant.TAROT
    sheet != null -> GameVariant.SHEET
    bidScoring != null -> GameVariant.BID
    exactTarget != null -> GameVariant.EXACT_TARGET
    countdownFrom != null -> GameVariant.COUNTDOWN
    else -> GameVariant.NONE
}

/**
 * Contenu du formulaire de création d'un jeu, déjà lu (textes convertis en nombres, ou null si vides).
 * [toRules] en tire des règles cohérentes : une valeur absente ou impossible fait retomber sur la
 * mécanique « classique », et les réglages qu'une mécanique impose (par exemple « le plus bas gagne »
 * pour un décompte) écrasent ceux du formulaire, comme pour les jeux prédéfinis.
 */
data class CustomGameDraft(
    val name: String,
    val scoreMode: ScoreMode,
    val variant: GameVariant = GameVariant.NONE,
    val lowestWins: Boolean = false,
    val allowNegative: Boolean = false,
    /** Règles de score ajoutées par l'utilisateur, sans la règle « Normal ». */
    val customMultipliers: List<ScoreMultiplier> = emptyList(),
    val minPlayers: Int? = null,
    val maxPlayers: Int? = null,
    /** Identifiant de feuille ([ScoreSheets.YAMS]…, ou [ScoreSheets.CUSTOM]), pour [GameVariant.SHEET]. */
    val sheet: String = ScoreSheets.YAMS,
    /** Catégories de la feuille quand [sheet] vaut [ScoreSheets.CUSTOM] ; ignorées sinon. */
    val sheetRows: List<CustomSheetRow> = emptyList(),
    /** Type d'annonce ([BidScoring.WIZARD]…), pour [GameVariant.BID]. */
    val bidScoring: String = BidScoring.WIZARD,
    val exactTarget: Int? = null,
    val exactReset: Int? = null,
    val countdownFrom: Int? = null,
    /** Nombre de phases à suivre (Phase 10) ; null si le jeu n'en a pas. */
    val phases: Int? = null,
    val endCondition: EndCondition = EndCondition(),
    /** Les quatre contrats du Tarot (Petite, Garde, Garde sans, Garde contre), déjà traduits. */
    val tarotContracts: List<ScoreMultiplier> = emptyList()
) {
    /** Mécanique réellement appliquée : celle choisie si elle convient au mode et qu'elle est complète. */
    fun effectiveVariant(): GameVariant = when (scoreMode) {
        ScoreMode.TABLE -> when (variant) {
            GameVariant.SHEET -> if (sheet == ScoreSheets.CUSTOM && sheetRows.isEmpty()) GameVariant.NONE else variant
            GameVariant.BID -> variant
            GameVariant.EXACT_TARGET -> if ((exactTarget ?: 0) > 0) variant else GameVariant.NONE
            GameVariant.COUNTDOWN -> if ((countdownFrom ?: 0) > 0) variant else GameVariant.NONE
            else -> GameVariant.NONE
        }
        ScoreMode.VARIABLE_TEAMS -> if (variant == GameVariant.TAROT && tarotContracts.isNotEmpty()) variant else GameVariant.NONE
        ScoreMode.COUNTER -> GameVariant.NONE
    }

    /** Vrai si le suivi des phases est possible (seulement pour un tableau de manches libres). */
    fun phasesApply(): Boolean = scoreMode == ScoreMode.TABLE && effectiveVariant() == GameVariant.NONE

    fun toRules(id: String, fallbackName: String): GameRules {
        val variant = effectiveVariant()
        val phaseCount = phases?.takeIf { it > 0 && phasesApply() }

        // Les coefficients n'ont de sens que là où l'on saisit un score libre (ou un nombre de points).
        val multipliers = when (variant) {
            GameVariant.SHEET, GameVariant.BID -> listOf(GameRules.NORMAL_MULTIPLIER)
            GameVariant.TAROT -> tarotContracts
            else -> {
                val extra = if (scoreMode == ScoreMode.COUNTER) emptyList() else customMultipliers
                listOf(GameRules.NORMAL_MULTIPLIER) + extra
            }
        }

        // Bornes du nombre de joueurs : au moins 1 ; le Tarot se joue de 3 à 5.
        val floor = if (variant == GameVariant.TAROT) 3 else 1
        val ceiling = if (variant == GameVariant.TAROT) 5 else Int.MAX_VALUE
        val min = (minPlayers ?: GameRules.DEFAULT_MIN_PLAYERS).coerceIn(floor, ceiling)
        val defaultMax = if (variant == GameVariant.TAROT) 5 else GameRules.DEFAULT_MAX_PLAYERS
        val max = (maxPlayers ?: defaultMax).coerceIn(min, ceiling)

        // La fin de partie est dictée par la mécanique pour ces jeux (voir ScoreViewModel.isGameOver).
        val end = when {
            variant == GameVariant.EXACT_TARGET -> EndCondition(
                type = EndConditionType.SCORE_THRESHOLD,
                scoreThreshold = exactTarget,
                stopImmediately = true
            )
            variant == GameVariant.SHEET || variant == GameVariant.COUNTDOWN || phaseCount != null -> EndCondition()
            else -> endCondition
        }

        return GameRules(
            id = id,
            name = name.ifBlank { fallbackName },
            lowestWins = when (variant) {
                GameVariant.COUNTDOWN -> true
                GameVariant.EXACT_TARGET, GameVariant.TAROT -> false
                else -> lowestWins
            },
            allowNegativeScores = when (variant) {
                // Une feuille personnalisée peut avoir des scores négatifs (ex : −1 par catégorie manquante).
                GameVariant.SHEET -> sheet == ScoreSheets.CUSTOM && allowNegative
                GameVariant.COUNTDOWN, GameVariant.EXACT_TARGET -> false
                GameVariant.TAROT, GameVariant.BID -> true
                else -> allowNegative
            },
            multipliers = multipliers,
            scoreMode = scoreMode,
            endCondition = end,
            minPlayers = min,
            maxPlayers = max,
            sheet = if (variant == GameVariant.SHEET) sheet else null,
            sheetRows = if (variant == GameVariant.SHEET && sheet == ScoreSheets.CUSTOM) sheetRows else emptyList(),
            bidScoring = if (variant == GameVariant.BID) bidScoring else null,
            phases = phaseCount,
            exactTarget = if (variant == GameVariant.EXACT_TARGET) exactTarget else null,
            exactReset = if (variant == GameVariant.EXACT_TARGET) (exactReset ?: 0) else null,
            countdownFrom = if (variant == GameVariant.COUNTDOWN) countdownFrom else null,
            tarotScoring = variant == GameVariant.TAROT
        )
    }
}
