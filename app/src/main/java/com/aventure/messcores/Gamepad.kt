package com.aventure.messcores

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Une commande envoyée par une manette : soit un bouton ([Key], y compris la croix si elle envoie des touches),
 * soit un axe poussé dans un sens ([Axis] : sticks, croix envoyée comme axe, gâchettes).
 */
internal sealed interface PadInput {
    data class Key(val code: Int) : PadInput
    /** Un bouton qu'Android ne sait pas nommer (KEYCODE_UNKNOWN) : seul son code matériel le distingue (ex. la croix d'un Joy-Con gauche). */
    data class Scan(val scanCode: Int) : PadInput
    /** [sign] vaut -1 (gauche ou haut) ou 1 (droite ou bas) ; les gâchettes n'ont que le sens 1. */
    data class Axis(val axis: Int, val sign: Int) : PadInput
}

/** Une action d'un jeu que la manette peut déclencher, avec ses commandes par défaut. */
internal class PadAction(val id: String, @StringRes val label: Int, val defaults: List<PadInput>)

/** Les commandes par défaut d'une direction : la croix (touches ou axe) et les sticks (gauche comme droit). */
internal fun padDirection(id: String, @StringRes label: Int, horizontal: Boolean, sign: Int): PadAction {
    val dpadKey = when {
        horizontal && sign < 0 -> KeyEvent.KEYCODE_DPAD_LEFT
        horizontal -> KeyEvent.KEYCODE_DPAD_RIGHT
        sign < 0 -> KeyEvent.KEYCODE_DPAD_UP
        else -> KeyEvent.KEYCODE_DPAD_DOWN
    }
    // Codes matériels (BTN_DPAD_*) de la croix d'un Joy-Con gauche, que Android envoie sans nom de touche.
    val dpadScan = when {
        horizontal && sign < 0 -> 546
        horizontal -> 547
        sign < 0 -> 544
        else -> 545
    }
    return PadAction(
        id, label,
        listOf(
            PadInput.Key(dpadKey),
            PadInput.Scan(dpadScan),
            PadInput.Axis(if (horizontal) MotionEvent.AXIS_HAT_X else MotionEvent.AXIS_HAT_Y, sign),
            PadInput.Axis(if (horizontal) MotionEvent.AXIS_X else MotionEvent.AXIS_Y, sign),
            // Stick « droit » : un Joy-Con droit seul l'envoie sur Z/RZ ou RX/RY selon le pilote Android.
            PadInput.Axis(if (horizontal) MotionEvent.AXIS_Z else MotionEvent.AXIS_RZ, sign),
            PadInput.Axis(if (horizontal) MotionEvent.AXIS_RX else MotionEvent.AXIS_RY, sign)
        )
    )
}

/** Une action déclenchée par des boutons (pas par une direction) : [buttons] sont les touches de manette par défaut. */
internal fun padButtons(id: String, @StringRes label: Int, vararg buttons: Int): PadAction =
    PadAction(id, label, buttons.map { PadInput.Key(it) })

/** Ajoute des boutons aux commandes par défaut d'une action (ex. « Haut » qui sert aussi à pivoter avec A). */
internal fun PadAction.plusButtons(vararg buttons: Int): PadAction =
    PadAction(id, label, defaults + buttons.map { PadInput.Key(it) })

/** Le bouton de pause / démarrage commun à tous les jeux (Start). */
internal fun padStart(id: String = "pause", @StringRes label: Int = R.string.snake_pad_pause): PadAction =
    padButtons(id, label, KeyEvent.KEYCODE_BUTTON_START)

/**
 * Reçoit les événements de manette de l'Activity ([MainActivity] les lui transmet) et les transforme en appuis
 * ([PadInput]) pour l'écran qui écoute : seul le dernier écouteur enregistré les reçoit (par exemple le réglage des
 * touches passe avant le jeu). Un stick ou une gâchette compte comme « appuyé » au-delà de [PRESS], et ne l'est plus
 * en dessous de [RELEASE] : la zone morte évite les appuis fantômes quand le stick revient au repos.
 */
internal object GamepadHub {
    private const val UNNAMED_BASE = 1_000_000
    private const val PRESS = 0.6f
    private const val RELEASE = 0.35f

