package com.chartlens.overlay

import android.app.Activity
import android.content.Context
import android.content.Intent
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
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.LifecycleEventListener
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class OverlayModule(private val reactContext: ReactApplicationContext) :
  ReactContextBaseJavaModule(reactContext), LifecycleEventListener {

  private val main = Handler(Looper.getMainLooper())
  private var wm: WindowManager? = null
  private var bubbleView: BubbleView? = null
  private var bubbleParams: WindowManager.LayoutParams? = null
  private var resultPanel: ResultPanelView? = null
  private var resultParams: WindowManager.LayoutParams? = null
  private var sizePx: Int = 56
  private var lastBrokerColor: String? = null

  init {
    reactContext.addLifecycleEventListener(this)
  }

  override fun getName(): String = "OverlayModule"

  override fun onHostResume() {}
  override fun onHostPause() {}
  override fun onHostDestroy() {
    main.post { hideBubbleInternal() }
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
      hideBubbleInternal()
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
  fun setResultText(text: String, promise: Promise) {
    main.post {
      ensureResultPanel()
      resultPanel?.resetMessageStyle()
      resultPanel?.showMessage(null, text, null)
      resultPanel?.visibility = View.VISIBLE
      promise.resolve(null)
    }
  }

  @ReactMethod
  fun showResultProgress(label: String, accentHex: String?, promise: Promise) {
    main.post {
      ensureResultPanel()
      val color = try {
        if (!accentHex.isNullOrBlank()) Color.parseColor(accentHex) else Color.parseColor("#5B6CFF")
      } catch (_: Exception) { Color.parseColor("#5B6CFF") }
      resultPanel?.showProgress(label, color)
      resultPanel?.visibility = View.VISIBLE
      promise.resolve(null)
    }
  }

  @ReactMethod
  fun showResultMessage(title: String?, content: String, timing: String?, promise: Promise) {
    main.post {
      ensureResultPanel()
      resultPanel?.resetMessageStyle()
      resultPanel?.showMessage(title, content, timing)
      resultPanel?.stopPulse()
      resultPanel?.visibility = View.VISIBLE
      promise.resolve(null)
    }
  }

  @ReactMethod
  fun showResultError(message: String, promise: Promise) {
    main.post {
      ensureResultPanel()
      resultPanel?.showError(message)
      resultPanel?.stopPulse()
      resultPanel?.visibility = View.VISIBLE
      promise.resolve(null)
    }
  }

  @ReactMethod
  fun hideResult(promise: Promise) {
    main.post {
      resultPanel?.visibility = View.GONE
      promise.resolve(null)
    }
  }

  @ReactMethod
  fun addListener(@Suppress("UNUSED_PARAMETER") eventName: String) {}

  @ReactMethod
  fun removeListeners(@Suppress("UNUSED_PARAMETER") count: Int) {}

  private fun ensureWm() {
    if (wm == null) {
      wm = reactContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }
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
            emit("OverlayBubbleTap", Arguments.createMap())
          } else if (dragging) {
            // snap to nearest edge
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

  private fun ensureResultPanel() {
    if (resultPanel != null) return
    val ctx = reactContext.applicationContext
    val panel = ResultPanelView(ctx)
    panel.onCloseClicked = {
      resultPanel?.visibility = View.GONE
      emit("OverlayResultClosed", Arguments.createMap())
    }
    val dm = reactContext.resources.displayMetrics
    val maxHeight = (dm.heightPixels * 0.55f).toInt()
    val lp = WindowManager.LayoutParams(
      WindowManager.LayoutParams.MATCH_PARENT,
      maxHeight,
      overlayType(),
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
      PixelFormat.TRANSLUCENT,
    ).apply {
      gravity = Gravity.BOTTOM or Gravity.START
      y = 0
    }
    resultParams = lp
    ensureWm()
    wm?.addView(panel, lp)
    panel.visibility = View.GONE
    resultPanel = panel
  }

  private fun hideBubbleInternal() {
    try {
      bubbleView?.let { wm?.removeView(it) }
    } catch (_: Exception) {}
    bubbleView = null
    bubbleParams = null
    try {
      resultPanel?.let { wm?.removeView(it) }
    } catch (_: Exception) {}
    resultPanel = null
    resultParams = null
  }
}
