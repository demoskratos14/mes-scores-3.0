package com.aventure.messcores

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Dominos par ligne dans la chaîne affichée sur la table. */
private const val CHAIN_PER_ROW = 4

/**
 * L'écran du Domino jouable : il n'y a pas de feuille de scores derrière, tout s'affiche ici (voir [DominoSection]).
 *
 * - Sur un seul téléphone, ou sur celui de l'organisateur : la partie elle-même, où l'on se passe le téléphone. Les places prises
 *   par un autre téléphone y sont jouées chez leur joueur : ici on ne touche à rien pour elles.
 * - Sur un téléphone invité : [DominoGuestSection], qui affiche la table reçue et envoie les actions de l'invité à l'organisateur.
 *
 * « Retour » et la fin de partie quittent la partie ([onBack]).
 */
@Composable
internal fun DominoScreen(
    viewModel: ScoreViewModel,
    session: SyncSession,
    online: DominoOnline,
    onBack: () -> Unit
) {
    if (viewModel.gameRules.id != DOMINOS_ID) {
        // Aucune partie de dominos ouverte (partie fermée entre-temps) : retour.
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val guest = session.role == SyncRole.GUEST && session.isConnected && session.kind == SyncKind.SCORE
    if (guest) {
        val shown = online.mirror
        CardGameScreen(
            viewModel = viewModel,
            onBack = onBack,
            scrollKeys = listOf(shown?.current, shown?.roundOver, shown?.roundNumber)
        ) {
            DominoGuestSection(viewModel = viewModel, online = online, onLeave = onBack)
        }
        return
    }
    val round = remember(viewModel, viewModel.players.size) { viewModel.dominoRound() }
    // Les invités reçoivent leur table à chaque changement (leur main seulement, jamais celle des autres).
    LaunchedEffect(round, online) {
        snapshotFlow { round.hands to round.tableFor(-1) }.collect { online.publish() }
    }
    // Un autre joueur, une nouvelle manche ou la main qu'on vient de dévoiler : on remonte en haut de la page.
    CardGameScreen(
        viewModel = viewModel,
        onBack = onBack,
        scrollKeys = listOf(round.current, round.revealed, round.roundOver, round.roundNumber)
    ) {
        DominoSection(
            viewModel = viewModel,
            round = round,
            onLeave = onBack,
            // Le joueur dont c'est le tour joue-t-il sur ce téléphone ?
            canAct = !online.isRemote(round.current)
        )
    }
}

/**
 * Domino sur un téléphone invité : l'invité choisit sa place à la table, voit la table et sa main, et joue quand c'est son tour ;
 * l'organisateur vérifie et applique ses actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DominoGuestSection(viewModel: ScoreViewModel, online: DominoOnline, onLeave: () -> Unit) {
    val players = viewModel.players
    val seat = online.mySeat
    val shown = online.mirror

    CardPanel {
        Text(stringResource(R.string.president_seat_title), style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            players.forEachIndexed { index, name ->
                FilterChip(
                    selected = index == seat,
                    // Une place jouée sur un autre téléphone ne se prend pas ; toucher la sienne la libère.
                    enabled = index == seat || index !in online.taken,
                    onClick = { online.claim(if (index == seat) -1 else index) },
                    label = { Text(name) }
                )
            }
        }
    }

    if (shown == null) {
        Text(stringResource(R.string.president_guest_wait), style = MaterialTheme.typography.bodyMedium, color = Color.White)
        return
    }
    if (seat < 0) {
        Text(stringResource(R.string.president_seat_hint), style = MaterialTheme.typography.bodyMedium, color = Color.White)
    }
    DominoSection(
        viewModel = viewModel,
        round = shown,
        onLeave = onLeave,
        // L'organisateur vérifie de toute façon que c'est bien à cette place de jouer.
        canAct = seat >= 0 && shown.current == seat && !shown.roundOver,
        guest = true,
        mySeat = seat,
        move = { op, tile, atLeft -> online.send(op, tile, atLeft) }
    )
}

/**
 * Domino tour par tour (voir [DominoRound]) : l'appli distribue, ouvre, vérifie chaque domino posé, compte les points de la manche
 * et les inscrit au tableau des scores. Le téléphone se passe de joueur en joueur : la main n'apparaît qu'après « Voir ma main ».
 */
@Composable
internal fun DominoSection(
    viewModel: ScoreViewModel,
    round: DominoRound,
    onLeave: () -> Unit,
    // Ce téléphone joue-t-il maintenant ? Sinon on regarde seulement : sa main n'est pas dévoilée ici.
    canAct: Boolean = true,
    // Téléphone invité : [round] n'est que la copie de la table, l'organisateur inscrit les points, et il n'y a pas de téléphone à passer.
    guest: Boolean = false,
    // La place de cet invité (-1 : aucune), pour lui montrer sa main même quand ce n'est pas son tour.
    mySeat: Int = -1,
    // Fait l'action demandée : directement sur la manche, ou envoyée à l'organisateur sur un téléphone invité.
    move: (DominoOp, Int, Boolean) -> Boolean = { op, tile, atLeft -> round.perform(op, tile, atLeft) }
) {
    val players = viewModel.players
    val over = viewModel.isGameOver()
    // Le texte (ressource) refusant le dernier coup ; effacé dès que la main ou le tour change.
    var error by remember(round.current, round.roundNumber, round.hands, round.chain) { mutableStateOf<Int?>(null) }

    // Une manche qui vient de finir est inscrite tout de suite : quitter ensuite ne la fait pas perdre.
    fun settleIfOver() {
        if (round.roundOver && !guest) round.settle(viewModel)
    }

    ScoresCard(viewModel = viewModel, current = if (over || round.roundOver) -1 else round.current)

    if (over) {
        // La partie est finie (ou reprise déjà finie) : le vainqueur, le résultat de la dernière manche s'il y en a une, puis la sortie.
        GameOverBanner(players = players, winners = viewModel.winners())
        if (round.roundOver) DominoRoundResult(round = round, players = players, canContinue = false, onNext = {})
        Button(onClick = onLeave, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.dice_leave_game))
        }
        return
    }

    Text(
        text = stringResource(R.string.huit_round, round.roundNumber),
        style = MaterialTheme.typography.labelLarge,
        color = Color.White
    )

    DominoTable(round = round, players = players)

    if (round.roundOver) {
        DominoRoundResult(round = round, players = players, canContinue = round.settled, onNext = { move(DominoOp.NEXT, -1, false) })
        return
    }

    val name = players.getOrElse(round.current) { "" }
    if (!canAct) {
        // Ce n'est pas à ce téléphone de jouer : on dit à qui c'est, et un invité garde sa main sous les yeux.
        CardPanel {
            Text(stringResource(R.string.dice_bunco_turn, name), style = MaterialTheme.typography.titleMedium)
        }
        if (guest && mySeat >= 0) {
            DominoHand(
                round = round,
                hand = round.hands.getOrElse(mySeat) { emptyList() },
                name = players.getOrElse(mySeat) { "" },
                enabled = false,
                error = null,
                onError = {},
                onPlay = { _, _ -> },
                onDraw = {},
                onPass = {}
            )
        }
        return
    }
    if (!guest && !round.revealed) {
        // Le téléphone change de mains : la main reste cachée jusqu'à ce que son joueur la demande.
        PassPhoneCard(name = name, onReveal = { round.reveal() })
        return
    }

    DominoHand(
        round = round,
        hand = round.currentHand,
        name = name,
        enabled = true,
        error = error,
        onError = { error = it },
        onPlay = { tile, atLeft ->
            if (move(DominoOp.PLAY, tile, atLeft)) {
                error = null
                settleIfOver()
            } else {
                error = R.string.domino_not_fit
            }
        },
        onDraw = {
            error = when {
                move(DominoOp.DRAW, -1, false) -> null
                round.canPlayAny -> R.string.domino_must_play
                else -> R.string.domino_stock_empty
            }
        },
        onPass = {
            if (move(DominoOp.PASS, -1, false)) {
                error = null
                settleIfOver()
            } else {
                error = if (round.canPlayAny) R.string.domino_must_play else R.string.domino_stock_left
            }
        }
    )
}

