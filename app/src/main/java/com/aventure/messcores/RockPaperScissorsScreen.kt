package com.aventure.messcores

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Pierre-feuille-ciseaux à deux joueurs : sur un seul téléphone (on se le passe, chacun choisit en secret) ou
 * chacun sur le sien (voir [RockPaperScissorsOnline]). On gagne la partie en remportant un nombre de manches choisi
 * avant de commencer ; le score (victoires de chacun, égalités) reste affiché.
 */
@Composable
internal fun RockPaperScissorsScreen(
    players: List<String>,
    online: RockPaperScissorsOnline,
    onExit: () -> Unit,
    onHostOnline: () -> Unit
) {
    var showQuit by rememberSaveable { mutableStateOf(false) }
    // Hors partie à deux téléphones, le retour système quitte simplement l'écran.
    BackHandler(enabled = online.active) {
        if (online.notice == RpsNotice.HOST_LEFT) {
            online.leave()
            onExit()
        } else {
            showQuit = true
        }
    }

    if (online.active) {
        OnlineRps(online = online, onQuit = { showQuit = true }, onClose = {
            online.leave()
            onExit()
        })
    } else {
        LocalRps(players = players, online = online, onPlayOnline = onHostOnline)
    }

    if (showQuit && online.active) {
        AlertDialog(
            onDismissRequest = { showQuit = false },
            title = { Text(stringResource(R.string.pfc_quit_title)) },
            text = { Text(stringResource(R.string.pfc_quit_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showQuit = false
                    online.leave()
                    onExit()
                }) {
                    Text(stringResource(R.string.mm_quit_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuit = false }) { Text(stringResource(R.string.quit_continue)) }
            }
        )
    }
}

// ---------- Un seul téléphone ----------

@Composable
private fun LocalRps(players: List<String>, online: RockPaperScissorsOnline, onPlayOnline: () -> Unit) {
    val nameA = players.getOrElse(0) { "1" }
    val nameB = players.getOrElse(1) { "2" }

    // La partie est gardée quand l'écran tourne (sous forme de texte, voir RpsMatch.encode).
    var encoded by rememberSaveable { mutableStateOf(RpsMatch().encode()) }
    // Faux tant que le téléphone n'a pas été passé au joueur qui doit choisir : ses choix restent cachés à l'autre.
    var handedOver by rememberSaveable { mutableStateOf(true) }

    val match = RpsMatch.decode(encoded)
    val picker = if (match.pickA == 0) 1 else 2
    val pickerName = if (picker == 1) nameA else nameB

    fun update(next: RpsMatch?) {
        if (next != null) encoded = next.encode()
    }

    RpsCard {
        RpsTitle()
        Text(
            text = stringResource(R.string.pfc_score, nameA, match.winsA, nameB, match.winsB, match.draws),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.pfc_goal, match.target),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        when {
            match.revealed -> {
                RevealRow(nameA = nameA, pickA = match.pickA, nameB = nameB, pickB = match.pickB)
                Text(
                    text = when {
                        match.isOver -> stringResource(R.string.pfc_match_win, if (match.winner == 1) nameA else nameB)
                        match.roundWinner == 0 -> stringResource(R.string.pfc_round_draw)
                        else -> stringResource(R.string.pfc_round_win, if (match.roundWinner == 1) nameA else nameB)
                    },
                    style = MaterialTheme.typography.titleMedium
                )
                Button(
                    onClick = {
                        update(match.advance())
                        handedOver = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(if (match.isOver) R.string.pfc_new_match else R.string.pfc_next_round))
                }
            }
            !handedOver -> {
                Text(
                    text = stringResource(R.string.pfc_handover, pickerName),
                    style = MaterialTheme.typography.titleMedium
                )
                Button(onClick = { handedOver = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.pfc_handover_button, pickerName))
                }
            }
            else -> {
                Text(
                    text = stringResource(R.string.pfc_pick_turn, pickerName),
                    style = MaterialTheme.typography.titleMedium
                )
                ChoiceButtons(extended = match.extended, enabled = true) { choice ->
                    update(match.pick(picker, choice))
                    // Le choix est fait : le téléphone doit être passé avant que l'autre joueur ne regarde l'écran.
                    handedOver = false
                }
            }
        }
        // Le nombre de manches et le passage à deux téléphones ne se changent que tant que rien n'a été joué.
        if (match.isFresh) {
            Text(
                text = stringResource(R.string.pfc_target_label),
                style = MaterialTheme.typography.labelLarge
            )
            TargetPicker(selected = match.target, onSelect = { update(match.withTarget(it)) })
            Text(
                text = stringResource(R.string.pfc_variant_label),
                style = MaterialTheme.typography.labelLarge
            )
            VariantPicker(selected = match.extended, onSelect = { update(match.withExtended(it)) })
            OutlinedButton(
                onClick = {
                    online.chooseTarget(match.target)
                    online.chooseExtended(match.extended)
                    onPlayOnline()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.pfc_online_button))
            }
        }
    }
}

