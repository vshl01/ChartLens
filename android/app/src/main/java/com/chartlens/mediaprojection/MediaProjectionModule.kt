package com.chartlens.mediaprojection

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import com.facebook.react.bridge.ActivityEventListener
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

class MediaProjectionModule(private val reactContext: ReactApplicationContext) :
  ReactContextBaseJavaModule(reactContext), ActivityEventListener {

  companion object {
    const val REQUEST_CODE = 9911

    @Volatile var pendingResultCode: Int = 0
    @Volatile var pendingData: Intent? = null
  }

  private var pendingProjectionPromise: Promise? = null

  init {
    reactContext.addActivityEventListener(this)
  }

  override fun getName(): String = "MediaProjectionModule"

  @ReactMethod
  fun requestProjection(promise: Promise) {
    val activity: Activity? = getCurrentActivity()
    if (activity == null) {
      promise.reject("ERR_NO_ACTIVITY", "No current activity")
      return
    }
    val mpm = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    pendingProjectionPromise = promise
    activity.startActivityForResult(mpm.createScreenCaptureIntent(), REQUEST_CODE)
  }

  override fun onActivityResult(
    activity: Activity,
    requestCode: Int,
    resultCode: Int,
    data: Intent?,
  ) {
    if (requestCode != REQUEST_CODE) return
    val promise = pendingProjectionPromise ?: return
    pendingProjectionPromise = null
    if (resultCode != Activity.RESULT_OK || data == null) {
      promise.reject("ERR_CANCELLED", "User cancelled MediaProjection consent")
      return
    }
    pendingResultCode = resultCode
    pendingData = data
    val map = Arguments.createMap()
    map.putInt("resultCode", resultCode)
    map.putBoolean("ok", true)
    promise.resolve(map)
  }

  override fun onNewIntent(intent: Intent) {}

  @ReactMethod
  fun startService(
    @Suppress("UNUSED_PARAMETER") resultCode: Int,
    promise: Promise,
  ) {
    val rc = pendingResultCode
    val dat = pendingData
    if (rc == 0 || dat == null) {
      promise.reject("ERR_NO_TOKEN", "Call requestProjection first")
      return
    }
    val intent = Intent(reactContext, CaptureService::class.java).apply {
      putExtra(CaptureService.EXTRA_RESULT_CODE, rc)
      putExtra(CaptureService.EXTRA_DATA, dat)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      reactContext.startForegroundService(intent)
    } else {
      reactContext.startService(intent)
    }
    promise.resolve(null)
  }

  @ReactMethod
  fun stopService(promise: Promise) {
    try {
      reactContext.stopService(Intent(reactContext, CaptureService::class.java))
      pendingResultCode = 0
      pendingData = null
      promise.resolve(null)
    } catch (e: Exception) {
      promise.reject("ERR_STOP", e)
    }
  }

  @ReactMethod
  fun isServiceRunning(promise: Promise) {
    promise.resolve(CaptureService.instance != null)
  }

  @ReactMethod
  fun captureFrame(promise: Promise) {
    val service = CaptureService.instance
    if (service == null) {
      android.util.Log.w("ChartLens", "captureFrame: service not running")
      promise.reject("ERR_NOT_RUNNING", "Capture service not running")
      return
    }
    Thread {
      val started = System.currentTimeMillis()
      val baseline = service.currentFrameCount()
      android.util.Log.d("ChartLens", "captureFrame: thread start, baseline=$baseline")
      if (!service.ensureDisplay()) {
        android.util.Log.w("ChartLens", "captureFrame: display unavailable and cannot be rebuilt")
        promise.reject(
          "ERR_NO_DISPLAY",
          "Capture display is gone — tap the broker card on Home to restart the session.",
        )
        return@Thread
      }
      // Try whatever's currently buffered first — if a frame is sitting in
      // the ImageReader queue, use it. Only wait for a new frame if the
      // queue is empty.
      var result: CaptureService.CaptureResult? = try {
        service.captureLatest()
      } catch (e: Throwable) {
        android.util.Log.e("ChartLens", "captureFrame: captureLatest threw (quick)", e)
        null
      }
      if (result == null) {
        val newCount = service.waitForNewFrame(baseline, 3000L)
        if (newCount < 0) {
          val elapsed = System.currentTimeMillis() - started
          android.util.Log.w("ChartLens", "captureFrame: no new frame within 3s (${elapsed}ms)")
          promise.reject(
            "ERR_NO_FRAME",
            "No new frame within 3s — interact with the chart (e.g. scroll) and tap again",
          )
          return@Thread
        }
        var attempts = 0
        while (attempts < 5 && result == null) {
          try {
            result = service.captureLatest()
          } catch (e: Throwable) {
            android.util.Log.e("ChartLens", "captureFrame: captureLatest threw", e)
            promise.reject("ERR_CAPTURE", e.message ?: "captureLatest threw")
            return@Thread
          }
          if (result == null) {
            try { Thread.sleep(60) } catch (_: InterruptedException) {}
          }
          attempts++
        }
      }
      val elapsed = System.currentTimeMillis() - started
      if (result == null) {
        android.util.Log.w("ChartLens", "captureFrame: acquire failed after wait (${elapsed}ms)")
        promise.reject("ERR_NO_FRAME", "Frame arrived but acquire failed")
        return@Thread
      }
      android.util.Log.d(
        "ChartLens",
        "captureFrame: ok in ${elapsed}ms ${result.width}x${result.height}",
      )
      val map = Arguments.createMap()
      map.putString("base64", result.base64)
      map.putInt("width", result.width)
      map.putInt("height", result.height)
      map.putInt("rotation", 0)
      map.putBoolean("isBlack", result.isBlack)
      map.putDouble("capturedAtMs", System.currentTimeMillis().toDouble())
      promise.resolve(map)
    }.start()
  }

  @ReactMethod
  fun addListener(@Suppress("UNUSED_PARAMETER") eventName: String) {}

  @ReactMethod
  fun removeListeners(@Suppress("UNUSED_PARAMETER") count: Int) {}
}
