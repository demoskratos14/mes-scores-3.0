package com.aventure.messcores

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import kotlin.random.Random

internal const val WEREWOLF_ID = "builtin_werewolf"

/** Nombre de joueurs (l'organisateur compris) : 4 permet de tester avec peu de téléphones, 8 ou plus est le vrai jeu. */
internal const val WEREWOLF_MIN_PLAYERS = 4
internal const val WEREWOLF_MAX_PLAYERS = 18

/** Taille maximale de la table du test complet (voir [WerewolfOnline.startFullTest]) : une carte de chaque rôle, bien plus que dans une vraie partie. */
internal const val WEREWOLF_FULL_TEST_MAX_PLAYERS = 40

/** Voix en plus que le Corbeau ajoute contre le joueur qu'il désigne. */
private const val RAVEN_EXTRA_VOTES = 2

/** Les cartes du jeu (images découpées dans la planche de cartes : res/drawable-nodpi/werewolf_*.webp). */
internal enum class WerewolfRole(
    @StringRes val nameRes: Int,
    @StringRes val descRes: Int,
    @DrawableRes val image: Int
) {
    VILLAGER(R.string.ww_role_villager, R.string.ww_desc_villager, R.drawable.werewolf_villager),
    WEREWOLF(R.string.ww_role_werewolf, R.string.ww_desc_werewolf, R.drawable.werewolf_werewolf),
    SEER(R.string.ww_role_seer, R.string.ww_desc_seer, R.drawable.werewolf_seer),
    SAVIOR(R.string.ww_role_savior, R.string.ww_desc_savior, R.drawable.werewolf_savior),
    WITCH(R.string.ww_role_witch, R.string.ww_desc_witch, R.drawable.werewolf_witch),
    HUNTER(R.string.ww_role_hunter, R.string.ww_desc_hunter, R.drawable.werewolf_hunter),
    CUPID(R.string.ww_role_cupid, R.string.ww_desc_cupid, R.drawable.werewolf_cupid),
    ELDER(R.string.ww_role_elder, R.string.ww_desc_elder, R.drawable.werewolf_elder),
    WHITE_WOLF(R.string.ww_role_white_wolf, R.string.ww_desc_white_wolf, R.drawable.werewolf_white_wolf),
    THIEF(R.string.ww_role_thief, R.string.ww_desc_thief, R.drawable.werewolf_thief),
    LITTLE_GIRL(R.string.ww_role_little_girl, R.string.ww_desc_little_girl, R.drawable.werewolf_little_girl),
    IDIOT(R.string.ww_role_idiot, R.string.ww_desc_idiot, R.drawable.werewolf_idiot),
    SCAPEGOAT(R.string.ww_role_scapegoat, R.string.ww_desc_scapegoat, R.drawable.werewolf_scapegoat),
    PIPER(R.string.ww_role_piper, R.string.ww_desc_piper, R.drawable.werewolf_piper),
    WILD_CHILD(R.string.ww_role_wild_child, R.string.ww_desc_wild_child, R.drawable.werewolf_wild_child),
    RAVEN(R.string.ww_role_raven, R.string.ww_desc_raven, R.drawable.werewolf_raven),
    ANGEL(R.string.ww_role_angel, R.string.ww_desc_angel, R.drawable.werewolf_angel),
    INFECT_FATHER(R.string.ww_role_infect_father, R.string.ww_desc_infect_father, R.drawable.werewolf_infect_father),
    BIG_BAD_WOLF(R.string.ww_role_big_bad_wolf, R.string.ww_desc_big_bad_wolf, R.drawable.werewolf_big_bad_wolf),
    SISTER(R.string.ww_role_sister, R.string.ww_desc_sister, R.drawable.werewolf_sister),
    GYPSY(R.string.ww_role_gypsy, R.string.ww_desc_gypsy, R.drawable.werewolf_gypsy),
    MAID(R.string.ww_role_maid, R.string.ww_desc_maid, R.drawable.werewolf_maid),
    JUDGE(R.string.ww_role_judge, R.string.ww_desc_judge, R.drawable.werewolf_judge),
    PYROMANIAC(R.string.ww_role_pyromaniac, R.string.ww_desc_pyromaniac, R.drawable.werewolf_pyromaniac),
    SECTARIAN(R.string.ww_role_sectarian, R.string.ww_desc_sectarian, R.drawable.werewolf_sectarian),
    COMEDIAN(R.string.ww_role_comedian, R.string.ww_desc_comedian, R.drawable.werewolf_comedian),

    /** Pas une carte distribuée : le Capitaine est élu par le village le premier jour. */
    CAPTAIN(R.string.ww_role_captain, R.string.ww_desc_captain, R.drawable.werewolf_captain),
    RUSTY_KNIGHT(R.string.ww_role_rusty_knight, R.string.ww_desc_rusty_knight, R.drawable.werewolf_rusty_knight),
    DOG_WOLF(R.string.ww_role_dog_wolf, R.string.ww_desc_dog_wolf, R.drawable.werewolf_dog_wolf),
    BEAR_TAMER(R.string.ww_role_bear_tamer, R.string.ww_desc_bear_tamer, R.drawable.werewolf_bear_tamer),
    FOX(R.string.ww_role_fox, R.string.ww_desc_fox, R.drawable.werewolf_fox),
    BROTHER(R.string.ww_role_brother, R.string.ww_desc_brother, R.drawable.werewolf_brother)
}

/** Le dos d'une carte. */
internal val WEREWOLF_BACK: Int = R.drawable.werewolf_back

/** Les rôles du camp des loups (les autres sont des villageois, sauf les rôles qui jouent seuls). */
internal val WOLF_ROLES = setOf(WerewolfRole.WEREWOLF, WerewolfRole.WHITE_WOLF, WerewolfRole.INFECT_FATHER, WerewolfRole.BIG_BAD_WOLF)

/** Les rôles dont le pouvoir disparaît quand le village tue l'Ancien. */
private val VILLAGE_POWERS = setOf(
    WerewolfRole.SEER, WerewolfRole.WITCH, WerewolfRole.HUNTER, WerewolfRole.LITTLE_GIRL, WerewolfRole.SAVIOR, WerewolfRole.RAVEN,
    WerewolfRole.FOX, WerewolfRole.GYPSY, WerewolfRole.COMEDIAN, WerewolfRole.JUDGE, WerewolfRole.MAID, WerewolfRole.BEAR_TAMER
)

/** Les pouvoirs que le Comédien peut emprunter : trois de ces cartes sont posées face visible devant lui. */
private val COMEDIAN_POOL = listOf(WerewolfRole.SEER, WerewolfRole.SAVIOR, WerewolfRole.RAVEN, WerewolfRole.FOX, WerewolfRole.GYPSY)

/** Nombre de cartes que prend un rôle à la table (les Sœurs sont deux, les Frères trois, le Capitaine n'est pas une carte). */
internal fun WerewolfRole.seatCount(): Int = when (this) {
    WerewolfRole.SISTER -> 2
    WerewolfRole.BROTHER -> 3
    WerewolfRole.CAPTAIN -> 0
    else -> 1
}