    /** Seuils des sticks : bas, car un Joy-Con seul n'atteint pas toujours le bout de course. */
    private const val STICK_PRESS = 0.4f
    private const val STICK_RELEASE = 0.22f
    /** Un stick change de direction dès que l'autre axe dépasse l'axe actuel de cette marge (évite le va-et-vient en diagonale). */
    private const val STICK_SWITCH_MARGIN = 0.08f

    private val listeners = mutableListOf<(PadInput) -> Boolean>()
    /** Les écouteurs de relâchement (jeux où l'on tient une direction ou un tir) : seul le dernier reçoit les événements. */
    private val releaseListeners = mutableListOf<(PadInput) -> Unit>()
    private val consumedKeys = mutableSetOf<Int>()
    private val activeAxes = mutableSetOf<Int>()
    /** La direction actuellement tenue par chaque stick (clé : l'axe horizontal du stick), ou absente au repos. */
    private val stickDirections = mutableMapOf<Int, PadInput.Axis>()

    /** Les sticks : (axe horizontal, axe vertical). Chacun donne une seule direction à la fois, la dominante. */
    private val STICKS = listOf(
        MotionEvent.AXIS_X to MotionEvent.AXIS_Y,
        MotionEvent.AXIS_Z to MotionEvent.AXIS_RZ,
        MotionEvent.AXIS_RX to MotionEvent.AXIS_RY
    )

    private val AXES = intArrayOf(
        MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y,
        MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_BRAKE, MotionEvent.AXIS_GAS
    )
    private val DIAG_AXES = intArrayOf(
        MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ, MotionEvent.AXIS_RX, MotionEvent.AXIS_RY,
        MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y, MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER
    )
    private val TRIGGERS = setOf(MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_BRAKE, MotionEvent.AXIS_GAS)
    private val DPAD_KEYS = setOf(
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER
    )

    /** Touches système : jamais détournées, même si elles viennent d'une manette (le Home d'un Joy-Con vaut Retour). */
    private val SYSTEM_KEYS = setOf(
        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_APP_SWITCH,
        KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE
    )

    /** Dernier signal reçu (touche ou axe), affiché dans les réglages pour savoir ce que la manette envoie vraiment. */
    var lastSignal by mutableStateOf("")
        private set

    fun register(listener: (PadInput) -> Boolean) {
        listeners.add(listener)
    }

    fun registerRelease(listener: (PadInput) -> Unit) {
        releaseListeners.add(listener)
    }

    fun unregisterRelease(listener: (PadInput) -> Unit) {
        releaseListeners.remove(listener)
    }

    /** Prévient l'écouteur de relâchement que [input], qu'il avait reçu comme appui, n'est plus tenu. */
    private fun released(input: PadInput) {
        releaseListeners.lastOrNull()?.invoke(input)
    }

    fun unregister(listener: (PadInput) -> Boolean) {
        listeners.remove(listener)
        if (listeners.isEmpty()) {
            consumedKeys.clear()
            activeAxes.clear()
            stickDirections.clear()
        }
    }

    private fun isSource(event: android.view.InputEvent, mask: Int) = (event.source and mask) == mask