// ---------- Chacun sur son téléphone ----------

/** La partie à deux téléphones : attente de l'autre joueur, puis les choix secrets, la révélation et le score. */
@Composable
private fun OnlineRps(online: RockPaperScissorsOnline, onQuit: () -> Unit, onClose: () -> Unit) {
    if (online.notice == RpsNotice.HOST_LEFT) {
        RpsCard {
            RpsTitle()
            Text(text = stringResource(R.string.pfc_online_host_left), style = MaterialTheme.typography.titleMedium)
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.common_close)) }
        }
        return
    }
    if (!online.ready) {
        RpsCard {
            RpsTitle()
            if (online.notice == RpsNotice.PLAYER_LEFT) {
                Text(
                    text = stringResource(R.string.pfc_online_notice_left),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Text(text = stringResource(R.string.pfc_online_waiting), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onQuit, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.pfc_online_leave))
            }
        }
        return
    }

    // La partie affichée cache le choix de l'autre joueur tant que la manche n'est pas révélée.
    val match = online.view
    val me = online.me
    val nameA = online.lobby.getOrElse(0) { "1" }
    val nameB = online.lobby.getOrElse(1) { "2" }
    val opponent = if (me == 1) nameB else nameA
    val myPick = if (me == 1) match.pickA else match.pickB

    RpsCard {
        RpsTitle()
        Text(text = stringResource(R.string.pfc_online_vs, opponent), style = MaterialTheme.typography.bodyLarge)
        Text(
            text = stringResource(R.string.pfc_score, nameA, match.winsA, nameB, match.winsB, match.draws),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.pfc_goal, match.target),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        when {
            match.revealed -> {
                RevealRow(nameA = nameA, pickA = match.pickA, nameB = nameB, pickB = match.pickB)
                Text(
                    text = when {
                        match.isOver && match.winner == me -> stringResource(R.string.pfc_online_you_win_match)
                        match.isOver -> stringResource(R.string.pfc_match_win, opponent)
                        match.roundWinner == 0 -> stringResource(R.string.pfc_round_draw)
                        match.roundWinner == me -> stringResource(R.string.pfc_online_you_win_round)
                        else -> stringResource(R.string.pfc_round_win, opponent)
                    },
                    style = MaterialTheme.typography.titleMedium
                )
                Button(onClick = { online.next() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (match.isOver) R.string.pfc_new_match else R.string.pfc_next_round))
                }
            }
            match.hasPicked(me) -> {
                // Mon choix est fait ; celui de l'autre joueur n'est pas connu de ce téléphone.
                Text(
                    text = RockPaperScissors.symbol(myPick),
                    fontSize = 56.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.pfc_online_wait_opponent, opponent),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            else -> {
                Text(text = stringResource(R.string.pfc_online_pick), style = MaterialTheme.typography.titleMedium)
                ChoiceButtons(extended = match.extended, enabled = online.canPick, onPick = online::pick)
            }
        }
        OutlinedButton(onClick = onQuit, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.pfc_online_leave))
        }
    }
}