/** Toutes les cartes qu'on peut ajouter à la partie, dans l'ordre où l'organisateur les voit. */
internal val WEREWOLF_SPECIALS = listOf(
    WerewolfRole.SEER, WerewolfRole.WITCH, WerewolfRole.HUNTER, WerewolfRole.CUPID, WerewolfRole.LITTLE_GIRL,
    WerewolfRole.SAVIOR, WerewolfRole.ELDER, WerewolfRole.IDIOT, WerewolfRole.SCAPEGOAT, WerewolfRole.RAVEN,
    WerewolfRole.FOX, WerewolfRole.BEAR_TAMER, WerewolfRole.RUSTY_KNIGHT, WerewolfRole.JUDGE, WerewolfRole.ANGEL,
    WerewolfRole.DOG_WOLF, WerewolfRole.WILD_CHILD, WerewolfRole.MAID, WerewolfRole.SISTER, WerewolfRole.BROTHER,
    WerewolfRole.THIEF, WerewolfRole.GYPSY, WerewolfRole.COMEDIAN, WerewolfRole.SECTARIAN, WerewolfRole.PIPER,
    WerewolfRole.PYROMANIAC, WerewolfRole.INFECT_FATHER, WerewolfRole.BIG_BAD_WOLF, WerewolfRole.WHITE_WOLF, WerewolfRole.CAPTAIN
)

/** Les cartes mises par défaut, dans l'ordre, tant qu'il reste de la place à la table. */
private val DEFAULT_SPECIALS = WEREWOLF_SPECIALS.take(18).filter { it != WerewolfRole.SISTER && it != WerewolfRole.BROTHER }

/** Nombre de loups pour [players] joueurs. */
internal fun werewolfCount(players: Int): Int = (players / 4).coerceAtLeast(1)

/** Nombre de places que prennent ces cartes à la table. */
internal fun werewolfSeats(specials: Collection<WerewolfRole>): Int = specials.sumOf { it.seatCount() }

/**
 * Les cartes à pouvoir mises par défaut à la table de [players] joueurs : tant qu'il reste de la place, en gardant au moins un simple
 * villageois. Le Capitaine est élu dès 8 joueurs.
 */
internal fun defaultWerewolfSpecials(players: Int): List<WerewolfRole> {
    var room = players - werewolfCount(players) - 1
    val specials = mutableListOf<WerewolfRole>()
    for (role in DEFAULT_SPECIALS) {
        if (room <= 0) break
        if (role.seatCount() <= room) {
            specials.add(role)
            room -= role.seatCount()
        }
    }
    if (players >= 8) specials.add(WerewolfRole.CAPTAIN)
    return specials
}

/** Vrai si [wolves] loups et ces cartes tiennent à une table de [players] joueurs, avec au moins un joueur qui n'est pas un loup. */
internal fun isValidWerewolfDeal(players: Int, wolves: Int, specials: Collection<WerewolfRole>): Boolean =
    wolves >= 1 && players - wolves - werewolfSeats(specials) >= 0 && players - wolves >= 1

/** Les cartes distribuées aux joueurs, et les deux cartes posées au milieu de la table pour le Voleur. */
internal class WerewolfDeal(val roles: List<WerewolfRole>, val table: List<WerewolfRole>)

/** Distribue les cartes choisies : [wolves] loups, les cartes à pouvoir [specials], puis des villageois pour les places restantes. */
internal fun dealWerewolf(players: Int, wolves: Int, specials: Collection<WerewolfRole>, random: Random = Random.Default): WerewolfDeal {
    require(isValidWerewolfDeal(players, wolves, specials)) { "cartes impossibles : $players joueurs, $wolves loups, ${specials.size} cartes" }
    val cards = MutableList(wolves) { WerewolfRole.WEREWOLF }
    for (role in specials) repeat(role.seatCount()) { cards.add(role) }
    while (cards.size < players) cards.add(WerewolfRole.VILLAGER)
    if (WerewolfRole.THIEF in specials) {
        // Le Voleur voit deux cartes de plus, tirées du paquet complété de deux villageois ; il est forcément parmi les joueurs.
        val deck = cards + listOf(WerewolfRole.VILLAGER, WerewolfRole.VILLAGER)
        var shuffled = deck.shuffled(random)
        while (WerewolfRole.THIEF !in shuffled.take(players)) shuffled = deck.shuffled(random)
        return WerewolfDeal(shuffled.take(players), shuffled.drop(players))
    }
    return WerewolfDeal(cards.shuffled(random), emptyList())
}

internal fun dealWerewolfRoles(players: Int, wolves: Int, specials: Collection<WerewolfRole>, random: Random = Random.Default): List<WerewolfRole> =
    dealWerewolf(players, wolves, specials, random).roles

/** Distribue les cartes réglées par défaut pour [players] joueurs. */
internal fun dealWerewolfRoles(players: Int, random: Random = Random.Default): List<WerewolfRole> =
    dealWerewolfRoles(players, werewolfCount(players), defaultWerewolfSpecials(players), random)

/** Les étapes d'une partie, dans l'ordre où le narrateur les mène. */
internal enum class WerewolfStep {
    /** Chacun regarde sa carte en secret. */
    REVEAL,
    NIGHT_START,
    THIEF,
    CUPID,
    SISTERS,
    BROTHERS,
    DOG_WOLF,
    WILD_CHILD,
    COMEDIAN,
    SAVIOR,
    SEER,
    FOX,
    GYPSY,
    WOLVES,
    INFECT,
    BIG_BAD,
    WHITE_WOLF,
    WITCH,
    PIPER,
    RAVEN,
    PYRO,
    DAWN,
    ELECT,
    HUNTER,
    HEIR,
    DEBATE,
    VOTE,
    MAID,
    VOTE_RESULT,
    GAME_OVER
}

private val NIGHT_ORDER = listOf(
    WerewolfStep.THIEF, WerewolfStep.CUPID, WerewolfStep.SISTERS, WerewolfStep.BROTHERS, WerewolfStep.DOG_WOLF,
    WerewolfStep.WILD_CHILD, WerewolfStep.COMEDIAN, WerewolfStep.SAVIOR, WerewolfStep.SEER, WerewolfStep.FOX,
    WerewolfStep.GYPSY, WerewolfStep.WOLVES, WerewolfStep.INFECT, WerewolfStep.BIG_BAD, WerewolfStep.WHITE_WOLF,
    WerewolfStep.WITCH, WerewolfStep.PIPER, WerewolfStep.RAVEN, WerewolfStep.PYRO
)

/** Les étapes qui n'ont lieu que la première nuit. */
private val FIRST_NIGHT_ONLY = setOf(
    WerewolfStep.THIEF, WerewolfStep.CUPID, WerewolfStep.SISTERS, WerewolfStep.BROTHERS, WerewolfStep.DOG_WOLF, WerewolfStep.WILD_CHILD
)

/** Le rôle appelé à une étape de la nuit (null hors de la nuit). */
internal fun WerewolfStep.nightRole(): WerewolfRole? = when (this) {
    WerewolfStep.THIEF -> WerewolfRole.THIEF
    WerewolfStep.CUPID -> WerewolfRole.CUPID
    WerewolfStep.SISTERS -> WerewolfRole.SISTER
    WerewolfStep.BROTHERS -> WerewolfRole.BROTHER
    WerewolfStep.DOG_WOLF -> WerewolfRole.DOG_WOLF
    WerewolfStep.WILD_CHILD -> WerewolfRole.WILD_CHILD
    WerewolfStep.COMEDIAN -> WerewolfRole.COMEDIAN
    WerewolfStep.SAVIOR -> WerewolfRole.SAVIOR
    WerewolfStep.SEER -> WerewolfRole.SEER
    WerewolfStep.FOX -> WerewolfRole.FOX
    WerewolfStep.GYPSY -> WerewolfRole.GYPSY
    WerewolfStep.WOLVES -> WerewolfRole.WEREWOLF
    WerewolfStep.INFECT -> WerewolfRole.INFECT_FATHER
    WerewolfStep.BIG_BAD -> WerewolfRole.BIG_BAD_WOLF
    WerewolfStep.WHITE_WOLF -> WerewolfRole.WHITE_WOLF
    WerewolfStep.WITCH -> WerewolfRole.WITCH
    WerewolfStep.PIPER -> WerewolfRole.PIPER
    WerewolfStep.RAVEN -> WerewolfRole.RAVEN
    WerewolfStep.PYRO -> WerewolfRole.PYROMANIAC
    else -> null
}

