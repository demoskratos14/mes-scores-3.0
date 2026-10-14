package com.aventure.messcores

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import java.io.ByteArrayOutputStream

/**
 * Classement partagé sous forme d'image (PNG) : titre, une ligne par joueur avec son rang et son
 * total, puis le gagnant. Le contenu ([Content]) est construit à part du dessin pour pouvoir être testé.
 */
object ResultImage {

    /** Une ligne du classement : les ex æquo ont le même [rank]. */
    data class Row(val rank: Int, val name: String, val total: Int)

    data class Content(val title: String, val rows: List<Row>, val winnerLine: String?, val footer: String)

    private const val WIDTH = 1080
    private const val MARGIN = 64f
    private const val ROW_HEIGHT = 120f
    private const val MAX_ROWS = 40

    /** Même contenu que [ResultText.game], rangé du premier au dernier. */
    fun content(
        context: Context,
        gameName: String,
        players: List<String>,
        totals: List<Int>,
        ranks: List<Int>,
        finished: Boolean,
        winners: List<Int>
    ): Content {
        val rows = players.indices
            .sortedWith(compareBy({ ranks.getOrElse(it) { Int.MAX_VALUE } }, { it }))
            .map { Row(ranks.getOrElse(it) { 1 }, players[it], totals.getOrElse(it) { 0 }) }
        val winnerLine = if (finished && winners.isNotEmpty()) {
            context.getString(
                if (winners.size > 1) R.string.result_winners else R.string.result_winner,
                winners.joinToString(context.getString(R.string.result_and)) { players[it] }
            )
        } else {
            null
        }
        return Content(
            title = context.getString(if (finished) R.string.result_game_finished else R.string.result_game_ongoing, gameName),
            rows = rows,
            winnerLine = winnerLine,
            footer = context.getString(R.string.result_footer)
        )
    }

    private fun medalColor(rank: Int): Int = when (rank) {
        1 -> Color.rgb(255, 193, 7)
        2 -> Color.rgb(176, 190, 197)
        3 -> Color.rgb(205, 127, 50)
        else -> Color.rgb(120, 144, 156)
    }

    private fun textPaint(size: Float, color: Int, bold: Boolean) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun wrapped(text: String, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(3)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

    /** Dessine le classement : fond blanc, bandeau de titre bleu, lignes alternées. */
    fun render(content: Content): Bitmap {
        val inner = (WIDTH - 2 * MARGIN).toInt()
        val titlePaint = textPaint(54f, Color.WHITE, bold = true)
        val title = wrapped(content.title, titlePaint, inner)
        val bandHeight = title.height + 2 * 48f

        val rows = content.rows.take(MAX_ROWS)
        val winnerPaint = textPaint(46f, Color.rgb(27, 94, 32), bold = true)
        val winner = content.winnerLine?.let { wrapped(it, winnerPaint, inner) }
        val footerPaint = textPaint(36f, Color.rgb(96, 125, 139), bold = false)

        val rowsTop = bandHeight + 32f
        val winnerTop = rowsTop + rows.size * ROW_HEIGHT + 24f
        val footerTop = winnerTop + (winner?.height ?: 0) + 48f
        val height = (footerTop + 36f + 56f).toInt()

        val bitmap = createBitmap(WIDTH, height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val band = Paint().apply { color = Color.rgb(30, 64, 110) }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), bandHeight, band)
        canvas.withTranslation(MARGIN, 48f) { title.draw(this) }

        val stripe = Paint().apply { color = Color.rgb(240, 244, 248) }
        val circle = Paint(Paint.ANTI_ALIAS_FLAG)
        val numberPaint = textPaint(46f, Color.WHITE, bold = true).apply { textAlign = Paint.Align.CENTER }
        val namePaint = textPaint(52f, Color.rgb(33, 33, 33), bold = false)
        val totalPaint = textPaint(56f, Color.rgb(33, 33, 33), bold = true).apply { textAlign = Paint.Align.RIGHT }

        rows.forEachIndexed { index, row ->
            val top = rowsTop + index * ROW_HEIGHT
            if (index % 2 == 0) canvas.drawRect(0f, top, WIDTH.toFloat(), top + ROW_HEIGHT, stripe)
            val centerY = top + ROW_HEIGHT / 2
            circle.color = medalColor(row.rank)
            canvas.drawCircle(MARGIN + 40f, centerY, 40f, circle)
            canvas.drawText(row.rank.toString(), MARGIN + 40f, centerY + 16f, numberPaint)

            val totalText = row.total.toString()
            val nameLeft = MARGIN + 110f
            val nameWidth = WIDTH - MARGIN - totalPaint.measureText(totalText) - 32f - nameLeft
            val name = TextUtils.ellipsize(row.name, namePaint, nameWidth.coerceAtLeast(100f), TextUtils.TruncateAt.END)
            canvas.drawText(name.toString(), nameLeft, centerY + 18f, namePaint)
            canvas.drawText(totalText, WIDTH - MARGIN, centerY + 20f, totalPaint)
        }

        winner?.let { layout ->
            canvas.withTranslation(MARGIN, winnerTop) { layout.draw(this) }
        }
        footerPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(content.footer, WIDTH - MARGIN, footerTop + 36f, footerPaint)
        return bitmap
    }

    fun toPng(bitmap: Bitmap): ByteArray =
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

    /** Dessine le classement et ouvre le menu de partage d'Android avec l'image. */
    fun share(context: Context, content: Content) {
        val bitmap = render(content)
        try {
            val file = ShareFiles.write(context, "classement.png", toPng(bitmap))
            ShareFiles.share(context, file, "image/png", context.getString(R.string.share_ranking))
        } finally {
            bitmap.recycle()
        }
    }
}
