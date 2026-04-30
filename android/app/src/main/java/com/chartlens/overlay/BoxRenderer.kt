package com.chartlens.overlay

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface

/**
 * Pure rendering helpers for the highlight overlay. Kept stateless so the
 * scaling math can be unit-tested without instantiating views.
 */
object BoxRenderer {

  data class Box(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val label: String,
    val colorInt: Int,
    val confidence: Float,
  )

  /**
   * Scale a box from frame-coordinate space to view-coordinate space.
   * Frame W/H come from the captured screenshot; view W/H is the overlay canvas.
   */
  fun scale(
    box: Box,
    frameW: Int,
    frameH: Int,
    viewW: Int,
    viewH: Int,
  ): Box {
    if (frameW <= 0 || frameH <= 0) return box
    val sx = viewW.toFloat() / frameW
    val sy = viewH.toFloat() / frameH
    return box.copy(
      x = box.x * sx,
      y = box.y * sy,
      w = box.w * sx,
      h = box.h * sy,
    )
  }

  fun strokePaint(colorInt: Int, strokePx: Float): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = strokePx
      color = colorInt
    }

  fun glowPaint(colorInt: Int): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.FILL
      color = (colorInt and 0x00FFFFFF) or 0x1F000000
    }

  fun chipBackgroundPaint(colorInt: Int): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.FILL
      color = colorInt
    }

  fun chipTextPaint(textColorInt: Int, sizePx: Float): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = textColorInt
      textSize = sizePx
      typeface = Typeface.MONOSPACE
      isAntiAlias = true
    }

  /**
   * Compute readable text color for a given background hex (white or near-black).
   */
  fun contrastingTextColor(bg: Int): Int {
    val r = (bg shr 16 and 0xFF) / 255f
    val g = (bg shr 8 and 0xFF) / 255f
    val b = (bg and 0xFF) / 255f
    val luminance = 0.299f * r + 0.587f * g + 0.114f * b
    return if (luminance > 0.6f) Color.parseColor("#0F1729") else Color.WHITE
  }

  /**
   * Draws a rounded box with a thin stroke + soft fill glow.
   */
  fun drawBox(canvas: Canvas, box: Box, strokePx: Float, cornerPx: Float) {
    val rect = RectF(box.x, box.y, box.x + box.w, box.y + box.h)
    canvas.drawRoundRect(rect, cornerPx, cornerPx, glowPaint(box.colorInt))
    canvas.drawRoundRect(rect, cornerPx, cornerPx, strokePaint(box.colorInt, strokePx))
  }

  /**
   * Draws a chip above (or, if no room, below) the box.
   * `verticalOffsetPx` lets callers stack chips when boxes overlap.
   */
  fun drawChip(
    canvas: Canvas,
    box: Box,
    chipText: String,
    sizePx: Float,
    paddingPx: Float,
    cornerPx: Float,
    verticalOffsetPx: Float,
    alpha: Int = 255,
  ) {
    val textPaint = chipTextPaint(contrastingTextColor(box.colorInt), sizePx)
    val textWidth = textPaint.measureText(chipText)
    val textHeight = textPaint.descent() - textPaint.ascent()
    val chipW = textWidth + paddingPx * 2
    val chipH = textHeight + paddingPx * 2
    val chipX = box.x
    var chipY = box.y - chipH - 4f - verticalOffsetPx
    if (chipY < 0f) chipY = box.y + box.h + 4f + verticalOffsetPx

    val rect = RectF(chipX, chipY, chipX + chipW, chipY + chipH)
    val bgPaint = chipBackgroundPaint(box.colorInt).apply { this.alpha = alpha }
    canvas.drawRoundRect(rect, cornerPx, cornerPx, bgPaint)

    val baseline = chipY + paddingPx - textPaint.ascent()
    textPaint.alpha = alpha
    canvas.drawText(chipText, chipX + paddingPx, baseline, textPaint)
  }
}
