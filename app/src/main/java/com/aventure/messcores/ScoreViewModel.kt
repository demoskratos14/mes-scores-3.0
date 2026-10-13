package com.aventure.messcores

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import java.util.UUID

/**
 * Gère l'état complet d'une partie, quel que soit son [ScoreMode] :
 * - la liste des noms de joueurs et les règles du jeu choisi
 * - une couleur distincte assignée aléatoirement à chaque joueur
 * - mode TABLE : une grille de cases [CellState], avec ajout automatique de manche
 * - mode COUNTER : un total par joueur, modifiable par +1/-1
 * - mode VARIABLE_TEAMS : un historique de [TeamRound]
 */
class ScoreViewModel : ViewModel() {

    var players by mutableStateOf<List<String>>(emptyList())
        private set

    // Valeur provisoire avant initGame / loadFromSaved : jamais affichée (le nom vient du jeu choisi).
    var gameRules by mutableStateOf(GameRules(id = "default", name = ""))
        private set

    /** Une couleur distincte par joueur (même index que [players]), tirée au sort à chaque nouvelle partie. */
    var playerColors by mutableStateOf<List<Color>>(emptyList())
        private set

    /**
     * Identifie la partie en cours dans le journal ([GameHistoryRepository]). Null tant
     * qu'elle n'a jamais été enregistrée ; fixé au premier appel à [snapshot], puis
     * réutilisé pour que les enregistrements suivants mettent à jour la même entrée
     * au lieu d'en créer une nouvelle à chaque fois.
     */
    var currentSaveId: String? by mutableStateOf(null)
        private set

    /** Vrai tant qu'une partie est ouverte (faux avant la première partie et après [closeGame]). */
    private var gameActive = false

    /**
     * Dernier état que l'utilisateur a lui-même validé dans le journal : la partie telle qu'elle était
     * à sa reprise depuis le journal, ou telle qu'elle était au dernier clic sur « Enregistrer ».
     * L'auto-sauvegarde ([MainActivity.onStop]) ne le modifie pas. Sert à « Quitter sans enregistrer » :
     * on remet le journal dans cet état. Null pour une partie jamais enregistrée volontairement.
     */
    private var savedBaseline: SavedGame? = null

    /** Index de la manche de Tarot en cours de correction, ou null pour une nouvelle manche. */
    var editingTeamRoundIndex: Int? = null

    // --- Mode TABLE ---
    private val _scores = mutableStateListOf<SnapshotStateList<CellState>>()
    val scores: List<SnapshotStateList<CellState>> get() = _scores

    // --- Mode COUNTER ---
    private val _counters = mutableStateListOf<Int>()

    /** Phase atteinte par chaque joueur (Phase 10) ; vide pour les autres jeux. */
    private val _phases = mutableStateListOf<Int>()
    val counters: List<Int> get() = _counters

    // --- Mode VARIABLE_TEAMS ---
    private val _teamRounds = mutableStateListOf<TeamRound>()
    val teamRounds: List<TeamRound> get() = _teamRounds

    // --- Annulation (modes TABLE et COUNTER) ---
    private val _undoStack = mutableStateListOf<UndoAction>()

    /** Vrai s'il y a une saisie à annuler (pour activer le bouton « Annuler »). */
    val canUndo: Boolean get() = _undoStack.isNotEmpty()

    // Saisies annulées, rétablissables tant qu'aucune nouvelle saisie n'est faite.
    private val _redoStack = mutableStateListOf<RedoAction>()

    /** Vrai s'il y a une saisie annulée à rétablir (pour activer le bouton « Rétablir »). */
    val canRedo: Boolean get() = _redoStack.isNotEmpty()

    companion object {
        private const val INITIAL_ROUNDS = 5

        /** Nombre maximal de saisies mémorisées pour l'annulation. */
        private const val MAX_UNDO = 100
    }

