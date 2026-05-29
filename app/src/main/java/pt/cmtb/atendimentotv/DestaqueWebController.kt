package pt.cmtb.atendimentotv

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.FragmentActivity
import pt.cmtb.atendimentotv.databinding.ActivityMainBinding

/**
 * Vista web no palco — modo quiosque: apenas [startUrl] (/eventos).
 * Bloqueia navegação interna (detalhes, menu Wix) e externa; otimizada para TV.
 */
class DestaqueWebController(
    private val activity: FragmentActivity,
    private val binding: ActivityMainBinding,
    private val startUrl: String
) {
    private var loaded = false
    private var loadInProgress = false
    private var jsLockInjected = false
    private var revertingNavigation = false

    private val allowedUri: Uri = Uri.parse(startUrl)
    private val allowedHost: String? = allowedUri.host?.lowercase()
    private val allowedPath: String = normalizePath(allowedUri.path)

    @SuppressLint("SetJavaScriptEnabled")
    fun setup() {
        val webView = binding.webDestaque
        webView.setBackgroundColor(activity.getColor(R.color.bg_destaque))
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.overScrollMode = View.OVER_SCROLL_NEVER

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = false
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                offscreenPreRaster = true
            }
            @Suppress("DEPRECATION")
            setRenderPriority(WebSettings.RenderPriority.HIGH)
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean = false
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                if (request?.isForMainFrame != true) return false
                val target = request.url?.toString() ?: return true
                if (loadInProgress && isListingPageUrl(target)) return false
                return true
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url == null) return true
                if (loadInProgress && isListingPageUrl(url)) return false
                return true
            }

            override fun onPageStarted(view: WebView?, pageUrl: String?, favicon: Bitmap?) {
                if (revertingNavigation) return
                if (pageUrl != null && !isListingPageUrl(pageUrl)) {
                    revertToListing(view)
                    return
                }
                view?.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                loadInProgress = false
                if (url != null && !isListingPageUrl(url)) {
                    revertToListing(view)
                    return
                }
                view?.let { injectNavigationLock(it) }
            }
        }

        preload()
    }

    private fun revertToListing(view: WebView?) {
        if (view == null || revertingNavigation) return
        revertingNavigation = true
        jsLockInjected = false
        view.stopLoading()
        view.post {
            view.loadUrl(startUrl)
            revertingNavigation = false
        }
    }

    /** Apenas a página de listagem /eventos (ignora query e fragmento). */
    private fun isListingPageUrl(url: String): Boolean {
        val uri = Uri.parse(url)
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return false
        if (uri.host?.lowercase() != allowedHost) return false
        return normalizePath(uri.path) == allowedPath
    }

    private fun normalizePath(path: String?): String {
        var p = path?.trim().orEmpty().ifEmpty { "/" }
        if (p.length > 1 && p.endsWith("/")) {
            p = p.dropLast(1)
        }
        return p
    }

  /**
   * Bloqueia cliques e history.pushState do Wix que contornam shouldOverrideUrlLoading.
   */
    private fun injectNavigationLock(webView: WebView) {
        if (jsLockInjected) return
        jsLockInjected = true
        val host = allowedHost ?: return
        val path = allowedPath.replace("'", "\\'")
        val script = """
            (function() {
              if (window.__cmtbNavLock) return;
              window.__cmtbNavLock = true;
              var ALLOWED_HOST = '$host';
              var ALLOWED_PATH = '$path';
              function normalizePath(p) {
                p = p || '/';
                if (p.length > 1 && p.charAt(p.length - 1) === '/') p = p.slice(0, -1);
                return p || '/';
              }
              function isListing(url) {
                try {
                  var u = new URL(url, location.origin);
                  return u.hostname.toLowerCase() === ALLOWED_HOST &&
                    normalizePath(u.pathname) === ALLOWED_PATH;
                } catch (e) { return false; }
              }
              document.addEventListener('click', function(e) {
                var a = e.target.closest('a[href]');
                if (!a) return;
                var href = a.getAttribute('href');
                if (!href || href.charAt(0) === '#') return;
                if (!isListing(href)) {
                  e.preventDefault();
                  e.stopImmediatePropagation();
                }
              }, true);
              var push = history.pushState.bind(history);
              var replace = history.replaceState.bind(history);
              history.pushState = function(state, title, url) {
                if (url == null || url === '' || isListing(url)) push(state, title, url);
              };
              history.replaceState = function(state, title, url) {
                if (url == null || url === '' || isListing(url)) replace(state, title, url);
              };
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    /** Carrega a página em background para o 1.º show ser mais rápido. */
    fun preload() {
        if (!loaded) {
            reloadFromStart()
        }
    }

    fun show() {
        hideDocumentLayers()
        binding.webDestaque.visibility = View.VISIBLE
        binding.webDestaque.onResume()
        binding.webDestaque.resumeTimers()
    }

    fun reloadFromStart() {
        loadInProgress = true
        jsLockInjected = false
        binding.webDestaque.loadUrl(startUrl)
        loaded = true
    }

    fun hide() {
        binding.webDestaque.apply {
            onPause()
            pauseTimers()
            visibility = View.GONE
        }
    }

    fun destroy() {
        binding.webDestaque.apply {
            stopLoading()
            destroy()
        }
        loaded = false
        loadInProgress = false
        jsLockInjected = false
    }

    private fun hideDocumentLayers() {
        binding.tvSemDocumento.visibility = View.GONE
        binding.tvPaginaIndicador.visibility = View.GONE
        binding.btnPaginaAnterior.visibility = View.GONE
        binding.btnPaginaSeguinte.visibility = View.GONE
        DestaqueImageLoader.clear(activity, binding)
    }
}