    /** Un bouton de manette ou une touche de la croix. Renvoie vrai si l'événement a été utilisé. */
    fun onKey(event: KeyEvent): Boolean {
        if (listeners.isEmpty()) return false
        val code = event.keyCode
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            lastSignal = "${KeyEvent.keyCodeToString(code)} ($code), scan ${event.scanCode}, source 0x${Integer.toHexString(event.source)}"
        }
        // Un Joy-Con seul n'annonce pas toujours « manette » ni les mêmes touches : on accepte tout bouton venant d'une
        // source manette/joystick/croix (quel que soit son code), et les boutons ou touches de croix connus de toute source.
        if (code in SYSTEM_KEYS) return false
        val fromPad = isSource(event, InputDevice.SOURCE_GAMEPAD) || isSource(event, InputDevice.SOURCE_JOYSTICK) ||
            isSource(event, InputDevice.SOURCE_DPAD)
        if (!(fromPad || KeyEvent.isGamepadButton(code) || code in DPAD_KEYS)) return false
        // Touche sans nom : on la reconnaît à son code matériel.
        val unnamed = code == KeyEvent.KEYCODE_UNKNOWN
        if (unnamed && event.scanCode == 0) return false
        val input: PadInput = if (unnamed) PadInput.Scan(event.scanCode) else PadInput.Key(code)
        val id = if (unnamed) UNNAMED_BASE + event.scanCode else code
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount > 0) return id in consumedKeys
                val used = listeners.last()(input)
                if (used) consumedKeys.add(id)
                used
            }
            KeyEvent.ACTION_UP -> {
                val wasConsumed = consumedKeys.remove(id)
                if (wasConsumed) released(input)
                wasConsumed
            }
            else -> false
        }
    }

    /** Un mouvement de stick, de croix (envoyée comme axe) ou de gâchette. Renvoie vrai si l'événement a été utilisé. */
    fun onMotion(event: MotionEvent): Boolean {
        if (listeners.isEmpty()) return false
        if (!(isSource(event, InputDevice.SOURCE_JOYSTICK) || isSource(event, InputDevice.SOURCE_GAMEPAD)) ||
            event.actionMasked != MotionEvent.ACTION_MOVE
        ) return false
        val moved = DIAG_AXES.filter { kotlin.math.abs(event.getAxisValue(it)) > 0.3f }
        if (moved.isNotEmpty()) {
            lastSignal = moved.joinToString(", ") { "${MotionEvent.axisToString(it)} ${"%.1f".format(event.getAxisValue(it))}" } +
                ", source 0x${Integer.toHexString(event.source)}"
        }
        var used = false
        for ((axisX, axisY) in STICKS) {
            if (handleStick(event, axisX, axisY)) used = true
        }
        for (axis in AXES) {
            val value = event.getAxisValue(axis)
            val signs = if (axis in TRIGGERS) intArrayOf(1) else intArrayOf(-1, 1)
            for (sign in signs) {
                val id = axis * 2 + (if (sign > 0) 1 else 0)
                val on = id in activeAxes
                val now = sign * value > (if (on) RELEASE else PRESS)
                if (now == on) continue
                if (now) {
                    activeAxes.add(id)
                    if (listeners.last()(PadInput.Axis(axis, sign))) used = true
                } else {
                    activeAxes.remove(id)
                    released(PadInput.Axis(axis, sign))
                }
            }
        }
        return used
    }

    /**
     * Un stick donne une seule direction, celle de l'axe le plus incliné, dès [STICK_PRESS] : passer de droite à haut
     * est détecté tout de suite, sans attendre que l'axe précédent soit revenu sous le seuil de relâchement.
     */
    private fun handleStick(event: MotionEvent, axisX: Int, axisY: Int): Boolean {
        val x = event.getAxisValue(axisX)
        val y = event.getAxisValue(axisY)
        val strength = maxOf(kotlin.math.abs(x), kotlin.math.abs(y))
        val held = stickDirections[axisX]
        if (strength < (if (held != null) STICK_RELEASE else STICK_PRESS)) {
            stickDirections.remove(axisX)
            if (held != null) released(held)
            return false
        }
        val horizontal = kotlin.math.abs(x) >= kotlin.math.abs(y)
        var candidate = if (horizontal) PadInput.Axis(axisX, if (x < 0) -1 else 1) else PadInput.Axis(axisY, if (y < 0) -1 else 1)
        if (held != null && candidate != held) {
            // On garde la direction tenue tant que l'autre axe ne la dépasse pas nettement.
            val heldValue = kotlin.math.abs(if (held.axis == axisX) x else y)
            val otherValue = kotlin.math.abs(if (held.axis == axisX) y else x)
            if (candidate.axis != held.axis && otherValue < heldValue + STICK_SWITCH_MARGIN) candidate = held
        }
        if (candidate == held) return false
        if (strength < STICK_PRESS) return false
        if (held != null) released(held)
        stickDirections[axisX] = candidate
        return listeners.last()(candidate)
    }
}

/**
 * Écoute la manette tant que l'écran est affiché ; [onInput] renvoie vrai si la commande a servi. [onRelease], s'il est
 * donné, est appelé quand une commande qui avait servi est relâchée (pour les jeux où l'on tient une direction ou un tir).
 */
@Composable
internal fun GamepadListener(onRelease: ((PadInput) -> Unit)? = null, onInput: (PadInput) -> Boolean) {
    val current by rememberUpdatedState(onInput)
    val currentRelease by rememberUpdatedState(onRelease)
    DisposableEffect(Unit) {
        val listener: (PadInput) -> Boolean = { current(it) }
        val releaseListener: (PadInput) -> Unit = { currentRelease?.invoke(it) }
        GamepadHub.register(listener)
        if (onRelease != null) GamepadHub.registerRelease(releaseListener)
        onDispose {
            GamepadHub.unregister(listener)
            GamepadHub.unregisterRelease(releaseListener)
        }
    }
}