    /**
     * Démarre une nouvelle partie avec la liste de noms et les règles de score fournies.
     * [chosenColors] : couleurs choisies à l'écran de départ (null = au hasard) ; les autres sont tirées au sort.
     */
    fun initGame(playerNames: List<String>, rules: GameRules, chosenColors: List<Color?> = emptyList()) {
        players = playerNames
        gameRules = rules
        playerColors = assignPlayerColors(playerNames.size, chosenColors)
        currentSaveId = null
        savedBaseline = null
        gameActive = true
        editingTeamRoundIndex = null
        _undoStack.clear()
        _redoStack.clear()
        _scores.clear()
        _counters.clear()
        _teamRounds.clear()
        _phases.clear()
        if (rules.phases != null) repeat(playerNames.size) { _phases.add(1) }
        when (rules.scoreMode) {
            ScoreMode.TABLE -> repeat(sheetRows?.size ?: INITIAL_ROUNDS) { addRound() }
            ScoreMode.COUNTER -> repeat(playerNames.size) { _counters.add(0) }
            ScoreMode.VARIABLE_TEAMS -> Unit
        }
    }

    /**
     * Change la couleur d'un joueur. Refusé (renvoie false, rien ne change) si un autre joueur
     * a déjà cette couleur : deux joueurs ne partagent jamais la même couleur.
     */
    fun setPlayerColor(playerIndex: Int, color: Color): Boolean {
        if (playerIndex !in players.indices || playerColors.size != players.size) return false
        if (isColorTakenByOther(playerColors, playerIndex, color)) return false
        playerColors = playerColors.toMutableList().also { it[playerIndex] = color }
        return true
    }

    /** Restaure une partie précédemment enregistrée dans le journal, telle quelle. */
    fun loadFromSaved(saved: SavedGame) {
        currentSaveId = saved.id
        savedBaseline = saved
        gameActive = true
        editingTeamRoundIndex = null
        players = saved.players
        gameRules = saved.gameRules
        playerColors = if (saved.playerColorsArgb.size == saved.players.size) {
            saved.playerColorsArgb.map { Color(it) }
        } else {
            assignPlayerColors(saved.players.size)
        }
        _undoStack.clear()
        _redoStack.clear()
        _scores.clear()
        _counters.clear()
        _teamRounds.clear()
        _phases.clear()
        if (saved.gameRules.phases != null) {
            val savedPhases = saved.phases
            if (savedPhases != null && savedPhases.size == saved.players.size) {
                savedPhases.forEach { _phases.add(it) }
            } else {
                repeat(saved.players.size) { _phases.add(1) }
            }
        }
        when (saved.gameRules.scoreMode) {
            ScoreMode.TABLE -> {
                val rounds = saved.cellSnapshots
                if (rounds.isNullOrEmpty()) {
                    repeat(sheetRows?.size ?: INITIAL_ROUNDS) { addRound() }
                } else {
                    rounds.forEach { roundSnapshot ->
                        val row = mutableStateListOf<CellState>()
                        roundSnapshot.forEach { snap ->
                            row.add(
                                CellState().apply {
                                    baseValue = snap.baseValue
                                    isNegative = snap.isNegative
                                    multiplierId = snap.multiplierId
                                }
                            )
                        }
                        _scores.add(row)
                    }
                }
            }
            ScoreMode.COUNTER -> {
                val counters = saved.counters
                if (counters.isNullOrEmpty()) {
                    repeat(saved.players.size) { _counters.add(0) }
                } else {
                    counters.forEach { _counters.add(it) }
                }
            }
            ScoreMode.VARIABLE_TEAMS -> {
                saved.teamRounds?.forEach { _teamRounds.add(it) }
            }
        }
    }

    /** Vrai si la partie ouverte contient au moins un score saisi (donc qu'il y a quelque chose à perdre). */
    fun hasProgress(): Boolean {
        if (!gameActive || players.isEmpty()) return false
        return when (gameRules.scoreMode) {
            ScoreMode.TABLE -> _scores.any { round -> round.any { it.baseValue != null } }
            ScoreMode.COUNTER -> _counters.any { it != 0 }
            ScoreMode.VARIABLE_TEAMS -> _teamRounds.isNotEmpty()
        }
    }

    /** Marque la partie comme abandonnée : elle ne sera plus enregistrée automatiquement. */
    fun closeGame() {
        gameActive = false
        currentSaveId = null
        savedBaseline = null
        _undoStack.clear()
        _redoStack.clear()
    }

    /** Enregistrement voulu par l'utilisateur (bouton « Enregistrer », « Enregistrer et quitter »). */
    fun saveToJournal(repository: GameHistoryRepository) {
        val snapshot = snapshot()
        repository.saveGame(snapshot)
        savedBaseline = snapshot
    }

