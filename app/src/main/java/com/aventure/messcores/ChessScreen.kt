package com.aventure.messcores

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * Échecs à deux joueurs : sur un seul téléphone (on se passe l'appareil) ou chacun sur le sien (voir [ChessOnline]).
 * Les manches s'enchaînent, les couleurs s'échangent à chaque manche et le score de la séance (victoires de chacun,
 * nulles) reste affiché. Les règles sont dans [Chess] : roque, prise en passant, promotion, pat, nulles automatiques.
 */
@Composable
internal fun ChessScreen(
    players: List<String>,
    online: ChessOnline,
    onExit: () -> Unit,
    onHostOnline: () -> Unit
) {
    var showQuit by rememberSaveable { mutableStateOf(false) }
    // Le style de l'échiquier (classique ou sorcier) est un choix de chacun, retenu d'une fois sur l'autre : il ne change
    // rien à la partie, et ne voyage pas entre les deux téléphones.
    val context = LocalContext.current
    var wizard by remember { mutableStateOf(ChessStylePreference.load(context)) }
    val toggleStyle = {
        wizard = !wizard
        ChessStylePreference.save(context, wizard)
    }
    // Hors partie à deux téléphones, le retour système quitte simplement l'écran.
    BackHandler(enabled = online.active) {
        if (online.notice == ChessNotice.HOST_LEFT) {
            online.leave()
            onExit()
        } else {
            showQuit = true
        }
    }

    if (online.active) {
        OnlineChess(online = online, wizard = wizard, onToggleStyle = toggleStyle, onQuit = { showQuit = true }, onClose = {
            online.leave()
            onExit()
        })
    } else {
        LocalChess(players = players, wizard = wizard, onToggleStyle = toggleStyle, onPlayOnline = onHostOnline)
    }

    if (showQuit && online.active) {
        AlertDialog(
            onDismissRequest = { showQuit = false },
            title = { Text(stringResource(R.string.chess_quit_title)) },
            text = { Text(stringResource(R.string.chess_quit_message)) },
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
private fun LocalChess(players: List<String>, wizard: Boolean, onToggleStyle: () -> Unit, onPlayOnline: () -> Unit) {
    val names = listOf(players.getOrElse(0) { "1" }, players.getOrElse(1) { "2" })

    // Tout est gardé quand l'écran tourne (la séance, voir ChessMatchSaver).
    var match by rememberSaveable(stateSaver = ChessMatchSaver) { mutableStateOf(ChessMatch()) }
    var flipped by rememberSaveable { mutableStateOf(false) }
    var showResign by rememberSaveable { mutableStateOf(false) }
    var showDraw by rememberSaveable { mutableStateOf(false) }

    val game = match.game
    val result = match.result
    // Proposé tant que rien n'a été joué : passer à deux téléphones en pleine séance perdrait le score.
    val fresh = match.moves.isEmpty() && match.wins0 + match.wins1 + match.draws == 0

    ChessCard {
        ChessTitle()
        Text(
            text = stringResource(R.string.chess_score, names[0], match.wins0, names[1], match.wins1, match.draws),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.chess_colors, names[match.whiteSeat], names[1 - match.whiteSeat]),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = when {
                result == null -> stringResource(
                    if (game.inCheck) R.string.chess_turn_check else R.string.chess_turn,
                    names[match.currentSeat],
                    colorName(game.position.whiteToMove)
                )
                match.winnerSeat >= 0 -> stringResource(R.string.chess_winner, names[match.winnerSeat])
                else -> stringResource(R.string.chess_draw)
            },
            style = MaterialTheme.typography.titleMedium
        )
        if (result != null) {
            Text(text = reasonText(result.reason), style = MaterialTheme.typography.bodyMedium)
        }
        ChessPlayArea(
            game = game,
            enabled = !match.isOver,
            flipped = flipped,
            wizard = wizard,
            onMove = { move -> match = match.play(move, match.currentSeat) ?: match }
        )
        if (result != null) {
            Button(onClick = { match = match.newRound() ?: match }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.chess_new_round))
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { showResign = true }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.chess_resign))
                }
                OutlinedButton(onClick = { showDraw = true }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.chess_offer_draw))
                }
            }
        }
        OutlinedButton(onClick = { flipped = !flipped }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.chess_flip))
        }
        StyleButton(wizard = wizard, onToggle = onToggleStyle)
        if (fresh) {
            OutlinedButton(onClick = onPlayOnline, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.chess_online_button))
            }
        }
    }

    if (showResign) {
        // Les deux joueurs partagent le téléphone : on demande lequel des deux abandonne.
        AlertDialog(
            onDismissRequest = { showResign = false },
            title = { Text(stringResource(R.string.chess_resign_local_title)) },
            text = {
                Column {
                    for (seat in 0..1) {
                        TextButton(
                            onClick = {
                                showResign = false
                                match = match.resign(seat) ?: match
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(names[seat], style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showResign = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
    if (showDraw) {
        AlertDialog(
            onDismissRequest = { showDraw = false },
            title = { Text(stringResource(R.string.chess_draw_local_title)) },
            text = { Text(stringResource(R.string.chess_draw_local_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDraw = false
                    match = match.agreeDraw() ?: match
                }) {
                    Text(stringResource(R.string.chess_draw_accept))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDraw = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}

// ---------- Chacun sur son téléphone ----------

/** La partie à deux téléphones : attente de l'autre joueur, puis l'échiquier et le score de la séance. */
@Composable
private fun OnlineChess(online: ChessOnline, wizard: Boolean, onToggleStyle: () -> Unit, onQuit: () -> Unit, onClose: () -> Unit) {
    if (online.notice == ChessNotice.HOST_LEFT) {
        ChessCard {
            ChessTitle()
            Text(text = stringResource(R.string.chess_online_host_left), style = MaterialTheme.typography.titleMedium)
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.common_close)) }
        }
        return
    }
    if (!online.ready) {
        ChessCard {
            ChessTitle()
            if (online.notice == ChessNotice.PLAYER_LEFT) {
                Text(
                    text = stringResource(R.string.chess_online_notice_left),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Text(text = stringResource(R.string.chess_online_waiting), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onQuit, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.chess_online_leave))
            }
        }
        return
    }

    var showResign by rememberSaveable { mutableStateOf(false) }

    val match = online.match
    val game = match.game
    val result = match.result
    val me = online.seat
    val names = listOf(online.lobby.getOrElse(0) { "1" }, online.lobby.getOrElse(1) { "2" })
    val opponent = names[1 - me]

    ChessCard {
        ChessTitle()
        Text(
            text = stringResource(
                if (online.playsWhite) R.string.chess_online_you_white else R.string.chess_online_you_black,
                opponent
            ),
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = stringResource(R.string.chess_score, names[0], match.wins0, names[1], match.wins1, match.draws),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = when {
                result == null && online.myTurn -> stringResource(
                    if (game.inCheck) R.string.chess_online_your_turn_check else R.string.chess_online_your_turn,
                    colorName(game.position.whiteToMove)
                )
                result == null -> stringResource(
                    if (game.inCheck) R.string.chess_turn_check else R.string.chess_turn,
                    opponent,
                    colorName(game.position.whiteToMove)
                )
                match.winnerSeat == me -> stringResource(R.string.chess_online_you_win)
                match.winnerSeat >= 0 -> stringResource(R.string.chess_winner, opponent)
                else -> stringResource(R.string.chess_draw)
            },
            style = MaterialTheme.typography.titleMedium
        )
        if (result != null) {
            Text(text = reasonText(result.reason), style = MaterialTheme.typography.bodyMedium)
        }
        // Mes pièces sont toujours en bas de l'échiquier.
        ChessPlayArea(
            game = game,
            enabled = online.myTurn,
            flipped = !online.playsWhite,
            wizard = wizard,
            onMove = online::play
        )
        if (result != null) {
            Button(onClick = { online.newRound() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.chess_new_round))
            }
        } else {
            when (match.drawOffer) {
                1 - me -> {
                    Text(
                        text = stringResource(R.string.chess_draw_offered_to_you, opponent),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = { online.answerDraw(true) }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.chess_draw_accept))
                        }
                        OutlinedButton(onClick = { online.answerDraw(false) }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.chess_draw_decline))
                        }
                    }
                }
                me -> Text(
                    text = stringResource(R.string.chess_draw_offered_by_you),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> Unit
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { showResign = true }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.chess_resign))
                }
                OutlinedButton(
                    onClick = { online.offerDraw() },
                    enabled = match.drawOffer == -1,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.chess_offer_draw))
                }
            }
        }
        StyleButton(wizard = wizard, onToggle = onToggleStyle)
        OutlinedButton(onClick = onQuit, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.chess_online_leave))
        }
    }

    if (showResign) {
        AlertDialog(
            onDismissRequest = { showResign = false },
            title = { Text(stringResource(R.string.chess_resign_title)) },
            text = { Text(stringResource(R.string.chess_resign_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showResign = false
                    online.resign()
                }) {
                    Text(stringResource(R.string.chess_resign), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResign = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}

// ---------- Éléments communs ----------

/** La carte centrée de l'écran, qui défile si le téléphone est petit ou tourné. */
@Composable
private fun ChessCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = cardSurface()),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        }
    }
}