/**
 * Les réglages de manette d'un jeu, gardés d'une séance à l'autre : pour chaque action, soit les commandes par défaut,
 * soit la commande choisie par le joueur (une seule, qui remplace les valeurs par défaut).
 */
internal object GamepadBindings {
    private const val PREFS = "gamepad_bindings"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun encode(input: PadInput): String = when (input) {
        is PadInput.Key -> "k${input.code}"
        is PadInput.Scan -> "s${input.scanCode}"
        is PadInput.Axis -> "a${input.axis}:${input.sign}"
    }

    private fun decode(text: String): PadInput? = runCatching {
        if (text.startsWith("k")) PadInput.Key(text.substring(1).toInt())
        else if (text.startsWith("s")) PadInput.Scan(text.substring(1).toInt())
        else {
            val (axis, sign) = text.substring(1).split(":")
            PadInput.Axis(axis.toInt(), sign.toInt())
        }
    }.getOrNull()

    /** La commande choisie par le joueur pour [actionId], ou null s'il garde celles par défaut. */
    fun custom(context: Context, gameId: String, actionId: String): PadInput? =
        prefs(context).getString("$gameId.$actionId", null)?.let { decode(it) }

    /** Associe [input] à [actionId] ; la même commande est retirée aux autres actions du jeu pour éviter les doublons. */
    fun set(context: Context, gameId: String, actions: List<PadAction>, actionId: String, input: PadInput) {
        prefs(context).edit {
            actions.forEach { other ->
                if (other.id != actionId && custom(context, gameId, other.id) == input) remove("$gameId.${other.id}")
            }
            putString("$gameId.$actionId", encode(input))
        }
    }

    fun reset(context: Context, gameId: String, actions: List<PadAction>) {
        prefs(context).edit {
            actions.forEach { remove("$gameId.${it.id}") }
        }
    }

    /**
     * L'action déclenchée par [input] : les commandes choisies par le joueur passent avant celles par défaut, et une
     * action personnalisée n'utilise plus ses commandes par défaut.
     */
    fun resolve(context: Context, gameId: String, actions: List<PadAction>, input: PadInput): String? {
        val customs = actions.associate { it.id to custom(context, gameId, it.id) }
        customs.entries.firstOrNull { it.value == input }?.let { return it.key }
        return actions.firstOrNull { customs[it.id] == null && input in it.defaults }?.id
    }
}

private fun arrow(horizontal: Boolean, sign: Int) = if (horizontal) (if (sign < 0) "←" else "→") else (if (sign < 0) "↑" else "↓")

/** Le nom d'une commande, tel qu'affiché dans les réglages. */
@Composable
private fun inputLabel(input: PadInput): String = when (input) {
    is PadInput.Key -> when (input.code) {
        KeyEvent.KEYCODE_DPAD_UP -> stringResource(R.string.gamepad_dpad, "↑")
        KeyEvent.KEYCODE_DPAD_DOWN -> stringResource(R.string.gamepad_dpad, "↓")
        KeyEvent.KEYCODE_DPAD_LEFT -> stringResource(R.string.gamepad_dpad, "←")
        KeyEvent.KEYCODE_DPAD_RIGHT -> stringResource(R.string.gamepad_dpad, "→")
        KeyEvent.KEYCODE_BUTTON_L1 -> "L"
        KeyEvent.KEYCODE_BUTTON_R1 -> "R"
        KeyEvent.KEYCODE_BUTTON_L2 -> "ZL"
        KeyEvent.KEYCODE_BUTTON_R2 -> "ZR"
        KeyEvent.KEYCODE_BUTTON_THUMBL -> stringResource(R.string.gamepad_stick_click, stringResource(R.string.gamepad_left))
        KeyEvent.KEYCODE_BUTTON_THUMBR -> stringResource(R.string.gamepad_stick_click, stringResource(R.string.gamepad_right))
        else -> KeyEvent.keyCodeToString(input.code).removePrefix("KEYCODE_").removePrefix("BUTTON_")
    }
    is PadInput.Scan -> when (input.scanCode) {
        544 -> stringResource(R.string.gamepad_dpad, "↑")
        545 -> stringResource(R.string.gamepad_dpad, "↓")
        546 -> stringResource(R.string.gamepad_dpad, "←")
        547 -> stringResource(R.string.gamepad_dpad, "→")
        else -> stringResource(R.string.gamepad_button, input.scanCode)
    }
    is PadInput.Axis -> when (input.axis) {
        MotionEvent.AXIS_X -> stringResource(R.string.gamepad_stick, stringResource(R.string.gamepad_left), arrow(true, input.sign))
        MotionEvent.AXIS_Y -> stringResource(R.string.gamepad_stick, stringResource(R.string.gamepad_left), arrow(false, input.sign))
        MotionEvent.AXIS_Z -> stringResource(R.string.gamepad_stick, stringResource(R.string.gamepad_right), arrow(true, input.sign))
        MotionEvent.AXIS_RZ -> stringResource(R.string.gamepad_stick, stringResource(R.string.gamepad_right), arrow(false, input.sign))
        MotionEvent.AXIS_RX -> stringResource(R.string.gamepad_stick, stringResource(R.string.gamepad_right), arrow(true, input.sign))
        MotionEvent.AXIS_RY -> stringResource(R.string.gamepad_stick, stringResource(R.string.gamepad_right), arrow(false, input.sign))
        MotionEvent.AXIS_HAT_X -> stringResource(R.string.gamepad_dpad, arrow(true, input.sign))
        MotionEvent.AXIS_HAT_Y -> stringResource(R.string.gamepad_dpad, arrow(false, input.sign))
        MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_BRAKE -> "ZL"
        MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_GAS -> "ZR"
        else -> "${input.axis}"
    }
}