    /**
     * « Quitter sans enregistrer » : annule l'auto-sauvegarde faite par [MainActivity.onStop].
     * Si la partie avait été reprise du journal ou enregistrée à la main, on remet l'entrée dans
     * l'état validé par l'utilisateur ; sinon l'entrée créée automatiquement est supprimée.
     * À appeler avant [closeGame].
     */
    fun discardToJournal(repository: GameHistoryRepository) {
        val baseline = savedBaseline
        val id = currentSaveId
        when {
            baseline != null -> repository.saveGame(baseline)
            id != null -> repository.deleteGame(id)
        }
    }

    /** Capture l'état actuel de la partie, prêt à être enregistré dans le journal via [GameHistoryRepository]. */
    fun snapshot(): SavedGame =
        buildSnapshot(currentSaveId ?: UUID.randomUUID().toString().also { currentSaveId = it })

    /** Photo de la partie pour la courbe d'évolution : comme [snapshot], sans créer d'identifiant de sauvegarde. */
    fun chartSnapshot(): SavedGame = buildSnapshot(currentSaveId ?: "chart")

    private fun buildSnapshot(id: String): SavedGame {
        return SavedGame(
            id = id,
            savedAt = System.currentTimeMillis(),
            gameRules = gameRules,
            players = players,
            playerColorsArgb = playerColors.map { it.toArgb() },
            cellSnapshots = if (gameRules.scoreMode == ScoreMode.TABLE) {
                _scores.map { round -> round.map { CellSnapshot(it.baseValue, it.isNegative, it.multiplierId) } }
            } else null,
            counters = if (gameRules.scoreMode == ScoreMode.COUNTER) _counters.toList() else null,
            teamRounds = if (gameRules.scoreMode == ScoreMode.VARIABLE_TEAMS) _teamRounds.toList() else null,
            isFinished = isGameOver(),
            phases = if (gameRules.phases != null) _phases.toList() else null
        )
    }

    // ---------- Mode TABLE ----------

    private fun addRound() {
        val row = mutableStateListOf<CellState>()
        repeat(players.size) { row.add(CellState()) }
        _scores.add(row)
    }

    /**
     * À appeler après toute modification d'une case (valeur, signe ou règle appliquée).
     * Ajoute une nouvelle manche vide si la dernière manche existante contient
     * désormais au moins un score, sauf si la partie est déjà terminée.
     * Renvoie vrai si une manche a été ajoutée.
     */
    fun notifyCellChanged(round: Int): Boolean {
        // Une feuille de catégories a un nombre fixe de lignes : jamais de manche ajoutée.
        if (sheetRows != null || isGameOver()) return false
        if (round == _scores.lastIndex && _scores[round].any { it.baseValue != null }) {
            addRound()
            return true
        }
        return false
    }

    /**
     * Saisie d'un score dans la case (manche [round], joueur [player]) : l'état d'avant est
     * mémorisé pour [undo]. Sans effet (et rien à annuler) si la case ne change pas.
     */
    fun setCell(round: Int, player: Int, baseValue: Int?, isNegative: Boolean) {
        val cell = _scores.getOrNull(round)?.getOrNull(player) ?: return
        // Un signe « − » sans valeur n'a pas de sens : une case vide reste positive.
        val negative = if (baseValue == null) false else isNegative
        if (cell.baseValue == baseValue && cell.isNegative == negative) return
        val before = CellSnapshot(cell.baseValue, cell.isNegative, cell.multiplierId)
        cell.baseValue = baseValue
        cell.isNegative = negative
        val added = notifyCellChanged(round)
        pushUndo(UndoAction.CellEdit(round, player, before, added))
    }

    /** Change la règle de multiplication appliquée à une case (annulable comme [setCell]). */
    fun setMultiplier(round: Int, player: Int, multiplierId: String) {
        val cell = _scores.getOrNull(round)?.getOrNull(player) ?: return
        if (cell.multiplierId == multiplierId) return
        val before = CellSnapshot(cell.baseValue, cell.isNegative, cell.multiplierId)
        cell.multiplierId = multiplierId
        val added = notifyCellChanged(round)
        pushUndo(UndoAction.CellEdit(round, player, before, added))
    }

