package com.chartlens.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator

/**
 * Full-screen transparent canvas that draws bounding boxes received from the
 * Gemini analysis.
 *
 * - Coordinates arrive in *frame* space (capture dimensions). On draw we scale
 *   to the current view dimensions.
 * - On appear: each box fades in (60ms stagger) and pulses once.
 * - Auto-clears after `autoDismissMs` (0 = never).
 * - Touches inside a box → onBoxTapped; outside → consumed=false (passes
 *   through to the underlying app via window flags).
 */
class HighlightOverlayView(context: Context) : View(context) {

  private val main = Handler(Looper.getMainLooper())
  private var boxes: List<BoxRenderer.Box> = emptyList()
  private var frameW: Int = 0
  private var frameH: Int = 0

  private var fadeProgress: FloatArray = FloatArray(0)
  private var pulseProgress: FloatArray = FloatArray(0)
  private val animators = mutableListOf<ValueAnimator>()
  private var dismissRunnable: Runnable? = null

  var onBoxTapped: ((index: Int) -> Unit)? = null
  var onCleared: (() -> Unit)? = null

  init {
    setBackgroundColor(Color.TRANSPARENT)
    isClickable = true
    isFocusable = false
  }

  fun setData(
    nextBoxes: List<BoxRenderer.Box>,
    frameWidth: Int,
    frameHeight: Int,
    autoDismissMs: Long,
  ) {
    cancelAnimators()
    cancelDismiss()
    boxes = nextBoxes
    frameW = frameWidth
    frameH = frameHeight
    fadeProgress = FloatArray(boxes.size) { 0f }
    pulseProgress = FloatArray(boxes.size) { 0f }
    invalidate()
    runAppearAnimations()
    if (autoDismissMs > 0L) {
      val r = Runnable { fadeOutAndClear() }
      dismissRunnable = r
      main.postDelayed(r, autoDismissMs)
    }
  }

  fun fadeOutAndClear() {
    cancelAnimators()
    cancelDismiss()
    if (boxes.isEmpty()) {
      onCleared?.invoke()
      return
    }
    val anim = ValueAnimator.ofFloat(1f, 0f).apply {
      duration = 200L
      addUpdateListener { a ->
        val v = a.animatedValue as Float
        for (i in fadeProgress.indices) fadeProgress[i] = v
        invalidate()
      }
    }
    anim.start()
    animators += anim
    main.postDelayed({
      boxes = emptyList()
      invalidate()
      onCleared?.invoke()
    }, 220L)
  }

  fun clearImmediate() {
    cancelAnimators()
    cancelDismiss()
    boxes = emptyList()
    invalidate()
    onCleared?.invoke()
  }

  private fun cancelDismiss() {
    dismissRunnable?.let { main.removeCallbacks(it) }
    dismissRunnable = null
  }

  private fun cancelAnimators() {
    animators.forEach { it.cancel() }
    animators.clear()
  }

  private fun runAppearAnimations() {
    val sorted = boxes.indices.sortedBy { boxes[it].x }
    val staggerMs = 60L
    sorted.forEachIndexed { order, idx ->
      val fadeAnim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 200L
        startDelay = order * staggerMs
        addUpdateListener { a ->
          fadeProgress[idx] = a.animatedValue as Float
          invalidate()
        }
      }
      val pulseAnim = ValueAnimator.ofFloat(1f, 1.06f, 1f).apply {
        duration = 600L
        startDelay = order * staggerMs + 120L
        interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
        addUpdateListener { a ->
          pulseProgress[idx] = a.animatedValue as Float
          invalidate()
        }
      }
      animators += fadeAnim
      animators += pulseAnim
      fadeAnim.start()
      pulseAnim.start()
    }
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    if (boxes.isEmpty()) return
    val viewW = width
    val viewH = height
    val strokePx = dp(2f)
    val cornerPx = dp(8f)
    val chipPad = dp(4f)
    val chipCorner = dp(4f)
    val chipText = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10f, resources.displayMetrics)

    val drawn = mutableListOf<android.graphics.RectF>()
    boxes.forEachIndexed { i, raw ->
      val scaled = BoxRenderer.scale(raw, frameW, frameH, viewW, viewH)
      val pulse = if (pulseProgress[i] == 0f) 1f else pulseProgress[i]
      val cx = scaled.x + scaled.w / 2f
      val cy = scaled.y + scaled.h / 2f
      val pulsedW = scaled.w * pulse
      val pulsedH = scaled.h * pulse
      val box = scaled.copy(
        x = cx - pulsedW / 2f,
        y = cy - pulsedH / 2f,
        w = pulsedW,
        h = pulsedH,
      )

      val alpha = (fadeProgress[i] * 255f).toInt().coerceIn(0, 255)
      canvas.save()
      val rect = android.graphics.RectF(box.x, box.y, box.x + box.w, box.y + box.h)
      val glow = BoxRenderer.glowPaint(box.colorInt).apply { this.alpha = (alpha * 0.4f).toInt() }
      canvas.drawRoundRect(rect, cornerPx, cornerPx, glow)
      val stroke = BoxRenderer.strokePaint(box.colorInt, strokePx).apply { this.alpha = alpha }
      canvas.drawRoundRect(rect, cornerPx, cornerPx, stroke)
      canvas.restore()

      val overlapStack = drawn.count { rect.intersects(it.left, it.top, it.right, it.bottom) }
      val chipAlpha = if (overlapStack > 0) ((alpha * 0.7f).toInt()) else alpha
      val verticalOffset = overlapStack * (chipText + chipPad * 2 + dp(2f))
      val confLabel = "${(box.confidence * 100).toInt()}%"
      val text = "${box.label} · $confLabel"
      BoxRenderer.drawChip(
        canvas, box, text, chipText, chipPad, chipCorner, verticalOffset, chipAlpha,
      )
      drawn += rect
    }
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (event.action != MotionEvent.ACTION_UP || boxes.isEmpty()) return false
    val viewW = width
    val viewH = height
    val x = event.x
    val y = event.y
    boxes.forEachIndexed { i, raw ->
      val scaled = BoxRenderer.scale(raw, frameW, frameH, viewW, viewH)
      if (x >= scaled.x && x <= scaled.x + scaled.w &&
        y >= scaled.y && y <= scaled.y + scaled.h
      ) {
        onBoxTapped?.invoke(i)
        return true
      }
    }
    return false
  }

  private fun dp(v: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    cancelAnimators()
    cancelDismiss()
  }
}