/** Les noms des manettes branchées en ce moment, mis à jour quand on en connecte ou déconnecte une. */
@Composable
private fun connectedGamepads(): List<String> {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val manager = context.getSystemService(Context.INPUT_SERVICE) as? InputManager
        val listener = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) { version++ }
            override fun onInputDeviceRemoved(deviceId: Int) { version++ }
            override fun onInputDeviceChanged(deviceId: Int) { version++ }
        }
        manager?.registerInputDeviceListener(listener, null)
        onDispose { manager?.unregisterInputDeviceListener(listener) }
    }
    return remember(version) {
        InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it) }
            .filter {
                (it.sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                    (it.sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
            }
            .map { it.name }
    }
}

/**
 * Le réglage de la manette pour un jeu : une ligne par action avec la commande actuelle, et un bouton « Changer » qui
 * attend ensuite le prochain bouton pressé ou stick incliné. Affiché dans la page du jeu (et non dans une boîte de
 * dialogue, dont la fenêtre ne reçoit pas les événements de manette).
 */
@Composable
internal fun GamepadSettings(gameId: String, actions: List<PadAction>, onClose: () -> Unit) {
    val context = LocalContext.current
    var capturing by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val pads = connectedGamepads()

    GamepadListener { input ->
        val target = capturing
        if (target != null) {
            GamepadBindings.set(context, gameId, actions, target, input)
            capturing = null
            refresh++
            true
        } else false
    }

    Text(
        text = stringResource(R.string.gamepad_title),
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center
    )
    Text(
        text = if (pads.isEmpty()) stringResource(R.string.gamepad_none) else stringResource(R.string.gamepad_connected, pads.joinToString(", ")),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Text(
        text = stringResource(R.string.gamepad_last, GamepadHub.lastSignal.ifEmpty { "—" }),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    actions.forEach { action ->
        val custom = remember(refresh) { GamepadBindings.custom(context, gameId, action.id) }
        val current = if (custom != null) {
            inputLabel(custom)
        } else {
            action.defaults.map { inputLabel(it) }.distinct().joinToString(" / ")
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(action.label), style = MaterialTheme.typography.titleSmall)
                Text(
                    text = if (capturing == action.id) stringResource(R.string.gamepad_press) else current,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (capturing == action.id) {
                OutlinedButton(onClick = { capturing = null }) { Text(stringResource(R.string.gamepad_cancel)) }
            } else {
                OutlinedButton(onClick = { capturing = action.id }) { Text(stringResource(R.string.gamepad_change)) }
            }
        }
    }
    OutlinedButton(
        onClick = {
            GamepadBindings.reset(context, gameId, actions)
            capturing = null
            refresh++
        },
        modifier = Modifier.fillMaxWidth()
    ) { Text(stringResource(R.string.gamepad_reset)) }
    Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.gamepad_close)) }
}