    private fun effectiveValue(base: Int, isNegative: Boolean, multiplierId: String): Int {
        val rule = gameRules.multipliers.find { it.id == multiplierId } ?: GameRules.NORMAL_MULTIPLIER
        val magnitude = base * rule.factor + rule.bonus
        return if (isNegative) -magnitude else magnitude
    }

    private fun effectiveValue(cell: CellState): Int? {
        val base = cell.baseValue ?: return null
        return effectiveValue(base, cell.isNegative, cell.multiplierId)
    }

    // ---------- Phases (Phase 10) ----------

    /** Vrai si le jeu en cours suit la phase de chaque joueur. */
    val hasPhases: Boolean get() = gameRules.phases != null

    /** Nombre de phases du jeu (0 si le jeu n'en a pas). */
    val phaseCount: Int get() = gameRules.phases ?: 0

    /** Phase d'un joueur : de 1 à [phaseCount], ou [phaseCount] + 1 s'il a terminé la dernière. */
    fun phaseFor(player: Int): Int = _phases.getOrElse(player) { 1 }

    fun setPhase(player: Int, phase: Int) {
        if (player in _phases.indices) _phases[player] = phase.coerceIn(1, phaseCount + 1)
    }

    // ---------- Feuille de catégories (Yams) ----------

    /** Lignes de la feuille de catégories du jeu en cours, ou null pour un tableau de manches classique. */
    val sheetRows: List<SheetRow>? get() = ScoreSheets.rows(gameRules)

    /** Vrai si la feuille de catégories du jeu a une ligne « Bonus » (partie haute du Yams). */
    val sheetHasBonus: Boolean get() = ScoreSheets.hasBonus(gameRules.sheet)

    private fun sheetValuesFor(player: Int): List<Int?> =
        _scores.map { round -> round.getOrNull(player)?.let { effectiveValue(it) } }

    /** Total de la partie haute d'un joueur (0 hors feuille de catégories). */
    fun sheetUpperTotalFor(player: Int): Int = ScoreSheets.upperTotal(gameRules.sheet, sheetValuesFor(player))

    /** Bonus de la partie haute d'un joueur (0 hors feuille de catégories). */
    fun sheetBonusFor(player: Int): Int =
        if (sheetRows == null) 0 else ScoreSheets.bonus(gameRules.sheet, sheetValuesFor(player))

    // ---------- Mode COUNTER ----------

    fun incrementCounter(player: Int, delta: Int) {
        if (isGameOver()) return
        if (player !in _counters.indices) return
        _counters[player] = _counters[player] + delta
        pushUndo(UndoAction.CounterChange(player, delta))
    }

    // ---------- Annulation ----------

    /** [clearRedo] est faux uniquement quand [redo] rejoue une saisie : toute autre saisie vide les saisies rétablissables. */
    private fun pushUndo(action: UndoAction, clearRedo: Boolean = true) {
        _undoStack.add(action)
        if (_undoStack.size > MAX_UNDO) _undoStack.removeAt(0)
        if (clearRedo) _redoStack.clear()
    }

    /** Annule la dernière saisie (case du tableau ou appui sur un compteur). Sans effet s'il n'y en a pas. */
    fun undo() {
        val action = _undoStack.removeLastOrNull() ?: return
        when (action) {
            is UndoAction.CounterChange -> {
                if (action.player in _counters.indices) {
                    _counters[action.player] = _counters[action.player] - action.delta
                    _redoStack.add(RedoAction.CounterChange(action.player, action.delta))
                }
            }
            is UndoAction.CellEdit -> {
                val cell = _scores.getOrNull(action.round)?.getOrNull(action.player) ?: return
                // État d'avant l'annulation : c'est celui que « Rétablir » doit remettre.
                val after = CellSnapshot(cell.baseValue, cell.isNegative, cell.multiplierId)
                cell.baseValue = action.before.baseValue
                cell.isNegative = action.before.isNegative
                cell.multiplierId = action.before.multiplierId
                // Retire la manche vide que cette saisie avait fait ajouter automatiquement.
                var removedRound = false
                if (action.addedRound && _scores.lastIndex == action.round + 1 &&
                    _scores.last().all { it.baseValue == null }
                ) {
                    _scores.removeAt(_scores.lastIndex)
                    removedRound = true
                }
                _redoStack.add(RedoAction.CellEdit(action.round, action.player, after, removedRound))
            }
        }
    }

