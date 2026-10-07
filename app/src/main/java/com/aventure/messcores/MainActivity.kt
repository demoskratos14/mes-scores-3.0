package com.aventure.messcores

import androidx.compose.ui.res.stringResource
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

class MainActivity : ComponentActivity() {

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
        if (scoreViewModel.hasProgress()) {
            GameHistoryRepository(this).saveGame(scoreViewModel.snapshot())
        }
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
            MesScoresTheme(mode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ScoreApp(
                        themeMode = themeMode,
                        onThemeModeChange = { mode ->
                            themeMode = mode
                            ThemePreference.save(this@MainActivity, mode)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ScoreApp(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {}
) {
    val navController = rememberNavController()
    // Le ViewModel est créé ici, au niveau du graphe de navigation,
    // afin d'être partagé entre tous les écrans.
    val viewModel: ScoreViewModel = viewModel()
    // Vit au même niveau que ScoreViewModel pour continuer de tourner sur tous les écrans.
    // Le minuteur (vibration, son, notification, alarme) est géré par TimerViewModel lui-même.
    val timerViewModel: TimerViewModel = viewModel()
    val tournamentViewModel: TournamentViewModel = viewModel()

    val context = LocalContext.current
    val gameRepository = remember { GameRepository(context) }
    val gameHistoryRepository = remember { GameHistoryRepository(context) }

    // Quitter une partie qui contient des scores demande confirmation.
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val onScoreRoute = currentRoute == "score" || currentRoute == "counter" || currentRoute == "teamRounds"
    var showQuitDialog by remember { mutableStateOf(false) }

    fun quitGame(save: Boolean) {
        // Enregistrer : état actuel. Ne pas enregistrer : on défait l'auto-sauvegarde faite par onStop.
        if (save) viewModel.saveToJournal(gameHistoryRepository) else viewModel.discardToJournal(gameHistoryRepository)
        viewModel.closeGame()
        showQuitDialog = false
        navController.popBackStack("setup", inclusive = false)
    }

    // Noms saisis à l'étape 1, en attente d'être associés à un jeu à l'étape 2.
    // rememberSaveable : sinon une rotation de l'écran vide la liste alors que la navigation reste
    // sur « choisir le jeu » (message « Tu as saisi 0 joueur »).
    var pendingPlayerNames by rememberSaveable(stateSaver = StringListSaver) { mutableStateOf(listOf<String>()) }

    // Route vers l'écran de score adapté au mode du jeu choisi.
    fun scoreRouteFor(rules: GameRules): String = when (rules.scoreMode) {
        ScoreMode.TABLE -> "score"
        ScoreMode.COUNTER -> "counter"
        ScoreMode.VARIABLE_TEAMS -> "teamRounds"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Fond commun à tous les écrans, décodé une seule fois.
        AppBackground()

        NavHost(navController = navController, startDestination = "setup") {
            composable("setup") {
                SetupScreen(
                    onNext = { names ->
                        pendingPlayerNames = names
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
                        viewModel.initGame(pendingPlayerNames, rules)
                        navController.navigate(scoreRouteFor(rules))
                    },
                    onCreateNewGame = {
                        navController.navigate("createGame")
                    },
                    onEditGame = { rules ->
                        navController.navigate("createGame?editId=${Uri.encode(rules.id)}")
                    }
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
                        viewModel.initGame(pendingPlayerNames, rules)
                        navController.navigate(scoreRouteFor(rules)) {
                            popUpTo("chooseGame") { inclusive = true }
                        }
                    },
                    // Retour à la liste des jeux : ChooseGameScreen relit alors les jeux à jour.
                    onGameUpdated = { navController.popBackStack() }
                )
            }
            composable("score") {
                ScoreScreen(viewModel = viewModel, historyRepository = gameHistoryRepository)
            }
            composable("counter") {
                CounterScreen(viewModel = viewModel, historyRepository = gameHistoryRepository)
            }
            composable("teamRounds") {
                TeamRoundsScreen(
                    viewModel = viewModel,
                    historyRepository = gameHistoryRepository,
                    onAddRound = { navController.navigate("newTeamRound") }
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

        // Superposé à toutes les pages ci-dessus.
        TimerOverlay(viewModel = timerViewModel)

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