internal enum class WerewolfWinner { NONE, VILLAGE, WOLVES, LOVERS, ANGEL, PIPER, WHITE_WOLF, PYROMANIAC, SECTARIAN }

/** Ce que le village a décidé au vote. */
internal enum class WerewolfVoteOutcome { NONE, TIE, EXECUTED, IDIOT_SPARED, SCAPEGOAT }

/** Ce qu'un joueur peut faire. */
internal enum class WerewolfActionKind {
    /** J'ai vu ma carte. */
    ACK,

    /** Cupidon : [a] et [b] sont les amoureux. */
    LOVERS,

    /** Salvateur : je protège [a]. */
    PROTECT,

    /** Voyante : je regarde la carte de [a]. */
    SEE,

    /** Loup : je choisis [a] comme victime. */
    WOLF,

    /** Sorcière : [a] = 1 pour sauver la victime ; [b] = joueur à empoisonner (-1 : personne). */
    WITCH,

    /** Corbeau : je désigne [a] (-1 : personne). */
    RAVEN,

    /** Chasseur : je tire sur [a]. */
    SHOOT,

    /** Vote du village contre [a]. */
    VOTE,

    /** Voleur : je prends la carte [a] de la table (-1 : je garde la mienne). */
    THIEF,

    /** Chien-loup : [a] = 1 pour devenir loup-garou, 0 pour rester villageois. */
    CAMP,

    /** Enfant sauvage : [a] est mon modèle. */
    MODEL,

    /** Renard : je flaire [a] et ses deux voisins (-1 : personne). */
    FOX,

    /** Gitane : [a] ne pourra pas voter demain (-1 : personne). */
    GYPSY,

    /** Comédien : j'emprunte le pouvoir de la carte de rôle d'ordinal [a] (-1 : aucun). */
    COMEDIAN,

    /** Infect père des loups : [a] = 1 pour infecter la victime. */
    INFECT,

    /** Grand méchant loup : sa seconde victime est [a]. */
    BIG_BAD,

    /** Loup-garou blanc : je dévore le loup [a] (-1 : personne). */
    WHITE,

    /** Joueur de flûte : je charme [a] et [b]. */
    CHARM,

    /** Pyromane : j'arrose [a] et [b] d'essence (-1 : personne). */
    DOUSE,

    /** Pyromane : j'allume le feu. */
    IGNITE,

    /** Servante dévouée : [a] = 1 pour prendre la carte du condamné. */
    MAID,

    /** Capitaine mort : [a] hérite du titre. */
    HEIR,

    /** Élection du Capitaine : je vote pour [a]. */
    ELECT,

    /** Juge bègue : je demande un second vote. */
    JUDGE
}

internal data class WerewolfAction(val kind: WerewolfActionKind, val a: Int = -1, val b: Int = -1)

/** Ce que l'écran d'un joueur lui demande de faire à cet instant. */
internal enum class WerewolfPrompt {
    NONE, ACK, LOVERS, PROTECT, SEE, WOLF, WITCH, RAVEN, SHOOT, VOTE, THIEF, CAMP, MODEL, FOX, GYPSY, COMEDIAN, INFECT, BIG_BAD,
    WHITE, CHARM, DOUSE, MAID, HEIR, ELECT
}

private enum class Cause { WOLVES, VOTE, OTHER }

/**
 * Une partie de Loup-garou de Thiercelieux. Le téléphone de l'organisateur la tient ; les joueurs (un téléphone chacun, places
 * 0 à n-1) envoient leurs actions ([perform]) et reçoivent leur vue ([viewFor]), qui ne contient que ce qu'ils ont le droit de savoir.
 * Le narrateur mène la partie étape par étape : il lit le texte de l'étape, attend que les joueurs concernés aient joué
 * ([isComplete]) puis passe à la suite ([advance]).
 *
 * [givenRoles] : les cartes des joueurs si on ne veut pas les tirer au hasard ; [givenTable] : les deux cartes du Voleur ;
 * [captainEnabled] : le village élit un Capitaine le premier jour.
 */
