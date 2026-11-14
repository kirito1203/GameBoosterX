package com.gamebooster.x.gamespace

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/** Valores mostrados no painel; nulos aparecem como "—" (sem dado inventado). */
data class OverlayData(
    val gameName: String,
    val fps: Int?,
    val batteryCelsius: Float?,
    val ramPercent: Int?,
    val batteryPercent: Int?,
    val clock: String,
    val dndActive: Boolean,
    val brightnessLocked: Boolean,
    val recording: Boolean,
    val captureAvailable: Boolean,
    val showFps: Boolean,
)

interface OverlayActions {
    fun onScreenshot()
    fun onToggleRecording()
    fun onToggleDnd()
    fun onToggleBrightness()
}

/**
 * Bolinha flutuante arrastável + painel expansível, desenhados com Views clássicas
 * (mais leves que Compose dentro de um serviço). Requer a permissão "Sobrepor a outros apps".
 */
class OverlayController(context: Context, private val actions: OverlayActions) {

    private val ctx = context.applicationContext
    private val wm: WindowManager = checkNotNull(ctx.getSystemService(WindowManager::class.java))
    private val main = Handler(Looper.getMainLooper())

    private var root: LinearLayout? = null
    private var bubble: TextView? = null
    private var panel: LinearLayout? = null
    private var ticker: TextView? = null
    private var params: WindowManager.LayoutParams? = null

    private val statsText = HashMap<String, TextView>()
    private var btnRecord: Button? = null
    private var btnDnd: Button? = null
    private var btnBrightness: Button? = null
    private var btnShot: Button? = null
    private var showFps = true

    val isShowing: Boolean get() = root != null

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).toInt()

    private fun rounded(color: Int, radiusDp: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        stroke?.let { setStroke(dp(1), it) }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun show() = main.post {
        if (root != null || !Settings.canDrawOverlays(ctx)) return@post

        val bubbleView = TextView(ctx).apply {
            text = "GBX"
            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            background = rounded(0xCC0B1220.toInt(), 24, 0xFF00B8FF.toInt())
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }

        val panelView = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(0xEE0B1220.toInt(), 14, 0xFF243040.toInt())
            setPadding(dp(12), dp(10), dp(12), dp(10))
            visibility = View.GONE
        }
        listOf("game", "fps", "temp", "ram", "bat", "clock").forEach { key ->
            val tv = TextView(ctx).apply {
                setTextColor(if (key == "game") 0xFF00B8FF.toInt() else Color.WHITE)
                typeface = Typeface.MONOSPACE
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            }
            statsText[key] = tv
            panelView.addView(tv)
        }
        fun button(label: String, onClick: () -> Unit) = Button(ctx).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            background = rounded(0xFF2F7BFF.toInt(), 8)
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(6) }
        }
        btnShot = button("📷 Capturar tela") { actions.onScreenshot() }.also { panelView.addView(it) }
        btnRecord = button("⏺ Gravar tela") { actions.onToggleRecording() }.also { panelView.addView(it) }
        btnDnd = button("🔕 Não perturbe") { actions.onToggleDnd() }.also { panelView.addView(it) }
        btnBrightness = button("☀ Travar brilho") { actions.onToggleBrightness() }.also { panelView.addView(it) }

        val tickerView = TextView(ctx).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            background = rounded(0xCC0B1220.toInt(), 10)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            maxLines = 2
            maxWidth = dp(220)
            visibility = View.GONE
        }

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(bubbleView)
            addView(tickerView)
            addView(panelView)
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = dp(120)
        }

        // Arrastar a bolinha; toque curto abre/fecha o painel.
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        bubbleView.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY; startX = lp.x; startY = lp.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = startX + (ev.rawX - downX).toInt()
                    lp.y = startY + (ev.rawY - downY).toInt()
                    runCatching { wm.updateViewLayout(container, lp) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (abs(ev.rawX - downX) < dp(6) && abs(ev.rawY - downY) < dp(6)) {
                        panelView.visibility = if (panelView.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                    }
                    true
                }
                else -> false
            }
        }

        runCatching { wm.addView(container, lp) }.onSuccess {
            root = container; bubble = bubbleView; panel = panelView; ticker = tickerView; params = lp
        }
    }

    fun update(d: OverlayData) = main.post {
        showFps = d.showFps
        bubble?.text = if (d.showFps) (d.fps?.let { "$it FPS" } ?: "— FPS") else "GBX"
        statsText["game"]?.text = d.gameName
        statsText["fps"]?.text = "FPS: " + (d.fps?.toString() ?: "sem fonte (Shizuku)")
        statsText["temp"]?.text = "Bateria: " + (d.batteryCelsius?.let { String.format(java.util.Locale.US, "%.1f °C", it) } ?: "—")
        statsText["ram"]?.text = "RAM: " + (d.ramPercent?.let { "$it%" } ?: "—")
        statsText["bat"]?.text = "Carga: " + (d.batteryPercent?.let { "$it%" } ?: "—")
        statsText["clock"]?.text = d.clock
        btnRecord?.text = if (d.recording) "⏹ Parar gravação" else "⏺ Gravar tela"
        btnRecord?.isEnabled = d.captureAvailable
        btnShot?.isEnabled = d.captureAvailable
        btnDnd?.text = if (d.dndActive) "🔕 Não perturbe: LIGADO" else "🔔 Não perturbe: desligado"
        btnBrightness?.text = if (d.brightnessLocked) "☀ Brilho travado" else "☀ Brilho automático"
    }

    fun showNotification(text: String) = main.post {
        val t = ticker ?: return@post
        t.text = text
        t.visibility = View.VISIBLE
        main.removeCallbacksAndMessages(TICKER_TOKEN)
        main.postAtTime({ t.visibility = View.GONE }, TICKER_TOKEN, android.os.SystemClock.uptimeMillis() + 4_000)
    }

    /** Esconde tudo (para a captura de tela não incluir o painel) e mostra de novo depois. */
    fun setHidden(hidden: Boolean) = main.post {
        root?.visibility = if (hidden) View.INVISIBLE else View.VISIBLE
        if (hidden) panel?.visibility = View.GONE
    }

    fun hide() = main.post {
        root?.let { runCatching { wm.removeView(it) } }
        root = null; bubble = null; panel = null; ticker = null; params = null
        statsText.clear()
    }

    private companion object {
        val TICKER_TOKEN = Any()
    }
}
