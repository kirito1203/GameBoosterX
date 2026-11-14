package com.gamebooster.x.gamespace

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs

/** Valores mostrados na barra; nulos aparecem como "—" (sem dado inventado). */
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

/** Atalho de app na barra lateral. */
data class SidebarShortcut(val packageName: String, val label: String)

interface OverlayActions {
    fun onScreenshot()
    fun onToggleRecording()
    fun onToggleDnd()
    fun onToggleBrightness()

    /** [floating] = tentar abrir em janela flutuante; false = tela cheia. */
    fun onLaunchShortcut(packageName: String, floating: Boolean)
}

/**
 * Barra lateral estilo Game Space: uma alça fina na borda direita (mostra o FPS) que abre um
 * painel com status, ferramentas e atalhos de apps. Toque fora fecha o painel.
 * Views clássicas (leves dentro de um serviço). Requer "Sobrepor a outros apps".
 */
class OverlayController(context: Context, private val actions: OverlayActions) {

    private val ctx = context.applicationContext
    private val wm: WindowManager = checkNotNull(ctx.getSystemService(WindowManager::class.java))
    private val main = Handler(Looper.getMainLooper())

    private var root: LinearLayout? = null
    private var handle: TextView? = null
    private var panel: ScrollView? = null
    private var ticker: TextView? = null
    private var shortcutsBox: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null

    private val statsText = HashMap<String, TextView>()
    private var btnRecord: Button? = null
    private var btnDnd: Button? = null
    private var btnBrightness: Button? = null
    private var btnShot: Button? = null
    private var shortcuts: List<SidebarShortcut> = emptyList()

