package com.aventure.messcores

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * Mode TABLE : un tableau de manches (ou de catégories pour une feuille de score comme le Yams),
 * une case par manche et par joueur, avec annulation/rétablissement de chaque saisie.
 *
 * [rules] donne les règles de la partie en cours ; [isGameOver] interroge la fin de partie générale
 * du ViewModel (une manche vide n'est plus ajoutée une fois la partie terminée).
 */
internal class TableMode(
    private val rules: () -> GameRules,
    private val isGameOver: () -> Boolean
) : ModeState {

    companion object {
        /** Nombre de manches proposées au départ (hors feuille de catégories, qui a ses lignes fixes). */
        const val INITIAL_ROUNDS = 5
    }

    private var playerCount = 0
    private val _scores = mutableStateListOf<SnapshotStateList<CellState>>()
    private val history = UndoHistory()

    /** Une liste par manche, une [CellState] par joueur. */
    val scores: List<SnapshotStateList<CellState>> get() = _scores

    /** Lignes de la feuille de catégories du jeu en cours, ou null pour un tableau de manches classique. */
    val sheetRows: List<SheetRow>? get() = ScoreSheets.rows(rules())

    /** Vrai s'il y a au moins une manche (ou une ligne de feuille) dans le tableau. */
    val hasRounds: Boolean get() = _scores.isNotEmpty()

    /** Vrai si toutes les cases de toutes les lignes sont remplies (feuille de catégories complète). */
    fun allCellsFilled(): Boolean = _scores.isNotEmpty() && _scores.all { round -> round.all { it.baseValue != null } }

    /** Vrai si la dernière manche entamée (au moins une case remplie) a été remplie par tous les joueurs. */
    fun activeRoundComplete(): Boolean {
        val activeIndex = _scores.indexOfLast { round -> round.any { it.baseValue != null } }
        if (activeIndex == -1) return false
        return _scores[activeIndex].all { it.baseValue != null }
    }

    // ---------- Cycle de vie ----------

    override fun clear() {
        _scores.clear()
        history.clear()
    }

    override fun close() = history.clear()

    override fun start(playerCount: Int) {
        this.playerCount = playerCount
        repeat(sheetRows?.size ?: INITIAL_ROUNDS) { addRound() }
    }

    override fun restore(saved: SavedGame, playerCount: Int) {
        this.playerCount = playerCount
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

    override fun applyTo(saved: SavedGame): SavedGame = saved.copy(
        cellSnapshots = _scores.map { round -> round.map { CellSnapshot(it.baseValue, it.isNegative, it.multiplierId) } }
    )

    override fun hasProgress(): Boolean = _scores.any { round -> round.any { it.baseValue != null } }

    // ---------- Saisie ----------

    private fun addRound() {
        val row = mutableStateListOf<CellState>()
        repeat(playerCount) { row.add(CellState()) }
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
        history.push(UndoAction.CellEdit(round, player, before, added))
    }

    /** Change la règle de multiplication appliquée à une case (annulable comme [setCell]). */
    fun setMultiplier(round: Int, player: Int, multiplierId: String) {
        val cell = _scores.getOrNull(round)?.getOrNull(player) ?: return
        if (cell.multiplierId == multiplierId) return
        val before = CellSnapshot(cell.baseValue, cell.isNegative, cell.multiplierId)
        cell.multiplierId = multiplierId
        val added = notifyCellChanged(round)
        history.push(UndoAction.CellEdit(round, player, before, added))
    }

    // ---------- Annulation ----------

    override val canUndo: Boolean get() = history.canUndo
    override val canRedo: Boolean get() = history.canRedo

    override fun undo() {
        val action = history.popUndo() as? UndoAction.CellEdit ?: return
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
        history.pushRedo(RedoAction.CellEdit(action.round, action.player, after, removedRound))
    }

    override fun redo() {
        val action = history.popRedo() as? RedoAction.CellEdit ?: return
        // Recrée la manche vide que l'annulation avait retirée.
        if (action.removedRound && _scores.lastIndex == action.round) addRound()
        val cell = _scores.getOrNull(action.round)?.getOrNull(action.player) ?: return
        val before = CellSnapshot(cell.baseValue, cell.isNegative, cell.multiplierId)
        cell.baseValue = action.state.baseValue
        cell.isNegative = action.state.isNegative
        cell.multiplierId = action.state.multiplierId
        history.push(UndoAction.CellEdit(action.round, action.player, before, action.removedRound), clearRedo = false)
    }

    // ---------- Totaux ----------

    private fun effectiveValue(cell: CellState): Int? {
        val base = cell.baseValue ?: return null
        return rules().effectiveValue(base, cell.isNegative, cell.multiplierId)
    }

    /** Score de chaque manche (ou catégorie) pour [player] ; null = case vide. */
    fun valuesFor(player: Int): List<Int?> =
        _scores.map { round -> round.getOrNull(player)?.let { effectiveValue(it) } }

    override fun total(player: Int): Int = rules().tableTotal(valuesFor(player))

    override fun roundsPlayed(): Int = _scores.count { round -> round.any { it.baseValue != null } }

    /** Total de la partie haute d'un joueur (0 hors feuille de catégories). */
    fun sheetUpperTotalFor(player: Int): Int = ScoreSheets.upperTotal(rules().sheet, valuesFor(player))

    /** Bonus de la partie haute d'un joueur (0 hors feuille de catégories). */
    fun sheetBonusFor(player: Int): Int =
        if (sheetRows == null) 0 else ScoreSheets.bonus(rules().sheet, valuesFor(player))
}
