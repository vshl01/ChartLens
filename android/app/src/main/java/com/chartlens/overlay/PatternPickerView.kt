package com.chartlens.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Animated pattern picker. Mounted as its own WindowManager view by
 * [OverlayModule]. Renders a vertical list of pattern rows that slide in from
 * the bubble's anchor edge.
 */
class PatternPickerView(context: Context) : FrameLayout(context) {

  data class Item(
    val id: String,
    val name: String,
    val hint: String,
    val emoji: String,
    val color: Int,
  )

  private val card: LinearLayout
  private val searchInput: EditText
  private val scrollView: ScrollView
  private val list: LinearLayout
  private var items: List<Item> = emptyList()
  private var rowViews: MutableList<View> = mutableListOf()

  var onPicked: ((id: String) -> Unit)? = null
  var onDismissed: (() -> Unit)? = null
  var anchorRight: Boolean = true

  init {
    setBackgroundColor(Color.parseColor("#66000000"))
    isClickable = true
    isFocusable = false
    setOnTouchListener { _, ev ->
      if (ev.action == MotionEvent.ACTION_UP) {
        // tap outside card → dismiss
        val r = android.graphics.Rect()
        card.getGlobalVisibleRect(r)
        if (!r.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
          dismissAnimated()
        }
      }
      true
    }

    card = LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
      val bg = GradientDrawable().apply {
        setColor(Color.parseColor("#EB0F1729"))
        cornerRadius = dpf(18f)
      }
      background = bg
      elevation = dpf(16f)
      setPadding(dp(12), dp(12), dp(12), dp(12))
    }

    val title = TextView(context).apply {
      text = "Find pattern"
      setTextColor(Color.WHITE)
      textSize = 16f
      typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
      val lp = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
      )
      lp.bottomMargin = dp(8)
      layoutParams = lp
    }

    searchInput = EditText(context).apply {
      hint = "Search…"
      setHintTextColor(Color.parseColor("#7280A1"))
      setTextColor(Color.WHITE)
      textSize = 13f
      val bg = GradientDrawable().apply {
        setColor(Color.parseColor("#1A2236"))
        cornerRadius = dpf(10f)
      }
      background = bg
      setPadding(dp(12), dp(8), dp(12), dp(8))
      val lp = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
      )
      lp.bottomMargin = dp(8)
      layoutParams = lp
      isFocusableInTouchMode = false // don't shove keyboard up
      isFocusable = false
      addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
          renderList(items, s?.toString() ?: "")
        }
        override fun afterTextChanged(s: Editable?) {}
      })
    }

    scrollView = ScrollView(context).apply {
      isVerticalScrollBarEnabled = true
      val lp = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        0,
        1f,
      )
      layoutParams = lp
    }
    list = LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
    }
    scrollView.addView(list)

    card.addView(title)
    card.addView(searchInput)
    card.addView(scrollView)

    addView(card, LayoutParams(
      ViewGroup.LayoutParams.WRAP_CONTENT,
      ViewGroup.LayoutParams.WRAP_CONTENT,
    ))
  }

  fun bind(nextItems: List<Item>, anchorOnRight: Boolean) {
    items = nextItems
    anchorRight = anchorOnRight
    val dm = resources.displayMetrics
    val cardWidth = (dm.widthPixels * 0.72f).toInt().coerceAtMost(dp(360))
    val cardHeight = (dm.heightPixels * 0.6f).toInt()
    val lp = card.layoutParams as LayoutParams
    lp.width = cardWidth
    lp.height = cardHeight
    lp.gravity = (if (anchorOnRight) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
    lp.rightMargin = if (anchorOnRight) dp(12) else 0
    lp.leftMargin = if (!anchorOnRight) dp(12) else 0
    card.layoutParams = lp
    renderList(items, "")
  }

  private fun renderList(source: List<Item>, query: String) {
    list.removeAllViews()
    rowViews.clear()
    val q = query.trim().lowercase()
    val filtered =
      if (q.isEmpty()) source
      else source.filter {
        it.name.lowercase().contains(q) || it.hint.lowercase().contains(q)
      }
    filtered.forEachIndexed { i, item ->
      val row = makeRow(item)
      list.addView(row)
      rowViews += row
      row.alpha = 0f
      row.translationX = (if (anchorRight) 1f else -1f) * dp(24).toFloat()
      row.animate()
        .alpha(1f)
        .translationX(0f)
        .setStartDelay(30L * i.coerceAtMost(6))
        .setDuration(220L)
        .setInterpolator(OvershootInterpolator(1.1f))
        .start()
    }
  }

  private fun makeRow(item: Item): View {
    val row = LinearLayout(context).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      setPadding(dp(8), dp(10), dp(8), dp(10))
      isClickable = true
      isFocusable = true
      val bg = GradientDrawable().apply {
        setColor(Color.TRANSPARENT)
        cornerRadius = dpf(10f)
      }
      background = bg
      val lp = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        dp(56),
      )
      lp.bottomMargin = dp(4)
      layoutParams = lp
      setOnClickListener {
        animateOutThen { onPicked?.invoke(item.id) }
      }
    }

    val emoji = TextView(context).apply {
      text = item.emoji
      textSize = 22f
      val lp = LinearLayout.LayoutParams(dp(40), dp(40))
      lp.rightMargin = dp(10)
      gravity = Gravity.CENTER
      val bg = GradientDrawable().apply {
        setColor(adjustAlpha(item.color, 0.18f))
        cornerRadius = dpf(10f)
      }
      background = bg
      layoutParams = lp
    }

    val textCol = LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
      val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
      layoutParams = lp
    }
    val name = TextView(context).apply {
      text = item.name
      setTextColor(Color.WHITE)
      textSize = 16f
      typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    val hint = TextView(context).apply {
      text = item.hint
      setTextColor(Color.parseColor("#99A6BD"))
      textSize = 12f
    }
    textCol.addView(name)
    textCol.addView(hint)

    row.addView(emoji)
    row.addView(textCol)
    return row
  }

  fun appearAnimated() {
    visibility = VISIBLE
    alpha = 0f
    card.translationX = (if (anchorRight) 1f else -1f) * dpf(40f)
    animate().alpha(1f).setDuration(160L).start()
    card.animate()
      .translationX(0f)
      .setDuration(220L)
      .setInterpolator(OvershootInterpolator(1.1f))
      .start()
  }

  fun dismissAnimated() {
    animate().alpha(0f).setDuration(140L).start()
    card.animate()
      .translationX((if (anchorRight) 1f else -1f) * dpf(40f))
      .setDuration(160L)
      .withEndAction {
        visibility = GONE
        onDismissed?.invoke()
      }
      .start()
  }

  private fun animateOutThen(action: () -> Unit) {
    animate().alpha(0f).setDuration(120L).start()
    card.animate()
      .translationX((if (anchorRight) 1f else -1f) * dpf(40f))
      .setDuration(140L)
      .withEndAction {
        visibility = GONE
        action()
      }
      .start()
  }

  private fun adjustAlpha(color: Int, factor: Float): Int {
    val a = (Color.alpha(color) * factor).toInt().coerceIn(0, 255)
    return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
  }

  private fun dp(v: Int): Int = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics,
  ).toInt()

  private fun dpf(v: Float): Float = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics,
  )
}
