package com.aventure.messcores

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Cartes par ligne dans la table. */
private const val TILES_PER_ROW = 3

/** Durée du débat avant que le narrateur lance le vote, en secondes. */
private const val DEBATE_SECONDS = 180

/** Durée du débat de la partie test, en secondes : juste de quoi voir qu'il a lieu. */
private const val TEST_DEBATE_SECONDS = 8

/** Les joueurs simulés de la partie test jouent toutes les BOT_TICKS pas de 250 ms. */
private const val BOT_TICKS = 5

/** Pour un rôle qui est dans le jeu mais ne peut plus jouer, le narrateur fait semblant d'attendre entre ces durées (ms). */
private const val DECOY_MIN_MS = 4_000L
private const val DECOY_MAX_MS = 8_000L

/** Vibration légère qui prévient un joueur (ms). */
private const val ALERT_MS = 200L

/**
 * Le Loup-garou de Thiercelieux, un téléphone par joueur (voir [WerewolfOnline]) : chacun joue sur son téléphone (voir [PlayerBody]) :
 * sa carte secrète, ses choix de la nuit, le vote du jour en touchant la carte du joueur visé. Le téléphone de l'organisateur est
 * en plus le narrateur : il lit la partie à voix haute et la mène (voir [NarratorControls]).
 */
@Composable
internal fun WerewolfScreen(online: WerewolfOnline, onExit: () -> Unit) {
    var showQuit by rememberSaveable { mutableStateOf(false) }
    var showRoles by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = online.active) {
        if (online.notice == WerewolfNotice.HOST_LEFT) {
            online.leave()
            onExit()
        } else {
            showQuit = true
        }
    }
    KeepScreenOn()

    val view = online.view
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.game_werewolf), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = { showRoles = true }) { Text(stringResource(R.string.ww_roles_button)) }
        }
        when {
            !online.active -> Text(stringResource(R.string.ww_connecting))
            online.notice == WerewolfNotice.HOST_LEFT -> {
                Text(stringResource(R.string.ww_notice_host_left))
                Button(onClick = {
                    online.leave()
                    onExit()
                }) { Text(stringResource(R.string.ww_quit)) }
            }
            online.isHost && !online.started -> HostLobby(online)
            view == null -> GuestLobby(online)
            else -> {
                // L'organisateur est aussi le narrateur : ses commandes passent avant son écran de joueur.
                if (online.isHost) NarratorControls(online)
                PlayerBody(view, online)
            }
        }
        if (online.active && online.notice != WerewolfNotice.HOST_LEFT) {
            TextButton(onClick = { showQuit = true }) { Text(stringResource(R.string.ww_quit), color = MaterialTheme.colorScheme.error) }
        }
    }

    if (showRoles) RolesDialog(onClose = { showRoles = false })
    if (showQuit && online.active) {
        AlertDialog(
            onDismissRequest = { showQuit = false },
            title = { Text(stringResource(R.string.ww_quit_title)) },
            text = { Text(stringResource(R.string.ww_quit_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showQuit = false
                    online.leave()
                    onExit()
                }) { Text(stringResource(R.string.mm_quit_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showQuit = false }) { Text(stringResource(R.string.quit_continue)) } }
        )
    }
}

// ---------- Salle d'attente ----------

@Composable
private fun HostLobby(online: WerewolfOnline) {
    val botNames = (1 until WEREWOLF_TEST_PLAYERS).map { stringResource(R.string.ww_bot_name, it) }
    Text(stringResource(R.string.ww_host_intro))
    PlayersList(online.lobby)
    Text(stringResource(R.string.ww_players_count, online.lobby.size, WEREWOLF_MAX_PLAYERS), fontWeight = FontWeight.Bold)
    if (online.lobby.size < WEREWOLF_MIN_PLAYERS) {
        Text(stringResource(R.string.ww_need_players, WEREWOLF_MIN_PLAYERS), color = MaterialTheme.colorScheme.error)
    }
    CardsSetup(online)
    Button(onClick = { online.start() }, enabled = online.ready, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.ww_start))
    }
    if (online.canTest) TestCard(online, botNames)
}

/** Le choix des cartes de la partie : réglées d'après le nombre de joueurs, l'organisateur peut en ajouter ou en retirer. */
@Composable
private fun CardsSetup(online: WerewolfOnline) {
    Text(stringResource(R.string.ww_config_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { online.changeWolves(-1) }, enabled = online.wolves > 1) { Text("−") }
        Text(stringResource(R.string.ww_config_wolves, online.wolves), modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        OutlinedButton(onClick = { online.changeWolves(1) }) { Text("+") }
    }
    WEREWOLF_SPECIALS.forEach { role ->
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(role.nameRes), modifier = Modifier.weight(1f))
            Switch(checked = role in online.specials, onCheckedChange = { online.toggleSpecial(role) })
        }
    }
    Text(stringResource(R.string.ww_config_villagers, online.villagers.coerceAtLeast(0)))
    if (online.lobby.size >= WEREWOLF_MIN_PLAYERS && !online.validCards) {
        Text(stringResource(R.string.ww_config_too_many, online.lobby.size), color = MaterialTheme.colorScheme.error)
    }
    if (online.customCards) {
        OutlinedButton(onClick = { online.resetCards() }) { Text(stringResource(R.string.ww_config_reset)) }
    }
}

