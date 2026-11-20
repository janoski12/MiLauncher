package cl.alejandro.milauncher

import android.content.Context
import android.view.MotionEvent
import kotlin.math.abs

/**
 * Detecta un deslizamiento horizontal en cualquier parte de la pantalla, aunque empiece
 * sobre un botón o una tarjeta. Se usa desde dispatchTouchEvent de la Activity.
 *
 * Cuando el dedo avanza lo suficiente en la dirección pedida (y más en horizontal que en
 * vertical), se cancela el toque para las vistas de abajo (así no se "aprieta" un botón
 * sin querer) y se llama a onSwipe una sola vez.
 */
class SwipeDetector(
    context: Context,
    private val toRight: Boolean,
    private val onSwipe: () -> Unit
) {
    private val minDistance = 90 * context.resources.displayMetrics.density
    private var downX = 0f
    private var downY = 0f
    private var tracking = false
    private var fired = false

    /**
     * Devuelve true si el evento ya se manejó (no hay que pasarlo a las vistas).
     * superDispatch es el dispatchTouchEvent original de la Activity.
     */
    fun handle(ev: MotionEvent, superDispatch: (MotionEvent) -> Boolean): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                tracking = true
                fired = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> tracking = false   // dos dedos: no es un deslizamiento
            MotionEvent.ACTION_MOVE -> if (tracking && !fired) {
                val dx = ev.x - downX
                val dy = ev.y - downY
                val forward = if (toRight) dx else -dx
                if (forward > minDistance && abs(dx) > abs(dy) * 2) {
                    fired = true
                    val cancel = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
                    superDispatch(cancel)
                    cancel.recycle()
                    onSwipe()
                }
            }
        }
        // Una vez detectado, el resto del gesto (hasta levantar el dedo) no llega a las vistas.
        if (fired) {
            if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
                tracking = false
            }
            return true
        }
        return false
    }
}
