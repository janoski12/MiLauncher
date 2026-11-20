package cl.alejandro.milauncher

import android.app.Activity
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView

/**
 * Logo de la marca a pantalla completa al encender la radio.
 *
 * Se dibuja encima de la pantalla principal (no es otra Activity, así es instantáneo y liviano):
 * aparece con un fundido, se queda unos segundos y desaparece. Tocar la pantalla lo salta.
 *
 * Para cambiar el logo, reemplaza res/drawable-nodpi/logo_marca.png.
 * Para cambiar la duración, edita TOTAL_MS. Para desactivarlo, pon ENABLED = false.
 */
class Splash(private val activity: Activity) {

    private var view: View? = null

    val isShowing get() = view != null

    fun show() {
        if (!ENABLED || view != null) return
        val root = activity.window.decorView as ViewGroup

        val logo = ImageView(activity).apply {
            setImageResource(R.drawable.logo_marca)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
        }
        val overlay = FrameLayout(activity).apply {
            setBackgroundColor(0xFF000000.toInt())
            isClickable = true   // que los toques no lleguen a los botones de abajo
            // El logo ocupa ~60 % del alto de la pantalla, centrado
            val h = (activity.resources.displayMetrics.heightPixels * 0.6f).toInt()
            addView(logo, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, h, Gravity.CENTER))
            setOnClickListener { hide() }
        }
        root.addView(overlay, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        view = overlay

        // Entrada: el logo aparece y crece un poco, como el encendido del tablero.
        logo.alpha = 0f
        logo.scaleX = 0.92f
        logo.scaleY = 0.92f
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(FADE_IN_MS).setInterpolator(DecelerateInterpolator()).start()

        overlay.postDelayed({ if (view === overlay) hide() }, TOTAL_MS - FADE_OUT_MS)
    }

    /** Desaparece con un fundido y deja ver la pantalla principal. */
    fun hide() {
        val v = view ?: return
        view = null
        v.isClickable = false
        v.animate().alpha(0f).setDuration(FADE_OUT_MS).setInterpolator(AccelerateInterpolator())
            .withEndAction { (v.parent as? ViewGroup)?.removeView(v) }
            .start()
    }

    /** Quita el logo de inmediato (por ejemplo, si la pantalla se apaga mientras se muestra). */
    fun cancel() {
        val v = view ?: return
        view = null
        v.animate().cancel()
        (v.parent as? ViewGroup)?.removeView(v)
    }

    companion object {
        const val ENABLED = true
        const val TOTAL_MS = 2500L
        private const val FADE_IN_MS = 600L
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