/** La partie test : seul, on déroule une nuit et un jour avec des joueurs simulés, pour vérifier que tout se passe bien. */
@Composable
private fun TestCard(online: WerewolfOnline, botNames: List<String>) {
    val resources = LocalResources.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.ww_test_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.ww_test_hint), style = MaterialTheme.typography.bodySmall)
            val role = online.testRole
            OutlinedButton(onClick = { online.cycleTestRole() }) {
                Text(stringResource(R.string.ww_test_role, if (role == null) stringResource(R.string.ww_test_role_random) else stringResource(role.nameRes)))
            }
            Button(onClick = { online.startTest(botNames) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ww_test_start)) }
            // Le test complet : toutes les cartes, et ce téléphone joue chaque place à tour de rôle.
            Text(stringResource(R.string.ww_test_all_hint), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(
                onClick = {
                    online.startFullTest { place, role ->
                        resources.getString(R.string.ww_test_all_name, place + 1, resources.getString(role.nameRes))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.ww_test_all_start)) }
        }
    }
}

@Composable
private fun GuestLobby(online: WerewolfOnline) {
    Text(stringResource(R.string.ww_guest_lobby))
    PlayersList(online.lobby)
}

@Composable
private fun PlayersList(names: List<String>) {
    names.forEachIndexed { index, name ->
        Text("${index + 1}. $name", style = MaterialTheme.typography.bodyLarge)
    }
}

// ---------- Les cartes ----------

/** La liste de toutes les cartes du jeu, avec leur rôle expliqué. */
@Composable
private fun RolesDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.ww_roles_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WerewolfRole.entries.forEach { role ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Image(
                            painter = painterResource(role.image),
                            contentDescription = null,
                            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(role.nameRes), fontWeight = FontWeight.Bold)
                            Text(stringResource(role.descRes), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.ww_close)) } }
    )
}

/** Une carte qui se retourne : le dos tant que [role] est null, sa face dès qu'elle est connue. */
@Composable
private fun FlipCard(role: WerewolfRole?, modifier: Modifier = Modifier) {
    var last by remember { mutableStateOf(role) }
    SideEffect { if (role != null) last = role }
    val rotation by animateFloatAsState(if (role != null) 180f else 0f, tween(600), label = "werewolfFlip")
    val density = LocalDensity.current.density
    Box(modifier = modifier.graphicsLayer {
        rotationY = rotation
        cameraDistance = 12f * density
    }) {
        val face = role ?: last
        if (rotation <= 90f || face == null) {
            Image(
                painter = painterResource(WEREWOLF_BACK),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Image(
                painter = painterResource(face.image),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }.clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
        }
    }
}

/**
 * Les cartes des joueurs, [TILES_PER_ROW] par ligne : le dos, ou la face dès que la carte est retournée. [selectable] : les joueurs
 * qu'on peut toucher ; [selected] : ceux qui sont choisis ; [badges] : un petit texte sous chaque nom.
 */
@Composable
private fun PlayerTiles(
    names: List<String>,
    alive: List<Boolean>,
    shown: List<WerewolfRole?>,
    me: Int,
    badges: List<String>,
    selectable: Set<Int>,
    selected: List<Int>,
    onToggle: (Int) -> Unit
) {
    names.indices.chunked(TILES_PER_ROW).forEach { row ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { seat ->
                val role = shown.getOrNull(seat)
                val enabled = seat in selectable
                val isAlive = alive.getOrElse(seat) { true }
                val description = if (role != null) "${names[seat]}, ${stringResource(role.nameRes)}" else names[seat]
                val shape = RoundedCornerShape(12.dp)
                val faded = !isAlive || (selectable.isNotEmpty() && !enabled)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .alpha(if (faded) 0.5f else 1f)
                        .border(if (seat in selected) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, shape)
                        .clip(shape)
                        .clickable(enabled = enabled) { onToggle(seat) }
                        .padding(4.dp)
                        .semantics { contentDescription = description },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    FlipCard(role = role, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                    Text(
                        names[seat],
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (seat == me) FontWeight.Bold else FontWeight.Normal
                    )
                    val badge = badges.getOrElse(seat) { "" }
                    if (badge.isNotEmpty()) Text(badge, style = MaterialTheme.typography.labelSmall)
                }
            }
            repeat(TILES_PER_ROW - row.size) { Spacer(modifier = Modifier.weight(1f)) }
        }
    }
}

// ---------- Le narrateur ----------

/** Ce que le narrateur lit avant d'attendre les joueurs, et après. */
private class Script(val intro: List<String>, val outro: List<String>)

