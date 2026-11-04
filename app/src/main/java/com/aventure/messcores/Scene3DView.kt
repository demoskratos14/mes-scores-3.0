package com.aventure.messcores

import android.content.res.AssetManager
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.createBitmap
import java.io.IOException

/*
 * Le côté Android de la 3D : chargement (avec mémoire) des modèles .glb des assets, et une vue Compose qui dessine une
 * [Scene3D] avec le moteur de Renderer3D.kt. Le dessin se fait dans la phase « draw » : une animation qui change la scène
 * dans [update] ne recompose rien, elle redessine seulement la vue.
 */

/** Les maillages des assets, chargés une fois et gardés en mémoire (environ 5 Mo pour les onze fichiers). */
internal object WizardModels {
    private val cache = HashMap<String, Mesh3D>()
    private val failed = HashSet<String>()

    /**
     * Le maillage du modèle [model] (nom sans extension, voir [WizardModelCatalog]), ou null si le fichier manque ou est
     * illisible : l'écran peut alors garder ses statues dessinées en vectoriel.
     */
    @Synchronized
    fun mesh(assets: AssetManager, model: String): Mesh3D? {
        cache[model]?.let { return it }
        if (model in failed) return null
        val loaded = try {
            assets.open(WizardModelCatalog.assetPath(model)).use { GlbParser.parse(it.readBytes()) }
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
        if (loaded == null) failed.add(model) else cache[model] = loaded
        return loaded
    }

    /** La pièce [piece] (lettre de l'échiquier) prête à placer dans une scène, de la matière de son camp, ou null. */
    fun pieceInstance(assets: AssetManager, piece: Char): Instance3D? {
        val model = WizardModelCatalog.pieceModel(piece) ?: return null
        val mesh = mesh(assets, model) ?: return null
        return Instance3D(mesh, WizardModelCatalog.materialFor(piece))
    }

    /** L'arme de la pièce [piece] (même matière que la pièce), ou null pour un pion ou si le fichier manque. */
    fun weaponInstance(assets: AssetManager, piece: Char): Instance3D? {
        val model = WizardModelCatalog.weaponModel(piece) ?: return null
        val mesh = mesh(assets, model) ?: return null
        return Instance3D(mesh, WizardModelCatalog.materialFor(piece))
    }

    /** Charge les onze modèles d'avance (à faire hors du fil principal : 50 à 100 ms au total). */
    fun preload(assets: AssetManager) {
        for (model in WizardModelCatalog.allModels) mesh(assets, model)
    }
}

/**
 * Dessine [scene] sur toute la surface de la vue, fond transparent. [update] est appelée avant chaque image : c'est là
 * qu'on place les pièces (`instance.setPose(…)`) et la caméra d'après l'avancement de l'animation. [renderScale] réduit la
 * résolution de calcul (0,5 = quatre fois moins de pixels) pour les téléphones lents ; [supersampling] (1 à 4, 1 par défaut ; 2 pour une image fixe) adoucit
 * les bords en calculant plus grand puis en réduisant.
 */
@Composable
internal fun Scene3DView(
    scene: Scene3D,
    modifier: Modifier = Modifier,
    renderScale: Float = 1f,
    supersampling: Int = 1,
    update: (Scene3D) -> Unit = {}
) {
    val renderer = remember { Renderer3D() }
    val holder = remember { Scene3DBuffers() }
    Canvas(modifier) {
        if (size.width < 1f || size.height < 1f) return@Canvas
        update(scene)
        val w = (size.width * renderScale).toInt().coerceIn(1, MAX_SIDE)
        val h = (size.height * renderScale).toInt().coerceIn(1, MAX_SIDE)
        val frame = holder.frame(w, h, supersampling)
        renderer.render(scene, frame)
        val bitmap = holder.bitmap(w, h)
        bitmap.setPixels(frame.pixels, 0, w, 0, 0, w, h)
        drawImage(
            image = bitmap.asImageBitmap(),
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            filterQuality = FilterQuality.Medium
        )
    }
}

private const val MAX_SIDE = 2048

// Garde l'image de travail et le Bitmap d'une image à l'autre (on ne les refait que si la taille change).
private class Scene3DBuffers {
    private var frame: Frame3D? = null
    private var bitmap: Bitmap? = null

    fun frame(w: Int, h: Int, supersampling: Int): Frame3D {
        val f = frame
        if (f != null && f.width == w && f.height == h && f.supersampling == supersampling) return f
        return Frame3D(w, h, supersampling).also { frame = it }
    }

    fun bitmap(w: Int, h: Int): Bitmap {
        val b = bitmap
        if (b != null && b.width == w && b.height == h) return b
        return createBitmap(w, h).also { bitmap = it }
    }
}