// ---------- Éléments communs ----------

/** La carte centrée de l'écran, qui défile si le téléphone est petit ou tourné. */
@Composable
private fun RpsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = cardSurface()),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content
            )
        }
    }
}

@Composable
private fun RpsTitle() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.game_pfc),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.weight(1f, fill = false).semantics { heading() }
        )
        Spacer(modifier = Modifier.width(12.dp))
        // Sur la carte claire, le bouton prend la couleur principale (il est blanc sur l'en-tête des autres parties).
        RulesButton(
            gameId = PFC_ID,
            gameName = stringResource(R.string.game_pfc),
            contentColor = MaterialTheme.colorScheme.primary
        )
    }
}

/** Le nom d'un choix : pierre, feuille, ciseaux, lézard ou Spock. */
@Composable
private fun choiceLabel(choice: Int): String = stringResource(
    when (choice) {
        RockPaperScissors.ROCK -> R.string.pfc_rock
        RockPaperScissors.PAPER -> R.string.pfc_paper
        RockPaperScissors.LIZARD -> R.string.pfc_lizard
        RockPaperScissors.SPOCK -> R.string.pfc_spock
        else -> R.string.pfc_scissors
    }
)

/**
 * Les boutons de choix (trois, ou cinq en version [extended], sur deux rangées) ; [enabled] faux : ils sont grisés
 * (le choix est déjà parti).
 */
@Composable
private fun ChoiceButtons(extended: Boolean, enabled: Boolean, onPick: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RockPaperScissors.choices(extended).chunked(CHOICES_PER_ROW).forEach { rowChoices ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Une rangée incomplète est centrée : les boutons gardent la même largeur que ceux de la première.
                val missing = CHOICES_PER_ROW - rowChoices.size
                if (missing > 0) Spacer(modifier = Modifier.weight(missing / 2f))
                rowChoices.forEach { choice ->
                    Button(
                        onClick = { onPick(choice) },
                        enabled = enabled,
                        modifier = Modifier.weight(1f).height(104.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = RockPaperScissors.symbol(choice), fontSize = 36.sp)
                            Text(
                                text = choiceLabel(choice),
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1
                            )
                        }
                    }
                }
                if (missing > 0) Spacer(modifier = Modifier.weight(missing / 2f))
            }
        }
    }
}

private const val CHOICES_PER_ROW = 3

/** La révélation d'une manche : le choix de chaque joueur, côte à côte. */
@Composable
private fun RevealRow(nameA: String, pickA: Int, nameB: String, pickB: Int) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(nameA to pickA, nameB to pickB).forEach { (name, pick) ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(text = name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(text = RockPaperScissors.symbol(pick), fontSize = 56.sp)
                Text(text = choiceLabel(pick), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Le choix du nombre de manches à gagner : le nombre retenu est plein, les autres sont cerclés. */
@Composable
private fun TargetPicker(selected: Int, onSelect: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RockPaperScissors.TARGETS.forEach { value ->
            if (value == selected) {
                Button(onClick = { onSelect(value) }, modifier = Modifier.weight(1f)) { Text(value.toString()) }
            } else {
                OutlinedButton(onClick = { onSelect(value) }, modifier = Modifier.weight(1f)) { Text(value.toString()) }
            }
        }
    }
}

/** Le choix de la version : classique ou « lézard Spock » ; la version retenue est pleine, l'autre est cerclée. */
@Composable
private fun VariantPicker(selected: Boolean, onSelect: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(false to R.string.pfc_variant_classic, true to R.string.pfc_variant_extended).forEach { (value, label) ->
            if (value == selected) {
                Button(onClick = { onSelect(value) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(label), textAlign = TextAlign.Center)
                }
            } else {
                OutlinedButton(onClick = { onSelect(value) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(label), textAlign = TextAlign.Center)
                }
            }
        }
    }
}
