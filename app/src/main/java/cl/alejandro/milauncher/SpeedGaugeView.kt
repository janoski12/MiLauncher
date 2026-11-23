package cl.alejandro.milauncher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Velocímetro circular estilo tablero: arco de 270°, avance en rojo,
 * marcas blancas cada 20 km/h, aro plateado y los diamantes de Mitsubishi abajo,
 * en el hueco del arco (como en el tablero del Lancer).
 */
class SpeedGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** Velocidad máxima de la escala (km/h). */
    var maxSpeed = 200f

    private var targetSpeed: Float? = null   // null = sin señal GPS
    private var shownSpeed = 0f
    private var animator: ValueAnimator? = null

    private val red = context.getColor(R.color.accent)
    private val silver = context.getColor(R.color.silver)
    private val pearl = context.getColor(R.color.pearl)
    private val track = context.getColor(R.color.gauge_track)
    private val secondary = context.getColor(R.color.text_secondary)

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = silver
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = secondary
    }
    private val speedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = pearl
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    }
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = red
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }
    private val oval = RectF()
    private val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = red
    }
    private val logoPath = Path()
    private var logoSize = -1f

    /** Cambia la velocidad (km/h). null = sin señal. */
    fun setSpeed(kmh: Float?) {
        targetSpeed = kmh
        val to = (kmh ?: 0f).coerceIn(0f, maxSpeed)
        animator?.cancel()
        animator = ValueAnimator.ofFloat(shownSpeed, to).apply {
            duration = 600
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                shownSpeed = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Ocupa todo el espacio disponible; el dibujo se centra usando el lado más corto.
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(if (w == 0) h else w, if (h == 0) w else h)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        if (size <= 0f) return
        val cx = width / 2f
        val cy = height / 2f
        val stroke = size * 0.065f
        val radius = size / 2f - stroke * 1.4f

        // Aro plateado exterior
        ringPaint.strokeWidth = size * 0.012f
        canvas.drawCircle(cx, cy, radius + stroke * 1.15f, ringPaint)

        oval.set(cx - radius, cy - radius, cx + radius, cy + radius)

        // Pista gris
        arcPaint.strokeWidth = stroke
        arcPaint.color = track
        arcPaint.alpha = 255
        canvas.drawArc(oval, START, SWEEP, false, arcPaint)

        // Avance rojo, con un "resplandor" más ancho y transparente detrás
        val sweep = SWEEP * (shownSpeed / maxSpeed)
        if (sweep > 0.5f) {
            arcPaint.color = red
            arcPaint.strokeWidth = stroke * 1.9f
            arcPaint.alpha = 50
            canvas.drawArc(oval, START, sweep, false, arcPaint)
            arcPaint.strokeWidth = stroke
            arcPaint.alpha = 255
            canvas.drawArc(oval, START, sweep, false, arcPaint)
        }

        // Marcas y números
        numberPaint.textSize = size * 0.058f
        val steps = (maxSpeed / 10f).roundToInt()
        for (i in 0..steps) {
            val value = i * 10f
            val angle = Math.toRadians((START + SWEEP * value / maxSpeed).toDouble())
            val major = i % 2 == 0
            val outer = radius - stroke * 0.9f
            val inner = outer - if (major) stroke * 0.9f else stroke * 0.45f
            tickPaint.strokeWidth = if (major) size * 0.011f else size * 0.006f
            tickPaint.color = if (value <= shownSpeed && shownSpeed > 0f) red else pearl
            canvas.drawLine(
                (cx + outer * cos(angle)).toFloat(), (cy + outer * sin(angle)).toFloat(),
                (cx + inner * cos(angle)).toFloat(), (cy + inner * sin(angle)).toFloat(),
                tickPaint
            )
            if (i % 4 == 0) {
                val r = inner - stroke * 0.9f
                val tx = (cx + r * cos(angle)).toFloat()
                val ty = (cy + r * sin(angle)).toFloat() - (numberPaint.descent() + numberPaint.ascent()) / 2f
                canvas.drawText(value.roundToInt().toString(), tx, ty, numberPaint)
            }
        }

        // Número central
        speedPaint.textSize = size * 0.25f
        val text = if (targetSpeed == null) "--" else shownSpeed.roundToInt().toString()
        canvas.drawText(text, cx, cy + size * 0.07f, speedPaint)

        unitPaint.textSize = size * 0.07f
        canvas.drawText(context.getString(R.string.kmh), cx, cy + size * 0.2f, unitPaint)

        // Diamantes de Mitsubishi en el hueco de abajo del arco
        if (logoSize != size) {
            buildLogo(size * 0.12f)
            logoSize = size
        }
        canvas.save()
        canvas.translate(cx - size * 0.06f, cy + size * 0.27f)
        canvas.drawPath(logoPath, logoPaint)
        canvas.restore()
    }

    /** Arma los tres diamantes (proporciones del logo: 641 x 555) con el ancho dado. */
    private fun buildLogo(w: Float) {
        val k = w / 641f
        val pts = arrayOf(
            floatArrayOf(320f, 0f, 427f, 185f, 320f, 370f, 213f, 185f),
            floatArrayOf(107f, 370f, 320f, 370f, 213f, 555f, 0f, 555f),
            floatArrayOf(320f, 370f, 534f, 370f, 641f, 555f, 427f, 555f)
        )
        logoPath.reset()
        for (d in pts) {
            logoPath.moveTo(d[0] * k, d[1] * k)
            for (i in 2 until d.size step 2) logoPath.lineTo(d[i] * k, d[i + 1] * k)
            logoPath.close()
        }
    }

    private companion object {
        const val START = 135f   // empieza abajo a la izquierda
        const val SWEEP = 270f   // termina abajo a la derecha
    }
}