/** Un domino posé à plat, [leftValue] à gauche : l'image debout (petit côté en haut) tournée d'un quart de tour. */
@Composable
private fun SidewaysDomino(tile: Int, leftValue: Int, description: String, modifier: Modifier = Modifier) {
    val rotation = if (leftValue == DominoScoring.low(tile)) -90f else 90f
    Layout(
        content = {
            Image(
                painter = painterResource(dominoImage(tile)),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.rotate(rotation)
            )
        },
        modifier = modifier.semantics { contentDescription = description }
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = (width * DOMINO_RATIO).roundToInt()
        // Une fois tourné, le domino est aussi large que l'image est haute : l'image est mesurée debout, puis centrée.
        val placeable = measurables.first().measure(Constraints.fixed(height, width))
        layout(width, height) {
            placeable.place((width - placeable.width) / 2, (height - placeable.height) / 2)
        }
    }
}

/** Un domino debout de la main (ou de la fin de manche) ; [onClick] null : simple affichage. */
@Composable
private fun DominoFace(tile: Int, modifier: Modifier = Modifier, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    ImageCard(
        image = dominoImage(tile),
        description = stringResource(R.string.domino_tile_desc, DominoScoring.low(tile), DominoScoring.high(tile)),
        playable = true,
        modifier = modifier,
        ratio = DOMINO_RATIO,
        selected = selected,
        onClick = onClick
    )
}

