package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Règles des échecs (moteur pur), séance de deux joueurs et messages. */
class ChessTest {

    private fun fen(text: String): ChessPosition = Chess.fromFen(text)!!

    private fun moves(text: String): ChessGame = ChessGame.fromMoves(text)

    private fun move(text: String): ChessMove = ChessMove.parse(text)!!

    private fun legalNames(position: ChessPosition): Set<String> = Chess.legalMoves(position).map { it.toText() }.toSet()

    // ---------- Génération des coups ----------

    @Test
    fun lesNombresDeCoupsDesPositionsDeReferenceSontExacts() {
        // Positions classiques de vérification des règles (« perft ») : roque, prise en passant, promotion, clouages.
        assertEquals(listOf(20L, 400L, 8902L), (1..3).map { Chess.perft(Chess.startPosition(), it) })
        val kiwipete = fen("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1")
        assertEquals(listOf(48L, 2039L, 97862L), (1..3).map { Chess.perft(kiwipete, it) })
        val endgame = fen("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1")
        assertEquals(listOf(14L, 191L, 2812L, 43238L), (1..4).map { Chess.perft(endgame, it) })
        val promotions = fen("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1")
        assertEquals(listOf(6L, 264L, 9467L), (1..3).map { Chess.perft(promotions, it) })
        val mixed = fen("rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8")
        assertEquals(listOf(44L, 1486L, 62379L), (1..3).map { Chess.perft(mixed, it) })
    }

    @Test
    fun auDepartLesBlancsOntVingtCoups() {
        val game = ChessGame.start()
        assertEquals(20, game.legalMoves.size)
        assertTrue(game.position.whiteToMove)
        assertFalse(game.inCheck)
        assertNull(game.result)
    }

    @Test
    fun lesCasesSeNommentEtSeLisent() {
        assertEquals("a8", Chess.squareName(0))
        assertEquals("h1", Chess.squareName(63))
        assertEquals("e4", Chess.squareName(Chess.squareOf("e4")!!))
        assertEquals(60, Chess.squareOf("e1"))
        assertNull(Chess.squareOf("i9"))
        assertNull(Chess.squareOf("e"))
    }

    @Test
    fun uneNotationDeCoupSeLitEtSEcrit() {
        assertEquals(ChessMove(Chess.squareOf("e2")!!, Chess.squareOf("e4")!!), move("e2e4"))
        assertEquals("e7e8q", move("e7e8q").toText())
        assertEquals('n', move("a7a8n").promotion)
        assertNull(ChessMove.parse("e2"))
        assertNull(ChessMove.parse("e2e9"))
        assertNull(ChessMove.parse("e7e8k"))
        assertNull(ChessMove.parse("e2e4e5"))
    }

    // ---------- Coups spéciaux ----------

    @Test
    fun leRoquePasseLaTourDeLAutreCote() {
        val position = fen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        assertTrue(legalNames(position).containsAll(listOf("e1g1", "e1c1")))

        val kingSide = Chess.apply(position, move("e1g1"))
        assertEquals('K', kingSide.board[Chess.squareOf("g1")!!])
        assertEquals('R', kingSide.board[Chess.squareOf("f1")!!])
        assertEquals('.', kingSide.board[Chess.squareOf("h1")!!])
        assertEquals("kq", kingSide.castling)

        val queenSide = Chess.apply(position, move("e1c1"))
        assertEquals('K', queenSide.board[Chess.squareOf("c1")!!])
        assertEquals('R', queenSide.board[Chess.squareOf("d1")!!])
        assertEquals('.', queenSide.board[Chess.squareOf("a1")!!])
    }

    @Test
    fun onNeRoquePasEnEchecNiAPartirNiVersNiParUneCaseAttaquee() {
        // Une tour noire en f2 attaque f1 : le petit roque est interdit, le grand roque reste permis.
        val throughAttack = legalNames(fen("r3k2r/8/8/8/8/8/5r2/R3K2R w KQkq - 0 1"))
        assertFalse("e1g1" in throughAttack)
        assertTrue("e1c1" in throughAttack)
        // Le roi est en échec : aucun roque.
        val inCheck = legalNames(fen("r3k2r/8/8/8/8/8/4r3/R3K2R w KQkq - 0 1"))
        assertFalse("e1g1" in inCheck)
        assertFalse("e1c1" in inCheck)
    }

