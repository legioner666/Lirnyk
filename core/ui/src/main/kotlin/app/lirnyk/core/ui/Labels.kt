package app.lirnyk.core.ui

import android.content.Context
import java.util.Locale
import app.lirnyk.core.model.SourceKind

object Labels {
    /** 3:07, 1:02:45. */
    fun time(ms: Long): String {
        val total = (ms.coerceAtLeast(0) / 1000)
        val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
    }

    fun source(context: Context, kind: SourceKind): String = context.getString(when (kind) {
        SourceKind.INTERNAL -> R.string.src_kind_internal
        SourceKind.SD_CARD -> R.string.src_kind_sd
        SourceKind.USB -> R.string.src_kind_usb
        SourceKind.REMOVABLE -> R.string.src_kind_removable
        SourceKind.PICKED_FOLDER -> R.string.src_kind_folder
    })
}