    val isShowing: Boolean get() = root != null

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).toInt()

    private fun rounded(color: Int, radiusDp: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        stroke?.let { setStroke(dp(1), it) }
    }

    private fun label(text: String, sizeSp: Float = 12f, color: Int = Color.WHITE) = TextView(ctx).apply {
        this.text = text
        setTextColor(color)
        typeface = Typeface.MONOSPACE
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    }

    private fun toolButton(text: String, onClick: () -> Unit) = Button(ctx).apply {
        this.text = text
        isAllCaps = false
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        background = rounded(0xFF1E3A70.toInt(), 10, 0xFF2F7BFF.toInt())
        minHeight = 0
        minimumHeight = 0
        minWidth = 0
        minimumWidth = 0
        setPadding(dp(6), dp(8), dp(6), dp(8))
        setOnClickListener { onClick() }
        layoutParams = GridLayout.LayoutParams().apply {
            width = dp(98)
            setMargins(dp(3), dp(3), dp(3), dp(3))
        }
    }

    private fun setPanelOpen(open: Boolean) {
        panel?.visibility = if (open) View.VISIBLE else View.GONE
        handle?.visibility = if (open) View.GONE else View.VISIBLE
    }

    @SuppressLint("ClickableViewAccessibility")
    fun show() = main.post {
        if (root != null || !Settings.canDrawOverlays(ctx)) return@post

        // Alça na borda: mostra o FPS; toque abre o painel, arrastar move na vertical.
        val handleView = TextView(ctx).apply {
            text = "GBX"
            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(0xCC0B1220.toInt())
                setStroke(dp(1), 0xFF00B8FF.toInt())
                cornerRadii = floatArrayOf(
                    dp(14).toFloat(), dp(14).toFloat(), 0f, 0f, 0f, 0f, dp(14).toFloat(), dp(14).toFloat(),
                )
            }
            setPadding(dp(8), dp(14), dp(6), dp(14))
        }

        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        content.addView(label("GAME BOOSTER X", 11f, 0xFF00B8FF.toInt()))
        listOf("game", "fps", "temp", "ram", "bat").forEach { key ->
            val tv = label("", 12f, if (key == "game") 0xFF8B98A9.toInt() else Color.WHITE)
            statsText[key] = tv
            content.addView(tv)
        }

        content.addView(label("FERRAMENTAS", 10f, 0xFF00B8FF.toInt()).apply { setPadding(0, dp(8), 0, dp(2)) })
        val tools = GridLayout(ctx).apply { columnCount = 2 }
        btnShot = toolButton("📷 Captura") { actions.onScreenshot() }.also { tools.addView(it) }
        btnRecord = toolButton("⏺ Gravar") { actions.onToggleRecording() }.also { tools.addView(it) }
        btnDnd = toolButton("🔕 Não perturbe") { actions.onToggleDnd() }.also { tools.addView(it) }
        btnBrightness = toolButton("☀ Brilho") { actions.onToggleBrightness() }.also { tools.addView(it) }
        content.addView(tools)

        content.addView(label("APPS · toque = janela · segurar = tela cheia", 9f, 0xFF00B8FF.toInt()).apply {
            setPadding(0, dp(8), 0, dp(2))
        })
        val shortcutsView = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        content.addView(shortcutsView)

        content.addView(toolButton("✕ Fechar") { setPanelOpen(false) }.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(8) }
        })

        val panelView = ScrollView(ctx).apply {
            background = rounded(0xF00B1220.toInt(), 16, 0xFF243040.toInt())
            addView(content)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(dp(224), LinearLayout.LayoutParams.WRAP_CONTENT)
        }

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
            gravity = Gravity.END
            addView(tickerView)
            addView(handleView)
            addView(panelView)
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 0
            y = dp(140)
        }

        // Toque fora do painel fecha (o toque continua indo para o jogo).
        container.setOnTouchListener { _, ev ->
            if (ev.action == MotionEvent.ACTION_OUTSIDE) setPanelOpen(false)
            false
        }

        var downY = 0f
        var downX = 0f
        var startY = 0
        handleView.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY; startY = lp.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.y = (startY + (ev.rawY - downY).toInt()).coerceAtLeast(0)
                    runCatching { wm.updateViewLayout(container, lp) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val moved = abs(ev.rawY - downY) > dp(8)
                    val swipedIn = downX - ev.rawX > dp(12) // deslizar da borda para dentro também abre
                    if (!moved || swipedIn) setPanelOpen(true)
                    true
                }
                else -> false
            }
        }

        runCatching { wm.addView(container, lp) }.onSuccess {
            root = container; handle = handleView; panel = panelView; ticker = tickerView
            shortcutsBox = shortcutsView; params = lp
            renderShortcuts()
        }
    }

    fun setShortcuts(list: List<SidebarShortcut>) = main.post {
        if (list == shortcuts) return@post
        shortcuts = list
        renderShortcuts()
    }

    private fun renderShortcuts() {
        val box = shortcutsBox ?: return
        box.removeAllViews()
        if (shortcuts.isEmpty()) {
            box.addView(label("Escolha apps na aba Game Space", 10f, 0xFF8B98A9.toInt()))
            return
        }
        val pm = ctx.packageManager
        shortcuts.forEach { s ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = rounded(0xFF161B22.toInt(), 10)
                setPadding(dp(6), dp(4), dp(6), dp(4))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(4) }
                setOnClickListener {
                    setPanelOpen(false)
                    actions.onLaunchShortcut(s.packageName, floating = true)
                }
                setOnLongClickListener {
                    setPanelOpen(false)
                    actions.onLaunchShortcut(s.packageName, floating = false)
                    true
                }
            }
            val icon = ImageView(ctx).apply {
                runCatching { setImageDrawable(pm.getApplicationIcon(s.packageName)) }
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
            }
            row.addView(icon)
            row.addView(label(s.label, 12f).apply { setPadding(dp(8), 0, 0, 0); maxLines = 1 })
            box.addView(row)
        }
    }

    fun update(d: OverlayData) = main.post {
        handle?.text = if (d.showFps) (d.fps?.toString() ?: "—") + "\nFPS" else "GBX"
        statsText["game"]?.text = d.gameName + " · " + d.clock
        statsText["fps"]?.text = "FPS      " + (d.fps?.toString() ?: "sem fonte")
        statsText["temp"]?.text = "Bateria  " + (d.batteryCelsius?.let { String.format(java.util.Locale.US, "%.1f °C", it) } ?: "—")
        statsText["ram"]?.text = "RAM      " + (d.ramPercent?.let { "$it%" } ?: "—")
        statsText["bat"]?.text = "Carga    " + (d.batteryPercent?.let { "$it%" } ?: "—")
        btnRecord?.text = if (d.recording) "⏹ Parar" else "⏺ Gravar"
        btnRecord?.isEnabled = d.captureAvailable
        btnShot?.isEnabled = d.captureAvailable
        btnDnd?.text = if (d.dndActive) "🔕 Silencioso ✔" else "🔔 Não perturbe"
        btnBrightness?.text = if (d.brightnessLocked) "☀ Brilho ✔" else "☀ Brilho auto"
    }

    fun showNotification(text: String) = main.post {
        val t = ticker ?: return@post
        t.text = text
        t.visibility = View.VISIBLE
        main.removeCallbacksAndMessages(TICKER_TOKEN)
        main.postAtTime({ t.visibility = View.GONE }, TICKER_TOKEN, SystemClock.uptimeMillis() + 4_000)
    }

    /** Esconde tudo (para a captura de tela não incluir a barra) e mostra de novo depois. */
    fun setHidden(hidden: Boolean) = main.post {
        if (hidden) setPanelOpen(false)
        root?.visibility = if (hidden) View.INVISIBLE else View.VISIBLE
    }

    fun hide() = main.post {
        root?.let { runCatching { wm.removeView(it) } }
        root = null; handle = null; panel = null; ticker = null; shortcutsBox = null; params = null
        statsText.clear()
    }

    private companion object {
        val TICKER_TOKEN = Any()
    }
}
