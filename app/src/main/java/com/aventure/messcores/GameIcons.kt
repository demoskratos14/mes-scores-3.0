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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Nombre de jeux par ligne dans la grille. */
private const val GRID_COLUMNS = 3

/** Jeux jouables qui ont une icône dessinée ci-dessous (un jeu sans icône reçoit une icône générique). */
internal val GAME_ICON_IDS = setOf(
    MASTERMIND_ID, MORPION_ID, CONNECT4_ID, BATTLESHIP_ID, NIM_ID, PFC_ID,
    GOMOKU_ID, DOTS_ID, DRAUGHTS_ID, OTHELLO_ID, HANGMAN_ID, CHESS_ID
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
            GOMOKU_ID -> drawGomoku()
            DOTS_ID -> drawDots()
            DRAUGHTS_ID -> drawDraughts()
            OTHELLO_ID -> drawOthello()
            HANGMAN_ID -> drawHangman()
            CHESS_ID -> drawChess()
            else -> drawGeneric()
        }
    }
}

// ---------- Dessins : chaque icône a son propre fond, lisible en thème clair comme en thème sombre ----------

private fun DrawScope.paper(color: Color) {
    drawRoundRect(color, cornerRadius = CornerRadius(size.minDimension * 0.18f))
}

private fun DrawScope.cross(center: Offset, radius: Float, color: Color, width: Float) {
    drawLine(color, Offset(center.x - radius, center.y - radius), Offset(center.x + radius, center.y + radius), width, StrokeCap.Round)
    drawLine(color, Offset(center.x - radius, center.y + radius), Offset(center.x + radius, center.y - radius), width, StrokeCap.Round)
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
