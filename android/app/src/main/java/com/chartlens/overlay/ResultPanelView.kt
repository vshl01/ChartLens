package com.chartlens.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Bottom-sheet style result card with:
 * - drag handle
 * - header (title + close)
 * - scrollable body
 * - progress / message / error states
 *
 * Closes ONLY via the × button. Body taps do nothing.
 */
class ResultPanelView(context: Context) : FrameLayout(context) {

  private val card: LinearLayout
  private val handle: View
  private val headerTitle: TextView
  private val closeBtn: TextView
  private val scrollView: ScrollView
  private val body: LinearLayout
  private val statusRow: LinearLayout
  private val statusDot: View
  private val statusLabel: TextView
  private val messageView: TextView
  private val timingLabel: TextView

  var onCloseClicked: (() -> Unit)? = null

  init {
    val ctx = context
    setBackgroundColor(Color.TRANSPARENT)

    card = LinearLayout(ctx).apply {
      orientation = LinearLayout.VERTICAL
      val bg = GradientDrawable().apply {
        setColor(Color.parseColor("#0F1729"))
        cornerRadii = floatArrayOf(
          dpf(20f), dpf(20f),
          dpf(20f), dpf(20f),
          0f, 0f, 0f, 0f,
        )
      }
      background = bg
      setPadding(dp(16), dp(8), dp(16), dp(20))
      elevation = dpf(12f)
    }

    handle = View(ctx).apply {
      val hg = GradientDrawable().apply {
        setColor(Color.parseColor("#3A4459"))
        cornerRadius = dpf(2f)
      }
      background = hg
      val lp = LinearLayout.LayoutParams(dp(40), dp(4))
      lp.gravity = Gravity.CENTER_HORIZONTAL
      lp.bottomMargin = dp(10)
      layoutParams = lp
    }

    val header = LinearLayout(ctx).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
    }
    headerTitle = TextView(ctx).apply {
      text = "ChartLens"
      setTextColor(Color.WHITE)
      textSize = 14f
      typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
      val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
      layoutParams = lp
    }
    closeBtn = TextView(ctx).apply {
      text = "×"
      setTextColor(Color.parseColor("#9AA3B7"))
      textSize = 26f
      typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
      gravity = Gravity.CENTER
      setPadding(dp(10), 0, dp(10), 0)
      isClickable = true
      isFocusable = true
      setOnClickListener { onCloseClicked?.invoke() }
    }
    header.addView(headerTitle)
    header.addView(closeBtn)

    val divider = View(ctx).apply {
      setBackgroundColor(Color.parseColor("#1F2937"))
      val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
      lp.topMargin = dp(8)
      lp.bottomMargin = dp(10)
      layoutParams = lp
    }

    scrollView = ScrollView(ctx).apply {
      isVerticalScrollBarEnabled = true
      val lp = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
      )
      layoutParams = lp
    }
    body = LinearLayout(ctx).apply {
      orientation = LinearLayout.VERTICAL
    }
    scrollView.addView(body)

    statusRow = LinearLayout(ctx).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      visibility = GONE
      setPadding(0, dp(4), 0, dp(8))
    }
    statusDot = View(ctx).apply {
      val d = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.parseColor("#5B6CFF"))
      }
      background = d
      val lp = LinearLayout.LayoutParams(dp(10), dp(10))
      lp.rightMargin = dp(10)
      layoutParams = lp
    }
    statusLabel = TextView(ctx).apply {
      setTextColor(Color.parseColor("#E6E8EE"))
      textSize = 14f
      typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    statusRow.addView(statusDot)
    statusRow.addView(statusLabel)

    messageView = TextView(ctx).apply {
      setTextColor(Color.parseColor("#E6E8EE"))
      textSize = 14f
      setLineSpacing(0f, 1.35f)
      typeface = Typeface.MONOSPACE
      visibility = GONE
      val bg = GradientDrawable().apply {
        setColor(Color.parseColor("#172033"))
        cornerRadius = dpf(12f)
      }
      background = bg
      setPadding(dp(12), dp(10), dp(12), dp(10))
      movementMethod = LinkMovementMethod.getInstance()
    }
    timingLabel = TextView(ctx).apply {
      setTextColor(Color.parseColor("#5B6478"))
      textSize = 11f
      visibility = GONE
      val lp = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
      )
      lp.topMargin = dp(8)
      layoutParams = lp
    }

    body.addView(statusRow)
    body.addView(messageView)
    body.addView(timingLabel)

    card.addView(handle)
    card.addView(header)
    card.addView(divider)
    card.addView(scrollView)

    val cardLp = LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.WRAP_CONTENT,
    )
    cardLp.gravity = Gravity.BOTTOM
    addView(card, cardLp)
  }

  fun showProgress(label: String, accentColor: Int = Color.parseColor("#5B6CFF")) {
    statusRow.visibility = VISIBLE
    messageView.visibility = GONE
    timingLabel.visibility = GONE
    statusLabel.text = label
    (statusDot.background as? GradientDrawable)?.setColor(accentColor)
    pulseDot()
  }

  fun showMessage(title: String?, content: String, timing: String? = null) {
    if (!title.isNullOrBlank()) headerTitle.text = title
    statusRow.visibility = GONE
    messageView.visibility = VISIBLE
    messageView.text = content
    if (!timing.isNullOrBlank()) {
      timingLabel.visibility = VISIBLE
      timingLabel.text = timing
    } else {
      timingLabel.visibility = GONE
    }
    scrollView.post { scrollView.scrollTo(0, 0) }
  }

  fun showError(message: String) {
    statusRow.visibility = GONE
    timingLabel.visibility = GONE
    messageView.visibility = VISIBLE
    val bg = GradientDrawable().apply {
      setColor(Color.parseColor("#3B1620"))
      cornerRadius = dpf(12f)
    }
    messageView.background = bg
    messageView.setTextColor(Color.parseColor("#FCA5A5"))
    messageView.text = message
  }

  fun resetMessageStyle() {
    val bg = GradientDrawable().apply {
      setColor(Color.parseColor("#172033"))
      cornerRadius = dpf(12f)
    }
    messageView.background = bg
    messageView.setTextColor(Color.parseColor("#E6E8EE"))
  }

  private var dotAnimator: ValueAnimator? = null
  private fun pulseDot() {
    dotAnimator?.cancel()
    dotAnimator = ValueAnimator.ofFloat(0.4f, 1f).apply {
      duration = 700
      repeatCount = ValueAnimator.INFINITE
      repeatMode = ValueAnimator.REVERSE
      addUpdateListener { a ->
        statusDot.alpha = a.animatedValue as Float
      }
      start()
    }
  }

  fun stopPulse() {
    dotAnimator?.cancel()
    dotAnimator = null
    statusDot.alpha = 1f
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    stopPulse()
  }

  private fun dp(v: Int): Int = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics,
  ).toInt()
  private fun dpf(v: Float): Float = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics,
  )
}