    /** Rétablit la dernière saisie annulée. Sans effet s'il n'y en a pas (ou si une nouvelle saisie a été faite depuis). */
    fun redo() {
        val action = _redoStack.removeLastOrNull() ?: return
        when (action) {
            is RedoAction.CounterChange -> {
                if (action.player in _counters.indices) {
                    _counters[action.player] = _counters[action.player] + action.delta
                    pushUndo(UndoAction.CounterChange(action.player, action.delta), clearRedo = false)
                }
            }
            is RedoAction.CellEdit -> {
                // Recrée la manche vide que l'annulation avait retirée.
                if (action.removedRound && _scores.lastIndex == action.round) addRound()
                val cell = _scores.getOrNull(action.round)?.getOrNull(action.player) ?: return
                val before = CellSnapshot(cell.baseValue, cell.isNegative, cell.multiplierId)
                cell.baseValue = action.state.baseValue
                cell.isNegative = action.state.isNegative
                cell.multiplierId = action.state.multiplierId
                pushUndo(UndoAction.CellEdit(action.round, action.player, before, action.removedRound), clearRedo = false)
            }
        }
    }

    // ---------- Mode VARIABLE_TEAMS ----------

    /**
     * Ajoute une manche à équipes variables : [teamAPlayers] regroupe les joueurs de
     * l'équipe qui reçoit [baseValue] (signé et multiplié selon [multiplierId]) ;
     * tous les autres joueurs reçoivent l'opposé de ce score.
     */
    fun addTeamRound(teamALabel: String, teamAPlayers: Set<Int>, baseValue: Int, isNegative: Boolean, multiplierId: String) {
        val value = effectiveValue(baseValue, isNegative, multiplierId)
        _teamRounds.add(TeamRound(teamALabel, teamAPlayers, value))
    }

    /** Ajoute une manche déjà entièrement calculée (voir [TeamRound.deltas]). */
    fun appendTeamRound(round: TeamRound) {
        _teamRounds.add(round)
    }

    /** Remplace la manche d'index [index] (correction d'une manche déjà saisie). */
    fun replaceTeamRound(index: Int, round: TeamRound) {
        if (index in _teamRounds.indices) _teamRounds[index] = round
    }

    fun removeTeamRound(index: Int) {
        if (index in _teamRounds.indices) _teamRounds.removeAt(index)
    }

    // ---------- Commun ----------

    /** Total cumulé d'un joueur, calculé en parcourant les manches. */
    private fun computeTotal(player: Int): Int = when (gameRules.scoreMode) {
        ScoreMode.TABLE ->
            gameRules.tableTotal(sheetValuesFor(player))
        ScoreMode.COUNTER ->
            _counters.getOrNull(player) ?: 0
        ScoreMode.VARIABLE_TEAMS ->
            _teamRounds.sumOf { round ->
                round.deltas?.getOrNull(player)
                    ?: if (player in round.teamAPlayers) round.value else -round.value
            }
    }

    /**
     * Total cumulé de chaque joueur (même index que [players]), quel que soit le mode de jeu.
     * Chaque appel parcourt toutes les manches : dans un écran, on le mémorise donc avec
     * `remember { derivedStateOf { viewModel.totals } }` plutôt que de le rappeler pour chaque ligne.
     */
    val totals: List<Int> get() = players.indices.map { computeTotal(it) }

    /** Rang (1 = premier) de chaque joueur d'après [totals] ; les ex æquo partagent le même rang. */
    fun ranksFor(totals: List<Int>): List<Int> {
        val phases = if (hasPhases && _phases.size == totals.size) _phases.toList() else null
        return computeRanks(totals, gameRules.lowestWins, phases)
    }

    /** Rang de chaque joueur (même index que [players]). Voir [ranksFor]. */
    val ranks: List<Int> get() = ranksFor(totals)

    /** Total cumulé d'un joueur, quel que soit le mode de jeu. */
    fun totalFor(player: Int): Int = computeTotal(player)

    /** Index des joueurs triés du meilleur score au moins bon, selon les règles du jeu. */
    fun rankingOrder(): List<Int> {
        val all = totals
        if (hasPhases) {
            val order = ranksFor(all)
            return players.indices.sortedBy { order[it] }
        }
        return if (gameRules.lowestWins) {
            players.indices.sortedBy { all[it] }
        } else {
            players.indices.sortedByDescending { all[it] }
        }
    }