/** La table : qui a ouvert, la chaîne de dominos, ses deux bouts et la pioche, puis le nombre de dominos de chaque joueur (rien de secret). */
@Composable
private fun DominoTable(round: DominoRound, players: List<String>) {
    CardPanel {
        if (round.opener >= 0) {
            Text(
                text = stringResource(
                    R.string.domino_opened,
                    players.getOrElse(round.opener) { "" },
                    DominoScoring.low(round.openingTile),
                    DominoScoring.high(round.openingTile)
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            text = stringResource(R.string.domino_table_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        round.chain.chunked(CHAIN_PER_ROW).forEach { line ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                line.forEach { placed ->
                    SidewaysDomino(
                        tile = placed.tile,
                        leftValue = placed.left,
                        description = stringResource(R.string.domino_tile_desc, placed.left, placed.right),
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(CHAIN_PER_ROW - line.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
        if (round.chain.isNotEmpty()) {
            Text(
                text = stringResource(R.string.domino_ends, round.leftEnd, round.rightEnd),
                style = MaterialTheme.typography.bodyLarge
            )
        }
        Text(text = stringResource(R.string.huit_stock, round.stock.size), style = MaterialTheme.typography.bodyMedium)
        Text(
            text = stringResource(R.string.huit_hands_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        players.forEachIndexed { index, name ->
            Text(
                text = stringResource(R.string.dice_pig_score_line, name, round.hands.getOrNull(index)?.size ?: 0),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * La main du joueur qui joue : on touche un domino, puis « Poser à gauche » ou « Poser à droite ». Sans domino à poser, il
 * pioche ; quand la pioche est vide, il passe. Les boutons sont toujours là et le moteur refuse un coup interdit (aucune aide).
 */
@Composable
private fun DominoHand(
    round: DominoRound,
    hand: List<Int>,
    name: String,
    enabled: Boolean,
    error: Int?,
    onError: (Int?) -> Unit,
    onPlay: (tile: Int, atLeft: Boolean) -> Unit,
    onDraw: () -> Unit,
    onPass: () -> Unit
) {
    // Le domino choisi ; remis à zéro dès que la main ou le tour change.
    var selection by remember(round.current, round.roundNumber, hand) { mutableStateOf<Int?>(null) }

    CardPanel(spacing = 10) {
        if (enabled) {
            Text(
                text = stringResource(R.string.dice_bunco_turn, name),
                style = MaterialTheme.typography.titleMedium
            )
        }
        Text(
            text = stringResource(R.string.domino_hand_points, DominoScoring.handPoints(hand)),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CardRows(hand) { tile, tileModifier ->
            DominoFace(
                tile = tile,
                modifier = tileModifier,
                selected = tile == selection,
                onClick = {
                    onError(null)
                    selection = if (tile == selection) null else tile
                }
            )
        }
        Text(stringResource(R.string.domino_hint), style = MaterialTheme.typography.bodyMedium)

        fun place(atLeft: Boolean) {
            val tile = selection
            if (tile == null) onError(R.string.domino_select_first) else onPlay(tile, atLeft)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { place(true) }, enabled = enabled, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.domino_play_left, round.leftEnd))
            }
            Button(onClick = { place(false) }, enabled = enabled, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.domino_play_right, round.rightEnd))
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onDraw, enabled = enabled, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.domino_draw, round.stock.size))
            }
            OutlinedButton(onClick = onPass, enabled = enabled, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.domino_pass))
            }
        }
        error?.let { CardErrorText(stringResource(it)) }
    }
}

/** La fin de la manche : qui l'emporte, les dominos restés en main, les points (déjà inscrits au tableau) et la manche suivante. */
@Composable
private fun DominoRoundResult(
    round: DominoRound,
    players: List<String>,
    canContinue: Boolean,
    onNext: () -> Unit
) {
    val winnerName = players.getOrElse(round.winner) { "" }
    val title = when {
        !round.blocked -> stringResource(R.string.domino_round_won, winnerName)
        round.winner >= 0 -> stringResource(R.string.domino_round_blocked, winnerName)
        else -> stringResource(R.string.domino_round_tie)
    }
    CardPanel {
        Text(
            text = stringResource(R.string.domino_hands_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        players.forEachIndexed { index, name ->
            val hand = round.hands.getOrElse(index) { emptyList() }
            val text = hand.joinToString(" ") { tile -> "${DominoScoring.low(tile)}-${DominoScoring.high(tile)}" }
            Text(
                text = stringResource(R.string.domino_hand_line, name, text, DominoScoring.handPoints(hand)),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
    RoundResultCard(
        title = title,
        players = players,
        points = round.roundPoints(),
        winner = round.winner,
        canContinue = canContinue,
        onNext = onNext
    )
}
