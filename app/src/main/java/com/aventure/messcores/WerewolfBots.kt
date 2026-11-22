package com.aventure.messcores

import kotlin.random.Random

/**
 * Les joueurs simulés de la partie test : à chaque étape, ils jouent un coup permis, au hasard (les loups s'accordent sur la même
 * victime). Ils ne servent qu'à vérifier le déroulement d'une nuit et d'une journée avec un seul téléphone.
 */
internal object WerewolfBots {

    /** Le coup du joueur simulé de la place [seat] à l'étape en cours de [round], ou null s'il n'a rien à faire. */
    fun action(round: WerewolfRound, seat: Int, random: Random): WerewolfAction? {
        val view = round.viewFor(seat)
        val candidates = view.candidates
        return when (view.prompt) {
            WerewolfPrompt.NONE -> null
            WerewolfPrompt.ACK -> WerewolfAction(WerewolfActionKind.ACK)
            WerewolfPrompt.LOVERS -> candidates.shuffled(random).take(2).let {
                if (it.size == 2) WerewolfAction(WerewolfActionKind.LOVERS, it[0], it[1]) else null
            }
            WerewolfPrompt.PROTECT -> pick(candidates, random)?.let { WerewolfAction(WerewolfActionKind.PROTECT, it) }
            WerewolfPrompt.SEE -> pick(candidates, random)?.let { WerewolfAction(WerewolfActionKind.SEE, it) }
            WerewolfPrompt.WOLF -> {
                // Les loups s'accordent : on reprend le choix d'un autre loup s'il y en a un.
                val chosen = view.picks.firstOrNull { it >= 0 && it in candidates } ?: pick(candidates, random)
                chosen?.let { WerewolfAction(WerewolfActionKind.WOLF, it) }
            }
            WerewolfPrompt.WITCH -> {
                val heal = view.healLeft && view.witchVictim >= 0
                WerewolfAction(WerewolfActionKind.WITCH, if (heal) 1 else 0, -1)
            }
            WerewolfPrompt.RAVEN -> WerewolfAction(WerewolfActionKind.RAVEN, pick(candidates, random) ?: -1)
            WerewolfPrompt.SHOOT -> pick(candidates, random)?.let { WerewolfAction(WerewolfActionKind.SHOOT, it) }
            WerewolfPrompt.VOTE -> pick(candidates, random)?.let { WerewolfAction(WerewolfActionKind.VOTE, it) }
            WerewolfPrompt.THIEF -> {
                val take = view.thiefCards.isNotEmpty() && (view.thiefForced || random.nextBoolean())
                WerewolfAction(WerewolfActionKind.THIEF, if (take) random.nextInt(view.thiefCards.size) else -1)
            }
            WerewolfPrompt.CAMP -> WerewolfAction(WerewolfActionKind.CAMP, random.nextInt(2))
            WerewolfPrompt.MODEL -> pick(candidates, random)?.let { WerewolfAction(WerewolfActionKind.MODEL, it) }
            WerewolfPrompt.FOX -> WerewolfAction(WerewolfActionKind.FOX, pick(candidates, random) ?: -1)
            WerewolfPrompt.GYPSY -> WerewolfAction(WerewolfActionKind.GYPSY, pick(candidates, random) ?: -1)
            WerewolfPrompt.COMEDIAN -> WerewolfAction(WerewolfActionKind.COMEDIAN, view.comedianLeft.randomOrNull(random) ?: -1)
            WerewolfPrompt.INFECT -> WerewolfAction(WerewolfActionKind.INFECT, random.nextInt(2))
            WerewolfPrompt.BIG_BAD -> pick(candidates, random)?.let { WerewolfAction(WerewolfActionKind.BIG_BAD, it) }
            WerewolfPrompt.WHITE -> WerewolfAction(WerewolfActionKind.WHITE, pick(candidates, random) ?: -1)
            WerewolfPrompt.CHARM -> candidates.shuffled(random).take(2).let {
                if (it.isEmpty()) null else WerewolfAction(WerewolfActionKind.CHARM, it[0], if (it.size == 2) it[1] else -1)
            }
            WerewolfPrompt.DOUSE -> candidates.shuffled(random).take(2).let {
                WerewolfAction(WerewolfActionKind.DOUSE, it.getOrElse(0) { -1 }, it.getOrElse(1) { -1 })
            }
            WerewolfPrompt.MAID -> WerewolfAction(WerewolfActionKind.MAID, random.nextInt(2))
            WerewolfPrompt.HEIR -> pick(candidates, random)?.let { WerewolfAction(WerewolfActionKind.HEIR, it) }
            WerewolfPrompt.ELECT -> pick(candidates, random)?.let { WerewolfAction(WerewolfActionKind.ELECT, it) }
        }
    }

    private fun pick(candidates: List<Int>, random: Random): Int? = if (candidates.isEmpty()) null else candidates.random(random)
}
