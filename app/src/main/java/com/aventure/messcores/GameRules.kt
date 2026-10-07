package com.aventure.messcores

import org.json.JSONArray
import org.json.JSONObject

/** Comment le tableau de score se comporte pour un jeu donné. */
enum class ScoreMode {
    /** Manches numérotées qui s'ajoutent automatiquement, une colonne par joueur. */
    TABLE,
    /** Liste des joueurs avec un total et des boutons +1/-1, sans notion de manche. */
    COUNTER,
    /** Manches où la composition des équipes peut changer à chaque fois (ex: Tarot). */
    VARIABLE_TEAMS
}

/** Type de condition qui termine la partie. */
enum class EndConditionType {
    /** La partie ne se termine jamais automatiquement. */
    NONE,
    /** La partie se termine après un nombre de manches donné. */
    ROUND_COUNT,
    /** La partie se termine dès qu'un joueur atteint un score seuil, dans un sens ou l'autre. */
    SCORE_THRESHOLD
}

/** Sens dans lequel un score doit franchir le seuil pour déclencher la fin de partie. */
enum class ThresholdDirection {
    /** La partie s'arrête dès qu'un score atteint ou dépasse le seuil (ex : Belote, Rami). */
    ABOVE,
    /** La partie s'arrête dès qu'un score atteint ou descend sous le seuil (cas plus rare). */
    BELOW
}

/**
 * Condition de fin de partie.
 *
 * @param roundCount utilisé si [type] == ROUND_COUNT : nombre de manches jouées avant la fin.
 * @param scoreThreshold utilisé si [type] == SCORE_THRESHOLD : score qui déclenche la fin.
 * @param thresholdDirection sens dans lequel le seuil doit être franchi (utilisé si
 *   [type] == SCORE_THRESHOLD). Par défaut ABOVE, pour ne rien changer au comportement existant.
 * @param stopImmediately si vrai (par défaut), la partie s'arrête dès que le seuil est franchi,
 *   même en plein milieu d'une manche. Si faux, on termine la manche en cours (pour que tous
 *   les joueurs aient joué le même nombre de tours) avant de considérer la partie terminée.
 *   Utilisé si [type] == SCORE_THRESHOLD.
 * @param tieBreakOnEqualLeaders si vrai, une manche supplémentaire est jouée si plusieurs
 *   joueurs sont à égalité en tête au moment où la partie devrait s'arrêter. Utilisé si
 *   [type] == SCORE_THRESHOLD.
 */
data class EndCondition(
    val type: EndConditionType = EndConditionType.NONE,
    val roundCount: Int? = null,
    val scoreThreshold: Int? = null,
    val thresholdDirection: ThresholdDirection = ThresholdDirection.ABOVE,
    val stopImmediately: Boolean = true,
    val tieBreakOnEqualLeaders: Boolean = false
)

/**
 * Une règle de score définie par l'utilisateur, ex : "Petit" ×2, "Garde" ×3 +10.
 * Appliquée au score de base saisi : (base × factor) + bonus, puis signé.
 */
data class ScoreMultiplier(
    val id: String,
    val label: String,
    val factor: Int,
    val bonus: Int = 0
)

/**
 * Règles de score propres à un jeu.
 *
 * @param lowestWins si vrai, le classement favorise le score le plus bas
 *   (ex: Skyjo). Si faux, le score le plus haut gagne (comportement par défaut).
 * @param allowNegativeScores si vrai, un joueur peut saisir un score négatif (ex: -3 à Skyjo).
 * @param multipliers règles disponibles (multiplicateur + bonus fixe). Contient toujours
 *   au moins la règle "Normal" (×1 +0). Utilisé en mode TABLE et VARIABLE_TEAMS.
 * @param scoreMode détermine l'écran de saisie utilisé pour ce jeu.
 * @param endCondition détermine quand la partie est considérée comme terminée.
 * @param minPlayers nombre minimum de joueurs pour créer une feuille de score.
 * @param maxPlayers nombre maximum de joueurs pour créer une feuille de score.
 */