private fun deathLines(context: Context, round: WerewolfRound, seats: List<Int>): List<String> =
    seats.map { context.getString(R.string.ww_say_death, round.names[it], context.getString(round.roleAt(it).nameRes)) }

/** Le texte de l'étape en cours de [round]. */
private fun werewolfScript(context: Context, round: WerewolfRound): Script {
    fun nightScript(wake: Int, sleep: Int) = Script(listOf(context.getString(wake)), listOf(context.getString(sleep)))
    // Les cartes sans texte à elles : on les appelle par leur nom.
    fun genericScript(extraOutro: List<String> = emptyList()): Script {
        val name = round.step.nightRole()?.let { context.getString(it.nameRes) } ?: ""
        return Script(listOf(context.getString(R.string.ww_say_wake, name)), listOf(context.getString(R.string.ww_say_sleep, name)) + extraOutro)
    }
    return when (round.step) {
        WerewolfStep.REVEAL -> {
            val lines = mutableListOf(context.getString(R.string.ww_say_reveal))
            if (round.groups.isNotEmpty()) lines.add(context.getString(R.string.ww_say_groups))
            Script(lines, emptyList())
        }
        WerewolfStep.NIGHT_START -> Script(listOf(context.getString(R.string.ww_say_night_start)), emptyList())
        WerewolfStep.CUPID -> nightScript(R.string.ww_say_cupid_wake, R.string.ww_say_cupid_sleep)
        WerewolfStep.SAVIOR -> nightScript(R.string.ww_say_savior_wake, R.string.ww_say_savior_sleep)
        WerewolfStep.SEER -> nightScript(R.string.ww_say_seer_wake, R.string.ww_say_seer_sleep)
        WerewolfStep.WOLVES -> nightScript(R.string.ww_say_wolves_wake, R.string.ww_say_wolves_sleep)
        WerewolfStep.WITCH -> nightScript(R.string.ww_say_witch_wake, R.string.ww_say_witch_sleep)
        WerewolfStep.RAVEN -> nightScript(R.string.ww_say_raven_wake, R.string.ww_say_raven_sleep)
        WerewolfStep.PIPER -> genericScript(listOf(context.getString(R.string.ww_say_piper_charmed)))
        WerewolfStep.THIEF, WerewolfStep.SISTERS, WerewolfStep.BROTHERS, WerewolfStep.DOG_WOLF, WerewolfStep.WILD_CHILD,
        WerewolfStep.COMEDIAN, WerewolfStep.FOX, WerewolfStep.GYPSY, WerewolfStep.INFECT, WerewolfStep.BIG_BAD,
        WerewolfStep.WHITE_WOLF, WerewolfStep.PYRO -> genericScript()
        WerewolfStep.DAWN -> {
            val lines = mutableListOf(context.getString(R.string.ww_say_dawn))
            if (round.lastDeaths.isEmpty()) lines.add(context.getString(R.string.ww_say_nobody_died))
            else lines.addAll(deathLines(context, round, round.lastDeaths))
            if (round.bearGrowl) lines.add(context.getString(R.string.ww_say_growl))
            Script(lines, emptyList())
        }
        WerewolfStep.ELECT -> Script(listOf(context.getString(R.string.ww_say_elect)), emptyList())
        WerewolfStep.HUNTER -> Script(
            listOf(context.getString(R.string.ww_say_hunter, round.names[round.hunterToShoot().coerceAtLeast(0)])),
            emptyList()
        )
        WerewolfStep.HEIR -> Script(
            listOf(context.getString(R.string.ww_say_heir, round.names[round.heirToName().coerceAtLeast(0)])),
            emptyList()
        )
        WerewolfStep.DEBATE -> {
            val lines = mutableListOf<String>()
            if (round.captainAnnounce >= 0) lines.add(context.getString(R.string.ww_say_captain, round.names[round.captainAnnounce]))
            lines.add(context.getString(R.string.ww_say_debate))
            Script(lines, emptyList())
        }
        WerewolfStep.VOTE -> {
            val lines = mutableListOf<String>()
            if (round.secondVote) lines.add(context.getString(R.string.ww_say_second_vote))
            val marked = round.ravenTarget()
            if (marked >= 0) lines.add(context.getString(R.string.ww_say_raven_mark, round.names[marked]))
            lines.add(context.getString(R.string.ww_say_vote))
            Script(lines, emptyList())
        }
        WerewolfStep.MAID -> Script(
            listOf(context.getString(R.string.ww_say_maid_ask, round.names[round.maidTargetSeat().coerceAtLeast(0)])),
            emptyList()
        )
        WerewolfStep.VOTE_RESULT -> {
            val who = if (round.executed >= 0) round.names[round.executed] else ""
            val what = if (round.executed >= 0) context.getString(round.roleAt(round.executed).nameRes) else ""
            val main = when (round.voteOutcome) {
                WerewolfVoteOutcome.NONE -> context.getString(R.string.ww_say_vote_none)
                WerewolfVoteOutcome.TIE -> context.getString(R.string.ww_say_vote_tie)
                WerewolfVoteOutcome.EXECUTED -> context.getString(R.string.ww_say_vote_executed, who, what)
                WerewolfVoteOutcome.IDIOT_SPARED -> context.getString(R.string.ww_say_vote_idiot, who)
                WerewolfVoteOutcome.SCAPEGOAT -> context.getString(R.string.ww_say_vote_scapegoat, who)
            }
            val lines = mutableListOf<String>()
            if (round.maidSwapped >= 0) lines.add(context.getString(R.string.ww_say_maid_took, round.names[round.maidSwapped]))
            lines.add(main)
            // Les éliminés en plus du condamné (l'amoureux qui le suit).
            lines.addAll(deathLines(context, round, round.lastDeaths.filter { it != round.executed }))
            Script(lines, emptyList())
        }
        WerewolfStep.GAME_OVER -> Script(
            listOf(
                context.getString(
                    when (round.winner) {
                        WerewolfWinner.VILLAGE -> R.string.ww_say_win_village
                        WerewolfWinner.WOLVES -> R.string.ww_say_win_wolves
                        WerewolfWinner.ANGEL -> R.string.ww_say_win_angel
                        WerewolfWinner.PIPER -> R.string.ww_say_win_piper
                        WerewolfWinner.WHITE_WOLF -> R.string.ww_say_win_white_wolf
                        WerewolfWinner.PYROMANIAC -> R.string.ww_say_win_pyromaniac
                        WerewolfWinner.SECTARIAN -> R.string.ww_say_win_sectarian
                        else -> R.string.ww_say_win_lovers
                    }
                )
            ),
            emptyList()
        )
    }
}

