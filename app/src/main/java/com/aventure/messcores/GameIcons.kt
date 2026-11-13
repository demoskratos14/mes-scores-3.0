package com.aventure.messcores

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** Nombre de jeux par ligne dans la grille. */
private const val GRID_COLUMNS = 3

/** Jeux jouables qui ont une icône dessinée ci-dessous (un jeu sans icône reçoit une icône générique). */
internal val GAME_ICON_IDS = setOf(
    MASTERMIND_ID, MORPION_ID, CONNECT4_ID, BATTLESHIP_ID, NIM_ID, PFC_ID, REFLEX_ID, CIRCLES_ID, SIMON_ID, RHYTHM_ID, TETRIS_ID, BREAKOUT_ID, ASTEROIDS_ID, INVADERS_ID, SHMUP_ID, XONIX_ID, SNAKE_ID, BUBBLE_ID, PANCAKE_ID,
    GOMOKU_ID, DOTS_ID, DRAUGHTS_ID, OTHELLO_ID, HANGMAN_ID, CHESS_ID, UNO_ID, FLIP7_ID, SKYJO_ACTION_ID
)

/**
 * Grille de jeux : une case par jeu, avec sa petite icône dessinée et son nom. Une seule case est
 * choisie à la fois (boutons radio pour TalkBack : le nom et les téléphones sont lus avec la case).
 */
