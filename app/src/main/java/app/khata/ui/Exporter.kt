package app.khata.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate

object Exporter {
    const val MIME = "text/tab-separated-values"
    // BOM so Excel opens names with non-ASCII characters (₹, Devanagari, etc.) correctly.
    private const val BOM = "﻿"

    fun fileName() = "khata-ledger-${LocalDate.now()}.tsv"

    fun share(context: Context, tsv: String) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, fileName()).apply { writeText(BOM + tsv) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(MIME)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "Khata ledger ${LocalDate.now()}")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(file.name, uri)
        context.startActivity(Intent.createChooser(send, "Share ledger"))
    }

    fun save(context: Context, target: Uri, tsv: String) {
        runCatching {
            context.contentResolver.openOutputStream(target)!!.use { it.write((BOM + tsv).toByteArray()) }
        }.onSuccess { Toast.makeText(context, "Ledger saved", Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(context, "Couldn't save: ${it.message}", Toast.LENGTH_LONG).show() }
    }
}