/** Le texte de victoire affiché aux joueurs. */
private fun winnerText(winner: WerewolfWinner): Int = when (winner) {
    WerewolfWinner.VILLAGE -> R.string.ww_win_village
    WerewolfWinner.WOLVES -> R.string.ww_win_wolves
    WerewolfWinner.ANGEL -> R.string.ww_win_angel
    WerewolfWinner.PIPER -> R.string.ww_win_piper
    WerewolfWinner.WHITE_WOLF -> R.string.ww_win_white_wolf
    WerewolfWinner.PYROMANIAC -> R.string.ww_win_pyromaniac
    WerewolfWinner.SECTARIAN -> R.string.ww_win_sectarian
    else -> R.string.ww_win_lovers
}

/** L'étape (nuit, jour, vote…) en un mot pour l'écran du narrateur. */
private fun phaseLabel(step: WerewolfStep): Int = when (step) {
    WerewolfStep.REVEAL -> R.string.ww_phase_reveal
    WerewolfStep.DAWN, WerewolfStep.DEBATE, WerewolfStep.ELECT, WerewolfStep.HEIR -> R.string.ww_phase_day
    WerewolfStep.HUNTER -> R.string.ww_phase_hunter
    WerewolfStep.VOTE, WerewolfStep.VOTE_RESULT, WerewolfStep.MAID -> R.string.ww_phase_vote
    WerewolfStep.GAME_OVER -> R.string.ww_phase_over
    else -> R.string.ww_phase_night
}

/** Combien de temps (ms) le narrateur laisse l'écran des joueurs affiché après une étape, avant de passer à la suite. */
private fun lingerMs(step: WerewolfStep): Long = when (step) {
    WerewolfStep.SEER -> 4_000L
    WerewolfStep.DAWN -> 2_500L
    WerewolfStep.VOTE_RESULT -> 3_500L
    WerewolfStep.HUNTER -> 2_500L
    WerewolfStep.NIGHT_START, WerewolfStep.DEBATE, WerewolfStep.VOTE, WerewolfStep.REVEAL, WerewolfStep.GAME_OVER -> 500L
    else -> 1_200L
}

/**
 * Les commandes du narrateur, pour le téléphone de l'organisateur (qui joue aussi, voir [PlayerBody]) : il lit chaque étape à voix
 * haute, attend les joueurs concernés puis passe à la suite. Il n'affiche que ce que tout le monde entend : jamais le choix des
 * loups, ni les cartes cachées. Pendant la partie test, il fait aussi jouer les joueurs simulés et s'arrête après une nuit et un jour.
 */