@Composable
internal fun GameIconGrid(
    title: String,
    games: List<GameRules>,
    selectedId: String,
    onSelect: (GameRules) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            games.chunked(GRID_COLUMNS).forEach { rowGames ->
                // IntrinsicSize.Min : toutes les cases d'une ligne ont la hauteur de la plus haute (nom sur 2 lignes).
                Row(
                    modifier = Modifier.height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowGames.forEach { game ->
                        GameTile(
                            game = game,
                            selected = game.id == selectedId,
                            onClick = { onSelect(game) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Dernière ligne incomplète : on garde la largeur des cases.
                    repeat(GRID_COLUMNS - rowGames.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun GameTile(game: GameRules, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(shape)
            .background(if (selected) colors.primaryContainer else Color.Transparent)
            .border(if (selected) 3.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        GameIcon(game.id)
        Text(
            text = game.name,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            color = if (selected) colors.onPrimaryContainer else colors.onSurface
        )
        if (game.isMultiPhone()) PhoneIcons(multi = true)
    }
}

/** Petite icône dessinée d'un jeu (le nom, lu à côté, suffit à TalkBack : l'image est décorative). */
@Composable
internal fun GameIcon(gameId: String, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    Canvas(modifier = modifier.size(size).clearAndSetSemantics {}) {
        when (gameId) {
            MASTERMIND_ID -> drawMastermind()
            MORPION_ID -> drawTicTacToe()
            CONNECT4_ID -> drawConnect4()
            BATTLESHIP_ID -> drawBattleship()
            NIM_ID -> drawNim()
            PFC_ID -> drawRockPaperScissors()
            REFLEX_ID -> drawReflex()
            CIRCLES_ID -> drawCircles()
            SIMON_ID -> drawSimon()
            RHYTHM_ID -> drawRhythm()
            TETRIS_ID -> drawTetris()
            BREAKOUT_ID -> drawBreakout()
            SHMUP_ID -> drawShmup()
            ASTEROIDS_ID -> drawAsteroids()
            INVADERS_ID -> drawInvaders()
            XONIX_ID -> drawXonix()
            SNAKE_ID -> drawSnake()
            BUBBLE_ID -> drawBubbleShooter()
            PANCAKE_ID -> drawPancake()
            GOMOKU_ID -> drawGomoku()
            DOTS_ID -> drawDots()
            DRAUGHTS_ID -> drawDraughts()
            OTHELLO_ID -> drawOthello()
            HANGMAN_ID -> drawHangman()
            CHESS_ID -> drawChess()
            UNO_ID -> drawUno()
            FLIP7_ID -> drawFlip7()
            SKYJO_ACTION_ID -> drawSkyjoAction()
            else -> drawGeneric()
        }
    }
}

// ---------- Dessins : chaque icône a son propre fond, lisible en thème clair comme en thème sombre ----------

private fun DrawScope.paper(color: Color) {
    drawRoundRect(color, cornerRadius = CornerRadius(size.minDimension * 0.18f))
}

private fun DrawScope.drawGeneric() {
    paper(Color(0xFFCFD8DC))
    val s = size.minDimension
    drawCircle(Color(0xFF607D8B), radius = s * 0.22f, center = Offset(s / 2, s / 2))
}

private fun DrawScope.drawTicTacToe() {
    paper(Color(0xFFFFF8E1))
    val s = size.minDimension
    val ink = Color(0xFF455A64)
    val margin = s * 0.16f
    val cell = (s - 2 * margin) / 3f
    val line = s * 0.045f
    for (i in 1..2) {
        drawLine(ink, Offset(margin + cell * i, margin), Offset(margin + cell * i, s - margin), line, StrokeCap.Round)
        drawLine(ink, Offset(margin, margin + cell * i), Offset(s - margin, margin + cell * i), line, StrokeCap.Round)
    }
    fun center(col: Int, row: Int) = Offset(margin + cell * (col + 0.5f), margin + cell * (row + 0.5f))
    val mark = cell * 0.13f
    // Croix bleues et ronds rouges, comme dans le jeu.
    listOf(0 to 0, 2 to 1, 1 to 2).forEach { (c, r) ->
        drawMorpionCross(center(c, r), cell * 0.5f * 0.8f - mark / 2f, MORPION_BLUE, mark)
    }
    listOf(1 to 1, 0 to 2, 2 to 0).forEach { (c, r) ->
        drawCircle(MORPION_RED, radius = cell * 0.5f * 0.8f - mark / 2f, center = center(c, r), style = Stroke(mark))
    }
}

private fun DrawScope.drawConnect4() {
    paper(Color(0xFF1E4DB7))
    val s = size.minDimension
    val rows = listOf("....", ".Y..", ".RY.", "RYRR")
    val margin = s * 0.1f
    val cell = (s - 2 * margin) / 4f
    rows.forEachIndexed { r, line ->
        line.forEachIndexed { c, ch ->
            val color = when (ch) {
                'R' -> Color(0xFFE53935)
                'Y' -> Color(0xFFFDD835)
                else -> Color(0xFFE3F2FD)
            }
            drawCircle(color, radius = cell * 0.4f, center = Offset(margin + cell * (c + 0.5f), margin + cell * (r + 0.5f)))
        }
    }
}

private fun DrawScope.drawBattleship() {
    paper(Color(0xFF1565C0))
    val s = size.minDimension
    val margin = s * 0.1f
    val cell = (s - 2 * margin) / 5f
    val grid = Color.White.copy(alpha = 0.35f)
    for (i in 0..5) {
        drawLine(grid, Offset(margin + cell * i, margin), Offset(margin + cell * i, s - margin), s * 0.02f)
        drawLine(grid, Offset(margin, margin + cell * i), Offset(s - margin, margin + cell * i), s * 0.02f)
    }
    // Un bateau de 3 cases, touché au milieu, et un tir dans l'eau.
    drawRoundRect(
        Color(0xFFB0BEC5),
        topLeft = Offset(margin + cell * 0.7f, margin + cell * 1.2f),
        size = Size(cell * 2.6f, cell * 0.6f),
        cornerRadius = CornerRadius(cell * 0.3f)
    )
    drawCircle(Color(0xFFE53935), radius = cell * 0.3f, center = Offset(margin + cell * 2.0f, margin + cell * 1.5f))
    drawCircle(Color.White, radius = cell * 0.16f, center = Offset(margin + cell * 3.5f, margin + cell * 3.5f))
    drawCircle(Color(0xFFE53935), radius = cell * 0.3f, center = Offset(margin + cell * 0.5f, margin + cell * 4.5f))
}

private fun DrawScope.drawNim() {
    paper(Color(0xFFFFF8E1))
    val s = size.minDimension
    val stickWidth = s * 0.05f
    val height = s * 0.3f
    val count = 7
    val spacing = s * 0.11f
    val firstX = s / 2 - (count - 1) / 2f * spacing
    // Une seule ligne d'allumettes ; deux ont glissé en haut, deux en bas, face à leur place.
    val shift = mapOf(0 to -1, 1 to -1, 5 to 1, 6 to 1)
    repeat(count) { i ->
        val x = firstX + i * spacing
        val top = s * 0.35f + (shift[i] ?: 0) * s * 0.3f
        drawLine(Color(0xFFD7A86E), Offset(x, top), Offset(x, top + height), stickWidth, StrokeCap.Round)
        drawCircle(Color(0xFFD32F2F), radius = stickWidth * 0.95f, center = Offset(x, top))
    }
}

private fun DrawScope.drawReflex() {
    paper(Color(0xFFECEFF1))
    val s = size.minDimension
    // Un feu : le rouge est éteint, le vert s'allume.
    drawRoundRect(
        Color(0xFF37474F),
        topLeft = Offset(s * 0.32f, s * 0.12f),
        size = Size(s * 0.36f, s * 0.76f),
        cornerRadius = CornerRadius(s * 0.1f)
    )
    drawCircle(Color(0xFF7F2A2A), radius = s * 0.11f, center = Offset(s * 0.5f, s * 0.3f))
    drawCircle(Color(0xFF4CAF50), radius = s * 0.11f, center = Offset(s * 0.5f, s * 0.68f))
    // Un éclair de chaque côté : tout se joue en un instant.
    for (side in listOf(-1f, 1f)) {
        val x = s * 0.5f + side * s * 0.33f
        drawLine(Color(0xFFFFB300), Offset(x, s * 0.3f), Offset(x - side * s * 0.06f, s * 0.5f), s * 0.035f, StrokeCap.Round)
        drawLine(Color(0xFFFFB300), Offset(x - side * s * 0.06f, s * 0.5f), Offset(x + side * s * 0.02f, s * 0.7f), s * 0.035f, StrokeCap.Round)
    }
}

private fun DrawScope.drawCircles() {
    paper(Color(0xFFE8EAF6))
    val s = size.minDimension
    // Trois ronds de tailles différentes : le plus petit est sur le point de disparaître.
    drawCircle(Color(0xFFE53935), radius = s * 0.2f, center = Offset(s * 0.34f, s * 0.36f))
    drawCircle(Color.White.copy(alpha = 0.85f), radius = s * 0.08f, center = Offset(s * 0.34f, s * 0.36f))
    drawCircle(Color(0xFF1E88E5), radius = s * 0.14f, center = Offset(s * 0.7f, s * 0.5f))
    drawCircle(Color.White.copy(alpha = 0.85f), radius = s * 0.056f, center = Offset(s * 0.7f, s * 0.5f))
    drawCircle(Color(0xFF43A047), radius = s * 0.08f, center = Offset(s * 0.4f, s * 0.74f))
    drawCircle(Color.White.copy(alpha = 0.85f), radius = s * 0.032f, center = Offset(s * 0.4f, s * 0.74f))
}

private fun DrawScope.drawSimon() {
    paper(Color(0xFF263238))
    val s = size.minDimension
    // Les quatre touches : vert, rouge, jaune, bleu ; la verte est allumée.
    val pad = s * 0.36f
    val gap = s * 0.04f
    val left = s * 0.5f - pad - gap / 2f
    val top = left
    val corner = CornerRadius(s * 0.1f)
    drawRoundRect(Color(0xFF43D675), Offset(left, top), Size(pad, pad), corner)
    drawRoundRect(Color(0xFF9E2A27), Offset(left + pad + gap, top), Size(pad, pad), corner)
    drawRoundRect(Color(0xFF9C7F10), Offset(left, top + pad + gap), Size(pad, pad), corner)
    drawRoundRect(Color(0xFF1F4FA0), Offset(left + pad + gap, top + pad + gap), Size(pad, pad), corner)
}

private fun DrawScope.drawTetris() {
    paper(Color(0xFF1A1F2B))
    val s = size.minDimension
    // Une pile de blocs dans un puits, et un « T » violet qui tombe.
    val unit = s * 0.15f
    val left = s * 0.2f
    val bottom = s * 0.86f
    val corner = CornerRadius(unit * 0.15f)
    fun block(col: Int, row: Int, color: Color) = drawRoundRect(
        color = color,
        topLeft = Offset(left + col * unit + unit * 0.04f, bottom - (row + 1) * unit + unit * 0.04f),
        size = Size(unit * 0.92f, unit * 0.92f),
        cornerRadius = corner
    )
    val red = Color(0xFFE53935)
    val cyan = Color(0xFF00BCD4)
    val orange = Color(0xFFFB8C00)
    val green = Color(0xFF43A047)
    listOf(red, red, cyan, cyan).forEachIndexed { col, color -> block(col, 0, color) }
    listOf(orange, green, green).forEachIndexed { col, color -> block(col, 1, color) }
    val purple = Color(0xFF8E24AA)
    block(1, 3, purple)
    block(0, 4, purple)
    block(1, 4, purple)
    block(2, 4, purple)
}

private fun DrawScope.drawBreakout() {
    paper(Color(0xFF0B1E2B))
    val s = size.minDimension
    // Trois rangées de briques arc-en-ciel en haut, une balle chromée et la raquette aux embouts rouges en bas.
    val colors = listOf(Color(0xFFE53935), Color(0xFFFDD835), Color(0xFF1E88E5))
    val brickW = s * 0.2f
    val brickH = s * 0.1f
    val left = s * 0.5f - brickW * 2f
    val corner = CornerRadius(brickH * 0.2f)
    colors.forEachIndexed { row, color ->
        for (col in 0 until 4) {
            drawRoundRect(
                color = color,
                topLeft = Offset(left + col * brickW + s * 0.01f, s * 0.16f + row * brickH + s * 0.01f),
                size = Size(brickW - s * 0.02f, brickH - s * 0.02f),
                cornerRadius = corner
            )
        }
    }
    drawCircle(Color(0xFFE3E9ED), s * 0.055f, Offset(s * 0.58f, s * 0.6f))
    val paddleW = s * 0.36f
    val paddleH = s * 0.07f
    val paddleLeft = s * 0.5f - paddleW / 2f
    val paddleTop = s * 0.8f
    drawRoundRect(Color(0xFFAAB6BE), Offset(paddleLeft, paddleTop), Size(paddleW, paddleH), CornerRadius(paddleH * 0.4f))
    drawRoundRect(Color(0xFFD32F2F), Offset(paddleLeft, paddleTop), Size(paddleW * 0.16f, paddleH), CornerRadius(paddleH * 0.4f))
    drawRoundRect(Color(0xFFD32F2F), Offset(paddleLeft + paddleW * 0.84f, paddleTop), Size(paddleW * 0.16f, paddleH), CornerRadius(paddleH * 0.4f))
}

private fun DrawScope.drawAsteroids() {
    paper(Color(0xFF0B1E2B))
    val s = size.minDimension
    // Deux roches grises (une grosse en haut à gauche, une petite en bas à droite), un tir jaune et le vaisseau pointé vers le haut.
    fun rock(cx: Float, cy: Float, r: Float) {
        val path = Path()
        for (i in 0 until 9) {
            val a = i * 6.2831855f / 9f
            val k = if (i % 2 == 0) 1f else 0.82f
            val x = cx + sin(a) * r * k
            val y = cy - cos(a) * r * k
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, Color(0xFF8D8378))
        drawPath(path, Color(0xFFD0C6B8), style = Stroke(width = s * 0.02f))
    }
    rock(s * 0.3f, s * 0.3f, s * 0.2f)
    rock(s * 0.78f, s * 0.74f, s * 0.1f)
    drawCircle(Color(0xFFFFF176), s * 0.03f, Offset(s * 0.5f, s * 0.34f))
    val ship = Path().apply {
        moveTo(s * 0.5f, s * 0.48f)
        lineTo(s * 0.64f, s * 0.84f)
        lineTo(s * 0.5f, s * 0.76f)
        lineTo(s * 0.36f, s * 0.84f)
        close()
    }
    drawPath(ship, Color(0xFFB7C7DB))
    drawCircle(Color(0xFF4DD0E1), s * 0.035f, Offset(s * 0.5f, s * 0.64f))
}

private fun DrawScope.drawInvaders() {
    paper(Color(0xFF0B1E2B))
    val s = size.minDimension
    // Un envahisseur en pixels, un tir jaune et le vaisseau pointé vers le haut.
    val rows = listOf("  #     #  ", "   #   #   ", "  #######  ", " ## ### ## ", "###########", "# ####### #", "# #     # #", "   ## ##   ")
    val cell = s * 0.065f
    val left = (s - cell * 11f) / 2f
    val top = s * 0.14f
    for ((r, line) in rows.withIndex()) {
        for ((c, ch) in line.withIndex()) {
            if (ch == '#') drawRect(Color(0xFF81C784), topLeft = Offset(left + c * cell, top + r * cell), size = Size(cell + 0.5f, cell + 0.5f))
        }
    }
    drawRect(Color(0xFFFFF176), topLeft = Offset(s * 0.49f, s * 0.64f), size = Size(s * 0.02f, s * 0.07f))
    val ship = Path().apply {
        moveTo(s * 0.5f, s * 0.74f)
        lineTo(s * 0.63f, s * 0.9f)
        lineTo(s * 0.5f, s * 0.85f)
        lineTo(s * 0.37f, s * 0.9f)
        close()
    }
    drawPath(ship, Color(0xFFB7C7DB))
}

private fun DrawScope.drawXonix() {
    paper(Color(0xFF0D2B52))
    val s = size.minDimension
    // Un cadre de terre ferme autour de la mer, un tracé jaune qui en part et tourne, le joueur au départ et une bille rouge.
    val land = Color(0xFF4E9F5B)
    val frame = s * 0.1f
    val band = s * 0.12f
    val inner = s - 2f * frame
    drawRect(land, Offset(frame, frame), Size(inner, band))
    drawRect(land, Offset(frame, s - frame - band), Size(inner, band))
    drawRect(land, Offset(frame, frame), Size(band, inner))
    drawRect(land, Offset(s - frame - band, frame), Size(band, inner))
    val trail = Color(0xFFFFD54F)
    val line = s * 0.07f
    val trailX = s * 0.36f
    val trailBottom = s * 0.62f
    drawLine(trail, Offset(trailX, frame + band), Offset(trailX, trailBottom), strokeWidth = line)
    drawLine(trail, Offset(trailX - line / 2f, trailBottom), Offset(frame + band, trailBottom), strokeWidth = line)
    val player = s * 0.11f
    drawRect(Color.White, Offset(trailX - player / 2f, frame + band - player / 2f), Size(player, player))
    drawCircle(Color.White, s * 0.1f, Offset(s * 0.66f, s * 0.42f))
    drawCircle(Color(0xFFE53935), s * 0.075f, Offset(s * 0.66f, s * 0.42f))
}

private fun DrawScope.drawSnake() {
    paper(Color(0xFF1B2A1F))
    val s = size.minDimension
    // Un serpent vert qui serpente sur un damier, et une petite souris grise devant sa tête.
    val unit = s * 0.15f
    val left = s * 0.125f
    val top = s * 0.12f
    val corner = CornerRadius(unit * 0.3f)
    fun block(col: Int, row: Int, color: Color) = drawRoundRect(
        color = color,
        topLeft = Offset(left + col * unit + unit * 0.05f, top + row * unit + unit * 0.05f),
        size = Size(unit * 0.9f, unit * 0.9f),
        cornerRadius = corner
    )
    val body = Color(0xFFC8652F)
    val light = Color(0xFFE09050)
    listOf(0 to 0, 1 to 0, 2 to 0, 3 to 0, 3 to 1, 3 to 2, 2 to 2, 1 to 2, 1 to 3, 1 to 4).forEachIndexed { i, (c, r) ->
        block(c, r, if (i % 2 == 0) body else light)
    }
    block(2, 4, Color(0xFFDC8E52))
    val tongue = Color(0xFFFF6FA3)
    val tongueFrom = Offset(left + 3 * unit - unit * 0.05f, top + 4.5f * unit)
    drawLine(tongue, tongueFrom, tongueFrom + Offset(unit * 0.3f, 0f), strokeWidth = unit * 0.1f)
    drawLine(tongue, tongueFrom + Offset(unit * 0.3f, 0f), tongueFrom + Offset(unit * 0.5f, -unit * 0.14f), strokeWidth = unit * 0.1f)
    drawLine(tongue, tongueFrom + Offset(unit * 0.3f, 0f), tongueFrom + Offset(unit * 0.5f, unit * 0.14f), strokeWidth = unit * 0.1f)
    drawCircle(Color.White, unit * 0.12f, Offset(left + 2 * unit + unit * 0.65f, top + 4 * unit + unit * 0.3f))
    drawCircle(Color.White, unit * 0.12f, Offset(left + 2 * unit + unit * 0.65f, top + 4 * unit + unit * 0.7f))
    // La souris.
    val mx = left + 4.5f * unit
    val my = top + 4.5f * unit
    drawCircle(Color(0xFFB8B0A8), unit * 0.42f, Offset(mx, my))
    drawCircle(Color(0xFFB8B0A8), unit * 0.22f, Offset(mx - unit * 0.3f, my - unit * 0.34f))
    drawCircle(Color(0xFFB8B0A8), unit * 0.22f, Offset(mx + unit * 0.3f, my - unit * 0.34f))
    drawCircle(Color(0xFFF4A6B8), unit * 0.12f, Offset(mx - unit * 0.3f, my - unit * 0.34f))
    drawCircle(Color(0xFFF4A6B8), unit * 0.12f, Offset(mx + unit * 0.3f, my - unit * 0.34f))
}

private fun DrawScope.drawPancake() {
    paper(Color(0xFFB8BCA4))
    val s = size.minDimension
    val ink = Color(0xFF2A2724)
    val red = Color(0xFFB5503C)
    // Le rideau rouge en haut, comme sur l'écran de la console d'origine.
    drawRect(red, Offset(s * 0.1f, s * 0.12f), Size(s * 0.8f, s * 0.07f))
    // La poêle à gauche (un manche et un rond) et la crêpe qui en part en arc vers l'assiette de droite.
    drawLine(ink, Offset(s * 0.12f, s * 0.62f), Offset(s * 0.27f, s * 0.58f), strokeWidth = s * 0.04f, cap = StrokeCap.Round)
    drawOval(ink, Offset(s * 0.27f, s * 0.55f), Size(s * 0.17f, s * 0.07f))
    val arc = Path().apply {
        moveTo(s * 0.35f, s * 0.5f)
        quadraticTo(s * 0.55f, s * 0.05f, s * 0.8f, s * 0.58f)
    }
    drawPath(arc, ink.copy(alpha = 0.55f), style = Stroke(width = s * 0.018f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(s * 0.04f, s * 0.04f))))
    drawOval(ink, Offset(s * 0.47f, s * 0.26f), Size(s * 0.15f, s * 0.06f))
    // Le serveur : un corps sombre portant une assiette où atterrit la crêpe.
    drawOval(ink, Offset(s * 0.64f, s * 0.6f), Size(s * 0.26f, s * 0.05f))
    drawRect(ink, Offset(s * 0.74f, s * 0.65f), Size(s * 0.06f, s * 0.12f))
    drawRect(ink, Offset(s * 0.7f, s * 0.77f), Size(s * 0.14f, s * 0.05f))
    // La crêpe posée sur l'assiette.
    drawOval(red, Offset(s * 0.69f, s * 0.55f), Size(s * 0.16f, s * 0.06f))
}

private fun DrawScope.drawBubbleShooter() {
    paper(Color(0xFF14213D))
    val s = size.minDimension
    val r = s * 0.085f
    val colors = listOf(Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFFDD835), Color(0xFF8E24AA))
    fun bubble(x: Float, y: Float, color: Color) {
        val center = Offset(s * x, s * y)
        drawCircle(color, r, center)
        drawCircle(Color.Black.copy(alpha = 0.35f), r, center, style = Stroke(r * 0.1f))
        drawCircle(Color.White.copy(alpha = 0.55f), r * 0.2f, center - Offset(r * 0.35f, r * 0.35f))
    }
    // Un tas de bulles accrochées en haut : trois rangées qui s'emboîtent, la deuxième décalée d'une demi-bulle.
    val step = 0.18f
    for (i in 0 until 5) bubble(0.14f + step * i, 0.15f, colors[i % colors.size])
    for (i in 0 until 4) bubble(0.23f + step * i, 0.306f, colors[(i + 2) % colors.size])
    for (i in 0 until 3) bubble(0.32f + step * i, 0.462f, colors[(i + 4) % colors.size])
    // Le guide de visée en pointillés, une bulle prête à partir et le lanceur.
    for (i in 0 until 3) drawCircle(Color.White.copy(alpha = 0.6f), s * 0.014f, Offset(s * 0.5f, s * (0.6f + 0.07f * i)))
    drawCircle(Color(0xFF546E7A), s * 0.1f, Offset(s * 0.5f, s * 0.9f))
    bubble(0.5f, 0.9f, colors[1])
}

private fun DrawScope.drawShmup() {
    paper(Color(0xFF0A1430))
    val s = size.minDimension
    // Quelques étoiles, un ennemi qui descend, un tir et le vaisseau pointé vers le haut.
    listOf(0.2f to 0.2f, 0.8f to 0.3f, 0.15f to 0.65f, 0.85f to 0.7f).forEach { (x, y) ->
        drawCircle(Color.White.copy(alpha = 0.6f), s * 0.015f, Offset(s * x, s * y))
    }
    val enemy = Path().apply {
        moveTo(s * 0.5f, s * 0.3f)
        lineTo(s * 0.4f, s * 0.12f)
        lineTo(s * 0.6f, s * 0.12f)
        close()
    }
    drawPath(enemy, Color(0xFFE0527A))
    drawRoundRect(Color(0xFF7FE8FF), Offset(s * 0.485f, s * 0.38f), Size(s * 0.03f, s * 0.12f), CornerRadius(s * 0.015f))
    val ship = Path().apply {
        moveTo(s * 0.5f, s * 0.55f)
        lineTo(s * 0.66f, s * 0.84f)
        lineTo(s * 0.5f, s * 0.76f)
        lineTo(s * 0.34f, s * 0.84f)
        close()
    }
    drawPath(ship, Color(0xFFC5CAE9))
}

private fun DrawScope.drawRhythm() {
    paper(Color(0xFF1A1F2B))
    val s = size.minDimension
    // Quatre pistes, une note qui tombe sur chacune et la ligne de frappe en bas.
    val colors = listOf(Color(0xFF43A047), Color(0xFFE53935), Color(0xFFFDD835), Color(0xFF1E88E5))
    val tops = listOf(0.52f, 0.22f, 0.4f, 0.12f)
    for (i in 0 until 4) {
        val x = s * (0.2f + 0.2f * i)
        drawCircle(colors[i], radius = s * 0.07f, center = Offset(x, s * tops[i]))
        drawCircle(Color.White.copy(alpha = 0.55f), radius = s * 0.07f, center = Offset(x, s * 0.78f), style = Stroke(s * 0.02f))
    }
    drawLine(Color.White.copy(alpha = 0.6f), Offset(s * 0.08f, s * 0.78f), Offset(s * 0.92f, s * 0.78f), s * 0.015f)
}

private fun DrawScope.drawRockPaperScissors() {
    paper(Color(0xFFE8F5E9))
    val s = size.minDimension
    // Pierre.
    drawCircle(Color(0xFF78909C), radius = s * 0.15f, center = Offset(s * 0.3f, s * 0.32f))
    // Feuille.
    val paperTop = Offset(s * 0.5f, s * 0.14f)
    drawRoundRect(Color.White, topLeft = paperTop, size = Size(s * 0.28f, s * 0.34f), cornerRadius = CornerRadius(s * 0.03f))
    drawRoundRect(
        Color(0xFF90A4AE), topLeft = paperTop, size = Size(s * 0.28f, s * 0.34f),
        cornerRadius = CornerRadius(s * 0.03f), style = Stroke(s * 0.025f)
    )
    for (i in 1..3) {
        val y = paperTop.y + s * 0.07f * i
        drawLine(Color(0xFFB0BEC5), Offset(paperTop.x + s * 0.05f, y), Offset(paperTop.x + s * 0.23f, y), s * 0.02f, StrokeCap.Round)
    }
    // Ciseaux.
    val red = Color(0xFFE53935)
    drawLine(red, Offset(s * 0.34f, s * 0.58f), Offset(s * 0.62f, s * 0.84f), s * 0.05f, StrokeCap.Round)
    drawLine(red, Offset(s * 0.62f, s * 0.58f), Offset(s * 0.34f, s * 0.84f), s * 0.05f, StrokeCap.Round)
    drawCircle(red, radius = s * 0.05f, center = Offset(s * 0.34f, s * 0.86f), style = Stroke(s * 0.025f))
    drawCircle(red, radius = s * 0.05f, center = Offset(s * 0.62f, s * 0.86f), style = Stroke(s * 0.025f))
}

private fun DrawScope.drawGomoku() {
    paper(Color(0xFFE2B875))
    val s = size.minDimension
    val margin = s * 0.16f
    val step = (s - 2 * margin) / 4f
    val ink = Color(0xFF5D4037)
    for (i in 0..4) {
        drawLine(ink, Offset(margin + step * i, margin), Offset(margin + step * i, s - margin), s * 0.02f)
        drawLine(ink, Offset(margin, margin + step * i), Offset(s - margin, margin + step * i), s * 0.02f)
    }
    fun point(c: Int, r: Int) = Offset(margin + step * c, margin + step * r)
    listOf(0 to 0, 1 to 1, 2 to 2, 3 to 3).forEach { (c, r) -> drawCircle(Color(0xFF212121), step * 0.42f, point(c, r)) }
    listOf(1 to 2, 2 to 1, 3 to 1, 2 to 3).forEach { (c, r) ->
        drawCircle(Color(0xFFFAFAFA), step * 0.42f, point(c, r))
        drawCircle(Color(0xFF9E9E9E), step * 0.42f, point(c, r), style = Stroke(s * 0.012f))
    }
}

private fun DrawScope.drawDots() {
    paper(Color(0xFFFFF8E1))
    val s = size.minDimension
    val margin = s * 0.2f
    val step = (s - 2 * margin) / 3f
    fun point(c: Int, r: Int) = Offset(margin + step * c, margin + step * r)
    val blue = Color(0xFF1976D2)
    val red = Color(0xFFD32F2F)
    // Une case fermée (bleue) et quelques traits posés.
    drawRect(blue.copy(alpha = 0.25f), topLeft = point(0, 0), size = Size(step, step))
    val width = s * 0.05f
    listOf(0 to 0, 0 to 1).forEach { (c, r) -> drawLine(blue, point(c, r), point(c + 1, r), width, StrokeCap.Round) }
    listOf(0 to 0, 1 to 0).forEach { (c, r) -> drawLine(blue, point(c, r), point(c, r + 1), width, StrokeCap.Round) }
    drawLine(red, point(1, 1), point(2, 1), width, StrokeCap.Round)
    drawLine(red, point(2, 1), point(2, 2), width, StrokeCap.Round)
    drawLine(red, point(1, 2), point(1, 3), width, StrokeCap.Round)
    for (c in 0..3) for (r in 0..3) drawCircle(Color(0xFF37474F), radius = s * 0.045f, center = point(c, r))
}

/** Damier de [n] cases de côté posé dans le cadre déjà peint. */
private fun DrawScope.checkerboard(n: Int, light: Color, dark: Color, margin: Float): Float {
    val cell = (size.minDimension - 2 * margin) / n
    for (r in 0 until n) for (c in 0 until n) {
        drawRect(
            if ((r + c) % 2 == 0) light else dark,
            topLeft = Offset(margin + cell * c, margin + cell * r),
            size = Size(cell, cell)
        )
    }
    return cell
}

private fun DrawScope.drawDraughts() {
    paper(Color(0xFF5D4037))
    val s = size.minDimension
    val margin = s * 0.08f
    val cell = checkerboard(4, Color(0xFFF0D9B5), Color(0xFF8B5A2B), margin)
    fun center(c: Int, r: Int) = Offset(margin + cell * (c + 0.5f), margin + cell * (r + 0.5f))
    // Pions sur les cases foncées : rouges en haut, crème en bas.
    listOf(1 to 0, 2 to 1).forEach { (c, r) ->
        drawCircle(Color(0xFFD32F2F), cell * 0.38f, center(c, r))
        drawCircle(Color(0xFF8E0000), cell * 0.24f, center(c, r), style = Stroke(s * 0.015f))
    }
    listOf(0 to 3, 3 to 2).forEach { (c, r) ->
        drawCircle(Color(0xFFFFF3E0), cell * 0.38f, center(c, r))
        drawCircle(Color(0xFFBCAAA4), cell * 0.24f, center(c, r), style = Stroke(s * 0.015f))
    }
}

private fun DrawScope.drawOthello() {
    paper(Color(0xFF1B5E20))
    val s = size.minDimension
    val margin = s * 0.1f
    val cell = (s - 2 * margin) / 4f
    drawRect(Color(0xFF2E7D32), topLeft = Offset(margin, margin), size = Size(cell * 4, cell * 4))
    for (i in 0..4) {
        drawLine(Color(0xFF1B5E20), Offset(margin + cell * i, margin), Offset(margin + cell * i, margin + cell * 4), s * 0.02f)
        drawLine(Color(0xFF1B5E20), Offset(margin, margin + cell * i), Offset(margin + cell * 4, margin + cell * i), s * 0.02f)
    }
    fun center(c: Int, r: Int) = Offset(margin + cell * (c + 0.5f), margin + cell * (r + 0.5f))
    listOf(1 to 1, 2 to 2, 1 to 0).forEach { (c, r) -> drawCircle(Color(0xFF212121), cell * 0.4f, center(c, r)) }
    listOf(2 to 1, 1 to 2, 3 to 3).forEach { (c, r) -> drawCircle(Color(0xFFFAFAFA), cell * 0.4f, center(c, r)) }
}

private fun DrawScope.drawUno() {
    paper(Color(0xFF263238))
    val s = size.minDimension
    val w = s * 0.4f
    val h = s * 0.6f
    // Trois cartes en éventail ; la dernière porte l'ovale blanc du Uno.
    fun card(color: Color, angle: Float, dx: Float, oval: Boolean) {
        val left = s / 2f + dx - w / 2f
        val top = s / 2f - h / 2f
        rotate(angle, pivot = Offset(s / 2f + dx, s * 0.8f)) {
            drawRoundRect(Color.White, topLeft = Offset(left, top), size = Size(w, h), cornerRadius = CornerRadius(s * 0.06f))
            val inset = s * 0.025f
            drawRoundRect(
                color,
                topLeft = Offset(left + inset, top + inset),
                size = Size(w - 2 * inset, h - 2 * inset),
                cornerRadius = CornerRadius(s * 0.045f)
            )
            if (oval) {
                rotate(30f, pivot = Offset(s / 2f + dx, s / 2f)) {
                    drawOval(Color.White, topLeft = Offset(left + w * 0.15f, top + h * 0.2f), size = Size(w * 0.7f, h * 0.6f))
                }
            }
        }
    }
    card(Color(0xFF1565C0), -26f, -s * 0.12f, oval = false)
    card(Color(0xFFF9A825), 0f, 0f, oval = false)
    card(Color(0xFFD32F2F), 26f, s * 0.12f, oval = true)
}

private fun DrawScope.drawSkyjoAction() {
    paper(Color(0xFF263238))
    val s = size.minDimension
    val w = s * 0.4f
    val h = s * 0.6f
    // Deux cartes en éventail : une carte action rose (avec son ovale blanc) devant une carte numérotée verte.
    fun card(color: Color, angle: Float, dx: Float, oval: Boolean) {
        val left = s / 2f + dx - w / 2f
        val top = s / 2f - h / 2f
        rotate(angle, pivot = Offset(s / 2f + dx, s * 0.8f)) {
            drawRoundRect(Color.White, topLeft = Offset(left, top), size = Size(w, h), cornerRadius = CornerRadius(s * 0.06f))
            val inset = s * 0.025f
            drawRoundRect(
                color,
                topLeft = Offset(left + inset, top + inset),
                size = Size(w - 2 * inset, h - 2 * inset),
                cornerRadius = CornerRadius(s * 0.045f)
            )
            if (oval) {
                drawOval(Color.White, topLeft = Offset(left + w * 0.18f, top + h * 0.3f), size = Size(w * 0.64f, h * 0.4f))
            }
        }
    }
    card(Color(0xFF43A047), -16f, -s * 0.12f, oval = false)
    card(Color(0xFFD81B8C), 16f, s * 0.12f, oval = true)
}

private fun DrawScope.drawHangman() {
    paper(Color(0xFFFFF8E1))
    val s = size.minDimension
    val wood = Color(0xFF6D4C41)
    val ink = Color(0xFF37474F)
    val width = s * 0.045f
    fun p(x: Float, y: Float) = Offset(s * x, s * y)
    drawLine(wood, p(0.16f, 0.88f), p(0.6f, 0.88f), width, StrokeCap.Round)
    drawLine(wood, p(0.3f, 0.88f), p(0.3f, 0.14f), width, StrokeCap.Round)
    drawLine(wood, p(0.3f, 0.14f), p(0.68f, 0.14f), width, StrokeCap.Round)
    drawLine(wood, p(0.68f, 0.14f), p(0.68f, 0.26f), width * 0.6f, StrokeCap.Round)
    drawCircle(ink, radius = s * 0.08f, center = p(0.68f, 0.34f), style = Stroke(width * 0.8f))
    drawLine(ink, p(0.68f, 0.42f), p(0.68f, 0.62f), width * 0.8f, StrokeCap.Round)
    drawLine(ink, p(0.68f, 0.48f), p(0.58f, 0.56f), width * 0.8f, StrokeCap.Round)
    drawLine(ink, p(0.68f, 0.48f), p(0.78f, 0.56f), width * 0.8f, StrokeCap.Round)
    drawLine(ink, p(0.68f, 0.62f), p(0.6f, 0.78f), width * 0.8f, StrokeCap.Round)
}

private fun DrawScope.drawChess() {
    paper(Color(0xFF4E342E))
    val s = size.minDimension
    val margin = s * 0.08f
    checkerboard(4, Color(0xFFEEEED2), Color(0xFF769656), margin)
    // Un pion blanc cerné de noir, au centre.
    val ink = Color(0xFF212121)
    val outline = s * 0.03f
    val body = Path().apply {
        moveTo(s * 0.42f, s * 0.44f)
        lineTo(s * 0.58f, s * 0.44f)
        lineTo(s * 0.65f, s * 0.7f)
        lineTo(s * 0.35f, s * 0.7f)
        close()
    }
    listOf<Pair<Color, DrawStyle>>(Color.White to Fill, ink to Stroke(outline)).forEach { (color, style) ->
        drawCircle(color, radius = s * 0.11f, center = Offset(s * 0.5f, s * 0.34f), style = style)
        drawPath(body, color, style = style)
        drawRoundRect(
            color, topLeft = Offset(s * 0.3f, s * 0.7f), size = Size(s * 0.4f, s * 0.09f),
            cornerRadius = CornerRadius(s * 0.04f), style = style
        )
    }
}

private fun DrawScope.drawMastermind() {
    paper(Color(0xFF37474F))
    val s = size.minDimension
    val colors = listOf(Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFFDD835))
    val rows = listOf(listOf(0, 1, 2, 3), listOf(3, 0, 1, 2))
    rows.forEachIndexed { r, row ->
        val y = s * (0.3f + 0.4f * r)
        row.forEachIndexed { c, color ->
            drawCircle(colors[color], radius = s * 0.075f, center = Offset(s * (0.16f + 0.14f * c), y))
        }
        // Petits pions d'indice (noir : bien placé, blanc : mal placé).
        val keys = listOf(Color(0xFF111111), Color(0xFFFAFAFA), Color(0xFF111111), Color(0xFFB0BEC5))
        keys.forEachIndexed { k, key ->
            drawCircle(key, radius = s * 0.035f, center = Offset(s * (0.8f + 0.08f * (k % 2)), y + s * (-0.045f + 0.09f * (k / 2))))
        }
    }
}

private fun DrawScope.drawFlip7() {
    paper(Color(0xFF00695C))
    val s = size.minDimension
    val w = s * 0.4f
    val h = s * 0.6f
    // Trois cartes en éventail ; la dernière porte le 7 de « Flip 7 ».
    fun card(color: Color, angle: Float, dx: Float, seven: Boolean) {
        val left = s / 2f + dx - w / 2f
        val top = s / 2f - h / 2f
        rotate(angle, pivot = Offset(s / 2f + dx, s * 0.8f)) {
            drawRoundRect(Color(0xFFFFF8E1), topLeft = Offset(left, top), size = Size(w, h), cornerRadius = CornerRadius(s * 0.06f))
            val inset = s * 0.025f
            drawRoundRect(
                color,
                topLeft = Offset(left + inset, top + inset),
                size = Size(w - 2 * inset, h - 2 * inset),
                cornerRadius = CornerRadius(s * 0.045f)
            )
            if (seven) {
                val path = Path().apply {
                    moveTo(left + w * 0.24f, top + h * 0.24f)
                    lineTo(left + w * 0.76f, top + h * 0.24f)
                    lineTo(left + w * 0.44f, top + h * 0.78f)
                }
                drawPath(path, Color(0xFFFFEB3B), style = Stroke(width = s * 0.07f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
    card(Color(0xFFEF6C00), -26f, -s * 0.12f, seven = false)
    card(Color(0xFF29B6F6), 0f, 0f, seven = false)
    card(Color(0xFF26A69A), 26f, s * 0.12f, seven = true)
}