    /** Rang (1 = premier) d'un joueur donné. Les joueurs à égalité partagent le même rang. */
    fun rankOf(player: Int, ranks: List<Int> = this.ranks): Int = ranks.getOrElse(player) { 1 }

    /**
     * Vrai si le joueur est en tête (ex æquo compris), sauf si tout le monde est à égalité.
     * Les écrans passent leurs [totals] et [ranks] mémorisés pour éviter de tout recalculer.
     */
    fun isLeader(
        player: Int,
        totals: List<Int> = this.totals,
        ranks: List<Int> = ranksFor(totals)
    ): Boolean = totals.distinct().size > 1 && rankOf(player, ranks) == 1

    /** Nombre de manches réellement jouées (au moins une case remplie), pour TABLE et VARIABLE_TEAMS. */
    fun roundsPlayed(): Int = when (gameRules.scoreMode) {
        ScoreMode.TABLE -> _scores.count { round -> round.any { it.baseValue != null } }
        ScoreMode.VARIABLE_TEAMS -> _teamRounds.size
        ScoreMode.COUNTER -> 0
    }

    /** Classement actuel en texte, prêt à être partagé (voir [ResultText]). */
    fun shareText(context: Context): String {
        val all = totals
        return ResultText.game(
            context = context,
            gameName = gameRules.name,
            players = players,
            totals = all,
            ranks = ranksFor(all),
            finished = isGameOver(),
            winners = winners()
        )
    }

    /** Vrai si la condition de fin de partie définie par les règles du jeu est atteinte. */
    fun isGameOver(): Boolean {
        // Fléchettes : terminée dès qu'un joueur arrive à 0 pile.
        if (gameRules.countdownFrom != null) return _scores.isNotEmpty() && totals.any { it == 0 }
        // Phase 10 : terminée dès qu'un joueur a terminé la dernière phase.
        gameRules.phases?.let { last -> return _phases.any { it > last } }
        // Feuille de catégories : terminée quand toutes les cases de tous les joueurs sont remplies.
        if (sheetRows != null) {
            return _scores.isNotEmpty() && _scores.all { round -> round.all { it.baseValue != null } }
        }
        val condition = gameRules.endCondition
        val rawStop = when (condition.type) {
            EndConditionType.NONE -> false
            EndConditionType.ROUND_COUNT -> {
                val limit = condition.roundCount ?: return false
                roundsPlayed() >= limit
            }
            EndConditionType.SCORE_THRESHOLD -> {
                if (!thresholdReached(condition, totals)) {
                    false
                } else if (condition.stopImmediately || gameRules.scoreMode != ScoreMode.TABLE) {
                    // Hors mode TABLE (pas de notion de "manche en cours" à terminer), ou si
                    // demandé explicitement, on s'arrête dès que le seuil est franchi.
                    true
                } else {
                    // On attend que la manche en cours soit complète (tous les joueurs ont
                    // saisi un score) avant de considérer la partie terminée.
                    activeRoundComplete()
                }
            }
        }
        if (!rawStop) return false
        // Manche décisive : si plusieurs joueurs sont à égalité en tête, on ne s'arrête pas.
        if (condition.type == EndConditionType.SCORE_THRESHOLD && condition.tieBreakOnEqualLeaders && hasTiedLeaders(totals, gameRules.lowestWins)) {
            return false
        }
        return true
    }

    /** Vrai si la dernière manche entamée (mode TABLE) a été remplie par tous les joueurs. */
    private fun activeRoundComplete(): Boolean {
        val activeIndex = _scores.indexOfLast { round -> round.any { it.baseValue != null } }
        if (activeIndex == -1) return false
        return _scores[activeIndex].all { it.baseValue != null }
    }

    /** Index du joueur en tête si la partie est terminée, sinon null. */
    fun winner(): Int? = winners().firstOrNull()

    /** Joueurs en tête (plusieurs en cas d'égalité) si la partie est terminée, sinon liste vide. */
    fun winners(): List<Int> =
        if (isGameOver() && players.isNotEmpty()) {
            val all = ranks
            players.indices.filter { all[it] == 1 }
        } else {
            emptyList()
        }
}