@Composable
private fun NarratorControls(online: WerewolfOnline) {
    val round = online.round ?: return
    val context = LocalContext.current
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    val voice = remember(locale) { WerewolfVoice(context, locale) }
    DisposableEffect(voice) { onDispose { voice.shutdown() } }
    var line by remember { mutableStateOf("") }
    var skip by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var debateLeft by remember { mutableIntStateOf(0) }
    // L'étape de la partie n'est pas un état Compose : on la relit à chaque changement de la partie pour redessiner l'écran.
    val uiStep = remember(online.version) { round.step }

    LaunchedEffect(round, round.stepVersion) {
        val step = round.step
        // Le test s'arrête après une nuit et un jour ; le test complet va jusqu'à la fin de la partie (ou jusqu'à ce qu'on l'arrête).
        if (online.isTest && ((round.night >= 2 && !online.testAll) || step == WerewolfStep.GAME_OVER)) {
            online.finishTest()
            return@LaunchedEffect
        }
        val before = round.lastDeaths
        val script = werewolfScript(context, round)
        val startSkip = skip
        suspend fun hold() {
            while (paused) delay(200)
        }
        for (text in script.intro) {
            hold()
            line = text
            voice.say(text)
        }
        when {
            step == WerewolfStep.GAME_OVER -> return@LaunchedEffect
            step == WerewolfStep.DEBATE -> {
                debateLeft = if (online.isTest) TEST_DEBATE_SECONDS else DEBATE_SECONDS
                while (debateLeft > 0 && skip == startSkip) {
                    delay(1_000)
                    hold()
                    debateLeft--
                }
                debateLeft = 0
            }
            round.isDecoy() -> {
                var waited = 0L
                val total = Random.nextLong(DECOY_MIN_MS, DECOY_MAX_MS)
                while (waited < total && skip == startSkip) {
                    delay(250)
                    waited += 250
                }
            }
            else -> {
                var tick = 0
                while (!round.isComplete() && skip == startSkip) {
                    // Les joueurs simulés de la partie test jouent à leur rythme.
                    if (online.isTest && tick > 0 && tick % BOT_TICKS == 0) online.runBots()
                    tick++
                    delay(250)
                }
            }
        }
        delay(lingerMs(step))
        hold()
        if (step == WerewolfStep.HUNTER) {
            // Ce que le tir du Chasseur a fait.
            val shot = round.lastDeaths.filter { it !in before && round.roleAt(it) != WerewolfRole.HUNTER }
            for (text in deathLines(context, round, shot)) {
                line = text
                voice.say(text)
            }
        }
        if (step == WerewolfStep.HEIR && round.captain >= 0) {
            // Le nouveau Capitaine.
            val text = resources.getString(R.string.ww_say_captain, round.names[round.captain])
            line = text
            voice.say(text)
        }
        for (text in script.outro) {
            hold()
            line = text
            voice.say(text)
        }
        online.advance()
    }

    if (online.isTest) {
        Text(
            stringResource(if (online.testAll) R.string.ww_test_all_banner else R.string.ww_test_banner),
            color = MaterialTheme.colorScheme.tertiary,
            fontWeight = FontWeight.Bold
        )
    }
    if (online.testAll && !online.testFinished) {
        // Test complet : la place que ce téléphone joue, et de quoi en changer ou laisser les autres au hasard.
        Text(stringResource(R.string.ww_test_all_seat, round.names.getOrElse(online.controlled) { "" }), fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { online.cycleControlled() }) { Text(stringResource(R.string.ww_test_all_next)) }
            OutlinedButton(onClick = { online.randomForOthers() }) { Text(stringResource(R.string.ww_test_all_random)) }
        }
        OutlinedButton(onClick = { online.backToLobby() }) { Text(stringResource(R.string.ww_test_all_stop)) }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            line,
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
    }
    if (uiStep == WerewolfStep.DEBATE && debateLeft > 0) {
        Text(stringResource(R.string.ww_debate_timer, debateLeft / 60, debateLeft % 60), fontWeight = FontWeight.Bold)
    }
    when {
        online.testFinished -> {
            Text(stringResource(if (online.testAll) R.string.ww_test_all_done else R.string.ww_test_done), fontWeight = FontWeight.Bold)
            Button(onClick = { online.backToLobby() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ww_test_back)) }
        }
        uiStep == WerewolfStep.GAME_OVER ->
            Button(onClick = { online.backToLobby() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ww_new_game)) }
        else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { paused = !paused }) {
                Text(stringResource(if (paused) R.string.ww_resume else R.string.ww_pause))
            }
            OutlinedButton(onClick = { skip++ }) {
                Text(stringResource(if (uiStep == WerewolfStep.DEBATE) R.string.ww_start_vote else R.string.ww_skip))
            }
        }
    }
}

// ---------- Un joueur ----------

