package com.aventure.messcores

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Cartes par ligne pour une combinaison posée sur la table. */
private const val MELD_CARDS_PER_ROW = 8

/** Cartes par ligne pour la main (13 ou 14 cartes : deux lignes). */
private const val HAND_CARDS_PER_ROW = 7

/**
 * L'écran du Rami jouable : il n'y a pas de feuille de scores derrière, tout s'affiche ici.
 *
 * - Sur un seul téléphone, ou sur celui de l'organisateur : [RamiHostSection], où l'on se passe le téléphone.
 * - Sur un téléphone invité : [RamiGuestSection], qui affiche la main du joueur à sa place et envoie ses actions à l'organisateur.
 *
 * « Retour » et la fin de partie quittent la partie ([onBack]).
 */
@Composable
internal fun RamiScreen(
    viewModel: ScoreViewModel,
    session: SyncSession,
    online: RamiOnline,
    onBack: () -> Unit
) {
    if (viewModel.gameRules.id != RAMI_ID) {
        // Aucune partie de Rami ouverte (partie fermée entre-temps) : retour.
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val guest = session.role == SyncRole.GUEST && session.isConnected && session.kind == SyncKind.SCORE
    if (guest) {
        val table = online.table
        CardGameScreen(
            viewModel = viewModel,
            onBack = onBack,
            scrollKeys = listOf(table?.current, table?.roundOver, table?.roundNumber, table?.drawing, online.mySeat)
        ) {
            RamiGuestSection(viewModel = viewModel, online = online)
        }
        return
    }
    val round = remember(viewModel, viewModel.players.size) { viewModel.ramiRound() }
    // Les invités reçoivent leur vue de la table à chaque changement ; une manche finie est inscrite tout de suite.
    LaunchedEffect(round, online) {
        snapshotFlow { round.tableFor(-1) }.collect { online.publish() }
    }
    LaunchedEffect(round, round.roundOver) {
        if (round.roundOver) round.settle(viewModel)
    }
    // Un autre joueur, une nouvelle manche, la main qu'on vient de dévoiler ou la pioche faite : on remonte en haut de la page.
    CardGameScreen(
        viewModel = viewModel,
        onBack = onBack,
        scrollKeys = listOf(round.current, round.revealed, round.roundOver, round.roundNumber)
    ) {
        RamiHostSection(viewModel = viewModel, round = round, online = online, onLeave = onBack)
    }
}

/**
 * Rami tour par tour (voir [RamiRound]) : l'appli distribue, vérifie chaque pose, chaque ajout et chaque joker repris, compte
 * les points de la manche et les inscrit au tableau des scores. Les places que personne n'a prises sur un autre téléphone se
 * jouent ici : le téléphone se passe de joueur en joueur et la main n'apparaît qu'après « Voir ma main ».
 */
@Composable
internal fun RamiHostSection(viewModel: ScoreViewModel, round: RamiRound, online: RamiOnline, onLeave: () -> Unit) {
    val players = viewModel.players
    val labels = rememberFrenchLabels()
    val over = viewModel.isGameOver()
    val remote = online.isRemote(round.current)
    // La main n'est montrée que quand le joueur de ce téléphone l'a demandée ; celle d'un autre téléphone ne vient jamais ici.
    val seat = if (round.revealed && !remote && !round.roundOver) round.current else -1
    val table = round.tableFor(seat)

    // Une manche qui vient de finir est inscrite tout de suite : quitter ensuite ne la fait pas perdre.
    fun settleIfOver() {
        if (round.roundOver) round.settle(viewModel)
    }

    ScoresCard(viewModel = viewModel, current = if (over || round.roundOver) -1 else round.current)

    if (over) {
        // La partie est finie (ou reprise déjà finie) : le vainqueur, le résultat de la dernière manche s'il y en a une, puis la sortie.
        GameOverBanner(players = players, winners = viewModel.winners())
        if (round.roundOver) RamiRoundResult(table = table, players = players, labels = labels, canContinue = false, onNext = {})
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

    val name = players.getOrElse(round.current) { "" }
    RamiBoard(
        table = table,
        labels = labels,
        players = players,
        seat = seat,
        name = name,
        active = seat >= 0,
        onStock = { round.drawFromStock() },
        onTakeDiscard = { round.takeDiscard() },
        onMeld = { cards -> if (round.meld(cards)) settleIfOver() },
        onAttach = { cards, index -> if (round.attach(cards, index)) settleIfOver() },
        onSwap = { card, index -> round.swapJoker(card, index) },
        onDiscard = { card -> if (round.discard(card)) settleIfOver() }
    )

    if (round.roundOver) {
        RamiRoundResult(
            table = table,
            players = players,
            labels = labels,
            canContinue = round.settled,
            onNext = { round.nextRound() }
        )
        return
    }
    if (remote) {
        // Ce joueur joue sur son téléphone : rien à faire ici que d'attendre.
        CardPanel {
            Text(stringResource(R.string.president_waiting_remote, name), style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    if (!round.revealed) {
        // Le téléphone change de mains : la main reste cachée jusqu'à ce que son joueur la demande.
        PassPhoneCard(name = name, onReveal = { round.reveal() })
    }
}

/**
 * Le Rami sur un téléphone invité : l'invité choisit sa place à la table, voit sa main (et elle seule) en permanence et joue
 * quand c'est son tour ; l'organisateur vérifie et applique ses actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RamiGuestSection(viewModel: ScoreViewModel, online: RamiOnline) {
    val players = viewModel.players
    val labels = rememberFrenchLabels()
    val table = online.table
    val seat = online.mySeat
    val over = viewModel.isGameOver()

    ScoresCard(viewModel = viewModel, current = if (table == null || over || table.roundOver) -1 else table.current)

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

    if (table == null) {
        Text(stringResource(R.string.president_guest_wait), style = MaterialTheme.typography.bodyMedium, color = Color.White)
        return
    }

    if (over) {
        // La partie est finie : le vainqueur et les points de la dernière manche ; l'organisateur n'en distribue plus.
        GameOverBanner(players = players, winners = viewModel.winners())
        if (table.roundOver) RamiRoundResult(table = table, players = players, labels = labels, canContinue = false, onNext = {})
        return
    }

    Text(
        text = stringResource(R.string.huit_round, table.roundNumber),
        style = MaterialTheme.typography.labelLarge,
        color = Color.White
    )

    // Seul le joueur dont c'est le tour peut agir ; l'organisateur vérifie de toute façon.
    val myTurn = seat >= 0 && table.current == seat && !table.roundOver
    RamiBoard(
        table = table,
        labels = labels,
        players = players,
        seat = seat,
        name = players.getOrElse(seat) { "" },
        active = myTurn,
        onStock = { online.drawFromStock() },
        onTakeDiscard = { online.takeDiscard() },
        onMeld = { cards -> online.meld(cards) },
        onAttach = { cards, index -> online.attach(cards, index) },
        onSwap = { card, index -> online.swapJoker(card, index) },
        onDiscard = { card -> online.discard(card) }
    )

    if (table.roundOver) {
        RamiRoundResult(table = table, players = players, labels = labels, canContinue = table.settled, onNext = { online.next() })
        return
    }
    if (seat < 0) {
        Text(stringResource(R.string.president_seat_hint), style = MaterialTheme.typography.bodyMedium, color = Color.White)
    }
}

/** Une carte du paquet de 108 cartes : l'image d'une carte, ou celle d'un joker. */
@Composable
private fun RamiCardFace(
    card: Int,
    labels: FrenchLabels,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    if (RamiScoring.isJoker(card)) {
        ImageCard(
            image = ramiCardImage(card),
            description = stringResource(R.string.yaniv_joker),
            playable = true,
            modifier = modifier,
            selected = selected,
            onClick = onClick
        )
    } else {
        FrenchCardFace(card = RamiScoring.base(card), labels = labels, playable = true, modifier = modifier, selected = selected, onClick = onClick)
    }
}

/** Le texte d'une erreur de pose : certains rappellent les points de la première pose. */
@Composable
private fun ramiErrorText(error: Int): String =
    if (error == R.string.rami_too_few || error == R.string.rami_not_opened) stringResource(error, RamiScoring.OPEN_MIN) else stringResource(error)

/**
 * La table et la main du joueur à la place [seat] (-1 : on ne montre que la table, sans main).
 *
 * - La table : les combinaisons posées, la défausse (dont on reprend la carte du dessus), la pioche et la taille des mains.
 * - La main : si [active] (c'est son tour), il pioche d'abord (en touchant la pioche ou la défausse). Ensuite il touche des
 *   cartes puis « Poser » pour les poser en combinaisons, ou une combinaison de la table pour y ajouter la carte choisie (ou
 *   reprendre un joker), et il finit son tour en défaussant une seule carte.
 *
 * Chaque action n'est envoyée que si elle est permise (les mêmes contrôles que le moteur, voir [RamiScoring]) : sinon un
 * message l'explique.
 */
@Composable
private fun RamiBoard(
    table: RamiTable,
    labels: FrenchLabels,
    players: List<String>,
    seat: Int,
    name: String,
    active: Boolean,
    onStock: () -> Unit,
    onTakeDiscard: () -> Unit,
    onMeld: (List<Int>) -> Unit,
    onAttach: (List<Int>, Int) -> Unit,
    onSwap: (Int, Int) -> Unit,
    onDiscard: (Int) -> Unit
) {
    val hand = table.hand
    val opened = table.opened.getOrElse(seat) { false }
    val canDraw = active && table.drawing
    val playing = active && !table.drawing
    // Les cartes choisies ; remises à zéro dès que la main, les combinaisons ou le tour changent.
    var selection by remember(table.current, table.roundNumber, table.hand, table.melds) { mutableStateOf(emptySet<Int>()) }
    // Le texte (ressource) refusant la dernière action ; effacé de même, et dès qu'on touche une carte.
    var error by remember(table.current, table.roundNumber, table.hand, table.melds, table.drawing) { mutableStateOf<Int?>(null) }
    // Les cartes choisies, dans l'ordre de la main.
    val chosen = hand.filter { it in selection }
    val stockDescription = pluralStringResource(R.plurals.card_stock_desc, table.stockSize, table.stockSize)

    fun drawFromStock() {
        if (table.canDrawFromStock) {
            error = null
            onStock()
        } else {
            error = R.string.rami_stock_empty
        }
    }

    // On touche une combinaison : un joker qu'une carte choisie remplace est repris, sinon les cartes choisies s'y ajoutent.
    fun useOnMeld(meld: RamiMeld, index: Int) {
        if (!opened) {
            error = R.string.rami_not_opened
            return
        }
        val swapped = if (chosen.size == 1) RamiScoring.swapJoker(meld, chosen.first()) else null
        if (swapped != null) {
            error = null
            onSwap(chosen.first(), index)
        } else if (RamiScoring.attachAll(meld, chosen) != null) {
            error = null
            onAttach(chosen, index)
        } else {
            error = R.string.rami_attach_failed
        }
    }

    CardPanel {
        Text(
            text = stringResource(R.string.rami_table_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (table.melds.isEmpty()) {
            Text(stringResource(R.string.rami_no_melds), style = MaterialTheme.typography.bodyMedium)
        }
        table.melds.forEachIndexed { index, meld ->
            val label = stringResource(if (meld.run) R.string.rami_meld_run else R.string.rami_meld_set, index + 1)
            val touchable = playing && chosen.isNotEmpty()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (touchable) Modifier.clickable(role = Role.Button) { useOnMeld(meld, index) } else Modifier),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CardRows(meld.cards, perRow = MELD_CARDS_PER_ROW) { card, cardModifier ->
                    RamiCardFace(card = card, labels = labels, modifier = cardModifier)
                }
            }
        }
        Text(
            text = stringResource(R.string.yaniv_discard_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (table.topDiscard >= 0) {
                RamiCardFace(
                    card = table.topDiscard,
                    labels = labels,
                    modifier = Modifier.width(64.dp),
                    onClick = if (canDraw) {
                        {
                            error = null
                            onTakeDiscard()
                        }
                    } else {
                        null
                    }
                )
            }
            FrenchCardBack(
                Modifier
                    .width(64.dp)
                    .then(if (canDraw) Modifier.clickable(role = Role.Button) { drawFromStock() } else Modifier)
                    .semantics { contentDescription = stockDescription }
            )
            Text(
                text = stringResource(R.string.huit_stock, table.stockSize),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            text = stringResource(R.string.huit_hands_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        players.forEachIndexed { index, player ->
            Text(
                text = stringResource(R.string.dice_pig_score_line, player, table.handSizes.getOrNull(index) ?: 0),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

    if (seat < 0 || table.roundOver) return

    CardPanel(spacing = 10) {
        if (active) {
            Text(
                text = stringResource(R.string.dice_bunco_turn, name),
                style = MaterialTheme.typography.titleMedium
            )
        }
        Text(
            text = stringResource(R.string.yaniv_hand_points, table.handPoints),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CardRows(hand, perRow = HAND_CARDS_PER_ROW) { card, cardModifier ->
            RamiCardFace(
                card = card,
                labels = labels,
                modifier = cardModifier,
                selected = card in selection,
                onClick = if (playing) {
                    {
                        error = null
                        selection = if (card in selection) selection - card else selection + card
                    }
                } else {
                    null
                }
            )
        }

        if (!active) {
            Text(
                text = stringResource(R.string.president_turn_of, players.getOrElse(table.current) { "" }),
                style = MaterialTheme.typography.bodyMedium
            )
            return@CardPanel
        }

        if (table.drawing) {
            Text(stringResource(R.string.rami_draw_hint), style = MaterialTheme.typography.bodyMedium)
            error?.let { CardErrorText(ramiErrorText(it)) }
            return@CardPanel
        }

        if (!opened) {
            Text(stringResource(R.string.rami_open_hint, RamiScoring.OPEN_MIN), style = MaterialTheme.typography.bodyMedium)
        }
        Text(stringResource(R.string.rami_play_hint), style = MaterialTheme.typography.bodyMedium)
        Button(
            onClick = {
                if (chosen.isEmpty()) {
                    error = R.string.rami_select_first
                } else {
                    when (RamiScoring.checkMeld(chosen, opened)) {
                        RamiCheck.OK -> {
                            error = null
                            onMeld(chosen)
                        }
                        RamiCheck.TOO_FEW_POINTS -> {
                            error = R.string.rami_too_few
                        }
                        RamiCheck.INVALID -> {
                            error = R.string.rami_not_valid
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.rami_meld))
        }
        OutlinedButton(
            onClick = {
                if (chosen.size == 1) {
                    error = null
                    onDiscard(chosen.first())
                } else {
                    error = R.string.rami_discard_one
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.rami_discard))
        }
        error?.let { CardErrorText(ramiErrorText(it)) }
    }
}

/** La fin de la manche : celui qui a posé sa dernière carte, les mains de chacun, les points (déjà inscrits au tableau) et la manche suivante. */
@Composable
private fun RamiRoundResult(
    table: RamiTable,
    players: List<String>,
    labels: FrenchLabels,
    canContinue: Boolean,
    onNext: () -> Unit
) {
    // joinToString n'est pas inline : le texte du joker est lu ici, hors de sa lambda.
    val jokerFace = stringResource(R.string.yaniv_joker_face)
    CardPanel {
        Text(
            text = stringResource(R.string.yaniv_hands_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        players.forEachIndexed { index, name ->
            val hand = table.finalHands.getOrElse(index) { emptyList() }
            val text = hand.joinToString(" ") { card -> if (RamiScoring.isJoker(card)) jokerFace else labels.face(RamiScoring.base(card)) }
            Text(
                text = stringResource(R.string.yaniv_hand_line, name, text, RamiScoring.handPoints(hand)),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
    RoundResultCard(
        title = stringResource(R.string.rami_round_won, players.getOrElse(table.winner) { "" }),
        players = players,
        points = table.points,
        winner = table.winner,
        canContinue = canContinue,
        onNext = onNext
    )
}
