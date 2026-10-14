package com.aventure.messcores

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Partage d'un fichier (image, JSON) par le menu de partage d'Android. Le fichier est écrit dans le
 * cache de l'appli et prêté à l'application choisie par un FileProvider : aucune permission.
 */
object ShareFiles {

    private const val DIRECTORY = "shared"

    /** Écrit [bytes] dans le cache sous [fileName] (les anciens fichiers partagés sont supprimés). */
    fun write(context: Context, fileName: String, bytes: ByteArray): File {
        val dir = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        return File(dir, fileName).also { it.writeBytes(bytes) }
    }

    /** Ouvre le menu de partage d'Android avec [file] ; [title] est le titre du menu. */
    fun share(context: Context, file: File, mimeType: String, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, title))
    }
}