    @Test
    fun onPerdLeRoqueDuCoteDeLaTourQuiABouge() {
        val position = fen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        val afterRookMove = Chess.apply(position, move("h1h2"))
        assertEquals("Qkq", afterRookMove.castling)
        val afterKingMove = Chess.apply(position, move("e1e2"))
        assertEquals("kq", afterKingMove.castling)
        // Une tour prise dans son coin fait aussi perdre le roque adverse.
        val captured = Chess.apply(fen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1"), move("a1a8"))
        assertEquals("Kk", captured.castling)
    }

    @Test
    fun laPriseEnPassantRetireLePionPris() {
        val game = moves("e2e4 a7a6 e4e5 d7d5 e5d6")
        assertEquals(5, game.moves.size)
        assertEquals('.', game.position.board[Chess.squareOf("d5")!!])
        assertEquals('P', game.position.board[Chess.squareOf("d6")!!])
    }

    @Test
    fun laPriseEnPassantEstPerdueSiOnNeLaFaitPasAussitot() {
        // Un coup d'attente de chaque camp : e5d6 n'est plus permis, la lecture s'arrête avant.
        val game = moves("e2e4 a7a6 e4e5 d7d5 h2h3 a6a5 e5d6")
        assertEquals(6, game.moves.size)
    }

    @Test
    fun unPionQuiArriveAuBoutEstPromuEnQuatrePiecesPossibles() {
        val position = fen("8/P6k/8/8/8/8/8/K7 w - - 0 1")
        val promotions = Chess.legalMoves(position).filter { it.from == Chess.squareOf("a7") }
        assertEquals(setOf('q', 'r', 'b', 'n'), promotions.map { it.promotion }.toSet())
        assertEquals('Q', Chess.apply(position, move("a7a8q")).board[Chess.squareOf("a8")!!])
        assertEquals('N', Chess.apply(position, move("a7a8n")).board[Chess.squareOf("a8")!!])
        // Sans choix de pièce, le coup n'est pas permis.
        assertNull(ChessGame.start(position).play(move("a7a8")))
        assertNotNull(ChessGame.start(position).play(move("a7a8q")))
    }

    @Test
    fun unPieceCloueeNePeutPasQuitterLaLigne() {
        // Le fou e2 est cloué sur le roi e1 par la tour e8 : il ne peut pas bouger.
        val position = fen("4r2k/8/8/8/8/8/4B3/4K3 w - - 0 1")
        assertTrue(Chess.legalMoves(position).none { it.from == Chess.squareOf("e2") })
    }

    // ---------- Fins de partie ----------

    @Test
    fun leMatDuCoupDuBerger() {
        val game = moves("e2e4 e7e5 d1h5 b8c6 f1c4 g8f6 h5f7")
        assertEquals(7, game.moves.size)
        assertEquals(ChessResult(winnerWhite = true, reason = ChessReason.CHECKMATE), game.result)
        assertTrue(game.inCheck)
        // La partie finie n'accepte plus aucun coup.
        assertNull(game.play(move("e8e7")))
    }

    @Test
    fun leMatDuCoupDuLapin() {
        val game = moves("f2f3 e7e5 g2g4 d8h4")
        assertEquals(ChessResult(winnerWhite = false, reason = ChessReason.CHECKMATE), game.result)
    }

    @Test
    fun leMatEstPrioritaireSurLaRegleDes50Coups() {
        // Mat en un coup au 99e demi-coup : le mat l'emporte sur la nulle.
        val game = ChessGame.start(fen("7k/8/6K1/8/8/8/8/R7 w - - 99 80")).play(move("a1a8"))!!
        assertEquals(ChessReason.CHECKMATE, game.result?.reason)
    }

    @Test
    fun lePatEstUneNulle() {
        val position = fen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1")
        assertTrue(Chess.legalMoves(position).isEmpty())
        assertFalse(Chess.inCheck(position, white = false))
        assertEquals(ChessResult(null, ChessReason.STALEMATE), ChessGame.start(position).result)
    }

    @Test
    fun leMaterielInsuffisantEstUneNulle() {
        assertTrue(Chess.insufficientMaterial(fen("8/8/8/4k3/8/3K4/8/8 w - - 0 1").board))
        assertTrue(Chess.insufficientMaterial(fen("8/8/8/4k3/8/3K4/8/6B1 w - - 0 1").board))
        assertTrue(Chess.insufficientMaterial(fen("8/8/8/4k3/8/3K4/8/6N1 w - - 0 1").board))
        // Deux fous noir et blanc sur des cases de la même couleur : aucun mat possible.
        assertTrue(Chess.insufficientMaterial(fen("5b2/8/8/4k3/8/3K4/8/2B5 w - - 0 1").board))
        // Fous de couleurs différentes, deux cavaliers, un pion, une tour : le mat reste possible.
        assertFalse(Chess.insufficientMaterial(fen("4b3/8/8/4k3/8/3K4/8/2B5 w - - 0 1").board))
        assertFalse(Chess.insufficientMaterial(fen("8/8/8/4k3/8/3K4/8/5NN1 w - - 0 1").board))
        assertFalse(Chess.insufficientMaterial(fen("8/8/8/4k3/8/3K4/4P3/8 w - - 0 1").board))
        assertFalse(Chess.insufficientMaterial(fen("8/8/8/4k3/8/3K4/8/6R1 w - - 0 1").board))
        assertEquals(
            ChessReason.INSUFFICIENT_MATERIAL,
            ChessGame.start(fen("8/8/8/4k3/8/3K4/8/6B1 w - - 0 1")).result?.reason
        )
    }

    @Test
    fun laTripleRepetitionEstUneNulle() {
        val twice = moves("g1f3 g8f6 f3g1 f6g8")
        assertNull(twice.result)
        val thrice = moves("g1f3 g8f6 f3g1 f6g8 g1f3 g8f6 f3g1 f6g8")
        assertEquals(ChessResult(null, ChessReason.REPETITION), thrice.result)
    }

    @Test
    fun laRegleDesCinquanteCoupsEstUneNulle() {
        val before = ChessGame.start(fen("4k3/8/8/8/8/8/8/R3K3 w - - 98 60"))
        val oneMore = before.play(move("a1a2"))!!
        assertNull(oneMore.result)
        val hundred = oneMore.play(move("e8d8"))!!
        assertEquals(ChessResult(null, ChessReason.FIFTY_MOVES), hundred.result)
    }

    @Test
    fun unCoupDePionOuUnePriseRemetLeCompteurAZero() {
        val position = fen("4k3/8/8/8/8/8/P7/R3K3 w - - 40 60")
        assertEquals(0, Chess.apply(position, move("a2a3")).halfmove)
        assertEquals(41, Chess.apply(position, move("a1b1")).halfmove)
    }

    @Test
    fun uneLectureIllisibleOuInterditeGardeLeDebutDeLaPartie() {
        assertEquals(2, moves("e2e4 e7e5 e2e4 d7d5").moves.size)
        assertEquals(1, moves("e2e4 zzzz e7e5").moves.size)
        assertEquals(0, moves("").moves.size)
    }

    // ---------- La séance ----------

    private fun play(match: ChessMatch, text: String, seat: Int): ChessMatch = match.play(move(text), seat)!!

    private fun foolsMate(): ChessMatch {
        var match = ChessMatch()
        match = play(match, "f2f3", 0)
        match = play(match, "e7e5", 1)
        match = play(match, "g2g4", 0)
        return play(match, "d8h4", 1)
    }

    @Test
    fun chacunJoueSonTourEtLesBlancsCommencent() {
        val match = ChessMatch()
        assertEquals(0, match.currentSeat)
        assertTrue(match.playsWhite(0))
        assertFalse(match.playsWhite(1))
        // Les noirs ne jouent pas en premier.
        assertNull(match.play(move("e7e5"), 1))
        // Un coup interdit est refusé.
        assertNull(match.play(move("e2e5"), 0))
        assertEquals(1, play(match, "e2e4", 0).currentSeat)
    }

    @Test
    fun unMatEstCompteEtFigeLaManche() {
        val match = foolsMate()
        assertTrue(match.isOver)
        assertEquals(1, match.winnerSeat)
        assertEquals(1, match.wins1)
        assertEquals(0, match.wins0)
        assertNull(match.play(move("a2a3"), 0))
    }

    @Test
    fun laMancheSuivanteEchangeLesCouleursEtGardeLeScore() {
        val next = foolsMate().newRound()!!
        assertEquals("", next.moves)
        assertEquals(1, next.whiteSeat)
        assertEquals(1, next.currentSeat)
        assertEquals(1, next.wins1)
        assertFalse(next.isOver)
        // Pas de nouvelle manche tant que celle-ci n'est pas finie.
        assertNull(next.newRound())
        // Le gagnant de la première manche joue maintenant les blancs et tombe dans le même piège : 1 partout.
        var match = next
        match = play(match, "f2f3", 1)
        match = play(match, "e7e5", 0)
        match = play(match, "g2g4", 1)
        match = play(match, "d8h4", 0)
        assertEquals(0, match.winnerSeat)
        assertEquals(1, match.wins0)
        assertEquals(1, match.wins1)
    }

    @Test
    fun unAbandonDonneLaMancheALAutre() {
        val match = ChessMatch().resign(0)!!
        assertEquals(ChessReason.RESIGNATION, match.result?.reason)
        assertEquals(1, match.winnerSeat)
        assertEquals(1, match.wins1)
        assertNull(match.resign(1))
    }

    @Test
    fun uneNulleDUnCommunAccordSeProposeEtSAccepte() {
        val offered = ChessMatch().offerDraw(0)!!
        assertEquals(0, offered.drawOffer)
        // Une seule proposition à la fois, et seul l'autre joueur peut répondre.
        assertNull(offered.offerDraw(1))
        assertNull(offered.answerDraw(0, accept = true))

        val agreed = offered.answerDraw(1, accept = true)!!
        assertEquals(ChessResult(null, ChessReason.AGREEMENT), agreed.result)
        assertEquals(1, agreed.draws)
        assertEquals(-1, agreed.winnerSeat)
    }

    @Test
    fun uneNulleRefuseeOuIgnoreeSeTermine() {
        val offered = ChessMatch().offerDraw(0)!!
        val declined = offered.answerDraw(1, accept = false)!!
        assertEquals(-1, declined.drawOffer)
        assertFalse(declined.isOver)

        // L'offreur joue : la proposition reste. L'autre joue sans répondre : elle est refusée.
        val afterOwnMove = play(offered, "e2e4", 0)
        assertEquals(0, afterOwnMove.drawOffer)
        assertEquals(-1, play(afterOwnMove, "e7e5", 1).drawOffer)
    }

    @Test
    fun lesDeuxJoueursDUnMemeTelephoneSAccordentSurLaNulle() {
        val match = ChessMatch().agreeDraw()!!
        assertEquals(1, match.draws)
        assertNull(match.agreeDraw())
    }

    // ---------- Messages ----------

    @Test
    fun chaqueMessageSurvitALEnvoi() {
        val match = ChessMatch(moves = "e2e4 e7e5", whiteSeat = 1, wins0 = 2, wins1 = 3, draws = 1, drawOffer = 0)
        val messages = listOf(
            ChessMessage.Table(listOf("Anna", "Ben"), 1, match, ChessNotice.NONE),
            ChessMessage.Table(listOf("Anna"), 1, ChessMatch(), ChessNotice.PLAYER_LEFT),
            ChessMessage.Table(listOf("Anna", "Ben"), 1, foolsMate(), ChessNotice.NONE),
            ChessMessage.Table(listOf("Anna", "Ben"), 1, ChessMatch(resigned = 1), ChessNotice.NONE),
            ChessMessage.Table(listOf("Anna", "Ben"), 1, ChessMatch(agreed = true), ChessNotice.NONE),
            ChessMessage.Move("e7e8q"),
            ChessMessage.NewRound,
            ChessMessage.Resign,
            ChessMessage.OfferDraw,
            ChessMessage.AnswerDraw(true),
            ChessMessage.AnswerDraw(false)
        )
        for (message in messages) {
            val decoded = SyncCodec.decode(SyncCodec.encode(SyncMessage.Chess(message)))
            assertEquals(message.toString(), SyncMessage.Chess(message), decoded)
        }
    }

    @Test
    fun desCoupsInterditsOuUneValeurAbsurdeSontRamenesAUneValeurPermise() {
        val json = ChessMessage.Table(listOf("Anna", "Ben"), 1, ChessMatch(), ChessNotice.NONE).toJson()
            .put("moves", "e2e4 e2e4 e7e5").put("white", 7).put("w0", -4).put("offer", 9)

        val decoded = chessMessageFromJson(json) as ChessMessage.Table

        assertEquals("e2e4", decoded.match.moves)
        assertEquals(1, decoded.match.whiteSeat)
        assertEquals(0, decoded.match.wins0)
        assertEquals(1, decoded.match.drawOffer)
    }

    @Test
    fun unMessageInconnuEstIgnore() {
        assertNull(chessMessageFromJson(org.json.JSONObject().put("k", "inconnu")))
        assertNotNull(chessMessageFromJson(ChessMessage.Move("e2e4").toJson()))
    }
}
