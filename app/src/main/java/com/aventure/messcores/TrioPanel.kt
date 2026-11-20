package com.aventure.messcores

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Cartes par ligne dans le milieu de la table. */
private const val CENTER_PER_ROW = 5

/** Cartes par ligne dans une main (9 cartes au maximum). */
private const val HAND_PER_ROW = 9

/** Cartes par ligne dans la main de celui qui joue. */
private const val MY_HAND_PER_ROW = 5

/** Cartes par ligne dans les cartes retournées (3 au maximum). */
private const val REVEALED_PER_ROW = 3

/** Les images des cartes (res/drawable-nodpi/trio_*.webp, découpées dans les scans des cartes) : 1 à 12, puis le dos. */
@androidx.annotation.DrawableRes
internal fun trioImage(value: Int): Int = when (value) {
    1 -> R.drawable.trio_1
    2 -> R.drawable.trio_2
    3 -> R.drawable.trio_3
    4 -> R.drawable.trio_4
    5 -> R.drawable.trio_5
    6 -> R.drawable.trio_6
    7 -> R.drawable.trio_7
    8 -> R.drawable.trio_8
    9 -> R.drawable.trio_9
    10 -> R.drawable.trio_10
    11 -> R.drawable.trio_11
    12 -> R.drawable.trio_12
    else -> R.drawable.trio_back
}

/** Largeur / hauteur de l'image d'une carte. */
internal const val TRIO_RATIO = 324f / 489f

/**
 * L'écran du Trio jouable : il n'y a pas de feuille de scores derrière, tout s'affiche ici (voir [TrioSection]).
 *
 * - Sur un seul téléphone, ou sur celui de l'organisateur : la partie elle-même, où l'on se passe le téléphone. Les places prises
 *   par un autre téléphone y sont jouées chez leur joueur : ici on ne touche à rien pour elles.
 * - Sur un téléphone invité : [TrioGuestSection], qui affiche la table reçue et envoie les actions de l'invité à l'organisateur.
 *
 * « Retour » et la fin de partie quittent la partie ([onBack]).
 */