@Composable
private fun PlayerBody(view: WerewolfView, online: WerewolfOnline) {
    val context = LocalContext.current
    var lastAlert by remember(view.seat) { mutableIntStateOf(view.alert) }
    // Mon téléphone vibre légèrement quand mon rôle se réveille ou peut se réveiller.
    LaunchedEffect(view.seat, view.alert) {
        if (view.alert > lastAlert) WerewolfHaptics.buzz(context, ALERT_MS)
        lastAlert = view.alert
    }
    var showMine by remember(view.seat) { mutableStateOf(false) }
    var selected by remember(view.seat, view.step, view.night, view.prompt) { mutableStateOf(listOf<Int>()) }
    var heal by remember(view.seat, view.step, view.night) { mutableStateOf(false) }
    val me = view.seat
    val role = view.role
    val over = view.step == WerewolfStep.GAME_OVER
    val iAmAlive = view.alive.getOrElse(me) { true }

    Text(stringResource(phaseLabel(view.step)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(stringResource(R.string.ww_alive_count, view.alive.count { it }, view.alive.size), style = MaterialTheme.typography.labelMedium)

    // Ma carte : cachée par défaut, un toucher la montre ou la cache.
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        FlipCard(
            role = if (showMine || over) role else null,
            modifier = Modifier
                .width(140.dp)
                .aspectRatio(1f)
                .clickable { showMine = !showMine }
        )
        if (showMine || over) {
            Text(stringResource(role.nameRes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(role.descRes), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        } else {
            Text(stringResource(R.string.ww_card_hint), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }

    // Ce que je sais en secret.
    if (view.lover >= 0) Text(stringResource(R.string.ww_lover_info, view.names[view.lover]))
    if (view.mates.size > 1) {
        Text(stringResource(R.string.ww_wolf_mates, view.mates.filter { it != me }.joinToString { view.names[it] }))
    }
    view.seerFound.chunked(2).forEach { pair ->
        if (pair.size == 2) {
            val found = WerewolfRole.entries.getOrNull(pair[1])
            if (found != null) Text(stringResource(R.string.ww_seer_result, view.names.getOrElse(pair[0]) { "" }, stringResource(found.nameRes)))
        }
    }
    if (view.wolfDisagree) Text(stringResource(R.string.ww_wolf_disagree), color = MaterialTheme.colorScheme.error)
    if (view.siblings.isNotEmpty()) Text(stringResource(R.string.ww_info_siblings, view.siblings.joinToString { view.names[it] }))
    if (view.charmedSeats.isNotEmpty()) Text(stringResource(R.string.ww_info_charmed, view.charmedSeats.joinToString { view.names[it] }))
    if (view.converted) Text(stringResource(R.string.ww_info_converted))
    if (view.foxSeat >= 0) {
        Text(stringResource(if (view.foxFound) R.string.ww_info_fox_wolf else R.string.ww_info_fox_clear, view.names[view.foxSeat]))
    }
    if (view.model >= 0) Text(stringResource(R.string.ww_info_model, view.names[view.model]))
    if (view.groups.isNotEmpty()) Text(stringResource(R.string.ww_info_group, if (view.groups.getOrElse(me) { 0 } == 0) "A" else "B"))
    if (view.captain >= 0) Text(stringResource(R.string.ww_info_captain, view.names[view.captain]))

    if (!iAmAlive) {
        Text(stringResource(R.string.ww_dead_info), fontWeight = FontWeight.Bold)
    }
    if (over) {
        Text(stringResource(winnerText(view.winner)), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }

    // Ce qu'on me demande.
    val prompt = view.prompt
    val selectable = view.candidates.toSet()
    val multi = prompt == WerewolfPrompt.LOVERS || prompt == WerewolfPrompt.CHARM || prompt == WerewolfPrompt.DOUSE
    val toggle: (Int) -> Unit = { seat ->
        selected = when {
            seat in selected -> selected - seat
            multi -> (selected + seat).takeLast(2)
            else -> listOf(seat)
        }
    }
    when (prompt) {
        WerewolfPrompt.ACK -> Button(onClick = { online.act(WerewolfActionKind.ACK) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.ww_ack))
        }
        WerewolfPrompt.NONE -> Text(waitingText(view, iAmAlive), style = MaterialTheme.typography.bodyMedium)
        WerewolfPrompt.THIEF -> {
            Text(stringResource(R.string.ww_prompt_thief), fontWeight = FontWeight.Bold)
            view.thiefCards.forEachIndexed { index, ordinal ->
                val card = WerewolfRole.entries.getOrNull(ordinal)
                if (card != null) {
                    Button(onClick = { online.act(WerewolfActionKind.THIEF, index) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.ww_thief_take, stringResource(card.nameRes)))
                    }
                }
            }
            if (!view.thiefForced) {
                OutlinedButton(onClick = { online.act(WerewolfActionKind.THIEF, -1) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.ww_thief_keep))
                }
            }
        }
        WerewolfPrompt.CAMP -> {
            Text(stringResource(R.string.ww_prompt_camp), fontWeight = FontWeight.Bold)
            Button(onClick = { online.act(WerewolfActionKind.CAMP, 0) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ww_camp_village)) }
            Button(onClick = { online.act(WerewolfActionKind.CAMP, 1) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ww_camp_wolf)) }
        }
        WerewolfPrompt.INFECT -> {
            Text(stringResource(R.string.ww_prompt_infect), fontWeight = FontWeight.Bold)
            if (view.witchVictim >= 0) Text(stringResource(R.string.ww_infect_victim, view.names[view.witchVictim]))
            Button(onClick = { online.act(WerewolfActionKind.INFECT, 1) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ww_infect_yes)) }
            OutlinedButton(onClick = { online.act(WerewolfActionKind.INFECT, 0) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ww_infect_no)) }
        }
        WerewolfPrompt.MAID -> {
            Text(stringResource(R.string.ww_prompt_maid), fontWeight = FontWeight.Bold)
            if (view.maidTarget >= 0) Text(stringResource(R.string.ww_maid_victim, view.names[view.maidTarget]))
            Button(onClick = { online.act(WerewolfActionKind.MAID, 1) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ww_maid_yes)) }
            OutlinedButton(onClick = { online.act(WerewolfActionKind.MAID, 0) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ww_maid_no)) }
        }
        WerewolfPrompt.COMEDIAN -> {
            Text(stringResource(R.string.ww_prompt_comedian), fontWeight = FontWeight.Bold)
            view.comedianLeft.forEach { ordinal ->
                val card = WerewolfRole.entries.getOrNull(ordinal)
                if (card != null) {
                    Button(onClick = { online.act(WerewolfActionKind.COMEDIAN, ordinal) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(card.nameRes))
                    }
                }
            }
            OutlinedButton(onClick = { online.act(WerewolfActionKind.COMEDIAN, -1) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ww_skip_power))
            }
        }
        else -> {
            Text(stringResource(promptText(prompt)), fontWeight = FontWeight.Bold)
            if (prompt == WerewolfPrompt.WITCH) {
                Text(
                    if (view.witchVictim >= 0) stringResource(R.string.ww_witch_victim, view.names[view.witchVictim])
                    else stringResource(R.string.ww_witch_nobody)
                )
                OutlinedButton(onClick = { heal = !heal }, enabled = view.healLeft && view.witchVictim >= 0) {
                    Text((if (heal) "✔ " else "") + stringResource(R.string.ww_witch_heal))
                }
                Text(stringResource(if (view.poisonLeft) R.string.ww_witch_poison_hint else R.string.ww_witch_no_poison))
            }
            val ready = when (prompt) {
                WerewolfPrompt.LOVERS -> selected.size == 2
                WerewolfPrompt.CHARM -> selected.size == minOf(2, selectable.size) && selected.isNotEmpty()
                WerewolfPrompt.DOUSE -> selected.size in 1..2
                WerewolfPrompt.WITCH -> true
                else -> selected.size == 1
            }
            Button(
                onClick = {
                    val first = selected.firstOrNull() ?: -1
                    val second = selected.getOrElse(1) { -1 }
                    when (prompt) {
                        WerewolfPrompt.LOVERS -> online.act(WerewolfActionKind.LOVERS, selected[0], selected[1])
                        WerewolfPrompt.PROTECT -> online.act(WerewolfActionKind.PROTECT, first)
                        WerewolfPrompt.SEE -> online.act(WerewolfActionKind.SEE, first)
                        WerewolfPrompt.WOLF -> online.act(WerewolfActionKind.WOLF, first)
                        WerewolfPrompt.WITCH -> online.act(WerewolfActionKind.WITCH, if (heal) 1 else 0, first)
                        WerewolfPrompt.RAVEN -> online.act(WerewolfActionKind.RAVEN, first)
                        WerewolfPrompt.SHOOT -> online.act(WerewolfActionKind.SHOOT, first)
                        WerewolfPrompt.VOTE -> online.act(WerewolfActionKind.VOTE, first)
                        WerewolfPrompt.MODEL -> online.act(WerewolfActionKind.MODEL, first)
                        WerewolfPrompt.FOX -> online.act(WerewolfActionKind.FOX, first)
                        WerewolfPrompt.GYPSY -> online.act(WerewolfActionKind.GYPSY, first)
                        WerewolfPrompt.BIG_BAD -> online.act(WerewolfActionKind.BIG_BAD, first)
                        WerewolfPrompt.WHITE -> online.act(WerewolfActionKind.WHITE, first)
                        WerewolfPrompt.CHARM -> online.act(WerewolfActionKind.CHARM, first, second)
                        WerewolfPrompt.DOUSE -> online.act(WerewolfActionKind.DOUSE, first, second)
                        WerewolfPrompt.HEIR -> online.act(WerewolfActionKind.HEIR, first)
                        WerewolfPrompt.ELECT -> online.act(WerewolfActionKind.ELECT, first)
                        else -> Unit
                    }
                },
                enabled = ready,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(if (prompt == WerewolfPrompt.VOTE) R.string.ww_vote_button else R.string.ww_confirm))
            }
            // Les pouvoirs qu'on n'est pas obligé d'utiliser.
            val skipKind = when (prompt) {
                WerewolfPrompt.RAVEN -> WerewolfActionKind.RAVEN
                WerewolfPrompt.FOX -> WerewolfActionKind.FOX
                WerewolfPrompt.GYPSY -> WerewolfActionKind.GYPSY
                WerewolfPrompt.WHITE -> WerewolfActionKind.WHITE
                WerewolfPrompt.DOUSE -> WerewolfActionKind.DOUSE
                else -> null
            }
            if (skipKind != null) {
                OutlinedButton(onClick = { online.act(skipKind, -1, -1) }) {
                    Text(stringResource(if (prompt == WerewolfPrompt.RAVEN) R.string.ww_raven_nobody else R.string.ww_skip_power))
                }
            }
            if (view.canIgnite) {
                OutlinedButton(onClick = { online.act(WerewolfActionKind.IGNITE) }) { Text(stringResource(R.string.ww_ignite)) }
            }
        }
    }
    if (view.canJudge) {
        OutlinedButton(onClick = { online.act(WerewolfActionKind.JUDGE) }) { Text(stringResource(R.string.ww_judge_button)) }
    }
    if ((view.step == WerewolfStep.VOTE || view.step == WerewolfStep.ELECT) && view.voters > 0) {
        Text(stringResource(R.string.ww_vote_progress, view.voted, view.voters), style = MaterialTheme.typography.labelLarge)
    }

    // La table : les cartes de tous les joueurs ; on touche celle d'un joueur pour le choisir ou voter contre lui.
    val votesBadge = stringResource(R.string.ww_votes_badge)
    val markedBadge = stringResource(R.string.ww_marked_badge)
    val badges = List(view.names.size) { seat ->
        buildString {
            if (seat == view.captain) append("👑 ")
            if (seat == view.lover) append("❤ ")
            if (view.groups.isNotEmpty()) append(if (view.groups.getOrElse(seat) { 0 } == 0) "A " else "B ")
            if (view.doused.getOrElse(seat) { false }) append("🔥 ")
            if (seat in view.charmedSeats) append("♪ ")
            if (seat in view.mates && me in view.mates) append("🐺 ")
            val count = view.tally.getOrElse(seat) { 0 }
            if (count > 0) append("$votesBadge $count ")
            val wolfVotes = view.picks.count { it == seat }
            if (wolfVotes > 0) append("🐺 $wolfVotes ")
            if (view.marked == seat) append(markedBadge)
        }.trim()
    }
    PlayerTiles(
        names = view.names,
        alive = view.alive,
        shown = List(view.names.size) { view.shownRole(it) },
        me = me,
        badges = badges,
        selectable = selectable,
        selected = selected,
        onToggle = toggle
    )
}

