package io.github.hypnoticHODL.bitprix.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.core.content.ContextCompat
import io.github.hypnoticHODL.bitprix.R
import kotlin.math.cos
import kotlin.math.sin

class FearAndGreedGauge @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var score: Int = 0
    private var classification: String = "--"

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }

    private val whiteFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val needlePath = Path()

    private val valueInsidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.BLACK
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val rectF = RectF()
    private val pivotRectF = RectF()
    private val strokeWidthRatio = 0.15f

    // PreAllocated objects to avoid allocations in onDraw
    private val shaderColors = intArrayOf(
        ContextCompat.getColor(context, R.color.gauge_deep_red),
        ContextCompat.getColor(context, R.color.gauge_deep_red),
        ContextCompat.getColor(context, R.color.gauge_orange),
        ContextCompat.getColor(context, R.color.gauge_yellow),
        ContextCompat.getColor(context, R.color.gauge_light_green),
        ContextCompat.getColor(context, R.color.gauge_deep_green),
        ContextCompat.getColor(context, R.color.gauge_deep_green)
    )
    private val shaderPos = floatArrayOf(0f, 0.25f, 0.375f, 0.5f, 0.625f, 0.75f, 1.0f)
    private val gradientMatrix = Matrix()
    private var cachedShader: SweepGradient? = null
    private var cachedShaderWidth = -1
    private var cachedShaderHeight = -1

    // Colors
    private val colorDeepRed = ContextCompat.getColor(context, R.color.gauge_deep_red)
    private val colorOrange = ContextCompat.getColor(context, R.color.gauge_orange)
    private val colorYellow = ContextCompat.getColor(context, R.color.gauge_yellow)
    private val colorLightGreen = ContextCompat.getColor(context, R.color.gauge_light_green)
    private val colorDeepGreen = ContextCompat.getColor(context, R.color.gauge_deep_green)

    fun setData(score: Int, classification: String) {
        this.score = score.coerceIn(0, 100)
        this.classification = classification
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        if (width <= 0 || height <= 0) return

        val centerX = width / 2

        // Gauge sizing
        val radius = (width * 0.42f).coerceAtMost(height * 0.6f)
        val currentStrokeWidth = radius * strokeWidthRatio

        // Pivot/Semicircle size - reduced by 10%
        val pivotDiameter = width * 0.198f
        val pivotRadius = pivotDiameter / 2f

        // Centering vertically
        val totalHeight = radius + (currentStrokeWidth / 2f) + (radius * 0.4f)
        val centerY = (height - totalHeight) / 2f + radius + (currentStrokeWidth / 2f)

        arcPaint.strokeWidth = currentStrokeWidth
        rectF.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)

        // Gradient Arc - reuse cached shader if size hasn't changed
        if (cachedShader == null || cachedShaderWidth != width.toInt() || cachedShaderHeight != height.toInt()) {
            cachedShader = SweepGradient(centerX, centerY, shaderColors, shaderPos)
            cachedShaderWidth = width.toInt()
            cachedShaderHeight = height.toInt()
        }
        gradientMatrix.reset()
        gradientMatrix.postRotate(90f, centerX, centerY)
        cachedShader?.setLocalMatrix(gradientMatrix)
        arcPaint.shader = cachedShader

        // Draw the smooth gradient arc with flat ends (BUTT)
        canvas.drawArc(rectF, 180f, 180f, false, arcPaint)
        arcPaint.shader = null

        // Draw improved needle (tapered)
        val angle = 180f + score * 1.8f
        val angleRad = Math.toRadians(angle.toDouble())
        val needleLength = radius - currentStrokeWidth * 0.5f

        val angleRadLeft = angleRad - Math.toRadians(2.0)
        val angleRadRight = angleRad + Math.toRadians(2.0)

        // Needle starts slightly inside the pivot circle
        val baseRadius = pivotRadius * 0.8f

        needlePath.reset()
        needlePath.moveTo(centerX + baseRadius * cos(angleRadLeft).toFloat(),
            centerY + baseRadius * sin(angleRadLeft).toFloat())
        needlePath.lineTo(centerX + needleLength * cos(angleRad).toFloat(),
            centerY + needleLength * sin(angleRad).toFloat())
        needlePath.lineTo(centerX + baseRadius * cos(angleRadRight).toFloat(),
            centerY + baseRadius * sin(angleRadRight).toFloat())
        needlePath.close()
        canvas.drawPath(needlePath, whiteFillPaint)

        // Draw white pivot semicircle
        pivotRectF.set(centerX - pivotRadius, centerY - pivotRadius, centerX + pivotRadius, centerY + pivotRadius)
        canvas.drawArc(pivotRectF, 180f, 180f, true, whiteFillPaint)

        // Draw value inside pivot semicircle
        val scoreText = score.toString()
        valueInsidePaint.textSize = pivotRadius * 0.64f

        val textWidth = valueInsidePaint.measureText(scoreText)
        if (textWidth > pivotDiameter * 0.8f) {
            valueInsidePaint.textSize *= (pivotDiameter * 0.8f) / textWidth
        }

        // Position at bottom with small padding
        val paddingBottom = 6f
        canvas.drawText(scoreText, centerX, centerY - paddingBottom, valueInsidePaint)

        // Draw sentiment label below
        val sentimentColor = getInterpolatedColor(score)
        labelPaint.textSize = radius * 0.22f
        labelPaint.color = sentimentColor
        canvas.drawText(classification.uppercase(), centerX, centerY + (radius * 0.3f), labelPaint)
    }

    private fun getInterpolatedColor(score: Int): Int {
        val fScore = score.toFloat()
        return when {
            fScore <= 25f -> ColorUtils.blendARGB(colorDeepRed, colorOrange, fScore / 25f)
            fScore <= 50f -> ColorUtils.blendARGB(colorOrange, colorYellow, (fScore - 25f) / 25f)
            fScore <= 75f -> ColorUtils.blendARGB(colorYellow, colorLightGreen, (fScore - 50f) / 25f)
            else -> ColorUtils.blendARGB(colorLightGreen, colorDeepGreen, (fScore - 75f) / 25f)
        }
    }
}