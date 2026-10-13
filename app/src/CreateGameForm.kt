package com.aventure.messcores

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable

/** Brouillon d'une règle en cours de saisie dans le formulaire (multiplicateur + bonus). */
internal data class RuleDraft(val label: String, val factorText: String, val bonusText: String)

/** Sauvegarde des brouillons de règles (3 textes par règle, à plat) pour survivre à une rotation. */
private val RuleDraftsSaver: Saver<List<RuleDraft>, Any> = listSaver(
    save = { drafts -> drafts.flatMap { listOf(it.label, it.factorText, it.bonusText) } },
    restore = { flat -> flat.chunked(3).map { RuleDraft(it[0], it[1], it[2]) } }
)

/**
 * État du formulaire de création / modification d'un jeu. Chaque champ est un [MutableState]
 * mémorisé avec rememberSaveable (voir [rememberCreateGameForm]) : les sections du formulaire
 * lisent et modifient leurs champs directement, sans avoir à se les passer un par un.
 */
internal class CreateGameForm(
    val name: MutableState<String>,
    val scoreMode: MutableState<ScoreMode>,
    val lowestWins: MutableState<Boolean>,
    val allowNegative: MutableState<Boolean>,
    val ruleDrafts: MutableState<List<RuleDraft>>,
    val endConditionType: MutableState<EndConditionType>,
    val endValueText: MutableState<String>,
    val thresholdDirection: MutableState<ThresholdDirection>,
    val stopImmediately: MutableState<Boolean>,
    val tieBreakOnEqualLeaders: MutableState<Boolean>,
    /** Écart minimum exigé pour finir (2 au ping-pong) ; vide = aucun. */
    val minLeadText: MutableState<String>,
    /** Score plafond (30 au badminton) ; vide = aucun. */
    val maxScoreText: MutableState<String>,
    /** Nombre de joueurs : vide = valeur par défaut (voir GameRules.DEFAULT_MIN_PLAYERS / DEFAULT_MAX_PLAYERS). */
    val minPlayersText: MutableState<String>,
    val maxPlayersText: MutableState<String>,
    /** Mécanique reprise d'un jeu prédéfini (feuille, annonces, score exact, décompte, Tarot, phases). */
    val variant: MutableState<GameVariant>,
    val sheetId: MutableState<String>,
    /** Catégories d'une feuille personnalisée : une par ligne, « Nom » ou « Nom : 0, 3, 5 ». */
    val sheetRowsText: MutableState<String>,
    val bidKind: MutableState<String>,
    val exactTargetText: MutableState<String>,
    val exactResetText: MutableState<String>,
    val countdownText: MutableState<String>,
    val phasesOn: MutableState<Boolean>,
    val phasesText: MutableState<String>
)

/**
 * Crée l'état du formulaire. rememberSaveable : le formulaire survit à une rotation de l'écran.
 * En mode édition ([editing] non nul), les champs démarrent avec les valeurs du jeu modifié.
 */