private fun promptText(prompt: WerewolfPrompt): Int = when (prompt) {
    WerewolfPrompt.LOVERS -> R.string.ww_prompt_lovers
    WerewolfPrompt.PROTECT -> R.string.ww_prompt_protect
    WerewolfPrompt.SEE -> R.string.ww_prompt_see
    WerewolfPrompt.WOLF -> R.string.ww_prompt_wolf
    WerewolfPrompt.WITCH -> R.string.ww_prompt_witch
    WerewolfPrompt.RAVEN -> R.string.ww_prompt_raven
    WerewolfPrompt.SHOOT -> R.string.ww_prompt_shoot
    WerewolfPrompt.VOTE -> R.string.ww_prompt_vote
    WerewolfPrompt.MODEL -> R.string.ww_prompt_model
    WerewolfPrompt.FOX -> R.string.ww_prompt_fox
    WerewolfPrompt.GYPSY -> R.string.ww_prompt_gypsy
    WerewolfPrompt.BIG_BAD -> R.string.ww_prompt_big_bad
    WerewolfPrompt.WHITE -> R.string.ww_prompt_white
    WerewolfPrompt.CHARM -> R.string.ww_prompt_charm
    WerewolfPrompt.DOUSE -> R.string.ww_prompt_douse
    WerewolfPrompt.HEIR -> R.string.ww_prompt_heir
    WerewolfPrompt.ELECT -> R.string.ww_prompt_elect
    else -> R.string.ww_confirm
}

@Composable
private fun waitingText(view: WerewolfView, iAmAlive: Boolean): String = when {
    view.step == WerewolfStep.GAME_OVER -> stringResource(R.string.ww_game_over_wait)
    view.step == WerewolfStep.REVEAL -> stringResource(R.string.ww_wait_reveal)
    (view.step == WerewolfStep.VOTE || view.step == WerewolfStep.ELECT) && iAmAlive && view.canVote && view.myVote >= 0 ->
        stringResource(R.string.ww_vote_sent)
    view.step == WerewolfStep.VOTE && iAmAlive && !view.canVote -> stringResource(R.string.ww_idiot_no_vote)
    view.step == WerewolfStep.DEBATE || view.step == WerewolfStep.DAWN || view.step == WerewolfStep.VOTE ||
        view.step == WerewolfStep.VOTE_RESULT || view.step == WerewolfStep.HUNTER || view.step == WerewolfStep.ELECT ||
        view.step == WerewolfStep.HEIR || view.step == WerewolfStep.MAID -> stringResource(R.string.ww_wait_day)
    else -> stringResource(R.string.ww_wait_night)
}
