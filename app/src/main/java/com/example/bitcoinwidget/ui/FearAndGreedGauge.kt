package com.example.bitcoinwidget.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import kotlin.math.cos
import kotlin.math.sin

class FearAndGreedGauge @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var score: Int = 0
    private var classification: String = "--"

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val rectF = RectF()
    private val needlePath = Path()
    private val strokeWidthRatio = 0.2f

    // Colors
    private val colorDeepRed = Color.parseColor("#B71C1C")
    private val colorOrange = Color.parseColor("#EF6C00")
    private val colorYellow = Color.parseColor("#FBC02D")
    private val colorLightGreen = Color.parseColor("#7CB342")
    private val colorDeepGreen = Color.parseColor("#2E7D32")

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

        val isDarkMode = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val primaryTextColor = if (isDarkMode) Color.WHITE else Color.parseColor("#212121")
        val needleColor = if (isDarkMode) Color.parseColor("#EEEEEE") else Color.parseColor("#424242")

        needlePaint.color = needleColor
        valuePaint.color = primaryTextColor

        val centerX = width / 2
        val radius = (width * 0.4f).coerceAtMost(height * 0.55f)
        val currentStrokeWidth = radius * strokeWidthRatio
        
        val totalDrawingHeight = radius + (currentStrokeWidth / 2f) + (radius * 0.5f)
        val centerY = (height - totalDrawingHeight) / 2f + radius + (currentStrokeWidth / 2f)

        arcPaint.strokeWidth = currentStrokeWidth
        rectF.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)

        val shaderColors = intArrayOf(
            colorDeepRed,   
            colorDeepRed,   
            colorOrange,
            colorYellow,
            colorLightGreen,
            colorDeepGreen, 
            colorDeepGreen  
        )
        val shaderPos = floatArrayOf(
            0f, 0.25f, 0.375f, 0.5f, 0.625f, 0.75f, 1.0f
        )
        
        val shader = SweepGradient(centerX, centerY, shaderColors, shaderPos)
        val matrix = Matrix()
        matrix.postRotate(90f, centerX, centerY)
        shader.setLocalMatrix(matrix)
        arcPaint.shader = shader

        canvas.drawArc(rectF, 180f, 180f, false, arcPaint)
        arcPaint.shader = null 

        // Draw needle
        val angle = 180f + score * 1.8f
        val angleRad = Math.toRadians(angle.toDouble())
        val needleLength = radius - currentStrokeWidth * 0.5f
        val stopX = centerX + needleLength * cos(angleRad).toFloat()
        val stopY = centerY + needleLength * sin(angleRad).toFloat()
        
        val needleBaseWidth = currentStrokeWidth * 0.2f
        val angleRadLeft = angleRad - Math.PI / 2
        val angleRadRight = angleRad + Math.PI / 2
        
        needlePath.reset()
        needlePath.moveTo(centerX + needleBaseWidth * cos(angleRadLeft).toFloat(), 
                          centerY + needleBaseWidth * sin(angleRadLeft).toFloat())
        needlePath.lineTo(stopX, stopY)
        needlePath.lineTo(centerX + needleBaseWidth * cos(angleRadRight).toFloat(), 
                          centerY + needleBaseWidth * sin(angleRadRight).toFloat())
        needlePath.close()
        
        canvas.drawPath(needlePath, needlePaint)
        canvas.drawCircle(centerX, centerY, needleBaseWidth * 0.8f, needlePaint)

        // Draw value and match label color to gauge position
        val sentimentColor = getInterpolatedColor(score)
        
        valuePaint.textSize = radius * 0.55f
        canvas.drawText(score.toString(), centerX, centerY + radius * 0.25f, valuePaint)
        
        labelPaint.textSize = radius * 0.22f
        labelPaint.color = sentimentColor
        canvas.drawText(classification.uppercase(), centerX, centerY + radius * 0.52f, labelPaint)
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