@Composable
internal fun TrioScreen(
    viewModel: ScoreViewModel,
    session: SyncSession,
    online: TrioOnline,
    onBack: () -> Unit
) {
    if (viewModel.gameRules.id != TRIO_ID) {
        // Aucune partie de Trio ouverte (partie fermée entre-temps) : retour.
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val guest = session.role == SyncRole.GUEST && session.isConnected && session.kind == SyncKind.SCORE
    if (guest) {
        val shown = online.mirror
        CardGameScreen(
            viewModel = viewModel,
            onBack = onBack,
            scrollKeys = listOf(shown?.current, shown?.roundOver, shown?.roundNumber, shown?.mismatch, shown?.lastTrio)
        ) {
            TrioGuestSection(viewModel = viewModel, online = online, onLeave = onBack)
        }
        return
    }
    val round = remember(viewModel, viewModel.players.size) { viewModel.trioRound() }
    // Les invités reçoivent leur table à chaque changement (leur main seulement, jamais celle des autres).
    LaunchedEffect(round, online) {
        snapshotFlow { round.hands to round.tableFor(-1) }.collect { online.publish() }
    }
    // Un autre joueur, une nouvelle manche, un tour perdu ou un trio : on remonte en haut de la page.
    CardGameScreen(
        viewModel = viewModel,
        onBack = onBack,
        scrollKeys = listOf(round.current, round.shown, round.roundOver, round.roundNumber, round.mismatch, round.lastTrio)
    ) {
        TrioSection(
            viewModel = viewModel,
            round = round,
            onLeave = onBack,
            // Le joueur dont c'est le tour joue-t-il sur ce téléphone ?
            canAct = !online.isRemote(round.current)
        )
    }
}

/**
 * Trio sur un téléphone invité : l'invité choisit sa place à la table, voit la table et sa main, et joue quand c'est son tour ;
 * l'organisateur vérifie et applique ses actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrioGuestSection(viewModel: ScoreViewModel, online: TrioOnline, onLeave: () -> Unit) {
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
    TrioSection(
        viewModel = viewModel,
        round = shown,
        onLeave = onLeave,
        // L'organisateur vérifie de toute façon que c'est bien à cette place de jouer.
        canAct = seat >= 0 && shown.current == seat && !shown.roundOver,
        guest = true,
        mySeat = seat,
        move = { op, target -> online.send(op, target) }
    )
}

/**
 * Trio tour par tour (voir [TrioRound]) : l'appli distribue, vérifie chaque carte retournée, repère les trios et inscrit le gagnant de
 * la manche au tableau des scores. Le téléphone se passe de joueur en joueur : la main n'apparaît qu'après « Voir ma main ».
 */
@Composable
internal fun TrioSection(
    viewModel: ScoreViewModel,
    round: TrioRound,
    onLeave: () -> Unit,
    // Ce téléphone joue-t-il maintenant ? Sinon on regarde seulement : sa main n'est pas dévoilée ici.
    canAct: Boolean = true,
    // Téléphone invité : [round] n'est que la copie de la table, l'organisateur inscrit les points, et il n'y a pas de téléphone à passer.
    guest: Boolean = false,
    // La place de cet invité (-1 : aucune), pour lui montrer sa main même quand ce n'est pas son tour.
    mySeat: Int = -1,
    // Fait l'action demandée : directement sur la manche, ou envoyée à l'organisateur sur un téléphone invité.
    move: (TrioOp, Int) -> Boolean = { op, target -> round.perform(op, target) }
) {
    val players = viewModel.players
    val over = viewModel.isGameOver()
    // Le texte (ressource) refusant le dernier coup ; effacé dès que le tour change.
    var error by remember(round.current, round.roundNumber, round.reveals) { mutableStateOf<Int?>(null) }

    // Une manche qui vient de finir est inscrite tout de suite : quitter ensuite ne la fait pas perdre.
    fun settleIfOver() {
        if (round.roundOver && !guest) round.settle(viewModel)
    }

    fun act(op: TrioOp, target: Int) {
        if (move(op, target)) {
            error = null
            settleIfOver()
        } else {
            error = R.string.trio_not_allowed
        }
    }

    ScoresCard(viewModel = viewModel, current = if (over || round.roundOver) -1 else round.current)

    if (over) {
        // La partie est finie (ou reprise déjà finie) : le vainqueur, le résultat de la dernière manche s'il y en a une, puis la sortie.
        GameOverBanner(players = players, winners = viewModel.winners())
        if (round.roundOver) TrioRoundResult(round = round, players = players, canContinue = false, onNext = {})
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

    if (round.roundOver) {
        TrioRoundResult(round = round, players = players, canContinue = round.settled, onNext = { move(TrioOp.NEXT, -1) })
        return
    }

    val name = players.getOrElse(round.current) { "" }
    // Le joueur qui vient de jouer avant lui (celui qui a gagné le dernier trio).
    val previous = players.getOrElse((round.current + round.playerCount - 1) % round.playerCount.coerceAtLeast(1)) { "" }
    // Quelle main est sous les yeux : celle de l'invité, ou celle du joueur qui joue une fois qu'il l'a demandée.
    val viewer = when {
        guest -> mySeat
        canAct && round.shown -> round.current
        else -> -1
    }

    if (!canAct) {
        // Ce n'est pas à ce téléphone de jouer : on dit à qui c'est, et un invité garde sa main sous les yeux.
        CardPanel {
            Text(stringResource(R.string.dice_bunco_turn, name), style = MaterialTheme.typography.titleMedium)
            TrioStatus(round = round, name = name, previous = previous)
        }
        TrioRevealed(round = round)
        TrioTable(round = round, players = players, viewer = viewer, interactive = false, onFlipHand = { _, _ -> }, onFlipCenter = {})
        if (viewer >= 0) TrioMyHand(round = round, player = viewer, name = players.getOrElse(viewer) { "" })
        return
    }
    if (!guest && !round.shown) {
        // Le téléphone change de mains : la main reste cachée jusqu'à ce que son joueur la demande.
        PassPhoneCard(name = name, onReveal = { round.show() })
        TrioTable(round = round, players = players, viewer = -1, interactive = false, onFlipHand = { _, _ -> }, onFlipCenter = {})
        return
    }

    CardPanel(spacing = 10) {
        Text(stringResource(R.string.dice_bunco_turn, name), style = MaterialTheme.typography.titleMedium)
        TrioStatus(round = round, name = name, previous = previous)
        if (round.mismatch) {
            Button(onClick = { act(TrioOp.END, -1) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.trio_end_turn))
            }
        } else {
            Text(stringResource(R.string.trio_hint), style = MaterialTheme.typography.bodyMedium)
        }
        error?.let { CardErrorText(stringResource(it)) }
    }
    TrioRevealed(round = round)
    TrioTable(
        round = round,
        players = players,
        viewer = viewer,
        interactive = !round.mismatch,
        onFlipHand = { player, high -> act(if (high) TrioOp.HIGH else TrioOp.LOW, player) },
        onFlipCenter = { index -> act(TrioOp.CENTER, index) }
    )
    if (viewer >= 0) TrioMyHand(round = round, player = viewer, name = players.getOrElse(viewer) { "" })
}

/** Ce qui vient de se passer pendant le tour : un tour perdu, un trio gagné, ou les cartes déjà retournées. */
@Composable
private fun TrioStatus(round: TrioRound, name: String, previous: String) {
    when {
        round.mismatch -> Text(stringResource(R.string.trio_mismatch, name), style = MaterialTheme.typography.bodyMedium)
        round.lastTrio > 0 && round.reveals.isEmpty() ->
            Text(stringResource(R.string.trio_found, previous, round.lastTrio), style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Une carte du Trio, avec son image. [value] 0 : le dos de la carte. [compact] : petite carte (les mains des joueurs).
 * [highlighted] entoure la carte retournée.
 */
@Composable
internal fun TrioCard(
    value: Int,
    description: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    highlighted: Boolean = false
) {
    val shape = RoundedCornerShape(if (compact) 4.dp else 8.dp)
    Image(
        painter = painterResource(trioImage(value)),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = modifier
            .aspectRatio(TRIO_RATIO)
            .clip(shape)
            .then(if (highlighted) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .semantics { contentDescription = description }
    )
}

/** Un dos de carte à toucher (le milieu de la table). */
@Composable
private fun TrioBackButton(description: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier = modifier.clickable(role = Role.Button, onClick = onClick)) {
        TrioCard(value = 0, description = description, modifier = Modifier.fillMaxWidth())
    }
}

/** Les cartes retournées pendant ce tour, bien visibles de tous. */
@Composable
private fun TrioRevealed(round: TrioRound) {
    if (round.reveals.isEmpty()) return
    CardPanel(spacing = 8) {
        Text(
            text = stringResource(R.string.trio_revealed_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CardRows(round.reveals, perRow = REVEALED_PER_ROW) { card, cardModifier ->
            TrioCard(
                value = card.value,
                description = stringResource(R.string.trio_card_desc, card.value),
                modifier = cardModifier,
                highlighted = true
            )
        }
    }
}

/**
 * La table : le milieu (cartes à toucher), puis chaque joueur avec ses trios et ses cartes. Les cartes cachées sont des dos ; celles qui
 * viennent d'être retournées sont visibles de tous, et le joueur [viewer] voit sa propre main (-1 : personne). Quand [interactive],
 * chaque main propose de retourner sa plus petite ou sa plus grande carte cachée.
 */
@Composable
private fun TrioTable(
    round: TrioRound,
    players: List<String>,
    viewer: Int,
    interactive: Boolean,
    onFlipHand: (player: Int, high: Boolean) -> Unit,
    onFlipCenter: (index: Int) -> Unit
) {
    CardPanel(spacing = 10) {
        Text(
            text = stringResource(R.string.trio_center_title, round.center.size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CardRows(round.center.indices.toList(), perRow = CENTER_PER_ROW) { index, cardModifier ->
            val reveal = round.centerRevealAt(index)
            if (reveal != null) {
                TrioCard(
                    value = reveal.value,
                    description = stringResource(R.string.trio_card_desc, reveal.value),
                    modifier = cardModifier,
                    highlighted = true
                )
            } else if (interactive) {
                TrioBackButton(
                    description = stringResource(R.string.trio_center_card_desc),
                    modifier = cardModifier,
                    onClick = { onFlipCenter(index) }
                )
            } else {
                TrioCard(value = 0, description = stringResource(R.string.trio_hidden_desc), modifier = cardModifier)
            }
        }
        players.forEachIndexed { player, name ->
            TrioPlayerRow(
                round = round,
                name = name,
                player = player,
                viewer = viewer,
                interactive = interactive,
                onFlipHand = onFlipHand
            )
        }
    }
}

/** La ligne d'un joueur : son nom et ses trios, ses cartes, et (quand on peut jouer) les deux boutons pour en retourner une. */
@Composable
private fun TrioPlayerRow(
    round: TrioRound,
    name: String,
    player: Int,
    viewer: Int,
    interactive: Boolean,
    onFlipHand: (player: Int, high: Boolean) -> Unit
) {
    val hand = round.hands.getOrElse(player) { emptyList() }
    val won = round.trios.getOrElse(player) { emptyList() }
    val wonText = if (won.isEmpty()) stringResource(R.string.trio_none) else won.joinToString(", ")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.trio_player_line, name, wonText),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (player == round.current) FontWeight.Bold else FontWeight.Normal,
            color = if (player == round.current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        if (hand.isEmpty()) {
            Text(stringResource(R.string.trio_no_cards), style = MaterialTheme.typography.bodySmall)
        } else {
            CardRows(hand.indices.toList(), perRow = HAND_PER_ROW) { index, cardModifier ->
                val reveal = round.revealAt(player, index)
                // Une carte retournée est visible de tous ; sa propre main se voit en entier.
                val value = reveal?.value ?: if (player == viewer) hand[index] else 0
                TrioCard(
                    value = value,
                    description = if (value > 0) stringResource(R.string.trio_card_desc, value) else stringResource(R.string.trio_hidden_desc),
                    modifier = cardModifier,
                    compact = true,
                    highlighted = reveal != null
                )
            }
            if (interactive) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val lowDescription = stringResource(R.string.trio_flip_low_desc, name)
                    val highDescription = stringResource(R.string.trio_flip_high_desc, name)
                    OutlinedButton(
                        onClick = { onFlipHand(player, false) },
                        enabled = round.canFlipHand(player, false),
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = lowDescription }
                    ) {
                        Text(stringResource(R.string.trio_flip_low))
                    }
                    OutlinedButton(
                        onClick = { onFlipHand(player, true) },
                        enabled = round.canFlipHand(player, true),
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = highDescription }
                    ) {
                        Text(stringResource(R.string.trio_flip_high))
                    }
                }
            }
        }
    }
}

/** La main de [player] en grand, de la plus petite carte à la plus grande, avec les numéros liés dans les coins. */
@Composable
private fun TrioMyHand(round: TrioRound, player: Int, name: String) {
    val hand = round.hands.getOrElse(player) { emptyList() }
    CardPanel(spacing = 10) {
        Text(
            text = stringResource(R.string.trio_hand_title, name),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (hand.isEmpty()) {
            Text(stringResource(R.string.trio_no_cards), style = MaterialTheme.typography.bodyMedium)
        } else {
            CardRows(hand.indices.toList(), perRow = MY_HAND_PER_ROW) { index, cardModifier ->
                TrioCard(
                    value = hand[index],
                    description = stringResource(R.string.trio_card_desc, hand[index]),
                    modifier = cardModifier,
                    highlighted = round.isRevealed(player, index)
                )
            }
        }
    }
}

/** La fin de la manche : qui l'emporte et comment, les trios de chacun, le point marqué (déjà inscrit au tableau) et la manche suivante. */
@Composable
private fun TrioRoundResult(
    round: TrioRound,
    players: List<String>,
    canContinue: Boolean,
    onNext: () -> Unit
) {
    val winnerName = players.getOrElse(round.winner) { "" }
    val title = when (round.winReason) {
        TrioWin.SEVENS -> stringResource(R.string.trio_win_sevens, winnerName)
        TrioWin.LINKED -> {
            val pair = TrioScoring.linkedPair(round.trios.getOrElse(round.winner) { emptyList() })
            stringResource(R.string.trio_win_linked, winnerName, pair?.first ?: 0, pair?.second ?: 0)
        }
        TrioWin.THREE_TRIOS -> stringResource(R.string.trio_win_three, winnerName)
        TrioWin.MOST -> stringResource(R.string.trio_win_most, winnerName)
        TrioWin.NONE -> stringResource(R.string.trio_win_none)
    }
    CardPanel {
        Text(
            text = stringResource(R.string.trio_trios_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        players.forEachIndexed { index, name ->
            val won = round.trios.getOrElse(index) { emptyList() }
            val text = if (won.isEmpty()) stringResource(R.string.trio_none) else won.joinToString(", ")
            Text(
                text = stringResource(R.string.trio_player_line, name, text),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (index == round.winner) FontWeight.Bold else FontWeight.Normal
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
