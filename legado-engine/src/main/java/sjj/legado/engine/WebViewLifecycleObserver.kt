package sjj.legado.engine

import android.view.ViewGroup
import android.webkit.WebView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class WebViewLifecycleObserver(private val webView: WebView) : DefaultLifecycleObserver {
    override fun onResume(owner: LifecycleOwner) {
        webView.onResume()
        // webView.resumeTimers() // 不常用
    }
    override fun onPause(owner: LifecycleOwner) {
        webView.onPause()
        // webView.pauseTimers()
    }
    override fun onDestroy(owner: LifecycleOwner) {
        // 完整销毁见下面示例
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.apply {
            stopLoading()
            settings.javaScriptEnabled = false
            clearHistory()
            clearCache(true)
            loadUrl("about:blank")
            removeAllViews()
            destroy()
        }
    }
}