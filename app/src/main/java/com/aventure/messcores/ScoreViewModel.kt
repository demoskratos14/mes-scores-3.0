package com.aventure.messcores

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import java.util.UUID
import kotlin.random.Random

/**
 * Gère l'état complet d'une partie, quel que soit son [ScoreMode]. Le ViewModel garde ce qui est
 * commun à tous les jeux : les noms et couleurs des joueurs, les règles du jeu choisi, la sauvegarde
 * dans le journal, le classement, la fin de partie générale et le partage. Tout ce qui dépend du
 * mode est délégué à un [ModeState] :
 * - mode TABLE : [TableMode], une grille de cases [CellState], avec ajout automatique de manche
 * - mode COUNTER : [CounterMode], un total par joueur, modifiable par +1/-1
 * - mode VARIABLE_TEAMS : [TeamsMode], un historique de [TeamRound]
 * - mode POKER : [PokerMode], un tournoi suivi action par action ([PokerGame]), avec son minuteur de blinds
 * - mode RACKET : [RacketMode], un match de tennis ou de padel, déduit de la liste des points gagnés ([RacketScoring])
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

    /** Vrai tant qu'une partie est ouverte (faux avant la première partie et après [closeGame]). */
    val isGameActive: Boolean get() = gameActive

    // La manche de 421 en cours (qui joue, jeux gardés, limite de lancers). Elle vit ici, et non dans l'écran des dés,
    // pour ne pas repartir de zéro quand on revient aux scores puis aux dés ; une nouvelle partie l'efface.
    private var round421: Round421? = null

    /** La manche de 421 de la partie ouverte : la même d'un écran à l'autre, créée au tirage de départ au premier appel. */
    internal fun round421(maxRolls: Int): Round421 {
        val current = round421
        if (current != null && current.playerCount == players.size && current.maxRolls == maxRolls) return current
        return Round421(players.size, maxRolls).also { round421 = it }
    }

    // La partie de Bunco en cours (manche, joueur qui joue, points de la manche). Elle vit ici pour la même raison que
    // la manche de 421 ; une partie reprise (ou rouverte) repart de la manche qui suit celles déjà inscrites au tableau.
    private var buncoRound: BuncoRound? = null

    /** La partie de Bunco ouverte : la même d'un écran à l'autre, créée au premier appel. */
    internal fun buncoRound(): BuncoRound {
        val current = buncoRound
        if (current != null && current.playerCount == players.size) return current
        return BuncoRound(players.size, startRound = roundsPlayed() + 1).also { buncoRound = it }
    }

    /**
     * Partage de la partie avec un autre téléphone (voir [SyncSession]). Sur un téléphone invité, les actions
     * ne s'appliquent pas ici : elles sont envoyées à l'organisateur, qui renvoie l'état de la partie.
     */
    internal var remote: SyncRemote? = null

    /** Envoie [op] à l'organisateur si ce téléphone est invité ; renvoie vrai dans ce cas (rien ne s'applique ici). */
    private fun forwardToHost(op: SyncOp): Boolean {
        val remote = remote ?: return false
        if (!remote.isGuest) return false
        remote.sendOp(op)
        return true
    }

    /**
     * Dernier état que l'utilisateur a lui-même validé dans le journal : la partie telle qu'elle était
     * à sa reprise depuis le journal, ou telle qu'elle était au dernier clic sur « Enregistrer ».
     * L'auto-sauvegarde ([MainActivity.onStop]) ne le modifie pas. Sert à « Quitter sans enregistrer » :
     * on remet le journal dans cet état. Null pour une partie jamais enregistrée volontairement.
     */
    private var savedBaseline: SavedGame? = null

    /**
     * Contenu de la partie ([SavedGame.contentKey]) tel qu'il est dans le journal : à la dernière
     * sauvegarde, automatique ou voulue, ou à la reprise depuis le journal. Évite de réécrire le
     * journal quand rien n'a changé.
     */
    private var lastSavedContent: SavedGame? = null

    /** Index de la manche de Tarot en cours de correction, ou null pour une nouvelle manche. */
    var editingTeamRoundIndex: Int? = null

    // ---------- Gestionnaires de mode ----------
    // Un exemplaire de chacun pour toute la vie du ViewModel : les écrans lisent leurs listes
    // observables, qui gardent donc la même identité d'une partie à l'autre.

    private val table = TableMode(rules = { gameRules }, isGameOver = { isGameOver() })
    private val counter = CounterMode(isGameOver = { isGameOver() })
    private val teams = TeamsMode(rules = { gameRules })
    private val poker = PokerMode()
    private val racket = RacketMode(rules = { gameRules })
    private val allModes: List<ModeState> = listOf(table, counter, teams, poker, racket)

    /** Phase atteinte par chaque joueur (Phase 10) : se superpose au mode du jeu. */
    private val phases = PhasesState()

    /** Gestionnaire du mode de la partie en cours. */
    private val mode: ModeState
        get() = when (gameRules.scoreMode) {
            ScoreMode.TABLE -> table
            ScoreMode.COUNTER -> counter
            ScoreMode.VARIABLE_TEAMS -> teams
            ScoreMode.POKER -> poker
            ScoreMode.RACKET -> racket
        }

    // --- Mode TABLE ---
    val scores: List<SnapshotStateList<CellState>> get() = table.scores

    // --- Mode COUNTER ---
    val counters: List<Int> get() = counter.values

    // --- Mode VARIABLE_TEAMS ---
    val teamRounds: List<TeamRound> get() = teams.rounds

    // --- Mode POKER ---
    /** Tournoi de poker en cours (mode POKER) : partie, annulation et minuteur de blinds. */
    val pokerSession: PokerSession get() = poker.session

    // --- Mode RACKET (tennis, padel) ---
    /** Réglages du match (nombre de sets, point d'or, set décisif) : modifiables tant qu'aucun point n'est joué. */
    val racketConfig: RacketConfig get() = racket.config

    /** État du match (points, jeux, sets) : recalculé à partir des points gagnés. */
    val racketState: RacketState get() = racket.state

    /** Vrai s'il y a une saisie à annuler (pour activer le bouton « Annuler »). */
    val canUndo: Boolean get() = remote?.takeIf { it.isGuest }?.hostCanUndo ?: mode.canUndo

    /** Vrai s'il y a une saisie annulée à rétablir (pour activer le bouton « Rétablir »). */
    val canRedo: Boolean get() = remote?.takeIf { it.isGuest }?.hostCanRedo ?: mode.canRedo

    // ---------- Cycle de vie de la partie ----------

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
        lastSavedContent = null
        gameActive = true
        round421 = null
        buncoRound = null
        editingTeamRoundIndex = null
        allModes.forEach { it.clear() }
        phases.clear()
        phases.start(rules, playerNames.size)
        // Le poker démarre avec ses réglages : voir [startPoker].
        mode.start(playerNames.size)
    }

    /**
     * Change la couleur d'un joueur. Refusé (renvoie false, rien ne change) si un autre joueur
     * a déjà cette couleur : deux joueurs ne partagent jamais la même couleur.
     */
    fun setPlayerColor(playerIndex: Int, color: Color): Boolean {
        if (forwardToHost(SyncOp.SetPlayerColor(playerIndex, color.toArgb()))) return true
        if (playerIndex !in players.indices || playerColors.size != players.size) return false
        if (isColorTakenByOther(playerColors, playerIndex, color)) return false
        playerColors = playerColors.toMutableList().also { it[playerIndex] = color }
        return true
    }

    /**
     * Restaure une partie précédemment enregistrée dans le journal, telle quelle.
     * [keepRound] : on garde la manche de 421 en cours (état reçu de l'organisateur, qui est la même partie).
     */
    fun loadFromSaved(saved: SavedGame, keepRound: Boolean = false) {
        if (!keepRound) {
            round421 = null
            buncoRound = null
        }
        currentSaveId = saved.id
        savedBaseline = saved
        lastSavedContent = saved.contentKey()
        gameActive = true
        editingTeamRoundIndex = null
        players = saved.players
        gameRules = saved.gameRules
        playerColors = if (saved.playerColorsArgb.size == saved.players.size) {
            saved.playerColorsArgb.map { Color(it) }
        } else {
            assignPlayerColors(saved.players.size)
        }
        allModes.forEach { it.clear() }
        phases.clear()
        phases.restore(saved)
        mode.restore(saved, saved.players.size)
    }

    /** Vrai si la partie ouverte contient au moins un score saisi (donc qu'il y a quelque chose à perdre). */
    fun hasProgress(): Boolean {
        if (!gameActive || players.isEmpty()) return false
        return mode.hasProgress()
    }

    /** Marque la partie comme abandonnée : elle ne sera plus enregistrée automatiquement. */
    fun closeGame() {
        gameActive = false
        round421 = null
        buncoRound = null
        currentSaveId = null
        savedBaseline = null
        lastSavedContent = null
        allModes.forEach { it.close() }
    }

    // ---------- Journal ----------

    /** Enregistrement voulu par l'utilisateur (bouton « Enregistrer », « Enregistrer et quitter »). */
    fun saveToJournal(repository: GameHistoryRepository) {
        val snapshot = snapshot()
        repository.saveGame(snapshot)
        savedBaseline = snapshot
        lastSavedContent = snapshot.contentKey()
    }

    /**
     * Auto-sauvegarde dans le journal, sans que l'utilisateur ait rien demandé : appelée peu après
     * chaque modification (voir [AutoSaveEffect]) et quand l'appli quitte l'écran
     * ([MainActivity.onStop]), pour qu'une panne de batterie ou un plantage ne fasse perdre qu'un
     * instant de jeu. Ne touche pas à [savedBaseline] : « Quitter sans enregistrer » défait
     * toujours l'auto-sauvegarde. Sans partie commencée, ne fait rien.
     *
     * [force] écrit même si le contenu n'a pas changé (sert au chrono du poker, qui avance seul).
     * Renvoie vrai si le journal a été écrit.
     */
    fun autoSave(repository: GameHistoryRepository, force: Boolean = false): Boolean {
        if (!hasProgress()) return false
        val snapshot = snapshot()
        val content = snapshot.contentKey()
        if (!force && content == lastSavedContent) return false
        repository.saveGame(snapshot)
        lastSavedContent = content
        return true
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
        val common = SavedGame(
            id = id,
            savedAt = System.currentTimeMillis(),
            gameRules = gameRules,
            players = players,
            playerColorsArgb = playerColors.map { it.toArgb() },
            isFinished = isGameOver(),
            phases = if (gameRules.phases != null) phases.values.toList() else null
        )
        // Chaque mode ajoute ses propres données (cases, compteurs, manches, tournoi ou match).
        return mode.applyTo(common)
    }

    // ---------- Mode TABLE ----------

    /**
     * À appeler après toute modification d'une case (valeur, signe ou règle appliquée).
     * Ajoute une nouvelle manche vide si la dernière manche existante contient
     * désormais au moins un score, sauf si la partie est déjà terminée.
     * Renvoie vrai si une manche a été ajoutée.
     */
    fun notifyCellChanged(round: Int): Boolean = table.notifyCellChanged(round)

    /**
     * Saisie d'un score dans la case (manche [round], joueur [player]) : l'état d'avant est
     * mémorisé pour [undo]. Sans effet (et rien à annuler) si la case ne change pas.
     */
    fun setCell(round: Int, player: Int, baseValue: Int?, isNegative: Boolean) {
        if (forwardToHost(SyncOp.SetCell(round, player, baseValue, isNegative))) return
        table.setCell(round, player, baseValue, isNegative)
    }

    /** Change la règle de multiplication appliquée à une case (annulable comme [setCell]). */
    fun setMultiplier(round: Int, player: Int, multiplierId: String) {
        if (forwardToHost(SyncOp.SetMultiplier(round, player, multiplierId))) return
        table.setMultiplier(round, player, multiplierId)
    }

    // ---------- Phases (Phase 10) ----------

    /** Vrai si le jeu en cours suit la phase de chaque joueur. */
    val hasPhases: Boolean get() = gameRules.phases != null

    /** Nombre de phases du jeu (0 si le jeu n'en a pas). */
    val phaseCount: Int get() = gameRules.phases ?: 0

    /** Phase d'un joueur : de 1 à [phaseCount], ou [phaseCount] + 1 s'il a terminé la dernière. */
    fun phaseFor(player: Int): Int = phases.phaseFor(player)

    fun setPhase(player: Int, phase: Int) {
        if (forwardToHost(SyncOp.SetPhase(player, phase))) return
        phases.set(player, phase, phaseCount)
    }

    // ---------- Feuille de catégories (Yams) ----------

    /** Lignes de la feuille de catégories du jeu en cours, ou null pour un tableau de manches classique. */
    val sheetRows: List<SheetRow>? get() = ScoreSheets.rows(gameRules)

    /** Vrai si la feuille de catégories du jeu a une ligne « Bonus » (partie haute du Yams). */
    val sheetHasBonus: Boolean get() = ScoreSheets.hasBonus(gameRules.sheet)

    /** Total de la partie haute d'un joueur (0 hors feuille de catégories). */
    fun sheetUpperTotalFor(player: Int): Int = table.sheetUpperTotalFor(player)

    /** Bonus de la partie haute d'un joueur (0 hors feuille de catégories). */
    fun sheetBonusFor(player: Int): Int = table.sheetBonusFor(player)

    // ---------- Mode COUNTER ----------

    fun incrementCounter(player: Int, delta: Int) {
        if (forwardToHost(SyncOp.IncrementCounter(player, delta))) return
        counter.increment(player, delta)
    }

    // ---------- Annulation ----------

    /** Annule la dernière saisie (case du tableau, appui sur un compteur, action de poker, point de tennis). Sans effet s'il n'y en a pas. */
    fun undo() {
        if (forwardToHost(SyncOp.Undo)) return
        mode.undo()
    }

    /** Rétablit la dernière saisie annulée. Sans effet s'il n'y en a pas (ou si une nouvelle saisie a été faite depuis). */
    fun redo() {
        if (forwardToHost(SyncOp.Redo)) return
        mode.redo()
    }

    // ---------- Mode VARIABLE_TEAMS ----------

    /**
     * Ajoute une manche à équipes variables : [teamAPlayers] regroupe les joueurs de
     * l'équipe qui reçoit [baseValue] (signé et multiplié selon [multiplierId]) ;
     * tous les autres joueurs reçoivent l'opposé de ce score.
     */
    fun addTeamRound(teamALabel: String, teamAPlayers: Set<Int>, baseValue: Int, isNegative: Boolean, multiplierId: String) {
        if (forwardToHost(SyncOp.AddTeamRound(teamALabel, teamAPlayers, baseValue, isNegative, multiplierId))) return
        teams.add(teamALabel, teamAPlayers, baseValue, isNegative, multiplierId)
    }

    /** Ajoute une manche déjà entièrement calculée (voir [TeamRound.deltas]). */
    fun appendTeamRound(round: TeamRound) {
        if (forwardToHost(SyncOp.AppendTeamRound(round))) return
        teams.append(round)
    }

    /** Remplace la manche d'index [index] (correction d'une manche déjà saisie). */
    fun replaceTeamRound(index: Int, round: TeamRound) {
        if (forwardToHost(SyncOp.ReplaceTeamRound(index, round))) return
        teams.replace(index, round)
    }

    fun removeTeamRound(index: Int) {
        if (forwardToHost(SyncOp.RemoveTeamRound(index))) return
        teams.remove(index)
    }

    // ---------- Mode RACKET (tennis, padel) ----------

    /** Ajoute un point au camp [side] (0 ou 1). Sans effet une fois le match terminé. */
    fun racketPoint(side: Int) {
        if (gameRules.scoreMode != ScoreMode.RACKET) return
        racket.point(side)
    }

    /** Change les réglages du match ; refusé dès que le premier point est joué. Renvoie vrai si changé. */
    fun setRacketConfig(config: RacketConfig): Boolean {
        if (gameRules.scoreMode != ScoreMode.RACKET) return false
        return racket.setConfig(config)
    }

    /**
     * Change la composition des camps : les joueurs d'index [side0] (index actuels) forment le camp 1,
     * les autres le camp 2. Les noms et les couleurs suivent les joueurs. Refusé dès que le premier
     * point est joué, ou si [side0] n'a pas la taille du camp 1 ou contient un index invalide ou en
     * double. Renvoie vrai si l'ordre des joueurs a changé.
     */
    fun setRacketTeams(side0: List<Int>): Boolean {
        val count = players.size
        if (gameRules.scoreMode != ScoreMode.RACKET) return false
        val order = racket.teamOrder(count, side0) ?: return false
        players = order.map { players[it] }
        if (playerColors.size == count) playerColors = order.map { playerColors[it] }
        racket.onTeamsChanged()
        return true
    }

    /** Camp (0 ou 1) du joueur d'index [player] : voir [RacketScoring.sideOf]. */
    fun racketSideOf(player: Int): Int = RacketScoring.sideOf(player, players.size)

    /** Joueurs d'un camp, dans l'ordre de la liste. */
    fun racketPlayers(side: Int): List<Int> = RacketScoring.playersOfSide(side, players.size)

    // ---------- Mode POKER ----------
    // Partie, annulation et minuteur : voir [PokerSession]. Changements de table : voir [PokerMode].
    // Ici, la mise à jour des noms et des couleurs qui en découle.

    /** Lance le tournoi avec les réglages choisis (après [initGame] avec les règles du poker). */
    fun startPoker(settings: PokerSettings) = poker.session.start(settings, players.size)

    private fun pokerRoster() = PokerRoster(players, playerColors)

    /** Applique le nouvel ordre des joueurs si le changement de table a eu lieu, puis renvoie son issue. */
    private fun PokerReseatOutcome.applied(): PokerReseatResult {
        roster?.let {
            players = it.names
            playerColors = it.colors
        }
        return result
    }

    /** Joueurs (nom, tapis, couleur) des places [indices] encore en lice, prêts à être écrits dans un fichier. */
    fun pokerTransferPlayers(indices: Collection<Int>): List<PokerTransferPlayer> =
        poker.transferPlayers(indices, pokerRoster())

    /** Fait quitter la table aux joueurs [indices] (après leur export). L'annulation est remise à zéro. */
    fun pokerRemovePlayers(indices: Collection<Int>): PokerReseatResult =
        poker.removePlayers(indices, pokerRoster()).applied()

    /** Vrai si l'arrivée de [count] joueurs obligerait à retirer de l'écran les joueurs déjà éliminés (table à 10 places). */
    fun pokerImportNeedsRoom(count: Int): Boolean = poker.importNeedsRoom(count, gameRules.maxPlayers)

    /**
     * Ajoute à la table les joueurs [incoming] (avec leur tapis), assis au hasard entre les joueurs
     * déjà présents : leur ordre et la place du bouton ne changent pas. Si la table n'a plus de place
     * à cause des joueurs éliminés, ceux-ci sont retirés de l'affichage. L'annulation est remise à zéro.
     */
    fun pokerImportPlayers(incoming: List<PokerTransferPlayer>, random: Random = Random.Default): PokerReseatResult =
        poker.importPlayers(incoming, pokerRoster(), gameRules.maxPlayers, random).applied()

    // ---------- Commun ----------

    /**
     * Total cumulé de chaque joueur (même index que [players]), quel que soit le mode de jeu.
     * Chaque appel parcourt toutes les manches : dans un écran, on le mémorise donc avec
     * `remember { derivedStateOf { viewModel.totals } }` plutôt que de le rappeler pour chaque ligne.
     */
    val totals: List<Int> get() = players.indices.map { mode.total(it) }

    /** Rang (1 = premier) de chaque joueur d'après [totals] ; les ex æquo partagent le même rang. */
    fun ranksFor(totals: List<Int>): List<Int> {
        // Poker : joueurs en lice d'après leurs jetons, puis éliminés dans l'ordre inverse d'élimination.
        mode.ranks()?.let { return it }
        val phaseList = if (hasPhases && phases.values.size == totals.size) phases.values.toList() else null
        return computeRanks(totals, gameRules.lowestWins, phaseList)
    }

    /** Rang de chaque joueur (même index que [players]). Voir [ranksFor]. */
    val ranks: List<Int> get() = ranksFor(totals)

    /** Total cumulé d'un joueur, quel que soit le mode de jeu. */
    fun totalFor(player: Int): Int = mode.total(player)

    /** Index des joueurs triés du meilleur score au moins bon, selon les règles du jeu. */
    fun rankingOrder(): List<Int> {
        val all = totals
        val custom = mode.ranks()
        if (hasPhases || custom != null) {
            val order = custom ?: ranksFor(all)
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
    fun roundsPlayed(): Int = mode.roundsPlayed()

    /** Classement actuel en texte, prêt à être partagé (voir [ResultText]). */
    fun shareText(context: Context): String {
        val all = totals
        return ResultText.game(
            context = context,
            gameName = shareGameName(),
            players = players,
            totals = all,
            ranks = ranksFor(all),
            finished = isGameOver(),
            winners = winners()
        )
    }

    /** Nom du jeu pour le partage ; tennis et padel y ajoutent le score en chiffres (« Tennis 6-4 3-6 2-1 »). */
    private fun shareGameName(): String {
        if (gameRules.scoreMode != ScoreMode.RACKET) return gameRules.name
        val line = RacketScoring.scoreLine(racketState)
        return if (line.isEmpty()) gameRules.name else gameRules.name + " " + line
    }

    /** Même classement que [shareText], mis en forme pour l'image partagée (voir [ResultImage]). */
    fun shareImageContent(context: Context): ResultImage.Content {
        val all = totals
        return ResultImage.content(
            context = context,
            gameName = shareGameName(),
            players = players,
            totals = all,
            ranks = ranksFor(all),
            finished = isGameOver(),
            winners = winners()
        )
    }

    /** Vrai si la condition de fin de partie définie par les règles du jeu est atteinte. */
    fun isGameOver(): Boolean {
        // Poker (un seul joueur avec des jetons) et tennis / padel (un camp a gagné ses sets) ont leur propre règle.
        mode.gameOver()?.let { return it }
        // Fléchettes : terminée dès qu'un joueur arrive à 0 pile.
        if (gameRules.countdownFrom != null) return table.hasRounds && totals.any { it == 0 }
        // Phase 10 : terminée dès qu'un joueur a terminé la dernière phase.
        gameRules.phases?.let { last -> return phases.values.any { it > last } }
        // Feuille de catégories : terminée quand toutes les cases de tous les joueurs sont remplies.
        if (sheetRows != null) return table.allCellsFilled()
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
                    table.activeRoundComplete()
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
