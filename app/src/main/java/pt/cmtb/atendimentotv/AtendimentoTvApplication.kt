package pt.cmtb.atendimentotv

import android.app.Application
import android.os.Build
import android.webkit.WebView

/** Pré-inicializa o motor Chromium da WebView para reduzir atraso no primeiro carregamento. */
class AtendimentoTvApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val process = getProcessName()
            if (process != null && process != packageName) return
        }
        try {
            WebView(this).destroy()
        } catch (_: Exception) {
            // Ignorar em dispositivos sem WebView instalada
        }
    }
}
