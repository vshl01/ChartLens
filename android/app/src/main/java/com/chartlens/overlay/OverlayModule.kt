package com.chartlens.overlay

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.LifecycleEventListener
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class OverlayModule(private val reactContext: ReactApplicationContext) :
  ReactContextBaseJavaModule(reactContext), LifecycleEventListener {

  companion object {
    @Volatile private var current: OverlayModule? = null

    /**
     * Synchronously toggle bubble visibility from a non-main thread.
     * Blocks until the main thread has applied the change. Used by
     * [com.chartlens.mediaprojection.MediaProjectionModule] so the
     * hide-capture-show sequence does not depend on the (often-throttled)
     * JS thread when the app is backgrounded.
     */
    fun setBubbleVisibilitySync(visible: Boolean) {
      val o = current ?: return
      val latch = java.util.concurrent.CountDownLatch(1)
      o.main.post {
        o.bubbleView?.visibility = if (visible) View.VISIBLE else View.INVISIBLE
        latch.countDown()
      }
      latch.await(500L, java.util.concurrent.TimeUnit.MILLISECONDS)
    }
  }

  private val main = Handler(Looper.getMainLooper())
  private var wm: WindowManager? = null
  private var bubbleView: BubbleView? = null
  private var bubbleParams: WindowManager.LayoutParams? = null

  // pattern picker overlay
  private var pickerView: PatternPickerView? = null
  private var pickerParams: WindowManager.LayoutParams? = null

  // highlight overlay
  private var highlightView: HighlightOverlayView? = null
  private var highlightParams: WindowManager.LayoutParams? = null

  // rotation tracked at capture time so we can refuse to render stale frames
  private var captureOrientation: Int = Configuration.ORIENTATION_UNDEFINED

  private var sizePx: Int = 56
  private var lastBrokerColor: String? = null

  init {
    reactContext.addLifecycleEventListener(this)
    current = this
  }

  override fun getName(): String = "OverlayModule"

  override fun onHostResume() {}
  override fun onHostPause() {}
  override fun onHostDestroy() {
    main.post { hideAllOverlays() }
  }

  private fun overlayType(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    else
      @Suppress("DEPRECATION")
      WindowManager.LayoutParams.TYPE_PHONE

  private fun emit(event: String, payload: WritableMap?) {
    reactContext
      .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
      .emit(event, payload)
  }

  private fun dp(v: Int): Int =
    TypedValue.applyDimension(
      TypedValue.COMPLEX_UNIT_DIP,
      v.toFloat(),
      reactContext.resources.displayMetrics,
    ).toInt()

  @ReactMethod
  fun hasOverlayPermission(promise: Promise) {
    promise.resolve(Settings.canDrawOverlays(reactContext))
  }

  @ReactMethod
  fun requestOverlayPermission(promise: Promise) {
    if (Settings.canDrawOverlays(reactContext)) {
      promise.resolve(true)
      return
    }
    try {
      val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${reactContext.packageName}"),
      )
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      reactContext.startActivity(intent)
      promise.resolve(false)
    } catch (e: Exception) {
      promise.reject("ERR_OVERLAY", e)
    }
  }

  @ReactMethod
  fun showBubble(config: ReadableMap, promise: Promise) {
    if (!Settings.canDrawOverlays(reactContext)) {
      promise.reject("ERR_PERM", "Overlay permission not granted")
      return
    }
    main.post {
      try {
        val sizeDp = if (config.hasKey("size")) config.getInt("size") else 56
        val opacity = if (config.hasKey("opacity")) config.getDouble("opacity") else 0.95
        val initX = if (config.hasKey("x")) config.getInt("x") else -1
        val initY = if (config.hasKey("y")) config.getInt("y") else 320
        val brokerColor = if (config.hasKey("brokerColor")) config.getString("brokerColor") else null
        val iconB64 = if (config.hasKey("brokerIconBase64")) config.getString("brokerIconBase64") else null
        val glyph = if (config.hasKey("glyph")) config.getString("glyph") else "C"
        sizePx = dp(sizeDp)
        lastBrokerColor = brokerColor
        ensureWm()
        if (bubbleView == null) {
          createBubble(initX, initY, opacity.toFloat(), brokerColor, iconB64, glyph ?: "C")
        } else {
          bubbleView?.setBrandColor(brokerColor)
          bubbleView?.setIconBase64(iconB64)
          if (!glyph.isNullOrBlank()) bubbleView?.setGlyph(glyph)
        }
        promise.resolve(null)
      } catch (e: Exception) {
        promise.reject("ERR_SHOW", e)
      }
    }
  }

  @ReactMethod
  fun hideBubble(promise: Promise) {
    main.post {
      hideAllOverlays()
      promise.resolve(null)
    }
  }

  @ReactMethod
  fun setBubbleState(state: String, promise: Promise) {
    main.post {
      bubbleView?.setState(state)
      promise.resolve(null)
    }
  }

  @ReactMethod
  fun setBubbleVisible(visible: Boolean, promise: Promise) {
    main.post {
      bubbleView?.visibility = if (visible) View.VISIBLE else View.INVISIBLE
      promise.resolve(null)
    }
  }

  @ReactMethod
  fun setBubbleBadge(text: String?, tone: String?, promise: Promise) {
    main.post {
      bubbleView?.setBadge(text, tone)
      promise.resolve(null)
    }
  }

  @ReactMethod
  fun showPatternPicker(items: ReadableArray, promise: Promise) {
    main.post {
      try {
        ensureWm()
        ensurePicker()
        val parsed = mutableListOf<PatternPickerView.Item>()
        for (i in 0 until items.size()) {
          val it = items.getMap(i) ?: continue
          val id = if (it.hasKey("id")) it.getString("id") ?: continue else continue
          val name = if (it.hasKey("name")) it.getString("name") ?: id else id
          val hint = if (it.hasKey("hint")) it.getString("hint") ?: "" else ""
          val emoji = if (it.hasKey("emoji")) it.getString("emoji") ?: "" else ""
          val colorHex = if (it.hasKey("color")) it.getString("color") else null
          val colorInt = try { Color.parseColor(colorHex ?: "#5B6CFF") } catch (_: Exception) { Color.parseColor("#5B6CFF") }
          parsed += PatternPickerView.Item(id, name, hint, emoji, colorInt)
        }

        val anchorRight = bubbleAnchoredRight()
        pickerView?.bind(parsed, anchorRight)
        pickerView?.appearAnimated()
        promise.resolve(null)
      } catch (e: Exception) {
        promise.reject("ERR_PICKER", e)
      }
    }
  }

  @ReactMethod
  fun hidePatternPicker(promise: Promise) {
    main.post {
      pickerView?.dismissAnimated()
      promise.resolve(null)
    }
  }

  @ReactMethod
  fun showHighlightOverlay(
    boxes: ReadableArray,
    frameWidth: Int,
    frameHeight: Int,
    autoDismissMs: Double,
    promise: Promise,
  ) {
    main.post {
      try {
        // staleness check: if device rotated since the captured frame, refuse
        val currentOrientation = reactContext.resources.configuration.orientation
        if (captureOrientation != Configuration.ORIENTATION_UNDEFINED &&
          currentOrientation != captureOrientation
        ) {
          emit("OverlayStaleFrame", Arguments.createMap())
          promise.resolve(null)
          return@post
        }
        captureOrientation = currentOrientation

        ensureWm()
        ensureHighlight()
        val parsed = mutableListOf<BoxRenderer.Box>()
        for (i in 0 until boxes.size()) {
          val b = boxes.getMap(i) ?: continue
          val x = if (b.hasKey("x")) b.getDouble("x").toFloat() else continue
          val y = if (b.hasKey("y")) b.getDouble("y").toFloat() else continue
          val w = if (b.hasKey("w")) b.getDouble("w").toFloat() else continue
          val h = if (b.hasKey("h")) b.getDouble("h").toFloat() else continue
          val label = if (b.hasKey("label")) b.getString("label") ?: "" else ""
          val colorHex = if (b.hasKey("color")) b.getString("color") else null
          val confidence =
            if (b.hasKey("confidence")) b.getDouble("confidence").toFloat() else 0f
          val colorInt = try { Color.parseColor(colorHex ?: "#5B6CFF") } catch (_: Exception) { Color.parseColor("#5B6CFF") }
          parsed += BoxRenderer.Box(x, y, w, h, label, colorInt, confidence)
        }
        highlightView?.visibility = View.VISIBLE
        highlightView?.setData(parsed, frameWidth, frameHeight, autoDismissMs.toLong())
        promise.resolve(null)
      } catch (e: Exception) {
        promise.reject("ERR_HIGHLIGHT", e)
      }
    }
  }

  @ReactMethod
  fun clearHighlightOverlay(promise: Promise) {
    main.post {
      highlightView?.fadeOutAndClear()
      promise.resolve(null)
    }
  }

  /**
   * One-shot end-to-end pattern detection that runs entirely on a native
   * background thread. Independent of the JS thread so it works even when
   * the OS throttles ChartLens because the broker app is foregrounded.
   *
   * The promise resolves with the analysis summary (matchCount, frame dims,
   * timings). Boxes are drawn on the screen as a side effect — no extra JS
   * call required.
   */
  @ReactMethod
  fun runAnalyzeAndDraw(args: ReadableMap, promise: Promise) {
    Thread {
      try {
        val parsed = AnalyzeRunner.Args(
          apiKey = args.getString("apiKey") ?: "",
          model = args.getString("model") ?: "gemini-2.5-flash",
          patternId = args.getString("patternId") ?: "",
          patternName = args.getString("patternName") ?: "",
          patternDef = args.getString("patternDef") ?: "",
          color = args.getString("color") ?: "#5B6CFF",
          minConfidence = if (args.hasKey("minConfidence")) args.getDouble("minConfidence") else 0.6,
          maxBoxes = if (args.hasKey("maxBoxes")) args.getInt("maxBoxes") else 30,
          autoDismissMs = if (args.hasKey("autoDismissMs")) args.getDouble("autoDismissMs").toLong() else 20000L,
        )
        val result = AnalyzeRunner.run(parsed)

        // Draw boxes on the main thread without any JS round-trip.
        val colorInt = AnalyzeRunner.parseColor(parsed.color)
        val boxes = result.matches.map {
          BoxRenderer.Box(
            x = it.x, y = it.y, w = it.w, h = it.h,
            label = parsed.patternName,
            colorInt = colorInt,
            confidence = it.confidence,
          )
        }

        main.post {
          ensureWm()
          ensureHighlight()
          if (boxes.isNotEmpty()) {
            highlightView?.visibility = View.VISIBLE
            highlightView?.setData(boxes, result.frameWidth, result.frameHeight, parsed.autoDismissMs)
            bubbleView?.setBadge(boxes.size.toString(), "success")
            bubbleView?.setState("highlighting")
          } else {
            // No matches or no chart — clear any old highlights, badge 0.
            highlightView?.fadeOutAndClear()
            bubbleView?.setBadge("0", "warning")
            bubbleView?.setState("idle")
            try {
              val msg = if (!result.gotChart) "No chart detected in current view"
              else "No ${parsed.patternName} found in current view"
              Toast.makeText(reactContext, msg, Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {}
          }
        }

        val summary = Arguments.createMap().apply {
          putInt("matchCount", boxes.size)
          putInt("frameWidth", result.frameWidth)
          putInt("frameHeight", result.frameHeight)
          putBoolean("gotChart", result.gotChart)
          putDouble("durationMs", result.durationMs.toDouble())
          putDouble("captureMs", result.captureMs.toDouble())
          putDouble("geminiMs", result.geminiMs.toDouble())
          putString("rawText", result.rawText.take(2000))
          val arr = Arguments.createArray()
          for (m in result.matches) {
            val o = Arguments.createMap()
            o.putInt("idx", m.idx)
            o.putDouble("confidence", m.confidence.toDouble())
            o.putDouble("x", m.x.toDouble())
            o.putDouble("y", m.y.toDouble())
            o.putDouble("w", m.w.toDouble())
            o.putDouble("h", m.h.toDouble())
            if (m.note != null) o.putString("note", m.note)
            arr.pushMap(o)
          }
          putArray("matches", arr)
        }
        promise.resolve(summary)
      } catch (e: Throwable) {
        android.util.Log.e("ChartLens", "runAnalyzeAndDraw failed", e)
        // Show toast natively too, since JS may be paused.
        main.post {
          try {
            Toast.makeText(reactContext, e.message ?: "Analyze failed", Toast.LENGTH_LONG).show()
            bubbleView?.setState("error")
          } catch (_: Exception) {}
        }
        promise.reject("ERR_ANALYZE", e.message ?: "Analyze failed")
      }
    }.start()
  }

  @ReactMethod
  fun showToast(text: String, tone: String?, promise: Promise) {
    main.post {
      try {
        Toast.makeText(reactContext, text, Toast.LENGTH_SHORT).show()
      } catch (_: Exception) {}
      promise.resolve(null)
    }
  }

  @ReactMethod
  fun addListener(@Suppress("UNUSED_PARAMETER") eventName: String) {}

  @ReactMethod
  fun removeListeners(@Suppress("UNUSED_PARAMETER") count: Int) {}

  // -- internals --

  private fun ensureWm() {
    if (wm == null) {
      wm = reactContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }
  }

  private fun bubbleAnchoredRight(): Boolean {
    val params = bubbleParams ?: return true
    val dm = reactContext.resources.displayMetrics
    return params.x + sizePx / 2 > dm.widthPixels / 2
  }

  private fun createBubble(
    initX: Int,
    initY: Int,
    opacity: Float,
    brokerColor: String?,
    iconB64: String?,
    glyph: String,
  ) {
    val view = BubbleView(reactContext.applicationContext)
    view.alpha = opacity
    view.setBrandColor(brokerColor)
    view.setIconBase64(iconB64)
    view.setGlyph(glyph)

    val lp = WindowManager.LayoutParams(
      sizePx,
      sizePx,
      overlayType(),
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
      PixelFormat.TRANSLUCENT,
    ).apply {
      gravity = Gravity.TOP or Gravity.START
      val dm = reactContext.resources.displayMetrics
      x = if (initX < 0) dm.widthPixels - sizePx - dp(8) else initX
      y = initY
    }
    bubbleParams = lp

    val touch = createTouchListener()
    view.setOnTouchListener(touch)

    wm?.addView(view, lp)
    bubbleView = view
  }

  private fun createTouchListener(): View.OnTouchListener {
    val slop = ViewConfiguration.get(reactContext).scaledTouchSlop
    val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    var startX = 0
    var startY = 0
    var touchX = 0f
    var touchY = 0f
    var dragging = false
    var downAt = 0L
    val longPressRunnable = Runnable {
      val map = Arguments.createMap()
      emit("OverlayLongPress", map)
    }

    return View.OnTouchListener { _, ev ->
      val params = bubbleParams ?: return@OnTouchListener false
      val viewWm = wm ?: return@OnTouchListener false
      when (ev.action) {
        MotionEvent.ACTION_DOWN -> {
          startX = params.x
          startY = params.y
          touchX = ev.rawX
          touchY = ev.rawY
          dragging = false
          downAt = System.currentTimeMillis()
          bubbleView?.playTapAnimation()
          main.postDelayed(longPressRunnable, longPressTimeout)
          true
        }
        MotionEvent.ACTION_MOVE -> {
          val dx = ev.rawX - touchX
          val dy = ev.rawY - touchY
          if (!dragging && (abs(dx) > slop || abs(dy) > slop)) {
            dragging = true
            main.removeCallbacks(longPressRunnable)
          }
          if (dragging) {
            params.x = (startX + dx).toInt()
            params.y = (startY + dy).toInt()
            try { viewWm.updateViewLayout(bubbleView, params) } catch (_: Exception) {}
          }
          true
        }
        MotionEvent.ACTION_UP -> {
          main.removeCallbacks(longPressRunnable)
          if (!dragging && System.currentTimeMillis() - downAt < longPressTimeout) {
            // record orientation at the moment of intent — close enough
            captureOrientation = reactContext.resources.configuration.orientation
            emit("OverlayBubbleTap", Arguments.createMap())
          } else if (dragging) {
            val dm = reactContext.resources.displayMetrics
            val midX = dm.widthPixels / 2
            val target = if (params.x + sizePx / 2 > midX) dm.widthPixels - sizePx - dp(8) else dp(8)
            params.x = target
            params.y = max(dp(24), min(params.y, dm.heightPixels - sizePx - dp(80)))
            try { viewWm.updateViewLayout(bubbleView, params) } catch (_: Exception) {}
            val pos = Arguments.createMap()
            pos.putInt("x", params.x)
            pos.putInt("y", params.y)
            emit("OverlayPositionChanged", pos)
          }
          true
        }
        MotionEvent.ACTION_CANCEL -> {
          main.removeCallbacks(longPressRunnable)
          true
        }
        else -> false
      }
    }
  }

  private fun ensurePicker() {
    if (pickerView != null) return
    val ctx = reactContext.applicationContext
    val view = PatternPickerView(ctx)
    view.onPicked = { id ->
      val map = Arguments.createMap()
      map.putString("id", id)
      emit("OverlayPatternPicked", map)
    }
    view.onDismissed = {
      emit("OverlayPickerDismissed", Arguments.createMap())
    }

    val lp = WindowManager.LayoutParams(
      WindowManager.LayoutParams.MATCH_PARENT,
      WindowManager.LayoutParams.MATCH_PARENT,
      overlayType(),
      WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
      PixelFormat.TRANSLUCENT,
    ).apply {
      gravity = Gravity.TOP or Gravity.START
    }
    pickerParams = lp
    view.visibility = View.GONE
    wm?.addView(view, lp)
    pickerView = view
  }

  private fun ensureHighlight() {
    if (highlightView != null) return
    val ctx = reactContext.applicationContext
    val view = HighlightOverlayView(ctx)
    view.onCleared = {
      emit("OverlayHighlightCleared", Arguments.createMap())
    }
    view.onBoxTapped = { idx ->
      val map = Arguments.createMap()
      map.putInt("index", idx)
      emit("OverlayHighlightTapped", map)
    }

    val lp = WindowManager.LayoutParams(
      WindowManager.LayoutParams.MATCH_PARENT,
      WindowManager.LayoutParams.MATCH_PARENT,
      overlayType(),
      // FLAG_NOT_TOUCHABLE: every touch passes through to the broker app so
      // panning/zooming the chart keeps working while boxes are drawn. Boxes
      // are visual-only — they get cleared via auto-dismiss, the bubble's
      // clear button, or a re-tap of the bubble.
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
      PixelFormat.TRANSLUCENT,
    ).apply {
      gravity = Gravity.TOP or Gravity.START
    }
    highlightParams = lp
    view.visibility = View.GONE
    wm?.addView(view, lp)
    highlightView = view
  }

  private fun hideAllOverlays() {
    try { highlightView?.let { wm?.removeView(it) } } catch (_: Exception) {}
    highlightView = null
    highlightParams = null
    try { pickerView?.let { wm?.removeView(it) } } catch (_: Exception) {}
    pickerView = null
    pickerParams = null
    try { bubbleView?.let { wm?.removeView(it) } } catch (_: Exception) {}
    bubbleView = null
    bubbleParams = null
  }
}