data class GameRules(
    val id: String,
    val name: String,
    val lowestWins: Boolean = false,
    val allowNegativeScores: Boolean = false,
    val multipliers: List<ScoreMultiplier> = listOf(NORMAL_MULTIPLIER),
    val scoreMode: ScoreMode = ScoreMode.TABLE,
    val endCondition: EndCondition = EndCondition(),
    val minPlayers: Int = DEFAULT_MIN_PLAYERS,
    val maxPlayers: Int = DEFAULT_MAX_PLAYERS
) {
    /** Sérialise cette règle en JSON, pour la sauvegarde dans les SharedPreferences. */
    fun toJson(): JSONObject {
        val obj = JSONObject()
        // Numéro de version du format, pour pouvoir migrer d'anciens jeux si le format change.
        obj.put("version", FORMAT_VERSION)
        obj.put("id", id)
        obj.put("name", name)
        obj.put("lowestWins", lowestWins)
        obj.put("allowNegativeScores", allowNegativeScores)
        obj.put("scoreMode", scoreMode.name)
        obj.put("minPlayers", minPlayers)
        obj.put("maxPlayers", maxPlayers)

        val multipliersArray = JSONArray()
        multipliers.forEach { m ->
            val mObj = JSONObject()
            mObj.put("id", m.id)
            mObj.put("label", m.label)
            mObj.put("factor", m.factor)
            mObj.put("bonus", m.bonus)
            multipliersArray.put(mObj)
        }
        obj.put("multipliers", multipliersArray)

        val endConditionObj = JSONObject()
        endConditionObj.put("type", endCondition.type.name)
        endCondition.roundCount?.let { endConditionObj.put("roundCount", it) }
        endCondition.scoreThreshold?.let { endConditionObj.put("scoreThreshold", it) }
        endConditionObj.put("thresholdDirection", endCondition.thresholdDirection.name)
        endConditionObj.put("stopImmediately", endCondition.stopImmediately)
        endConditionObj.put("tieBreakOnEqualLeaders", endCondition.tieBreakOnEqualLeaders)
        obj.put("endCondition", endConditionObj)

        return obj
    }

    companion object {
        /**
         * Version actuelle du format JSON d'une règle. Les anciennes entrées sans numéro sont en version 1.
         * Les anciens champs teamMode, scoringFormula et roundBonuses (jamais utilisés) sont simplement
         * ignorés à la lecture : aucune migration nécessaire.
         */
        const val FORMAT_VERSION = 1
        const val DEFAULT_MIN_PLAYERS = 1
        const val DEFAULT_MAX_PLAYERS = 12
        const val NORMAL_MULTIPLIER_ID = "normal"
        val NORMAL_MULTIPLIER = ScoreMultiplier(NORMAL_MULTIPLIER_ID, "Normal", factor = 1, bonus = 0)

        /** Reconstruit une règle depuis le JSON produit par [toJson]. */
        fun fromJson(obj: JSONObject): GameRules {
            val multipliersJson = obj.optJSONArray("multipliers")
            val multipliers = if (multipliersJson == null || multipliersJson.length() == 0) {
                listOf(NORMAL_MULTIPLIER)
            } else {
                (0 until multipliersJson.length()).map { i ->
                    val m = multipliersJson.getJSONObject(i)
                    ScoreMultiplier(
                        id = m.getString("id"),
                        label = m.getString("label"),
                        factor = m.getInt("factor"),
                        bonus = m.optInt("bonus", 0)
                    )
                }
            }

            val scoreMode = try {
                ScoreMode.valueOf(obj.optString("scoreMode", ScoreMode.TABLE.name))
            } catch (e: IllegalArgumentException) {
                ScoreMode.TABLE
            }

            val endConditionJson = obj.optJSONObject("endCondition")
            val endCondition = if (endConditionJson == null) {
                EndCondition()
            } else {
                val type = try {
                    EndConditionType.valueOf(endConditionJson.optString("type", EndConditionType.NONE.name))
                } catch (e: IllegalArgumentException) {
                    EndConditionType.NONE
                }
                EndCondition(
                    type = type,
                    roundCount = if (endConditionJson.has("roundCount") && !endConditionJson.isNull("roundCount")) {
                        endConditionJson.getInt("roundCount")
                    } else null,
                    scoreThreshold = if (endConditionJson.has("scoreThreshold") && !endConditionJson.isNull("scoreThreshold")) {
                        endConditionJson.getInt("scoreThreshold")
                    } else null,
                    thresholdDirection = try {
                        ThresholdDirection.valueOf(
                            endConditionJson.optString("thresholdDirection", ThresholdDirection.ABOVE.name)
                        )
                    } catch (e: IllegalArgumentException) {
                        ThresholdDirection.ABOVE
                    },
                    stopImmediately = endConditionJson.optBoolean("stopImmediately", true),
                    tieBreakOnEqualLeaders = endConditionJson.optBoolean("tieBreakOnEqualLeaders", false)
                )
            }

            return GameRules(
                id = obj.getString("id"),
                name = obj.getString("name"),
                lowestWins = obj.getBoolean("lowestWins"),
                allowNegativeScores = obj.getBoolean("allowNegativeScores"),
                multipliers = multipliers,
                scoreMode = scoreMode,
                endCondition = endCondition,
                minPlayers = obj.optInt("minPlayers", DEFAULT_MIN_PLAYERS),
                maxPlayers = obj.optInt("maxPlayers", DEFAULT_MAX_PLAYERS)
            )
        }
    }
}
