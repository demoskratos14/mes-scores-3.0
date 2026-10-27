package com.aventure.messcores

import androidx.compose.ui.res.stringResource
import android.net.Uri
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

// AppCompatActivity (et non ComponentActivity) : c'est ce qui permet à AppCompatDelegate.setApplicationLocales
// de changer la langue de l'appli depuis le bouton drapeau sur Android 12 et moins.
class MainActivity : AppCompatActivity() {

    // Même instance que celle récupérée par viewModel() dans ScoreApp (même Activity, même clé).
    private val scoreViewModel: ScoreViewModel by viewModels()

    /**
     * Sauvegarde automatique : dès que l'appli quitte l'écran (retour à l'accueil, autre appli…),
     * la partie en cours est enregistrée dans le journal. Si Android ferme ensuite l'appli en
     * arrière-plan, la partie reste récupérable via « Reprendre ».
     */
    override fun onStart() {
        super.onStart()
        // L'appli est visible : le minuteur sonne directement, et une éventuelle notification
        // de fin de minuteur n'a plus lieu d'être.
        TimerAlert.appInForeground = true
        TimerAlert.cancelNotification(this)
    }

    override fun onStop() {
        super.onStop()
        TimerAlert.appInForeground = false
        // Dernière sauvegarde avant que le système ne puisse arrêter l'appli : toujours écrite.
        scoreViewModel.autoSave(GameHistoryRepository(this), force = true)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Nécessaire pour que Modifier.imePadding() fonctionne correctement et que le
        // contenu remonte automatiquement au-dessus du clavier au lieu d'être masqué.
        // La photo de fond est toujours assombrie par un voile : icônes claires dans la barre d'état
        // et la barre de navigation, en thème clair comme en thème sombre.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            // Apparence choisie sur l'écran d'accueil (Auto = réglage du téléphone), conservée entre deux lancements.
            var themeMode by remember { mutableStateOf(ThemePreference.load(this@MainActivity)) }
            // Mode daltonien (code sur chaque couleur), réglé sur l'écran d'accueil et conservé entre deux lancements.
            var colorBlindMode by remember { mutableStateOf(ColorBlindPreference.load(this@MainActivity)) }
            CompositionLocalProvider(LocalColorBlindMode provides colorBlindMode) {
                MesScoresTheme(mode = themeMode) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        ScoreApp(
                            themeMode = themeMode,
                            onThemeModeChange = { mode ->
                                themeMode = mode
                                ThemePreference.save(this@MainActivity, mode)
                            },
                            colorBlindMode = colorBlindMode,
                            onColorBlindModeChange = { enabled ->
                                colorBlindMode = enabled
                                ColorBlindPreference.save(this@MainActivity, enabled)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ScoreApp(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    colorBlindMode: Boolean = false,
    onColorBlindModeChange: (Boolean) -> Unit = {}
) {
    val navController = rememberNavController()
    // Le ViewModel est créé ici, au niveau du graphe de navigation,
    // afin d'être partagé entre tous les écrans.
    val viewModel: ScoreViewModel = viewModel()
    // Vit au même niveau que ScoreViewModel pour continuer de tourner sur tous les écrans.
    // Le minuteur (vibration, son, notification, alarme) est géré par TimerViewModel lui-même.
    val timerViewModel: TimerViewModel = viewModel()
    val tournamentViewModel: TournamentViewModel = viewModel()
    val mastermindViewModel: MastermindViewModel = viewModel()
    val battleshipViewModel: BattleshipViewModel = viewModel()
    // Partage de la partie avec un autre téléphone : vit au même niveau pour garder la connexion sur tous les écrans.
    val syncViewModel: SyncViewModel = viewModel()
    val sync = syncViewModel.session
    val mastermindOnline = syncViewModel.mastermind
    val ticTacToeOnline = syncViewModel.ticTacToe
    val connect4Online = syncViewModel.connect4
    val battleshipOnline = syncViewModel.battleship
    val nimOnline = syncViewModel.nim
    val rockPaperScissorsOnline = syncViewModel.rockPaperScissors
    val gomokuOnline = syncViewModel.gomoku
    val dotsOnline = syncViewModel.dots
    val draughtsOnline = syncViewModel.draughts
    val othelloOnline = syncViewModel.othello
    SideEffect { sync.attach(viewModel) }

    val context = LocalContext.current
    val gameRepository = remember { GameRepository(context) }
    val gameHistoryRepository = remember { GameHistoryRepository(context) }
    // La partie est sauvegardée dans le journal peu après chaque modification, pas seulement en quittant l'appli.
    AutoSaveEffect(viewModel, gameHistoryRepository)

    // Quitter une partie qui contient des scores demande confirmation.
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val onScoreRoute = currentRoute == "score" || currentRoute == "counter" || currentRoute == "teamRounds" ||
        currentRoute == "poker" || currentRoute == "racket"
    var showQuitDialog by remember { mutableStateOf(false) }

    // Partie en cours la plus récente du journal, proposée sur l'accueil ; relue à chaque retour sur l'accueil.
    val unfinishedGame = remember(currentRoute) {
        if (currentRoute == "setup") {
            gameHistoryRepository.listGames().filter { !it.isFinished }.maxByOrNull { it.savedAt }
        } else {
            null
        }
    }

    fun quitGame(save: Boolean) {
        // Enregistrer : état actuel. Ne pas enregistrer : on défait l'auto-sauvegarde faite par onStop.
        if (save) viewModel.saveToJournal(gameHistoryRepository) else viewModel.discardToJournal(gameHistoryRepository)
        viewModel.closeGame()
        sync.disconnect()
        showQuitDialog = false
        if (!navController.popBackStack("setup", inclusive = false)) {
            navController.popBackStack("home", inclusive = false)
        }
    }

    // Noms saisis à l'étape 1, en attente d'être associés à un jeu à l'étape 2.
    // rememberSaveable : sinon une rotation de l'écran vide la liste alors que la navigation reste
    // sur « choisir le jeu » (message « Tu as saisi 0 joueur »).
    var pendingPlayerNames by rememberSaveable(stateSaver = StringListSaver) { mutableStateOf(listOf<String>()) }
    // Couleurs choisies à l'étape 1 (ARGB ; 0 = pas de choix, tirée au sort au lancement de la partie).
    var pendingPlayerColors by rememberSaveable(
        stateSaver = listSaver<List<Int>, Int>(save = { it }, restore = { it })
    ) { mutableStateOf(listOf<Int>()) }
    fun pendingColors(): List<Color?> = pendingPlayerColors.map { if (it == 0) null else Color(it) }

    // Accueil : false = feuilles de scores seules, true = jeux jouables dans l'appli (dés, Mastermind).
    var playMode by rememberSaveable { mutableStateOf(false) }
    // Le bouton « Dés » n'existe que côté jeux jouables ; un invité qui rejoint une partie le garde (les dés se partagent).
    val openDice: (() -> Unit)? =
        if (playMode || sync.role == SyncRole.GUEST) { { navController.navigate("dice") } } else null

    // Route vers l'écran de score adapté au mode du jeu choisi.
    fun scoreRouteFor(rules: GameRules): String = when (rules.scoreMode) {
        ScoreMode.TABLE -> "score"
        ScoreMode.COUNTER -> "counter"
        ScoreMode.VARIABLE_TEAMS -> "teamRounds"
        ScoreMode.POKER -> "poker"
        ScoreMode.RACKET -> "racket"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Fond commun à tous les écrans, décodé une seule fois.
        AppBackground()

        NavHost(navController = navController, startDestination = "home") {
            composable("home") {
                HomeScreen(
                    onOpenScores = {
                        playMode = false
                        navController.navigate("setup")
                    },
                    onOpenPlayable = {
                        playMode = true
                        navController.navigate("setup")
                    },
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    colorBlindMode = colorBlindMode,
                    onColorBlindModeChange = onColorBlindModeChange
                )
            }
            composable("setup") {
                SetupScreen(
                    onNext = { names, colors ->
                        pendingPlayerNames = names
                        pendingPlayerColors = colors.map { it?.toArgb() ?: 0 }
                        navController.navigate("chooseGame")
                    },
                    onOpenJournal = {
                        navController.navigate("journal")
                    },
                    onOpenStats = {
                        navController.navigate("stats")
                    },
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    colorBlindMode = colorBlindMode,
                    onColorBlindModeChange = onColorBlindModeChange,
                    unfinishedGame = unfinishedGame,
                    onResumeGame = {
                        unfinishedGame?.let { saved ->
                            viewModel.loadFromSaved(saved)
                            navController.navigate(scoreRouteFor(saved.gameRules)) { popUpTo("setup") }
                        }
                    },
                    onOpenShare = { navController.navigate("share") },
                    playableMode = playMode,
                    onOpenTournament = {
                        // Un championnat sauvegardé se reprend directement ; sinon on en prépare un.
                        navController.navigate(
                            if (tournamentViewModel.hasTournament) "tournamentBracket" else "tournamentSetup"
                        )
                    }
                )
            }
            composable("tournamentSetup") {
                TournamentSetupScreen(
                    onNext = { names, format, ordered ->
                        tournamentViewModel.startTournament(names, format, ordered)
                        navController.navigate("tournamentBracket") { popUpTo("setup") }
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("tournamentBracket") {
                TournamentBracketScreen(
                    viewModel = tournamentViewModel,
                    onBack = { navController.popBackStack("setup", inclusive = false) },
                    onNewTournament = { navController.navigate("tournamentSetup") }
                )
            }
            composable("stats") {
                StatsScreen(
                    repository = gameHistoryRepository,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("journal") {
                GameHistoryScreen(
                    repository = gameHistoryRepository,
                    onResumeGame = { saved ->
                        viewModel.loadFromSaved(saved)
                        navController.navigate(scoreRouteFor(saved.gameRules)) {
                            popUpTo("setup")
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("chooseGame") {
                ChooseGameScreen(
                    repository = gameRepository,
                    playerCount = pendingPlayerNames.size,
                    onGameChosen = { rules ->
                        if (rules.id == MASTERMIND_ID) {
                            // Le Mastermind a son propre écran et n'utilise ni le tableau ni les compteurs.
                            mastermindViewModel.open(rules, pendingPlayerNames)
                            navController.navigate("mastermind")
                        } else if (rules.id == MORPION_ID) {
                            // Le Morpion a son propre écran : deux joueurs, sur un téléphone ou chacun sur le sien.
                            navController.navigate("morpion")
                        } else if (rules.id == GOMOKU_ID) {
                            // Le Gomoku a son propre écran : deux joueurs, sur un téléphone ou chacun sur le sien.
                            navController.navigate("gomoku")
                        } else if (rules.id == DOTS_ID) {
                            // La Pipopipette aussi.
                            navController.navigate("dots")
                        } else if (rules.id == BATTLESHIP_ID) {
                            // Le touché coulé a lui aussi son propre écran : deux joueurs, sur un téléphone ou chacun sur le sien.
                            battleshipViewModel.open(rules, pendingPlayerNames)
                            navController.navigate("battleship")
                        } else if (rules.id == CONNECT4_ID) {
                            // Le Puissance 4 a son propre écran : deux joueurs, sur un téléphone ou chacun sur le sien.
                            navController.navigate("connect4")
                        } else if (rules.id == PFC_ID) {
                            // Pierre-feuille-ciseaux a son propre écran : deux joueurs, sur un téléphone ou chacun sur le sien.
                            navController.navigate("pfc")
                        } else if (rules.id == NIM_ID) {
                            // Le jeu des allumettes a aussi son écran : deux joueurs, sur un téléphone ou chacun sur le sien.
                            navController.navigate("nim")
                        } else if (rules.id == DRAUGHTS_ID) {
                            // Les dames ont leur propre écran : deux joueurs, sur un téléphone ou chacun sur le sien.
                            navController.navigate("draughts")
                        } else if (rules.id == OTHELLO_ID) {
                            // L'Othello aussi : deux joueurs, sur un téléphone ou chacun sur le sien.
                            navController.navigate("othello")
                        } else if (rules.scoreMode == ScoreMode.POKER) {
                            // Le poker se règle d'abord (tapis, blinds, antes…) avant de lancer la partie.
                            navController.navigate("pokerSetup")
                        } else {
                            viewModel.initGame(pendingPlayerNames, rules, pendingColors())
                            navController.navigate(scoreRouteFor(rules))
                        }
                    },
                    onCreateNewGame = {
                        navController.navigate("createGame")
                    },
                    onEditGame = { rules ->
                        navController.navigate("createGame?editId=${Uri.encode(rules.id)}")
                    },
                    playableOnly = playMode
                )
            }
            // editId absent : création d'un jeu ; présent : modification du jeu personnalisé correspondant.
            composable(
                route = "createGame?editId={editId}",
                arguments = listOf(
                    navArgument("editId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { entry ->
                val editId = entry.arguments?.getString("editId")
                val editing = remember(editId) {
                    editId?.let { id -> gameRepository.customGames().firstOrNull { it.id == id } }
                }
                CreateGameScreen(
                    repository = gameRepository,
                    editing = editing,
                    onGameCreated = { rules ->
                        viewModel.initGame(pendingPlayerNames, rules, pendingColors())
                        navController.navigate(scoreRouteFor(rules)) {
                            popUpTo("chooseGame") { inclusive = true }
                        }
                    },
                    // Retour à la liste des jeux : ChooseGameScreen relit alors les jeux à jour.
                    onGameUpdated = { navController.popBackStack() }
                )
            }
            composable("pokerSetup") {
                val pokerRules = remember { gameRepository.allGames().firstOrNull { it.scoreMode == ScoreMode.POKER } }
                PokerSetupScreen(
                    playerNames = pendingPlayerNames,
                    onStart = { settings ->
                        pokerRules?.let { rules ->
                            viewModel.initGame(pendingPlayerNames, rules, pendingColors())
                            viewModel.startPoker(settings)
                            navController.navigate("poker") { popUpTo("pokerSetup") { inclusive = true } }
                        }
                    }
                )
            }
            composable("battleship") {
                BattleshipScreen(
                    viewModel = battleshipViewModel,
                    online = battleshipOnline,
                    onExit = {
                        // Retour à la saisie des joueurs (ou à l'accueil pour un invité qui n'est pas passé par là).
                        if (!navController.popBackStack("setup", inclusive = false)) {
                            navController.popBackStack("home", inclusive = false)
                        }
                    },
                    onHostOnline = { navController.navigate("shareBattleship") }
                )
            }
            composable("shareBattleship") {
                ShareScreen(
                    game = viewModel,
                    session = sync,
                    onBack = { navController.popBackStack() },
                    kind = ShareKind.BATTLESHIP,
                    battleship = battleshipOnline,
                    battleshipSettings = battleshipViewModel.settings,
                    // La salle d'attente du touché coulé est l'écran du jeu, juste derrière.
                    onHostedBattleship = { navController.popBackStack() }
                )
            }
            composable("morpion") {
                TicTacToeScreen(
                    players = pendingPlayerNames,
                    online = ticTacToeOnline,
                    onExit = {
                        // Retour à la saisie des joueurs (ou à l'accueil pour un invité qui n'est pas passé par là).
                        if (!navController.popBackStack("setup", inclusive = false)) {
                            navController.popBackStack("home", inclusive = false)
                        }
                    },
                    onHostOnline = { navController.navigate("shareMorpion") }
                )
            }
            composable("shareMorpion") {
                ShareScreen(
                    game = viewModel,
                    session = sync,
                    onBack = { navController.popBackStack() },
                    kind = ShareKind.MORPION,
                    ticTacToe = ticTacToeOnline,
                    // La table du Morpion est l'écran du jeu, juste derrière.
                    onHostedTicTacToe = { navController.popBackStack() }
                )
            }
            composable("connect4") {
                Connect4Screen(
                    players = pendingPlayerNames,
                    online = connect4Online,
                    onExit = {
                        // Retour à la saisie des joueurs (ou à l'accueil pour un invité qui n'est pas passé par là).
                        if (!navController.popBackStack("setup", inclusive = false)) {
                            navController.popBackStack("home", inclusive = false)
                        }
                    },
                    onHostOnline = { navController.navigate("shareConnect4") }
                )
            }
            composable("shareConnect4") {
                ShareScreen(
                    game = viewModel,
                    session = sync,
                    onBack = { navController.popBackStack() },
                    kind = ShareKind.CONNECT4,
                    connect4 = connect4Online,
                    // La table du Puissance 4 est l'écran du jeu, juste derrière.
                    onHostedConnect4 = { navController.popBackStack() }
                )
            }
            composable("nim") {
                NimScreen(
                    players = pendingPlayerNames,
                    online = nimOnline,
                    onExit = {
                        // Retour à la saisie des joueurs (ou à l'accueil pour un invité qui n'est pas passé par là).
                        if (!navController.popBackStack("setup", inclusive = false)) {
                            navController.popBackStack("home", inclusive = false)
                        }
                    },
                    onHostOnline = { navController.navigate("shareNim") }
                )
            }
            composable("shareNim") {
                ShareScreen(
                    game = viewModel,
                    session = sync,
                    onBack = { navController.popBackStack() },
                    kind = ShareKind.NIM,
                    nim = nimOnline,
                    // La table du jeu des allumettes est l'écran du jeu, juste derrière.
                    onHostedNim = { navController.popBackStack() }
                )
            }
            composable("pfc") {
                RockPaperScissorsScreen(
                    players = pendingPlayerNames,
                    online = rockPaperScissorsOnline,
                    onExit = {
                        // Retour à la saisie des joueurs (ou à l'accueil pour un invité qui n'est pas passé par là).
                        if (!navController.popBackStack("setup", inclusive = false)) {
                            navController.popBackStack("home", inclusive = false)
                        }
                    },
                    onHostOnline = { navController.navigate("sharePfc") }
                )
            }
            composable("sharePfc") {
                ShareScreen(
                    game = viewModel,
                    session = sync,
                    onBack = { navController.popBackStack() },
                    kind = ShareKind.PFC,
                    rockPaperScissors = rockPaperScissorsOnline,
                    // La table est l'écran du jeu, juste derrière.
                    onHostedRockPaperScissors = { navController.popBackStack() }
                )
            }
            composable("gomoku") {
                DuoGameScreen(
                    spec = GomokuSpec,
                    players = pendingPlayerNames,
                    online = gomokuOnline,
                    onExit = {
                        // Retour à la saisie des joueurs (ou à l'accueil pour un invité qui n'est pas passé par là).
                        if (!navController.popBackStack("setup", inclusive = false)) {
                            navController.popBackStack("home", inclusive = false)
                        }
                    },
                    onHostOnline = { navController.navigate("shareGomoku") }
                )
            }
            composable("shareGomoku") {
                ShareScreen(
                    game = viewModel,
                    session = sync,
                    onBack = { navController.popBackStack() },
                    kind = ShareKind.GOMOKU,
                    duo = gomokuOnline,
                    // La table du Gomoku est l'écran du jeu, juste derrière.
                    onHostedDuo = { navController.popBackStack() }
                )
            }
            composable("dots") {
                DuoGameScreen(
                    spec = DotsSpec,
                    players = pendingPlayerNames,
                    online = dotsOnline,
                    onExit = {
                        if (!navController.popBackStack("setup", inclusive = false)) {
                            navController.popBackStack("home", inclusive = false)
                        }
                    },
                    onHostOnline = { navController.navigate("shareDots") }
                )
            }
            composable("shareDots") {
                ShareScreen(
                    game = viewModel,
                    session = sync,
                    onBack = { navController.popBackStack() },
                    kind = ShareKind.DOTS,
                    duo = dotsOnline,
                    // La table de la Pipopipette est l'écran du jeu, juste derrière.
                    onHostedDuo = { navController.popBackStack() }
                )
            }
            composable("draughts") {
                DraughtsScreen(
                    players = pendingPlayerNames,
                    online = draughtsOnline,
                    onExit = {
                        // Retour à la saisie des joueurs (ou à l'accueil pour un invité qui n'est pas passé par là).
                        if (!navController.popBackStack("setup", inclusive = false)) {
                            navController.popBackStack("home", inclusive = false)
                        }
                    },
                    onHostOnline = { navController.navigate("shareDraughts") }
                )
            }
            composable("shareDraughts") {
                ShareScreen(
                    game = viewModel,
                    session = sync,
                    onBack = { navController.popBackStack() },
                    kind = ShareKind.DRAUGHTS,
                    draughts = draughtsOnline,
                    // La table des dames est l'écran du jeu, juste derrière.
                    onHostedDraughts = { navController.popBackStack() }
                )
            }
            composable("othello") {
                OthelloScreen(
                    players = pendingPlayerNames,
                    online = othelloOnline,
                    onExit = {
                        // Retour à la saisie des joueurs (ou à l'accueil pour un invité qui n'est pas passé par là).
                        if (!navController.popBackStack("setup", inclusive = false)) {
                            navController.popBackStack("home", inclusive = false)
                        }
                    },
                    onHostOnline = { navController.navigate("shareOthello") }
                )
            }
            composable("shareOthello") {
                ShareScreen(
                    game = viewModel,
                    session = sync,
                    onBack = { navController.popBackStack() },
                    kind = ShareKind.OTHELLO,
                    othello = othelloOnline,
                    // La table de l'Othello est l'écran du jeu, juste derrière.
                    onHostedOthello = { navController.popBackStack() }
                )
            }
            composable("poker") {
                PokerScreen(viewModel = viewModel, historyRepository = gameHistoryRepository)
            }
            composable("score") {
                ScoreScreen(
                    viewModel = viewModel,
                    historyRepository = gameHistoryRepository,
                    onOpenDice = openDice,
                    onOpenShare = { navController.navigate("share") },
                    shareActive = sync.isConnected
                )
            }
            composable("share") {
                ShareScreen(game = viewModel, session = sync, onBack = { navController.popBackStack() })
            }
            composable("mastermind") {
                MastermindScreen(
                    viewModel = mastermindViewModel,
                    online = mastermindOnline,
                    onExit = {
                        // Retour à la saisie des joueurs (ou à l'accueil pour un invité qui n'est pas passé par là).
                        if (!navController.popBackStack("setup", inclusive = false)) {
                            navController.popBackStack("home", inclusive = false)
                        }
                    },
                    onHostOnline = { navController.navigate("shareMastermind") }
                )
            }
            composable("shareMastermind") {
                ShareScreen(
                    game = viewModel,
                    session = sync,
                    onBack = { navController.popBackStack() },
                    kind = ShareKind.MASTERMIND,
                    mastermind = mastermindOnline,
                    mastermindSettings = mastermindViewModel.settings,
                    // La salle d'attente du Mastermind est l'écran du jeu, juste derrière.
                    onHostedMastermind = { navController.popBackStack() }
                )
            }
            composable("dice") {
                DiceScreen(viewModel = viewModel, session = sync, onBack = { navController.popBackStack() })
            }
            composable("racket") {
                RacketScreen(viewModel = viewModel, historyRepository = gameHistoryRepository)
            }
            composable("counter") {
                CounterScreen(
                    viewModel = viewModel,
                    historyRepository = gameHistoryRepository,
                    onOpenDice = openDice,
                    onOpenShare = { navController.navigate("share") },
                    shareActive = sync.isConnected
                )
            }
            composable("teamRounds") {
                TeamRoundsScreen(
                    viewModel = viewModel,
                    historyRepository = gameHistoryRepository,
                    onAddRound = { navController.navigate("newTeamRound") },
                    onOpenShare = { navController.navigate("share") },
                    shareActive = sync.isConnected
                )
            }
            composable("newTeamRound") {
                NewTeamRoundScreen(
                    viewModel = viewModel,
                    onDone = { navController.popBackStack() }
                )
            }
        }

        // Doit être déclaré APRÈS le NavHost : le dernier BackHandler enregistré est prioritaire,
        // sinon celui du NavHost quitte l'écran sans jamais afficher la confirmation.
        BackHandler(enabled = onScoreRoute && viewModel.hasProgress()) { showQuitDialog = true }

        // Envoie la partie aux téléphones connectés à chaque changement, et ouvre la partie reçue (invité).
        SyncEffects(
            game = viewModel,
            session = sync,
            navController = navController,
            routeFor = ::scoreRouteFor,
            mastermind = mastermindOnline,
            onMastermindJoined = {
                gameRepository.builtInGames().firstOrNull { it.id == MASTERMIND_ID }?.let { mastermindViewModel.prepareOnline(it) }
                navController.navigate("mastermind") { popUpTo("home") }
            },
            ticTacToe = ticTacToeOnline,
            onTicTacToeJoined = { navController.navigate("morpion") { popUpTo("home") } },
            connect4 = connect4Online,
            onConnect4Joined = { navController.navigate("connect4") { popUpTo("home") } },
            battleship = battleshipOnline,
            onBattleshipJoined = {
                gameRepository.builtInGames().firstOrNull { it.id == BATTLESHIP_ID }?.let { battleshipViewModel.prepareOnline(it) }
                navController.navigate("battleship") { popUpTo("home") }
            },
            nim = nimOnline,
            onNimJoined = { navController.navigate("nim") { popUpTo("home") } },
            rockPaperScissors = rockPaperScissorsOnline,
            onRockPaperScissorsJoined = { navController.navigate("pfc") { popUpTo("home") } },
            gomoku = gomokuOnline,
            onGomokuJoined = { navController.navigate("gomoku") { popUpTo("home") } },
            dots = dotsOnline,
            onDotsJoined = { navController.navigate("dots") { popUpTo("home") } },
            draughts = draughtsOnline,
            onDraughtsJoined = { navController.navigate("draughts") { popUpTo("home") } },
            othello = othelloOnline,
            onOthelloJoined = { navController.navigate("othello") { popUpTo("home") } }
        )
        // Demande de connexion d'un autre téléphone : visible sur n'importe quel écran.
        SyncPendingDialog(sync)

        // Superposé à toutes les pages ci-dessus, sauf le poker qui a son propre minuteur de blinds.
        if (currentRoute != "poker") TimerOverlay(viewModel = timerViewModel)

        if (showQuitDialog) {
            AlertDialog(
                onDismissRequest = { showQuitDialog = false },
                title = { Text(stringResource(R.string.quit_title)) },
                text = {
                    Column {
                        Text(stringResource(R.string.quit_message))
                        TextButton(onClick = { quitGame(save = false) }) {
                            Text(stringResource(R.string.quit_without_saving), color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { quitGame(save = true) }) { Text(stringResource(R.string.quit_save_and_quit)) }
                },
                dismissButton = {
                    TextButton(onClick = { showQuitDialog = false }) { Text(stringResource(R.string.quit_continue)) }
                }
            )
        }
    }
}
