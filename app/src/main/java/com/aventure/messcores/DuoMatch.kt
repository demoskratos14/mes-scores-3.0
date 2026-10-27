package com.aventure.messcores

/**
 * Une séance de jeu à deux joueurs au tour par tour (Gomoku, Pipopipette) : la manche en cours, qui a commencé et le
 * score. Le premier joueur (1) est l'organisateur quand on joue chacun sur son téléphone (voir [DuoOnline]), le
 * deuxième (2) est l'invité. Un coup est un simple nombre (l'index de la case ou du trait), ce qui permet d'utiliser le
 * même écran ([DuoGameScreen]) et la même table en ligne pour les deux jeux.
 */
internal interface DuoMatch<M : DuoMatch<M>> {
    /** Manches gagnées par le premier joueur. */
    val winsA: Int

    /** Manches gagnées par le deuxième joueur. */
    val winsB: Int

    /** Manches sans vainqueur. */
    val draws: Int

    /** Vrai quand la manche est finie (victoire ou égalité). */
    val isOver: Boolean

    /** 1 ou 2 pour le joueur qui a gagné la manche, 0 sinon (manche en cours ou égalité). */
    val winner: Int

    /** Joueur (1 ou 2) à qui c'est de jouer. */
    val current: Int

    /** La séance après le coup [move] du joueur [player], ou null si ce n'est pas son tour ou si le coup n'est pas permis. */
    fun play(move: Int, player: Int): M?

    /** Manche suivante (l'autre joueur commence), ou null tant que la manche n'est pas finie. */
    fun newRound(): M?

    /** La séance sous forme de texte, pour l'envoyer à l'autre téléphone ou la garder quand l'écran tourne. */
    fun encode(): String
}

/** Lit un entier d'un texte de séance : [min] si le texte n'est pas un nombre, et toujours ramené entre [min] et [max]. */
internal fun parseDuoInt(text: String, min: Int, max: Int = Int.MAX_VALUE): Int =
    (text.toIntOrNull() ?: min).coerceIn(min, max)
