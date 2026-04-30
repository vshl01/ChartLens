package com.chartlens.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.Base64
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

class BubbleView(context: Context) : View(context) {

  private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeWidth = 6f
    color = Color.WHITE
  }
  private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
    color = Color.parseColor("#5B6CFF")
  }
  private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
    color = Color.parseColor("#33FFFFFF")
  }
  private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.WHITE
    textAlign = Paint.Align.CENTER
    textSize = 28f
    isFakeBoldText = true
  }
  private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.parseColor("#55000000")
    style = Paint.Style.FILL
  }

  private var iconBitmap: Bitmap? = null
  private var glyph: String = "C"
  private var spinAngle: Float = 0f
  private var pulse: Float = 0f
  private var state: String = "idle"

  private var badgeText: String? = null
  private var badgeTone: String = "neutral"
  private val badgeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
  }
  private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.WHITE
    textAlign = Paint.Align.CENTER
    textSize = 18f
    isFakeBoldText = true
  }

  private var spinAnimator: ValueAnimator? = null
  private var pulseAnimator: ValueAnimator? = null

  fun setBrandColor(hex: String?) {
    try {
      if (!hex.isNullOrBlank()) fillPaint.color = Color.parseColor(hex)
    } catch (_: Exception) {}
    invalidate()
  }

  fun setIconBase64(b64: String?) {
    iconBitmap = if (b64.isNullOrBlank()) null else try {
      val bytes = Base64.decode(b64, Base64.DEFAULT)
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (_: Exception) { null }
    invalidate()
  }

  fun setGlyph(letter: String) {
    glyph = letter.take(1)
    invalidate()
  }

  fun setState(newState: String) {
    state = newState
    when (state) {
      "capturing", "processing", "analyzing" -> {
        startSpin()
        startPulse()
      }
      "result", "highlighting" -> {
        stopSpin()
        startPulse()
      }
      "expanded" -> {
        stopSpin()
        stopPulse()
      }
      else -> {
        stopSpin()
        stopPulse()
      }
    }
    invalidate()
  }

  fun setBadge(text: String?, tone: String?) {
    badgeText = if (text.isNullOrBlank()) null else text
    badgeTone = tone ?: "neutral"
    invalidate()
  }

  fun playTapAnimation() {
    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    animate().scaleX(0.85f).scaleY(0.85f).setDuration(80).withEndAction {
      animate().scaleX(1f).scaleY(1f).setDuration(140).start()
    }.start()
  }

  private fun startSpin() {
    if (spinAnimator?.isRunning == true) return
    spinAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
      duration = 1100
      repeatCount = ValueAnimator.INFINITE
      addUpdateListener { a ->
        spinAngle = a.animatedValue as Float
        invalidate()
      }
      start()
    }
  }

  private fun stopSpin() {
    spinAnimator?.cancel()
    spinAnimator = null
    spinAngle = 0f
  }

  private fun startPulse() {
    if (pulseAnimator?.isRunning == true) return
    pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
      duration = 900
      repeatCount = ValueAnimator.INFINITE
      repeatMode = ValueAnimator.REVERSE
      addUpdateListener { a ->
        pulse = a.animatedValue as Float
        invalidate()
      }
      start()
    }
  }

  private fun stopPulse() {
    pulseAnimator?.cancel()
    pulseAnimator = null
    pulse = 0f
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    stopSpin()
    stopPulse()
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    val w = width.toFloat()
    val h = height.toFloat()
    val cx = w / 2f
    val cy = h / 2f
    val pad = 6f
    val r = (Math.min(w, h) / 2f) - pad

    // outer pulse glow
    if (pulse > 0f) {
      val glowR = r + 6f + pulse * 8f
      val alpha = (40 * (1f - pulse)).toInt().coerceIn(0, 255)
      val p = Paint(Paint.ANTI_ALIAS_FLAG)
      p.color = Color.argb(alpha, 91, 108, 255)
      p.style = Paint.Style.FILL
      canvas.drawCircle(cx, cy, glowR, p)
    }

    // shadow
    canvas.drawCircle(cx + 2f, cy + 4f, r, shadowPaint)
    // body
    canvas.drawCircle(cx, cy, r, fillPaint)
    // top highlight (gives a 3D feel)
    canvas.drawArc(RectF(cx - r * 0.7f, cy - r, cx + r * 0.7f, cy - r * 0.2f), 0f, 360f, false, highlightPaint)

    // icon or glyph
    val icon = iconBitmap
    if (icon != null) {
      val iconR = (r * 0.7f).toInt()
      val src = Rect(0, 0, icon.width, icon.height)
      val dst = RectF(cx - iconR, cy - iconR, cx + iconR, cy + iconR)
      canvas.drawBitmap(icon, src, dst, null)
    } else {
      val txtY = cy - (iconPaint.descent() + iconPaint.ascent()) / 2f
      canvas.drawText(glyph, cx, txtY, iconPaint)
    }

    // state ring
    val rr = r - 4f
    when (state) {
      "capturing" -> {
        ringPaint.color = Color.WHITE
        canvas.drawArc(RectF(cx - rr, cy - rr, cx + rr, cy + rr), spinAngle, 90f, false, ringPaint)
      }
      "processing", "analyzing" -> {
        ringPaint.color = Color.parseColor("#10B981")
        canvas.drawArc(RectF(cx - rr, cy - rr, cx + rr, cy + rr), spinAngle, 130f, false, ringPaint)
      }
      "result", "highlighting" -> {
        ringPaint.color = Color.parseColor("#22C55E")
        canvas.drawCircle(cx, cy, rr, ringPaint)
      }
      "expanded" -> {
        ringPaint.color = Color.parseColor("#5B6CFF")
        canvas.drawCircle(cx, cy, rr, ringPaint)
      }
      "error" -> {
        ringPaint.color = Color.parseColor("#EF4444")
        canvas.drawCircle(cx, cy, rr, ringPaint)
      }
      else -> {}
    }

    // badge (top-right)
    val text = badgeText
    if (!text.isNullOrEmpty()) {
      val badgeR = r * 0.32f
      val bx = cx + r * 0.65f
      val by = cy - r * 0.65f
      val toneColor = when (badgeTone) {
        "success" -> Color.parseColor("#22C55E")
        "warning" -> Color.parseColor("#F59E0B")
        else -> Color.parseColor("#475569")
      }
      badgeFillPaint.color = toneColor
      // white outline so the badge reads against any bubble color
      val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.WHITE
      }
      canvas.drawCircle(bx, by, badgeR, badgeFillPaint)
      canvas.drawCircle(bx, by, badgeR, outline)
      val ts = (badgeR * 1.15f).coerceAtMost(22f)
      badgeTextPaint.textSize = ts
      val txtY = by - (badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2f
      canvas.drawText(text, bx, txtY, badgeTextPaint)
    }
  }
}