internal class WerewolfRound(
    val names: List<String>,
    private val random: Random = Random.Default,
    givenRoles: List<WerewolfRole>? = null,
    givenTable: List<WerewolfRole> = emptyList(),
    val captainEnabled: Boolean = false
) {

    val size: Int = names.size

    private val roleList: MutableList<WerewolfRole> = (givenRoles ?: dealWerewolfRoles(size, random)).toMutableList()

    /** Les cartes, dans l'ordre des places (elles changent si le Voleur ou la Servante dévouée en prennent une autre). */
    val roles: List<WerewolfRole> get() = roleList

    private val table: MutableList<WerewolfRole> = givenTable.toMutableList()

    var step: WerewolfStep = WerewolfStep.REVEAL
        private set

    /** Augmente à chaque changement d'étape : le narrateur relance son texte quand il change. */
    var stepVersion: Int = 0
        private set

    var night: Int = 0
        private set

    val alive = BooleanArray(size) { true }

    /** Vrai pour une carte visible de tous : un éliminé, ou l'Idiot gracié. */
    val revealed = BooleanArray(size)

    var winner: WerewolfWinner = WerewolfWinner.NONE
        private set

    /** Les éliminés de la dernière résolution (aube, vote, tir du Chasseur), dans l'ordre. */
    var lastDeaths: List<Int> = emptyList()
        private set

    private val acked = BooleanArray(size)
    private var lover1 = -1
    private var lover2 = -1

    /** Ce qui a déjà été fait cette nuit : (étape, place). */
    private val acted = mutableSetOf<Pair<WerewolfStep, Int>>()

    // Nuit
    private val protectedSeats = mutableSetOf<Int>()
    private var lastProtected = -1
    private val wolfPicks = IntArray(size) { -1 }
    private var victim = -1
    var wolfDisagree: Boolean = false
        private set
    private var victimInfected = false
    private var infectUsed = false
    private var victim2 = -1
    private var wolfDied = false
    private var whiteTarget = -1
    private var healLeft = true
    private var poisonLeft = true
    private var witchHealed = false
    private var poisonTarget = -1
    private var ravenMark = -1
    private val seerFound = mutableMapOf<Int, MutableList<Int>>()
    private var foxLost = false
    private val foxResult = mutableMapOf<Int, Pair<Int, Boolean>>()
    private var elderLives = 2
    private var powersLost = false
    private val converted = BooleanArray(size)
    private var wildModel = -1
    private val silenced = BooleanArray(size)
    private val charmed = BooleanArray(size)
    private val doused = BooleanArray(size)
    private var igniteUsed = false
    private var igniteNow = false
    private val comedianLeft: MutableList<WerewolfRole> = COMEDIAN_POOL.shuffled(random).take(3).toMutableList()
    private var borrowed: WerewolfRole? = null
    private var rustyDue = -1
    private var angelWon = false

    /** Vrai à l'aube si l'ours du Montreur d'ours a grogné. */
    var bearGrowl: Boolean = false
        private set

    // Capitaine
    var captain: Int = -1
        private set
    private var captainDone = false

    /** La place du Capitaine qui vient d'être élu, à annoncer au début du débat (-1 : aucune). */
    var captainAnnounce: Int = -1
        private set
    private var heirPending = -1

    // Jour
    private var hunterPending = -1
    private var resumeTo = WerewolfStep.DEBATE
    private val votes = IntArray(size) { -1 }
    private val idiotSpared = BooleanArray(size)
    private var tally = IntArray(size)
    var voteOutcome: WerewolfVoteOutcome = WerewolfVoteOutcome.NONE
        private set
    var executed: Int = -1
        private set
    private var judgeFlag = false
    private var judgeUsed = false

    /** Vrai pendant le second vote demandé par le Juge bègue. */
    var secondVote: Boolean = false
        private set
    private var maidTarget = -1
    private var maidUsed = false
    private var pendingPick = -1
    private var pendingOutcome = WerewolfVoteOutcome.NONE

    /** La place de la Servante dévouée qui vient de prendre la carte du condamné (-1 : personne). */
    var maidSwapped: Int = -1
        private set

    /** Les groupes du village (0 ou 1) quand l'Abominable sectaire est en jeu ; sinon vide. */
    val groups: List<Int> = if (WerewolfRole.SECTARIAN in roleList) List(size) { if (it < size / 2) 0 else 1 } else emptyList()

    /** Un compteur par joueur, augmenté quand son téléphone doit vibrer (c'est à lui, ou il peut se réveiller, ou il apprend quelque chose). */
    private val alerts = IntArray(size)

    init {
        require(size in WEREWOLF_MIN_PLAYERS..WEREWOLF_FULL_TEST_MAX_PLAYERS) { "nombre de joueurs : $size" }
        require(roleList.size == size) { "une carte par joueur : ${roleList.size} cartes pour $size joueurs" }
        beginStep()
    }

    // ---------- Questions ----------

    private fun seatOf(role: WerewolfRole): Int = roleList.indexOf(role)

    fun isWolf(seat: Int): Boolean = roleList[seat] in WOLF_ROLES || converted[seat]

    private fun wolfSeats(): List<Int> = (0 until size).filter { alive[it] && isWolf(it) }

    private fun canUsePower(seat: Int): Boolean = alive[seat] && !(powersLost && roleList[seat] in VILLAGE_POWERS)

    fun aliveCount(): Int = alive.count { it }

    private fun partnerOf(seat: Int): Int = when (seat) {
        lover1 -> lover2
        lover2 -> lover1
        else -> -1
    }

    /** Les joueurs qui jouent le rôle [role] cette nuit : ceux qui ont la carte, et le Comédien s'il l'a empruntée. */
    private fun actors(role: WerewolfRole): List<Int> {
        val seats = (0 until size).filter { roleList[it] == role }.toMutableList()
        if (borrowed == role) {
            val comedian = seatOf(WerewolfRole.COMEDIAN)
            if (comedian >= 0 && comedian !in seats) seats.add(comedian)
        }
        return seats
    }

    /** Les places qui ont droit de voter : les vivants, sauf un Idiot gracié ou un joueur réduit au silence par la Gitane. */
    private fun voters(): List<Int> = (0 until size).filter { alive[it] && !idiotSpared[it] && !silenced[it] }

    /** Les voisins vivants de [seat] (à sa gauche et à sa droite autour de la table). */
    private fun neighbours(seat: Int): List<Int> {
        val result = mutableListOf<Int>()
        for (dir in listOf(1, -1)) {
            var s = seat
            for (i in 0 until size) {
                s = (s + dir + size) % size
                if (s == seat) break
                if (alive[s]) {
                    result.add(s)
                    break
                }
            }
        }
        return result.distinct()
    }

    private fun nextWolfClockwise(seat: Int): Int {
        for (i in 1 until size) {
            val s = (seat + i) % size
            if (alive[s] && isWolf(s)) return s
        }
        return -1
    }

    private fun thiefForced(): Boolean = table.size == 2 && table.all { it in WOLF_ROLES }

    /** Les joueurs dont une action est attendue pour finir l'étape en cours. */
    fun expected(): List<Int> = when (step) {
        WerewolfStep.REVEAL -> (0 until size).filter { !acked[it] }
        WerewolfStep.WOLVES ->
            if (victim >= 0 || candidates(WerewolfActionKind.WOLF, -1).isEmpty()) emptyList()
            else wolfSeats().filter { wolfPicks[it] < 0 }
        WerewolfStep.HUNTER -> if (hunterPending >= 0) listOf(hunterPending) else emptyList()
        WerewolfStep.HEIR -> if (heirPending >= 0) listOf(heirPending) else emptyList()
        WerewolfStep.ELECT -> (0 until size).filter { alive[it] && votes[it] < 0 }
        WerewolfStep.VOTE -> voters().filter { votes[it] < 0 }
        WerewolfStep.MAID -> seatOf(WerewolfRole.MAID).let { if (it >= 0 && maidTarget >= 0 && (step to it) !in acted) listOf(it) else emptyList() }
        else -> nightActors(step)
    }

    private fun nightActors(step: WerewolfStep): List<Int> {
        val role = step.nightRole() ?: return emptyList()
        if (step in FIRST_NIGHT_ONLY && night > 1) return emptyList()
        return actors(role).filter { canUsePower(it) && (step to it) !in acted && stepCondition(step) }
    }

    /** Vrai si l'étape de nuit a quelque chose à demander à celui qui a le rôle. */
    private fun stepCondition(step: WerewolfStep): Boolean = when (step) {
        WerewolfStep.SISTERS, WerewolfStep.BROTHERS, WerewolfStep.WOLVES -> false
        WerewolfStep.CUPID -> lover1 < 0
        WerewolfStep.COMEDIAN -> comedianLeft.isNotEmpty()
        WerewolfStep.FOX -> !foxLost
        WerewolfStep.INFECT -> !infectUsed && victim >= 0 && !victimInfected && !isWolf(victim)
        WerewolfStep.BIG_BAD -> !wolfDied && victim2 < 0 && candidates(WerewolfActionKind.BIG_BAD, -1).isNotEmpty()
        WerewolfStep.WHITE_WOLF -> night % 2 == 0 && candidates(WerewolfActionKind.WHITE, -1).isNotEmpty()
        WerewolfStep.WITCH -> healLeft || poisonLeft
        WerewolfStep.PIPER -> candidates(WerewolfActionKind.CHARM, -1).isNotEmpty()
        else -> true
    }

    fun isComplete(): Boolean = expected().isEmpty()

    /** Vrai si l'étape de nuit en cours appelle un rôle qui est dans le jeu mais ne peut plus jouer : le narrateur fait semblant d'attendre. */
    fun isDecoy(): Boolean = step.nightRole() != null && expected().isEmpty() && !stepActed()

    private fun stepActed(): Boolean = step == WerewolfStep.WOLVES && victim >= 0 || acted.any { it.first == step }

    /** Les places qu'on peut viser avec l'action [kind] (le joueur [seat] la fait). */
    fun candidates(kind: WerewolfActionKind, seat: Int): List<Int> {
        val living = (0 until size).filter { alive[it] }
        return when (kind) {
            WerewolfActionKind.LOVERS, WerewolfActionKind.RAVEN, WerewolfActionKind.FOX, WerewolfActionKind.HEIR -> living
            WerewolfActionKind.PROTECT -> living.filter { it != lastProtected }
            WerewolfActionKind.WOLF -> living.filter { !isWolf(it) }
            WerewolfActionKind.BIG_BAD -> living.filter { !isWolf(it) && it != victim }
            WerewolfActionKind.WHITE -> living.filter { isWolf(it) && it != seat }
            WerewolfActionKind.CHARM -> living.filter { !charmed[it] && it != seat }
            WerewolfActionKind.DOUSE -> living.filter { !doused[it] && it != seat }
            WerewolfActionKind.SEE, WerewolfActionKind.SHOOT, WerewolfActionKind.VOTE, WerewolfActionKind.ELECT,
            WerewolfActionKind.MODEL, WerewolfActionKind.GYPSY -> living.filter { it != seat }
            else -> emptyList()
        }
    }

    // ---------- Étapes ----------

    private fun setStep(next: WerewolfStep) {
        step = next
        stepVersion++
        beginStep()
    }

    private fun bump(seat: Int) {
        if (seat in 0 until size) alerts[seat]++
    }

    private fun beginStep() {
        if (step != WerewolfStep.REVEAL) expected().forEach { bump(it) }
        // Les Sœurs et les Frères se réveillent ensemble : leurs téléphones vibrent, sans rien leur demander.
        val siblings = when (step) {
            WerewolfStep.SISTERS -> WerewolfRole.SISTER
            WerewolfStep.BROTHERS -> WerewolfRole.BROTHER
            else -> null
        }
        if (siblings != null) (0 until size).filter { roleList[it] == siblings && alive[it] }.forEach { bump(it) }
        // La Petite fille n'est pas obligée de se réveiller : son téléphone vibre seulement, sans rien attendre d'elle ni rien afficher
        // (c'est à elle d'entrouvrir les yeux pour voir si un loup se sert de son téléphone).
        if (step == WerewolfStep.WOLVES) {
            val girl = seatOf(WerewolfRole.LITTLE_GIRL)
            if (girl >= 0 && canUsePower(girl)) bump(girl)
        }
    }

    private fun startNight() {
        night++
        acted.clear()
        protectedSeats.clear()
        wolfPicks.fill(-1)
        victim = -1
        victim2 = -1
        victimInfected = false
        wolfDisagree = false
        whiteTarget = -1
        witchHealed = false
        poisonTarget = -1
        ravenMark = -1
        silenced.fill(false)
        foxResult.clear()
        igniteNow = false
        borrowed = null
        secondVote = false
        bearGrowl = false
        maidSwapped = -1
        votes.fill(-1)
        lastDeaths = emptyList()
        setStep(WerewolfStep.NIGHT_START)
    }

    /** Passe à l'étape suivante (le narrateur l'appelle quand l'étape est finie, ou pour la passer). */
    fun advance() {
        when (step) {
            WerewolfStep.REVEAL -> startNight()
            WerewolfStep.NIGHT_START -> nextNightStep(null)
            WerewolfStep.DAWN -> proceed(WerewolfStep.DEBATE)
            WerewolfStep.ELECT -> resolveElect()
            WerewolfStep.HUNTER -> {
                // Si le narrateur passe l'étape sans que le Chasseur ait tiré, il ne tire pas.
                hunterPending = -1
                proceed(resumeTo)
            }
            WerewolfStep.HEIR -> {
                heirPending = -1
                proceed(resumeTo)
            }
            WerewolfStep.DEBATE -> startVote()
            WerewolfStep.VOTE -> resolveVote()
            WerewolfStep.MAID -> applyExecution(pendingPick, pendingOutcome)
            WerewolfStep.VOTE_RESULT -> proceed(if (secondVote) WerewolfStep.VOTE else WerewolfStep.NIGHT_START)
            WerewolfStep.GAME_OVER -> Unit
            else -> nextNightStep(step)
        }
    }

    private fun nextNightStep(after: WerewolfStep?) {
        val start = if (after == null) 0 else NIGHT_ORDER.indexOf(after) + 1
        for (i in start until NIGHT_ORDER.size) {
            val candidate = NIGHT_ORDER[i]
            val role = candidate.nightRole() ?: continue
            if (candidate in FIRST_NIGHT_ONLY && night > 1) continue
            // Une carte qui n'est pas dans le jeu n'est pas appelée (sauf si le Comédien l'a empruntée).
            if (!roleList.contains(role) && borrowed != role) continue
            setStep(candidate)
            return
        }
        resolveNight()
    }

    /** La victime des loups, telle que la Sorcière la voit (-1 : personne). */
    private fun witchVictim(): Int = victim.takeIf { it >= 0 && alive[it] } ?: -1

    private fun resolveNight() {
        val killed = mutableListOf<Int>()
        // L'épée rouillée du Chevalier : le loup à sa gauche meurt au matin qui suit.
        if (rustyDue >= 0) {
            kill(rustyDue, killed, Cause.OTHER)
            rustyDue = -1
        }
        val target = witchVictim()
        if (target >= 0 && !victimInfected) {
            val saved = target in protectedSeats || witchHealed
            if (!saved) {
                if (roleList[target] == WerewolfRole.ELDER && elderLives > 1) {
                    // L'Ancien survit à la première attaque des loups.
                    elderLives--
                } else {
                    val knight = roleList[target] == WerewolfRole.RUSTY_KNIGHT
                    kill(target, killed, Cause.WOLVES)
                    if (knight) rustyDue = nextWolfClockwise(target)
                }
            }
        }
        if (victim2 >= 0 && alive[victim2] && victim2 !in protectedSeats) kill(victim2, killed, Cause.WOLVES)
        if (whiteTarget >= 0 && alive[whiteTarget] && whiteTarget !in protectedSeats) kill(whiteTarget, killed, Cause.OTHER)
        if (poisonTarget >= 0) kill(poisonTarget, killed, Cause.OTHER)
        if (igniteNow) {
            for (seat in 0 until size) if (doused[seat]) {
                doused[seat] = false
                kill(seat, killed, Cause.OTHER)
            }
        }
        val bear = seatOf(WerewolfRole.BEAR_TAMER)
        bearGrowl = bear >= 0 && alive[bear] && canUsePower(bear) && neighbours(bear).any { isWolf(it) }
        lastDeaths = killed
        setStep(WerewolfStep.DAWN)
    }

    private fun startElect() {
        votes.fill(-1)
        lastDeaths = emptyList()
        setStep(WerewolfStep.ELECT)
    }

    private fun resolveElect() {
        val counts = IntArray(size)
        for (voter in 0 until size) if (alive[voter] && votes[voter] in 0 until size) counts[votes[voter]]++
        val top = counts.max()
        val leaders = (0 until size).filter { alive[it] && counts[it] == top && top > 0 }
        captain = if (leaders.isEmpty()) -1 else leaders.random(random)
        captainDone = true
        captainAnnounce = captain
        if (captain >= 0) bump(captain)
        proceed(WerewolfStep.DEBATE)
    }

    private fun startVote() {
        captainAnnounce = -1
        votes.fill(-1)
        tally = IntArray(size)
        voteOutcome = WerewolfVoteOutcome.NONE
        executed = -1
        maidSwapped = -1
        lastDeaths = emptyList()
        setStep(WerewolfStep.VOTE)
    }

    private fun resolveVote() {
        val counts = IntArray(size)
        for (voter in voters()) {
            val target = votes[voter]
            if (target in 0 until size) counts[target] += if (voter == captain) 2 else 1
        }
        if (ravenMark in 0 until size && alive[ravenMark]) counts[ravenMark] += RAVEN_EXTRA_VOTES
        tally = counts
        val top = counts.max()
        val leaders = (0 until size).filter { alive[it] && counts[it] == top && top > 0 }
        var pick = -1
        var outcome = WerewolfVoteOutcome.NONE
        when {
            leaders.isEmpty() -> outcome = WerewolfVoteOutcome.NONE
            leaders.size == 1 -> pick = leaders.first()
            captain >= 0 && alive[captain] && votes[captain] in leaders -> pick = votes[captain]
            else -> {
                // Égalité : le Bouc émissaire paie pour tout le monde, sinon personne n'est éliminé.
                val goat = seatOf(WerewolfRole.SCAPEGOAT)
                if (goat >= 0 && alive[goat]) {
                    pick = goat
                    outcome = WerewolfVoteOutcome.SCAPEGOAT
                } else {
                    outcome = WerewolfVoteOutcome.TIE
                }
            }
        }
        ravenMark = -1
        // La Servante dévouée peut prendre la carte du condamné avant qu'elle soit retournée.
        val maid = seatOf(WerewolfRole.MAID)
        val doomed = pick >= 0 && !(roleList[pick] == WerewolfRole.IDIOT && !idiotSpared[pick])
        if (doomed && maid >= 0 && canUsePower(maid) && !maidUsed && pick != maid) {
            pendingPick = pick
            pendingOutcome = outcome
            maidTarget = pick
            setStep(WerewolfStep.MAID)
            return
        }
        applyExecution(pick, outcome)
    }

    private fun applyExecution(pick: Int, outcome: WerewolfVoteOutcome) {
        val killed = mutableListOf<Int>()
        var result = outcome
        executed = -1
        if (pick >= 0) {
            if (roleList[pick] == WerewolfRole.IDIOT && !idiotSpared[pick]) {
                idiotSpared[pick] = true
                revealed[pick] = true
                result = WerewolfVoteOutcome.IDIOT_SPARED
                executed = pick
            } else {
                if (result != WerewolfVoteOutcome.SCAPEGOAT) result = WerewolfVoteOutcome.EXECUTED
                executed = pick
                if (roleList[pick] == WerewolfRole.ELDER) powersLost = true
                kill(pick, killed, Cause.VOTE)
            }
        }
        voteOutcome = result
        // Le Juge bègue a fait signe : un second vote a lieu tout de suite.
        if (judgeFlag) {
            judgeFlag = false
            judgeUsed = true
            secondVote = true
        } else {
            secondVote = false
        }
        maidTarget = -1
        lastDeaths = killed
        setStep(WerewolfStep.VOTE_RESULT)
    }

    /** Après une résolution : la partie est-elle finie, le Chasseur doit-il tirer, le Capitaine nommer son héritier, sinon étape [next]. */
    private fun proceed(next: WerewolfStep) {
        checkWin()
        if (winner != WerewolfWinner.NONE) {
            // Tout le monde voit les cartes à la fin.
            revealed.fill(true)
            setStep(WerewolfStep.GAME_OVER)
            return
        }
        if (hunterPending >= 0) {
            resumeTo = next
            setStep(WerewolfStep.HUNTER)
            return
        }
        if (heirPending >= 0) {
            resumeTo = next
            setStep(WerewolfStep.HEIR)
            return
        }
        when {
            next == WerewolfStep.NIGHT_START -> startNight()
            next == WerewolfStep.VOTE -> startVote()
            next == WerewolfStep.DEBATE && captainEnabled && !captainDone -> startElect()
            else -> setStep(next)
        }
    }

    private fun kill(seat: Int, killed: MutableList<Int>, cause: Cause) {
        if (!alive[seat]) return
        alive[seat] = false
        revealed[seat] = true
        killed.add(seat)
        val role = roleList[seat]
        if (isWolf(seat)) wolfDied = true
        if (role == WerewolfRole.HUNTER && !powersLost) hunterPending = seat
        if (seat == captain) {
            captain = -1
            heirPending = seat
        }
        // L'Ange gagne seul s'il est éliminé dès le premier tour, par les loups ou par le village.
        if (role == WerewolfRole.ANGEL && night == 1 && cause != Cause.OTHER) angelWon = true
        // L'Enfant sauvage devient loup-garou quand son modèle meurt.
        val child = seatOf(WerewolfRole.WILD_CHILD)
        if (seat == wildModel && child >= 0 && alive[child] && !converted[child]) {
            converted[child] = true
            bump(child)
        }
        val partner = partnerOf(seat)
        if (partner >= 0) kill(partner, killed, Cause.OTHER)
    }

    private fun checkWin() {
        val living = (0 until size).filter { alive[it] }
        val wolves = living.count { isWolf(it) }
        val piper = seatOf(WerewolfRole.PIPER)
        val pyro = seatOf(WerewolfRole.PYROMANIAC)
        val white = seatOf(WerewolfRole.WHITE_WOLF)
        val sectarian = seatOf(WerewolfRole.SECTARIAN)
        val soloAlive = living.any { roleList[it] == WerewolfRole.PIPER || roleList[it] == WerewolfRole.PYROMANIAC }
        winner = when {
            angelWon -> WerewolfWinner.ANGEL
            lover1 >= 0 && living.size == 2 && alive[lover1] && alive[lover2] && isWolf(lover1) != isWolf(lover2) -> WerewolfWinner.LOVERS
            piper >= 0 && alive[piper] && living.size > 1 && living.all { it == piper || charmed[it] } -> WerewolfWinner.PIPER
            pyro >= 0 && alive[pyro] && living.size == 1 -> WerewolfWinner.PYROMANIAC
            white >= 0 && alive[white] && living.size == 1 -> WerewolfWinner.WHITE_WOLF
            sectarian >= 0 && alive[sectarian] && groups.isNotEmpty() && living.none { groups[it] != groups[sectarian] } -> WerewolfWinner.SECTARIAN
            wolves == 0 && !soloAlive -> WerewolfWinner.VILLAGE
            wolves > 0 && wolves == living.size && !(white >= 0 && alive[white]) -> WerewolfWinner.WOLVES
            else -> WerewolfWinner.NONE
        }
    }

    /** Un joueur qui quitte la partie est éliminé (sans pouvoir tirer s'il était Chasseur ni nommer d'héritier). */
    fun forfeit(seat: Int) {
        if (seat !in 0 until size || !alive[seat]) return
        val killed = mutableListOf<Int>()
        kill(seat, killed, Cause.OTHER)
        if (roleList[seat] == WerewolfRole.HUNTER) hunterPending = -1
        heirPending = -1
        votes[seat] = -1
        wolfPicks[seat] = -1
        lastDeaths = lastDeaths + killed
        checkWin()
        if (winner != WerewolfWinner.NONE) {
            revealed.fill(true)
            setStep(WerewolfStep.GAME_OVER)
        }
    }

    // ---------- Actions des joueurs ----------

    private fun inStep(seat: Int, wanted: WerewolfStep): Boolean = step == wanted && seat in expected()

    private fun done(seat: Int) {
        acted.add(step to seat)
    }

    /** Applique l'action de la place [seat] ; faux si elle n'est pas permise à cet instant (rien ne change). */
    fun perform(seat: Int, action: WerewolfAction): Boolean {
        if (seat !in 0 until size || step == WerewolfStep.GAME_OVER) return false
        val a = action.a
        val b = action.b
        return when (action.kind) {
            WerewolfActionKind.ACK -> step == WerewolfStep.REVEAL && !acked[seat].also { acked[seat] = true }
            WerewolfActionKind.LOVERS -> {
                val cand = candidates(action.kind, seat)
                val ok = inStep(seat, WerewolfStep.CUPID) && a != b && a in cand && b in cand
                if (ok) {
                    lover1 = a
                    lover2 = b
                    bump(a)
                    bump(b)
                    done(seat)
                }
                ok
            }
            WerewolfActionKind.PROTECT -> {
                val ok = inStep(seat, WerewolfStep.SAVIOR) && a in candidates(action.kind, seat)
                if (ok) {
                    protectedSeats.add(a)
                    lastProtected = a
                    done(seat)
                }
                ok
            }
            WerewolfActionKind.SEE -> {
                val ok = inStep(seat, WerewolfStep.SEER) && a in candidates(action.kind, seat)
                if (ok) {
                    seerFound.getOrPut(seat) { mutableListOf() }.apply {
                        add(a)
                        add(roleList[a].ordinal)
                    }
                    done(seat)
                }
                ok
            }
            WerewolfActionKind.WOLF -> {
                val ok = inStep(seat, WerewolfStep.WOLVES) && a in candidates(action.kind, seat)
                if (ok) {
                    wolfPicks[seat] = a
                    wolfDisagree = false
                    val wolves = wolfSeats()
                    if (wolves.all { wolfPicks[it] >= 0 }) {
                        if (wolves.map { wolfPicks[it] }.distinct().size == 1) {
                            victim = wolfPicks[wolves.first()]
                        } else {
                            // Les loups ne sont pas d'accord : ils choisissent de nouveau.
                            wolfDisagree = true
                            wolves.forEach {
                                wolfPicks[it] = -1
                                bump(it)
                            }
                        }
                    }
                }
                ok
            }
            WerewolfActionKind.WITCH -> {
                val wantsHeal = a == 1
                val ok = inStep(seat, WerewolfStep.WITCH) &&
                    (!wantsHeal || (healLeft && witchVictim() >= 0)) &&
                    (b < 0 || (poisonLeft && alive.getOrElse(b) { false } && b != seat))
                if (ok) {
                    if (wantsHeal) {
                        healLeft = false
                        witchHealed = true
                    }
                    if (b >= 0) {
                        poisonLeft = false
                        poisonTarget = b
                    }
                    done(seat)
                }
                ok
            }
            WerewolfActionKind.RAVEN -> {
                val ok = inStep(seat, WerewolfStep.RAVEN) && (a < 0 || a in candidates(action.kind, seat))
                if (ok) {
                    ravenMark = a
                    done(seat)
                }
                ok
            }
            WerewolfActionKind.SHOOT -> {
                val ok = step == WerewolfStep.HUNTER && seat == hunterPending && a in candidates(action.kind, seat)
                if (ok) {
                    val killed = mutableListOf<Int>()
                    hunterPending = -1
                    kill(a, killed, Cause.OTHER)
                    lastDeaths = lastDeaths + killed
                }
                ok
            }
            WerewolfActionKind.VOTE -> {
                val ok = inStep(seat, WerewolfStep.VOTE) && a in candidates(action.kind, seat)
                if (ok) votes[seat] = a
                ok
            }
            WerewolfActionKind.ELECT -> {
                val ok = inStep(seat, WerewolfStep.ELECT) && a in candidates(action.kind, seat)
                if (ok) votes[seat] = a
                ok
            }
            WerewolfActionKind.THIEF -> {
                val ok = inStep(seat, WerewolfStep.THIEF) && ((a == -1 && !thiefForced()) || a in table.indices)
                if (ok) {
                    if (a >= 0) {
                        val old = roleList[seat]
                        roleList[seat] = table[a]
                        table[a] = old
                    }
                    done(seat)
                }
                ok
            }
            WerewolfActionKind.CAMP -> {
                val ok = inStep(seat, WerewolfStep.DOG_WOLF) && (a == 0 || a == 1)
                if (ok) {
                    if (a == 1) converted[seat] = true
                    done(seat)
                }
                ok
            }
            WerewolfActionKind.MODEL -> {
                val ok = inStep(seat, WerewolfStep.WILD_CHILD) && a in candidates(action.kind, seat)
                if (ok) {
                    wildModel = a
                    done(seat)
                }
                ok
            }
            WerewolfActionKind.FOX -> {
                val ok = inStep(seat, WerewolfStep.FOX) && (a < 0 || a in candidates(action.kind, seat))
                if (ok) {
                    if (a >= 0) {
                        val found = (listOf(a) + neighbours(a)).any { isWolf(it) }
                        foxResult[seat] = a to found
                        // Sans loup parmi les trois, le Renard perd son flair.
                        if (!found) foxLost = true
                    }
                    done(seat)
                }
                ok
            }
            WerewolfActionKind.GYPSY -> {
                val ok = inStep(seat, WerewolfStep.GYPSY) && (a < 0 || a in candidates(action.kind, seat))
                if (ok) {
                    if (a >= 0) silenced[a] = true
                    done(seat)
                }
                ok
            }
            WerewolfActionKind.COMEDIAN -> {
                val role = WerewolfRole.entries.getOrNull(a)
                val ok = inStep(seat, WerewolfStep.COMEDIAN) && (a < 0 || (role != null && role in comedianLeft))
                if (ok) {
                    if (role != null && a >= 0) {
                        borrowed = role
                        comedianLeft.remove(role)
                    }
                    done(seat)
                }
                ok
            }
            WerewolfActionKind.INFECT -> {
                val ok = inStep(seat, WerewolfStep.INFECT) && (a == 0 || a == 1)
                if (ok) {
                    if (a == 1) {
                        // La victime ne meurt pas : elle devient loup-garou.
                        infectUsed = true
                        victimInfected = true
                        converted[victim] = true
                        bump(victim)
                    }
                    done(seat)
                }
                ok
            }
            WerewolfActionKind.BIG_BAD -> {
                val ok = inStep(seat, WerewolfStep.BIG_BAD) && a in candidates(action.kind, seat)
                if (ok) {
                    victim2 = a
                    done(seat)
                }
                ok
            }
            WerewolfActionKind.WHITE -> {
                val ok = inStep(seat, WerewolfStep.WHITE_WOLF) && (a < 0 || a in candidates(action.kind, seat))
                if (ok) {
                    whiteTarget = a
                    done(seat)
                }
                ok
            }
            WerewolfActionKind.CHARM -> {
                val cand = candidates(action.kind, seat)
                val ok = inStep(seat, WerewolfStep.PIPER) && a in cand &&
                    (if (b < 0) cand.size == 1 else b in cand && b != a)
                if (ok) {
                    charmed[a] = true
                    bump(a)
                    if (b >= 0) {
                        charmed[b] = true
                        bump(b)
                    }
                    bump(seat)
                    done(seat)
                }
                ok
            }
            WerewolfActionKind.DOUSE -> {
                val cand = candidates(action.kind, seat)
                val ok = inStep(seat, WerewolfStep.PYRO) && (a < 0 || a in cand) && (b < 0 || (a >= 0 && b in cand && b != a))
                if (ok) {
                    if (a >= 0) doused[a] = true
                    if (b >= 0) doused[b] = true
                    done(seat)
                }
                ok
            }
            WerewolfActionKind.IGNITE -> {
                val ok = inStep(seat, WerewolfStep.PYRO) && !igniteUsed && (0 until size).any { doused[it] && alive[it] }
                if (ok) {
                    igniteUsed = true
                    igniteNow = true
                    done(seat)
                }
                ok
            }
            WerewolfActionKind.MAID -> {
                val ok = step == WerewolfStep.MAID && seat in expected() && (a == 0 || a == 1)
                if (ok) {
                    if (a == 1) {
                        // Elle prend la carte du condamné et lui laisse la sienne.
                        val taken = roleList[maidTarget]
                        roleList[maidTarget] = roleList[seat]
                        roleList[seat] = taken
                        maidUsed = true
                        maidSwapped = seat
                        bump(seat)
                    }
                    done(seat)
                }
                ok
            }
            WerewolfActionKind.HEIR -> {
                val ok = step == WerewolfStep.HEIR && seat == heirPending && a in candidates(action.kind, seat).filter { it != seat }
                if (ok) {
                    captain = a
                    heirPending = -1
                    bump(a)
                }
                ok
            }
            WerewolfActionKind.JUDGE -> {
                val ok = step == WerewolfStep.VOTE && roleList[seat] == WerewolfRole.JUDGE && canUsePower(seat) && !judgeUsed && !judgeFlag
                if (ok) judgeFlag = true
                ok
            }
        }
    }

    // ---------- Ce que voit chaque joueur ----------

    /** La carte de la place [seat] telle que le narrateur peut la dire à voix haute (elle est visible de tous). */
    fun roleAt(seat: Int): WerewolfRole = roleList[seat]

    /** La place du Chasseur qui doit tirer (-1 : aucun). */
    fun hunterToShoot(): Int = hunterPending

    /** La place du Capitaine mort qui doit nommer son héritier (-1 : aucune). */
    fun heirToName(): Int = heirPending

    /** La place désignée par le Corbeau pour le prochain vote (-1 : aucune). */
    fun ravenTarget(): Int = ravenMark

    /** La place du condamné que la Servante dévouée peut remplacer (-1 : aucune). */
    fun maidTargetSeat(): Int = maidTarget

    /** Vrai si le Charme du Joueur de flûte vient d'opérer et que des joueurs charmés sont à réveiller. */
    fun anyCharmed(): Boolean = charmed.any { it }

    /** La vue de la place [seat] : sa carte, ce qu'elle a le droit de savoir et ce qu'on lui demande. */
    fun viewFor(seat: Int): WerewolfView {
        val role = roleList[seat]
        val over = step == WerewolfStep.GAME_OVER
        val prompt = promptFor(seat)
        val showTally = step == WerewolfStep.VOTE_RESULT || over
        val siblingRole = if (role == WerewolfRole.SISTER || role == WerewolfRole.BROTHER) role else null
        val piper = seatOf(WerewolfRole.PIPER)
        val knowsCharmed = charmed[seat] || seat == piper
        val fox = foxResult[seat]
        val iAmWolf = isWolf(seat)
        return WerewolfView(
            names = names,
            seat = seat,
            myRole = role.ordinal,
            alive = alive.toList(),
            shown = List(size) { if (revealed[it] || over) roleList[it].ordinal else -1 },
            step = step,
            night = night,
            prompt = prompt,
            candidates = candidatesFor(prompt, seat),
            mates = if (iAmWolf) (0 until size).filter { isWolf(it) } else emptyList(),
            lover = partnerOf(seat),
            seerFound = seerFound[seat]?.toList() ?: emptyList(),
            picks = if (iAmWolf && step == WerewolfStep.WOLVES) wolfPicks.toList() else emptyList(),
            wolfDisagree = iAmWolf && step == WerewolfStep.WOLVES && wolfDisagree,
            witchVictim = if (prompt == WerewolfPrompt.WITCH || prompt == WerewolfPrompt.INFECT) witchVictim() else -1,
            healLeft = role == WerewolfRole.WITCH && healLeft,
            poisonLeft = role == WerewolfRole.WITCH && poisonLeft,
            myVote = votes[seat],
            voted = if (step == WerewolfStep.VOTE || step == WerewolfStep.ELECT) votes.count { it >= 0 } else 0,
            voters = when (step) {
                WerewolfStep.VOTE -> voters().size
                WerewolfStep.ELECT -> aliveCount()
                else -> 0
            },
            canVote = alive[seat] && !idiotSpared[seat] && !silenced[seat],
            marked = if (step == WerewolfStep.VOTE) ravenMark else -1,
            tally = if (showTally) tally.toList() else emptyList(),
            voteOf = if (showTally) votes.toList() else emptyList(),
            outcome = voteOutcome,
            executed = if (showTally) executed else -1,
            lastDeaths = lastDeaths,
            winner = winner,
            alert = alerts[seat],
            thiefCards = if (prompt == WerewolfPrompt.THIEF) table.map { it.ordinal } else emptyList(),
            thiefForced = prompt == WerewolfPrompt.THIEF && thiefForced(),
            foxSeat = fox?.first ?: -1,
            foxFound = fox?.second ?: false,
            siblings = if (siblingRole != null) (0 until size).filter { it != seat && roleList[it] == siblingRole } else emptyList(),
            charmedSeats = if (knowsCharmed) (0 until size).filter { charmed[it] } else emptyList(),
            converted = converted[seat],
            comedianLeft = if (role == WerewolfRole.COMEDIAN) comedianLeft.map { it.ordinal } else emptyList(),
            maidTarget = if (prompt == WerewolfPrompt.MAID) maidTarget else -1,
            captain = captain,
            groups = groups,
            doused = if (role == WerewolfRole.PYROMANIAC) doused.toList() else emptyList(),
            canIgnite = prompt == WerewolfPrompt.DOUSE && !igniteUsed && (0 until size).any { doused[it] && alive[it] },
            canJudge = step == WerewolfStep.VOTE && role == WerewolfRole.JUDGE && canUsePower(seat) && !judgeUsed && !judgeFlag,
            secondVote = secondVote,
            model = if (role == WerewolfRole.WILD_CHILD) wildModel else -1
        )
    }

    private fun promptFor(seat: Int): WerewolfPrompt {
        if (seat !in expected()) return WerewolfPrompt.NONE
        return when (step) {
            WerewolfStep.REVEAL -> WerewolfPrompt.ACK
            WerewolfStep.THIEF -> WerewolfPrompt.THIEF
            WerewolfStep.CUPID -> WerewolfPrompt.LOVERS
            WerewolfStep.DOG_WOLF -> WerewolfPrompt.CAMP
            WerewolfStep.WILD_CHILD -> WerewolfPrompt.MODEL
            WerewolfStep.COMEDIAN -> WerewolfPrompt.COMEDIAN
            WerewolfStep.SAVIOR -> WerewolfPrompt.PROTECT
            WerewolfStep.SEER -> WerewolfPrompt.SEE
            WerewolfStep.FOX -> WerewolfPrompt.FOX
            WerewolfStep.GYPSY -> WerewolfPrompt.GYPSY
            WerewolfStep.WOLVES -> WerewolfPrompt.WOLF
            WerewolfStep.INFECT -> WerewolfPrompt.INFECT
            WerewolfStep.BIG_BAD -> WerewolfPrompt.BIG_BAD
            WerewolfStep.WHITE_WOLF -> WerewolfPrompt.WHITE
            WerewolfStep.WITCH -> WerewolfPrompt.WITCH
            WerewolfStep.PIPER -> WerewolfPrompt.CHARM
            WerewolfStep.RAVEN -> WerewolfPrompt.RAVEN
            WerewolfStep.PYRO -> WerewolfPrompt.DOUSE
            WerewolfStep.HUNTER -> WerewolfPrompt.SHOOT
            WerewolfStep.HEIR -> WerewolfPrompt.HEIR
            WerewolfStep.ELECT -> WerewolfPrompt.ELECT
            WerewolfStep.VOTE -> WerewolfPrompt.VOTE
            WerewolfStep.MAID -> WerewolfPrompt.MAID
            else -> WerewolfPrompt.NONE
        }
    }

    private fun candidatesFor(prompt: WerewolfPrompt, seat: Int): List<Int> {
        val kind = when (prompt) {
            WerewolfPrompt.LOVERS -> WerewolfActionKind.LOVERS
            WerewolfPrompt.PROTECT -> WerewolfActionKind.PROTECT
            WerewolfPrompt.SEE -> WerewolfActionKind.SEE
            WerewolfPrompt.WOLF -> WerewolfActionKind.WOLF
            WerewolfPrompt.WITCH -> WerewolfActionKind.SEE
            WerewolfPrompt.RAVEN -> WerewolfActionKind.RAVEN
            WerewolfPrompt.SHOOT -> WerewolfActionKind.SHOOT
            WerewolfPrompt.VOTE -> WerewolfActionKind.VOTE
            WerewolfPrompt.MODEL -> WerewolfActionKind.MODEL
            WerewolfPrompt.FOX -> WerewolfActionKind.FOX
            WerewolfPrompt.GYPSY -> WerewolfActionKind.GYPSY
            WerewolfPrompt.BIG_BAD -> WerewolfActionKind.BIG_BAD
            WerewolfPrompt.WHITE -> WerewolfActionKind.WHITE
            WerewolfPrompt.CHARM -> WerewolfActionKind.CHARM
            WerewolfPrompt.DOUSE -> WerewolfActionKind.DOUSE
            WerewolfPrompt.HEIR -> WerewolfActionKind.HEIR
            WerewolfPrompt.ELECT -> WerewolfActionKind.ELECT
            else -> return emptyList()
        }
        val list = candidates(kind, seat)
        return if (prompt == WerewolfPrompt.HEIR) list.filter { it != seat } else list
    }
}
