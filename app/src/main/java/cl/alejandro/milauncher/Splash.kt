package cl.alejandro.milauncher

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Logo de la marca a pantalla completa al encender la radio, con "LANCER GLX" debajo.
 *
 * Se dibuja encima de la pantalla principal (no es otra Activity, así es instantáneo y liviano):
 * los diamantes llegan uno por uno, luego el texto, se queda unos segundos y desaparece.
 * Tocar la pantalla lo salta.
 *
 * Los diamantes se dibujan en DiamondsView (abajo) y "MITSUBISHI" es res/drawable-nodpi/logo_texto.png.
 * Para cambiar la duración, edita TOTAL_MS. Para desactivarlo, pon ENABLED = false.
 */
class Splash(private val activity: Activity) {

    private var view: View? = null

    /** Se llama cuando el logo empieza a desaparecer (para el barrido del velocímetro). */
    var onHide: (() -> Unit)? = null

    val isShowing get() = view != null

    fun show() {
        if (!ENABLED || view != null) return
        val root = activity.window.decorView as ViewGroup

        val dp = activity.resources.displayMetrics.density
        val screenH = activity.resources.displayMetrics.heightPixels

        // Los tres diamantes (dibujados, para animarlos por separado) y "MITSUBISHI" debajo
        val diamonds = DiamondsView(activity)
        val diamondsH = (screenH * 0.36f).toInt()
        val diamondsW = diamondsH * 641 / 555
        val brand = ImageView(activity).apply {
            setImageResource(R.drawable.logo_texto)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
        }
        // Debajo: el nombre del auto en plata, como la placa del maletero
        val name = TextView(activity).apply {
            setText(R.string.car_name)
            setTextColor(activity.getColor(R.color.silver))
            textSize = 30f
            letterSpacing = 0.45f
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        val line = View(activity).apply { setBackgroundResource(R.drawable.accent_line) }

        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(diamonds, LinearLayout.LayoutParams(diamondsW, diamondsH))
            // El texto mide 553 de los 641 de ancho del logo original
            addView(brand, LinearLayout.LayoutParams(diamondsW * 553 / 641, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (12 * dp).toInt()
            })
            addView(line, LinearLayout.LayoutParams((90 * dp).toInt(), (3 * dp).toInt()).apply {
                topMargin = (22 * dp).toInt()
            })
            addView(name, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (10 * dp).toInt()
            })
        }
        val overlay = FrameLayout(activity).apply {
            setBackgroundColor(0xFF000000.toInt())
            isClickable = true   // que los toques no lleguen a los botones de abajo
            addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
            setOnClickListener { hide() }
        }
        root.addView(overlay, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        view = overlay

        // 1) Los diamantes llegan uno por uno: arriba, izquierda, derecha.
        diamonds.start()
        // 2) Aparece "MITSUBISHI".
        brand.alpha = 0f
        brand.animate().alpha(1f).setStartDelay(1000).setDuration(500)
            .setInterpolator(DecelerateInterpolator()).start()
        // 3) Suben la línea roja y "LANCER GLX".
        for (v in listOf(line, name)) {
            v.alpha = 0f
            v.translationY = 12 * dp
            v.animate().alpha(1f).translationY(0f).setStartDelay(1300)
                .setDuration(600).setInterpolator(DecelerateInterpolator()).start()
        }

        overlay.postDelayed({ if (view === overlay) hide() }, TOTAL_MS - FADE_OUT_MS)
    }

    /** Desaparece con un fundido y deja ver la pantalla principal. */
    fun hide() {
        val v = view ?: return
        view = null
        onHide?.invoke()
        onHide = null
        v.isClickable = false
        v.animate().alpha(0f).setDuration(FADE_OUT_MS).setInterpolator(AccelerateInterpolator())
            .withEndAction { (v.parent as? ViewGroup)?.removeView(v) }
            .start()
    }

    /** Quita el logo de inmediato (por ejemplo, si la pantalla se apaga mientras se muestra). */
    fun cancel() {
        onHide = null
        val v = view ?: return
        view = null
        v.animate().cancel()
        (v.parent as? ViewGroup)?.removeView(v)
    }

    companion object {
        const val ENABLED = true
        const val TOTAL_MS = 3000L
        private const val FADE_OUT_MS = 500L

        /**
         * ¿La radio arrancó desde la última vez que se revisó? Usa el contador de encendidos
         * de Android, así no se repite si la app se cierra y vuelve a abrir sola.
         */
        fun isNewBoot(context: Context): Boolean {
            val prefs = context.getSharedPreferences("general", Context.MODE_PRIVATE)
            val current = bootId(context)
            if (prefs.getString("last_boot", null) == current) return false
            prefs.edit().putString("last_boot", current).apply()
            return true
        }

        private fun bootId(context: Context): String {
            val count = try {
                Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
            } catch (e: Exception) {
                -1
            }
            if (count >= 0) return "count:$count"
            // Sin contador: hora aproximada del arranque, redondeada a 1 minuto.
            val bootAt = System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime()
            return "time:" + (bootAt / 60_000) + ":" + Build.VERSION.SDK_INT
        }
    }
}

/**
 * Los tres diamantes de Mitsubishi, cada uno entrando desde su lado con un fundido:
 * el de arriba baja, el de la izquierda y el de la derecha suben desde abajo.
 */
private class DiamondsView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LOGO_RED }
    private val path = Path()
    private var t = 0f   // tiempo de la animación en segundos
    private var animator: ValueAnimator? = null

    fun start() {
        animator = ValueAnimator.ofFloat(0f, TOTAL_S).apply {
            duration = (TOTAL_S * 1000).toLong()
            interpolator = LinearInterpolator()
            addUpdateListener {
                t = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val k = width / 641f
        for ((i, d) in DIAMONDS.withIndex()) {
            val u = ease((t - i * STAGGER_S) / PIECE_S)
            if (u <= 0f) continue
            paint.alpha = (255 * u).toInt()
            val dx = OFFSETS[i * 2] * (1 - u) * k
            val dy = OFFSETS[i * 2 + 1] * (1 - u) * k
            path.reset()
            path.moveTo(d[0] * k + dx, d[1] * k + dy)
            for (j in 2 until d.size step 2) path.lineTo(d[j] * k + dx, d[j + 1] * k + dy)
            path.close()
            canvas.drawPath(path, paint)
        }
    }

    /** Frena al llegar (curva "ease out"). */
    private fun ease(x: Float): Float {
        val c = x.coerceIn(0f, 1f)
        return 1 - (1 - c) * (1 - c) * (1 - c)
    }

    private companion object {
        const val LOGO_RED = 0xFFED1C24.toInt()   // rojo del logo original
        const val PIECE_S = 0.55f                 // lo que demora cada diamante
        const val STAGGER_S = 0.25f               // separación entre uno y otro
        const val TOTAL_S = 1.1f
        // Coordenadas del logo original (641 x 555)
        val DIAMONDS = arrayOf(
            floatArrayOf(320f, 0f, 427f, 185f, 320f, 370f, 213f, 185f),
            floatArrayOf(107f, 370f, 320f, 370f, 213f, 555f, 0f, 555f),
            floatArrayOf(320f, 370f, 534f, 370f, 641f, 555f, 427f, 555f)
        )
        // Desde dónde llega cada uno (en las mismas unidades)
        val OFFSETS = floatArrayOf(0f, -120f, -110f, 70f, 110f, 70f)
    }
}
