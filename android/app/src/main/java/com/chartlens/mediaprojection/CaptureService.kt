package com.chartlens.mediaprojection

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Base64
import android.view.Surface
import android.view.WindowManager
import com.chartlens.MainActivity
import com.chartlens.R
import java.io.ByteArrayOutputStream

class CaptureService : Service() {

  private var projection: MediaProjection? = null
  private var virtualDisplay: VirtualDisplay? = null
  private var imageReader: ImageReader? = null
  private var handlerThread: HandlerThread? = null
  private var handler: Handler? = null
  private var width: Int = 0
  private var height: Int = 0
  private var density: Int = 0

  companion object {
    const val CHANNEL_ID = "chartlens.capture"
    const val NOTIFICATION_ID = 4242

    @Volatile var instance: CaptureService? = null

    const val EXTRA_RESULT_CODE = "resultCode"
    const val EXTRA_DATA = "data"
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    instance = this
    createChannel()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val notif = buildNotification()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      startForeground(
        NOTIFICATION_ID,
        notif,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
      )
    } else {
      startForeground(NOTIFICATION_ID, notif)
    }

    if (intent != null && intent.hasExtra(EXTRA_RESULT_CODE)) {
      val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
      val data: Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
          @Suppress("DEPRECATION")
          intent.getParcelableExtra(EXTRA_DATA)
        }
      if (data != null) {
        startProjection(resultCode, data)
      } else {
        android.util.Log.e("ChartLens", "CaptureService: missing projection data extra")
      }
    }
    return START_NOT_STICKY
  }

  private fun createChannel() {
    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (nm.getNotificationChannel(CHANNEL_ID) == null) {
      val ch = NotificationChannel(CHANNEL_ID, "ChartLens capture", NotificationManager.IMPORTANCE_LOW)
      ch.description = "Holds screen capture session"
      nm.createNotificationChannel(ch)
    }
  }

  private fun buildNotification(): Notification {
    val pi = PendingIntent.getActivity(
      this, 0,
      Intent(this, MainActivity::class.java),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    return Notification.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle("ChartLens")
      .setContentText("Ready to capture")
      .setOngoing(true)
      .setContentIntent(pi)
      .build()
  }

  private fun computeMetrics() {
    val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val dm = DisplayMetrics()
    @Suppress("DEPRECATION")
    wm.defaultDisplay.getRealMetrics(dm)
    width = dm.widthPixels
    height = dm.heightPixels
    density = dm.densityDpi
  }

  private fun startProjection(resultCode: Int, data: Intent) {
    val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    // If a previous projection is alive, stop it cleanly first so its
    // onStop() callback fires while we're still on the OLD generation —
    // the callback won't touch the NEW display because it captures the
    // generation by value.
    try { projection?.stop() } catch (_: Exception) {}
    teardownDisplay()

    val gen = ++projectionGen
    val newProjection: MediaProjection? = mpm.getMediaProjection(resultCode, data)
    if (newProjection == null) {
      android.util.Log.e("ChartLens", "startProjection: getMediaProjection returned null")
      return
    }
    newProjection.registerCallback(object : MediaProjection.Callback() {
      override fun onStop() {
        // Only tear down if this callback belongs to the *current*
        // generation. Old callbacks from replaced projections must not
        // null out the new display.
        if (gen == projectionGen) {
          android.util.Log.d("ChartLens", "MediaProjection.onStop gen=$gen current — tearing down")
          teardownDisplay()
        } else {
          android.util.Log.d("ChartLens", "MediaProjection.onStop gen=$gen stale (current=$projectionGen), ignoring")
        }
      }
    }, null)
    projection = newProjection

    if (handlerThread == null) {
      handlerThread = HandlerThread("CaptureThread").also { it.start() }
      handler = Handler(handlerThread!!.looper)
    }

    setupVirtualDisplay()
  }

  private var projectionGen: Int = 0

  /**
   * Best-effort recovery if the display was torn down but the projection
   * is still alive. Returns true if the display is usable on return.
   */
  fun ensureDisplay(): Boolean {
    if (imageReader != null && virtualDisplay != null) return true
    if (projection == null) return false
    android.util.Log.w("ChartLens", "ensureDisplay: rebuilding torn-down display")
    setupVirtualDisplay()
    return imageReader != null && virtualDisplay != null
  }

  private fun setupVirtualDisplay() {
    teardownDisplay()
    computeMetrics()
    val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
    imageReader = reader
    // Track frame arrivals so captureLatest() can wait for a fresh frame
    // when the screen content is static (otherwise acquireLatestImage()
    // returns null until something redraws).
    reader.setOnImageAvailableListener({
      synchronized(frameLock) {
        frameCount++
        frameLock.notifyAll()
      }
    }, handler)
    virtualDisplay = projection?.createVirtualDisplay(
      "ChartLensVD",
      width, height, density,
      DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
      reader.surface, null, handler,
    )
  }

  @Volatile private var frameCount: Long = 0L
  private val frameLock = Object()

  fun currentFrameCount(): Long = synchronized(frameLock) { frameCount }

  /**
   * Block up to [timeoutMs] for a NEW frame to arrive after [sinceCount].
   * Returns the latest frame count (>= sinceCount + 1) or -1 on timeout.
   */
  fun waitForNewFrame(sinceCount: Long, timeoutMs: Long): Long {
    val deadline = System.currentTimeMillis() + timeoutMs
    synchronized(frameLock) {
      while (frameCount <= sinceCount) {
        val remaining = deadline - System.currentTimeMillis()
        if (remaining <= 0L) return -1
        try { frameLock.wait(remaining) } catch (_: InterruptedException) { return -1 }
      }
      return frameCount
    }
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    setupVirtualDisplay()
  }

  /** Capture latest frame, returns base64 PNG + meta. Caller invokes from any thread. */
  fun captureLatest(): CaptureResult? {
    val reader = imageReader
    if (reader == null) {
      android.util.Log.w("ChartLens", "captureLatest: imageReader is null (projection=$projection vd=$virtualDisplay)")
      return null
    }
    val image: Image = reader.acquireLatestImage() ?: return null
    return try {
      val plane = image.planes[0]
      val buffer = plane.buffer
      val pixelStride = plane.pixelStride
      val rowStride = plane.rowStride
      val rowPadding = rowStride - pixelStride * image.width

      val bmp = Bitmap.createBitmap(
        image.width + rowPadding / pixelStride,
        image.height,
        Bitmap.Config.ARGB_8888,
      )
      bmp.copyPixelsFromBuffer(buffer)
      val cropped = Bitmap.createBitmap(bmp, 0, 0, image.width, image.height)
      bmp.recycle()

      val isBlack = isMostlyBlack(cropped)

      // Keep candles legible to Gemini. Phones today are 1080–1440px wide;
      // capping at 1920 gives better OCR than 1280 without ballooning upload.
      val maxLong = 1920
      val longSide = max(cropped.width, cropped.height)
      val scaled =
        if (longSide > maxLong) {
          val ratio = maxLong.toFloat() / longSide
          val nw = (cropped.width * ratio).toInt()
          val nh = (cropped.height * ratio).toInt()
          val s = Bitmap.createScaledBitmap(cropped, nw, nh, true)
          cropped.recycle()
          s
        } else cropped

      val out = ByteArrayOutputStream()
      scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
      val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
      val w = scaled.width
      val h = scaled.height
      scaled.recycle()
      CaptureResult(b64, w, h, isBlack)
    } catch (e: Exception) {
      null
    } finally {
      image.close()
    }
  }

  private fun isMostlyBlack(bitmap: Bitmap): Boolean {
    val step = max(1, bitmap.width / 20)
    val stepY = max(1, bitmap.height / 20)
    var darkCount = 0
    var total = 0
    var x = 0
    while (x < bitmap.width) {
      var y = 0
      while (y < bitmap.height) {
        val px = bitmap.getPixel(x, y)
        val r = (px shr 16) and 0xFF
        val g = (px shr 8) and 0xFF
        val b = px and 0xFF
        if (r < 12 && g < 12 && b < 12) darkCount++
        total++
        y += stepY
      }
      x += step
    }
    return total > 0 && darkCount.toFloat() / total > 0.985f
  }

  private fun max(a: Int, b: Int) = if (a > b) a else b

  private fun teardownDisplay() {
    try { virtualDisplay?.release() } catch (_: Exception) {}
    try { imageReader?.close() } catch (_: Exception) {}
    virtualDisplay = null
    imageReader = null
  }

  override fun onDestroy() {
    teardownDisplay()
    try { projection?.stop() } catch (_: Exception) {}
    projection = null
    handlerThread?.quitSafely()
    handlerThread = null
    handler = null
    instance = null
    super.onDestroy()
  }

  data class CaptureResult(
    val base64: String,
    val width: Int,
    val height: Int,
    val isBlack: Boolean,
  )
}