@Composable
private fun ChessTitle() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.game_chess),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.weight(1f, fill = false).semantics { heading() }
        )
        Spacer(modifier = Modifier.width(12.dp))
        // Sur la carte claire, le bouton prend la couleur principale (il est blanc sur l'en-tête des autres parties).
        RulesButton(
            gameId = CHESS_ID,
            gameName = stringResource(R.string.game_chess),
            contentColor = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun colorName(white: Boolean): String =
    stringResource(if (white) R.string.chess_white else R.string.chess_black)

@Composable
private fun reasonText(reason: ChessReason): String = stringResource(
    when (reason) {
        ChessReason.CHECKMATE -> R.string.chess_reason_mate
        ChessReason.STALEMATE -> R.string.chess_reason_stalemate
        ChessReason.INSUFFICIENT_MATERIAL -> R.string.chess_reason_material
        ChessReason.FIFTY_MOVES -> R.string.chess_reason_fifty
        ChessReason.REPETITION -> R.string.chess_reason_repetition
        ChessReason.RESIGNATION -> R.string.chess_reason_resign
        ChessReason.AGREEMENT -> R.string.chess_reason_agreement
    }
)

// Les pièces que choisit un pion qui arrive au bout de l'échiquier.
private val PROMOTION_CHOICES = listOf(
    'q' to R.string.chess_promote_queen,
    'r' to R.string.chess_promote_rook,
    'b' to R.string.chess_promote_bishop,
    'n' to R.string.chess_promote_knight
)

/**
 * L'échiquier et ce qui se fait dessus : toucher une pièce du camp qui joue la sélectionne et montre ses coups, toucher
 * une case marquée joue le coup ([onMove]), avec le choix de la pièce quand un pion est promu. [enabled] : on peut jouer ;
 * [flipped] : les noirs sont en bas.
 */
@Composable
private fun ChessPlayArea(game: ChessGame, enabled: Boolean, flipped: Boolean, wizard: Boolean, onMove: (ChessMove) -> Unit) {
    // Une sélection ne survit pas à un coup joué.
    var selected by rememberSaveable(game.moves.size) { mutableIntStateOf(-1) }
    var promotionFrom by rememberSaveable(game.moves.size) { mutableIntStateOf(-1) }
    var promotionTo by rememberSaveable(game.moves.size) { mutableIntStateOf(-1) }

    fun play(move: ChessMove) {
        selected = -1
        promotionFrom = -1
        promotionTo = -1
        onMove(move)
    }

    fun tap(square: Int) {
        if (!enabled) return
        val moves = if (selected >= 0) game.legalMoves.filter { it.from == selected && it.to == square } else emptyList()
        val piece = game.position.board[square]
        when {
            moves.isNotEmpty() -> {
                if (moves.first().promotion != null) {
                    promotionFrom = selected
                    promotionTo = square
                } else {
                    play(moves.first())
                }
            }
            piece != '.' && Chess.isWhite(piece) == game.position.whiteToMove ->
                selected = if (selected == square) -1 else square
            else -> selected = -1
        }
    }

    ChessBoard(
        game = game,
        flipped = flipped,
        selected = if (enabled) selected else -1,
        enabled = enabled,
        wizard = wizard,
        onTap = ::tap
    )

    if (promotionFrom >= 0 && promotionTo >= 0) {
        val white = game.position.whiteToMove
        AlertDialog(
            onDismissRequest = {
                promotionFrom = -1
                promotionTo = -1
            },
            title = { Text(stringResource(R.string.chess_promotion_title)) },
            text = {
                Column {
                    for ((letter, nameRes) in PROMOTION_CHOICES) {
                        TextButton(
                            onClick = { play(ChessMove(promotionFrom, promotionTo, letter)) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val piece = if (white) letter.uppercaseChar() else letter
                            if (wizard) {
                                Canvas(modifier = Modifier.size(40.dp).clearAndSetSemantics {}) {
                                    drawWizardPiece(piece, Offset.Zero, size.minDimension, shadow = false)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = stringResource(nameRes), style = MaterialTheme.typography.titleLarge)
                            } else {
                                Text(
                                    text = glyphOf(piece) + "  " + stringResource(nameRes),
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    promotionFrom = -1
                    promotionTo = -1
                }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

// L'échiquier garde ses couleurs en thème clair comme en thème sombre : les pièces blanches et noires doivent
// toujours se distinguer des cases.
private val LIGHT_SQUARE = Color(0xFFF0D9B5)
private val DARK_SQUARE = Color(0xFFB58863)
private val SELECTED_SQUARE = Color(0xFFF6F669)
private val LAST_MOVE_SQUARE = Color(0xFFCDD26A)
private val CHECK_SQUARE = Color(0xFFE57373)
private val WHITE_PIECE = Color.White
private val BLACK_PIECE = Color(0xFF1A1A1A)

// Le style sorcier : dalles de marbre blanc givré et noir veiné, dans un cadre sombre à bordure d'argent
// (voir drawWizardTile et drawWizardFrame).
private val WIZARD_FRAME_WIDTH = 18.dp

/** Le bouton qui passe l'échiquier du style classique au style sorcier, et inversement. */
@Composable
private fun StyleButton(wizard: Boolean, onToggle: () -> Unit) {
    OutlinedButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(if (wizard) R.string.chess_style_to_classic else R.string.chess_style_to_wizard))
    }
}

/**
 * L'échiquier de 8 × 8 cases ; [selected] : la case de la pièce choisie (-1 si aucune), dont les coups sont marqués.
 * [wizard] : le style sorcier (statues de pierre, et animation de chaque coup, avec des éclats quand une pièce est prise).
 */
@Composable
private fun ChessBoard(
    game: ChessGame,
    flipped: Boolean,
    selected: Int,
    enabled: Boolean,
    wizard: Boolean,
    onTap: (Int) -> Unit
) {
    val targets = if (selected >= 0) game.legalMoves.filter { it.from == selected }.map { it.to }.toSet() else emptySet()
    val checked = if (game.inCheck) game.position.board.indexOf(if (game.position.whiteToMove) 'K' else 'k') else -1
    val last = game.lastMove
    val selectedText = stringResource(R.string.chess_square_selected)
    val targetText = stringResource(R.string.chess_square_target)

    // L'animation du dernier coup (style sorcier) : la position d'avant le coup sert à savoir ce qui a été pris.
    // Rien ne s'anime à l'ouverture de l'écran, au changement de style ni au début d'une nouvelle manche.
    var fx by remember { mutableStateOf<MoveFx?>(null) }
    val progress = remember { Animatable(1f) }
    var previousBoard by remember { mutableStateOf(game.position.board) }
    var previousCount by remember { mutableIntStateOf(game.moves.size) }
    LaunchedEffect(game.moves.size, wizard) {
        fx = null
        val before = previousBoard
        val countBefore = previousCount
        previousBoard = game.position.board
        previousCount = game.moves.size
        val move = game.lastMove
        if (wizard && move != null && game.moves.size == countBefore + 1) {
            val found = WizardFx.detect(before, move)
            if (found != null) {
                fx = found
                progress.snapTo(0f)
                progress.animateTo(1f, tween(durationMillis = wizardMoveDuration(found), easing = LinearEasing))
                fx = null
            }
        }
    }
    // Pendant l'animation, la case d'arrivée est laissée vide : la pièce y est dessinée par l'animation.
    val animatedSquare = fx?.to ?: -1

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .then(
                    if (wizard) {
                        Modifier
                            .drawBehind { drawWizardFrame(WIZARD_FRAME_WIDTH.toPx()) }
                            .padding(WIZARD_FRAME_WIDTH)
                    } else {
                        Modifier
                    }
                )
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val cell = maxWidth / 8
                val fontSize = with(LocalDensity.current) { (cell * 0.8f).toSp() }
                Column {
                    for (displayRow in 0 until 8) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            for (displayCol in 0 until 8) {
                                val row = if (flipped) 7 - displayRow else displayRow
                                val col = if (flipped) 7 - displayCol else displayCol
                                val square = row * 8 + col
                                val piece = game.position.board[square]
                                val dark = (row + col) % 2 != 0
                                val highlight = when {
                                    square == selected -> SELECTED_SQUARE
                                    square == checked -> CHECK_SQUARE
                                    last != null && (square == last.from || square == last.to) -> LAST_MOVE_SQUARE
                                    else -> null
                                }
                                val squareColor = when {
                                    wizard -> if (dark) WIZARD_DARK_TILE else WIZARD_LIGHT_TILE
                                    highlight != null -> highlight
                                    dark -> DARK_SQUARE
                                    else -> LIGHT_SQUARE
                                }
                                val name = Chess.squareName(square)
                                val description = if (piece == '.') {
                                    stringResource(R.string.chess_square_empty, name)
                                } else {
                                    stringResource(R.string.chess_square_piece, name, stringResource(pieceNameRes(piece)))
                                }
                                val state = when {
                                    square == selected -> selectedText
                                    square in targets -> targetText
                                    else -> null
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .background(squareColor)
                                        .drawBehind { if (wizard) drawWizardTile(square, dark, highlight) }
                                        .semantics {
                                            contentDescription = description
                                            if (state != null) stateDescription = state
                                        }
                                        .clickable(enabled = enabled, role = Role.Button) { onTap(square) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (piece != '.' && square != animatedSquare) ChessPiece(piece, fontSize, wizard)
                                    if (square in targets) {
                                        if (piece == '.') {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize(0.3f)
                                                    .clip(CircleShape)
                                                    .background(Color(0x66000000))
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .border(3.dp, Color(0x88000000), CircleShape)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                // Le coup en cours d'animation, dessiné par-dessus les cases (sans gêner les touches).
                val playing = fx
                if (playing != null) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        drawMoveFx(playing, progress.value, flipped)
                    }
                }
            }
        }
    }
}

/**
 * Une pièce : en style sorcier, une statue dessinée (voir [drawWizardPiece]) ; sinon le même dessin pour les deux camps,
 * rempli en blanc ou en noir, cerné de noir pour les blancs.
 */
@Composable
private fun ChessPiece(piece: Char, fontSize: TextUnit, wizard: Boolean) {
    if (wizard) {
        // La case décrit déjà la pièce : le dessin n'est pas lu une deuxième fois par TalkBack.
        Canvas(modifier = Modifier.fillMaxSize().clearAndSetSemantics {}) {
            drawWizardPiece(piece, Offset.Zero, size.minDimension)
        }
        return
    }
    val glyph = glyphOf(piece)
    val white = Chess.isWhite(piece)
    // La case décrit déjà la pièce : le dessin n'est pas lu une deuxième fois par TalkBack.
    Box(contentAlignment = Alignment.Center, modifier = Modifier.clearAndSetSemantics {}) {
        if (white) {
            Text(
                text = glyph,
                fontSize = fontSize,
                style = TextStyle(drawStyle = Stroke(width = 5f, join = StrokeJoin.Round), color = BLACK_PIECE)
            )
        }
        Text(text = glyph, fontSize = fontSize, color = if (white) WHITE_PIECE else BLACK_PIECE)
    }
}

// Les figurines pleines (♚ ♛ ♜ ♝ ♞ ♟) servent aux deux camps ; le sélecteur de variante force le dessin en texte
// (sinon certains téléphones affichent le pion noir en émoji).
private fun glyphOf(piece: Char): String = when (piece.uppercaseChar()) {
    'K' -> "\u265A\uFE0E"
    'Q' -> "\u265B\uFE0E"
    'R' -> "\u265C\uFE0E"
    'B' -> "\u265D\uFE0E"
    'N' -> "\u265E\uFE0E"
    else -> "\u265F\uFE0E"
}

private fun pieceNameRes(piece: Char): Int = when (piece) {
    'K' -> R.string.chess_piece_wk
    'Q' -> R.string.chess_piece_wq
    'R' -> R.string.chess_piece_wr
    'B' -> R.string.chess_piece_wb
    'N' -> R.string.chess_piece_wn
    'P' -> R.string.chess_piece_wp
    'k' -> R.string.chess_piece_bk
    'q' -> R.string.chess_piece_bq
    'r' -> R.string.chess_piece_br
    'b' -> R.string.chess_piece_bb
    'n' -> R.string.chess_piece_bn
    else -> R.string.chess_piece_bp
}