@Composable
internal fun rememberCreateGameForm(editing: GameRules?): CreateGameForm {
    val endCondition = editing?.endCondition
    return CreateGameForm(
        name = rememberSaveable { mutableStateOf(editing?.name ?: "") },
        scoreMode = rememberSaveable { mutableStateOf(editing?.scoreMode ?: ScoreMode.TABLE) },
        lowestWins = rememberSaveable { mutableStateOf(editing?.lowestWins ?: false) },
        allowNegative = rememberSaveable { mutableStateOf(editing?.allowNegativeScores ?: false) },
        ruleDrafts = rememberSaveable(stateSaver = RuleDraftsSaver) {
            mutableStateOf(
                editing?.multipliers.orEmpty()
                    .filter { it.id != GameRules.NORMAL_MULTIPLIER_ID }
                    .map { RuleDraft(it.label, it.factor.toString(), if (it.bonus == 0) "" else it.bonus.toString()) }
            )
        },
        endConditionType = rememberSaveable { mutableStateOf(endCondition?.type ?: EndConditionType.NONE) },
        endValueText = rememberSaveable {
            mutableStateOf(
                when (endCondition?.type) {
                    EndConditionType.ROUND_COUNT -> endCondition?.roundCount?.toString()
                    EndConditionType.SCORE_THRESHOLD -> endCondition?.scoreThreshold?.toString()
                    else -> null
                } ?: ""
            )
        },
        thresholdDirection = rememberSaveable {
            mutableStateOf(endCondition?.thresholdDirection ?: ThresholdDirection.ABOVE)
        },
        stopImmediately = rememberSaveable { mutableStateOf(endCondition?.stopImmediately ?: true) },
        tieBreakOnEqualLeaders = rememberSaveable { mutableStateOf(endCondition?.tieBreakOnEqualLeaders ?: false) },
        minLeadText = rememberSaveable {
            mutableStateOf(endCondition?.minLead?.takeIf { it > 0 }?.toString() ?: "")
        },
        maxScoreText = rememberSaveable { mutableStateOf(endCondition?.maxScore?.toString() ?: "") },
        minPlayersText = rememberSaveable {
            mutableStateOf(editing?.minPlayers?.takeIf { it != GameRules.DEFAULT_MIN_PLAYERS }?.toString() ?: "")
        },
        maxPlayersText = rememberSaveable {
            mutableStateOf(editing?.maxPlayers?.takeIf { it != GameRules.DEFAULT_MAX_PLAYERS }?.toString() ?: "")
        },
        variant = rememberSaveable { mutableStateOf(editing?.mechanic() ?: GameVariant.NONE) },
        sheetId = rememberSaveable { mutableStateOf(editing?.sheet ?: ScoreSheets.YAMS) },
        sheetRowsText = rememberSaveable { mutableStateOf(editing?.sheetRows?.let { CustomSheetText.format(it) } ?: "") },
        bidKind = rememberSaveable { mutableStateOf(editing?.bidScoring ?: BidScoring.WIZARD) },
        exactTargetText = rememberSaveable { mutableStateOf(editing?.exactTarget?.toString() ?: "") },
        exactResetText = rememberSaveable { mutableStateOf(editing?.exactReset?.toString() ?: "") },
        countdownText = rememberSaveable { mutableStateOf(editing?.countdownFrom?.toString() ?: "") },
        phasesOn = rememberSaveable { mutableStateOf(editing?.phases != null) },
        phasesText = rememberSaveable { mutableStateOf(editing?.phases?.toString() ?: "10") }
    )
}

/**
 * Convertit le formulaire en [CustomGameDraft] (voir [CustomGameDraft.toRules]).
 * [activeVariant] est la mécanique réellement valable pour le mode choisi.
 */
internal fun CreateGameForm.toDraft(activeVariant: GameVariant, tarotContracts: List<ScoreMultiplier>): CustomGameDraft {
    val customRules = ruleDrafts.value.mapIndexedNotNull { index, draft ->
        val factor = draft.factorText.toIntOrNull() ?: 1
        val bonus = draft.bonusText.toIntOrNull() ?: 0
        if (draft.label.isBlank() || (factor == 1 && bonus == 0)) {
            null
        } else {
            ScoreMultiplier(id = "custom_$index", label = draft.label, factor = factor, bonus = bonus)
        }
    }
    val endValue = endValueText.value.toIntOrNull()
    val endCondition = when (endConditionType.value) {
        EndConditionType.NONE -> EndCondition()
        EndConditionType.ROUND_COUNT -> EndCondition(type = EndConditionType.ROUND_COUNT, roundCount = endValue)
        EndConditionType.SCORE_THRESHOLD -> EndCondition(
            type = EndConditionType.SCORE_THRESHOLD,
            scoreThreshold = endValue,
            thresholdDirection = thresholdDirection.value,
            stopImmediately = stopImmediately.value,
            tieBreakOnEqualLeaders = tieBreakOnEqualLeaders.value,
            minLead = minLeadText.value.toIntOrNull() ?: 0,
            maxScore = maxScoreText.value.toIntOrNull()
        )
    }
    return CustomGameDraft(
        name = name.value,
        scoreMode = scoreMode.value,
        variant = activeVariant,
        lowestWins = lowestWins.value,
        allowNegative = allowNegative.value,
        customMultipliers = customRules,
        minPlayers = minPlayersText.value.toIntOrNull(),
        maxPlayers = maxPlayersText.value.toIntOrNull(),
        sheet = sheetId.value,
        sheetRows = CustomSheetText.parse(sheetRowsText.value),
        bidScoring = bidKind.value,
        exactTarget = exactTargetText.value.toIntOrNull(),
        exactReset = exactResetText.value.toIntOrNull(),
        countdownFrom = countdownText.value.toIntOrNull(),
        phases = if (phasesOn.value) phasesText.value.toIntOrNull() else null,
        endCondition = endCondition,
        tarotContracts = tarotContracts
    )
}
