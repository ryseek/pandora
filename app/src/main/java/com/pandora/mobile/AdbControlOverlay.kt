package com.pandora.mobile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.RoundedCorner
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager

/** Draws Pandora's safety frame above every app while phone control is active. */
class SystemAdbControlOverlay(
    context: Context,
    private val onStop: () -> Unit,
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var frameView: ControlFrameView? = null
    private var stopView: StopControlView? = null

    fun canShow(): Boolean = Settings.canDrawOverlays(appContext)

    fun show(): Boolean {
        if (!canShow()) return false
        handler.post {
            if (frameView != null) return@post
            val frame = ControlFrameView(appContext)
            val stop = StopControlView(appContext, onStop)
            runCatching {
                windowManager.addView(frame, frameLayoutParams())
                windowManager.addView(stop, stopLayoutParams())
                frameView = frame
                stopView = stop
            }.onFailure {
                runCatching { windowManager.removeViewImmediate(frame) }
                runCatching { windowManager.removeViewImmediate(stop) }
                Log.w(TAG, "Could not show control overlay", it)
            }
        }
        return true
    }

    fun hide() {
        handler.post {
            frameView?.let { runCatching { windowManager.removeViewImmediate(it) } }
            stopView?.let { runCatching { windowManager.removeViewImmediate(it) } }
            frameView = null
            stopView = null
        }
    }

    override fun close() = hide()

    private fun frameLayoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                setFitInsetsTypes(0)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun stopLayoutParams() = WindowManager.LayoutParams(
        appContext.dp(112),
        appContext.dp(40),
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        y = bottomControlInset() + appContext.dp(4)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setFitInsetsTypes(0)
    }

    private fun bottomControlInset(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return appContext.dp(9)
        val insets = windowManager.currentWindowMetrics.windowInsets
        return insets.getInsetsIgnoringVisibility(
            WindowInsets.Type.navigationBars() or WindowInsets.Type.mandatorySystemGestures(),
        ).bottom
    }

    private companion object {
        const val TAG = "AdbControlOverlay"
    }
}

private class ControlFrameView(context: Context) : View(context) {
    private val stroke = context.dp(2).toFloat()
    private val bounds = RectF()
    private val displayPath = Path()
    private val shaderMatrix = Matrix()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
    }
    private var shader: SweepGradient? = null

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        shader = SweepGradient(
            width / 2f,
            height / 2f,
            intArrayOf(0xFF6E5AEF.toInt(), 0xFFA758E8.toInt(), 0xFFE95D8F.toInt(), 0xFFF2A24B.toInt(), 0xFF69BFA7.toInt(), 0xFF6E5AEF.toInt()),
            null,
        )
        paint.shader = shader
        updateDisplayPath(rootWindowInsets)
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        updateDisplayPath(insets)
        return super.onApplyWindowInsets(insets)
    }

    private fun updateDisplayPath(insets: WindowInsets?) {
        if (width == 0 || height == 0) return
        val inset = stroke / 2f
        bounds.set(inset, inset, width - inset, height - inset)
        displayPath.reset()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val exactShape = insets?.displayShape?.path
            if (exactShape != null && !exactShape.isEmpty) {
                val shapeBounds = RectF()
                exactShape.computeBounds(shapeBounds, true)
                if (!shapeBounds.isEmpty) {
                    val fit = Matrix().apply {
                        setRectToRect(shapeBounds, bounds, Matrix.ScaleToFit.FILL)
                    }
                    displayPath.set(exactShape)
                    displayPath.transform(fit)
                    invalidate()
                    return
                }
            }
        }

        val radii = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            floatArrayOf(
                cornerRadius(insets, RoundedCorner.POSITION_TOP_LEFT, inset),
                cornerRadius(insets, RoundedCorner.POSITION_TOP_LEFT, inset),
                cornerRadius(insets, RoundedCorner.POSITION_TOP_RIGHT, inset),
                cornerRadius(insets, RoundedCorner.POSITION_TOP_RIGHT, inset),
                cornerRadius(insets, RoundedCorner.POSITION_BOTTOM_RIGHT, inset),
                cornerRadius(insets, RoundedCorner.POSITION_BOTTOM_RIGHT, inset),
                cornerRadius(insets, RoundedCorner.POSITION_BOTTOM_LEFT, inset),
                cornerRadius(insets, RoundedCorner.POSITION_BOTTOM_LEFT, inset),
            )
        } else {
            val fallback = context.dp(12).toFloat()
            FloatArray(8) { fallback }
        }
        displayPath.addRoundRect(bounds, radii, Path.Direction.CW)
        invalidate()
    }

    private fun cornerRadius(insets: WindowInsets?, position: Int, pathInset: Float): Float {
        val systemRadius = insets?.getRoundedCorner(position)?.radius?.toFloat() ?: 0f
        return (systemRadius - pathInset).coerceAtLeast(0f)
    }

    override fun onDraw(canvas: Canvas) {
        val phase = (SystemClock.uptimeMillis() % WAVE_DURATION_MS).toFloat() / WAVE_DURATION_MS
        shaderMatrix.setRotate(phase * 360f, width / 2f, height / 2f)
        shader?.setLocalMatrix(shaderMatrix)
        canvas.drawPath(displayPath, paint)
        postInvalidateOnAnimation()
    }

    private companion object {
        const val WAVE_DURATION_MS = 6_000L
    }
}

private class StopControlView(
    context: Context,
    private val onStop: () -> Unit,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val bounds = RectF()
    private var pressed = false
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFAFAF8FF.toInt() }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = PANDORA
        style = Paint.Style.STROKE
        strokeWidth = context.dp(1.5f)
    }
    private val cross = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = PANDORA
        style = Paint.Style.STROKE
        strokeWidth = context.dp(1.8f)
        strokeCap = Paint.Cap.ROUND
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = PANDORA
        textSize = context.sp(13)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    init {
        isClickable = true
        contentDescription = "Stop phone control"
    }

    override fun onDraw(canvas: Canvas) {
        val inset = outline.strokeWidth / 2f
        bounds.set(inset, inset, width - inset, height - inset)
        fill.alpha = if (pressed) 235 else 250
        val radius = height / 2f
        canvas.drawRoundRect(bounds, radius, radius, fill)
        canvas.drawRoundRect(bounds, radius, radius, outline)

        val cx = context.dp(25).toFloat()
        val cy = height / 2f
        val arm = context.dp(4.5f)
        canvas.drawLine(cx - arm, cy - arm, cx + arm, cy + arm, cross)
        canvas.drawLine(cx + arm, cy - arm, cx - arm, cy + arm, cross)

        val metrics = label.fontMetrics
        val baseline = cy - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText("Stop", context.dp(43).toFloat(), baseline, label)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            pressed = true
            invalidate()
            true
        }
        MotionEvent.ACTION_UP -> {
            pressed = false
            invalidate()
            if (event.x in 0f..width.toFloat() && event.y in 0f..height.toFloat()) performClick()
            true
        }
        MotionEvent.ACTION_CANCEL -> {
            pressed = false
            invalidate()
            true
        }
        else -> true
    }

    override fun performClick(): Boolean {
        super.performClick()
        onStop()
        return true
    }

    private companion object {
        const val PANDORA = 0xFF6E5AEF.toInt()
    }
}

private fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
private fun Context.dp(value: Float): Float = value * resources.displayMetrics.density
private fun Context.sp(value: Int): Float = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_SP,
    value.toFloat(),
    resources.displayMetrics,
)
