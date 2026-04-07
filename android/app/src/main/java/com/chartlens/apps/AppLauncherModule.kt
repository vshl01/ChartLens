package com.chartlens.apps

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.util.Base64
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import java.io.ByteArrayOutputStream

class AppLauncherModule(reactContext: ReactApplicationContext) :
  ReactContextBaseJavaModule(reactContext) {

  override fun getName(): String = "AppLauncherModule"

  private fun pm(): PackageManager = reactApplicationContext.packageManager

  private fun drawableToBase64(drawable: Drawable, maxSize: Int = 96): String {
    val bmp =
      if (drawable is BitmapDrawable && drawable.bitmap != null) {
        Bitmap.createScaledBitmap(drawable.bitmap, maxSize, maxSize, true)
      } else {
        val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else maxSize
        val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else maxSize
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        Bitmap.createScaledBitmap(bitmap, maxSize, maxSize, true)
      }
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
  }

  @ReactMethod
  fun isAppInstalled(packageName: String, promise: Promise) {
    try {
      pm().getPackageInfo(packageName, 0)
      promise.resolve(true)
    } catch (_: PackageManager.NameNotFoundException) {
      promise.resolve(false)
    } catch (e: Exception) {
      promise.reject("ERR_QUERY", e)
    }
  }

  @ReactMethod
  fun getAppIcon(packageName: String, promise: Promise) {
    try {
      val icon = pm().getApplicationIcon(packageName)
      promise.resolve(drawableToBase64(icon))
    } catch (_: PackageManager.NameNotFoundException) {
      promise.resolve(null)
    } catch (e: Exception) {
      promise.reject("ERR_ICON", e)
    }
  }

  @ReactMethod
  fun launchApp(packageName: String, deepLink: String?, promise: Promise) {
    try {
      val intent: Intent? =
        if (!deepLink.isNullOrBlank()) {
          Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).apply {
            setPackage(packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
        } else {
          pm().getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
        }
      if (intent == null) {
        promise.resolve(false)
        return
      }
      reactApplicationContext.startActivity(intent)
      promise.resolve(true)
    } catch (e: Exception) {
      promise.reject("ERR_LAUNCH", e)
    }
  }

  @ReactMethod
  fun openAppSettings(packageName: String, promise: Promise) {
    try {
      val intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
          data = Uri.fromParts("package", packageName, null)
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      reactApplicationContext.startActivity(intent)
      promise.resolve(null)
    } catch (e: Exception) {
      promise.reject("ERR_SETTINGS", e)
    }
  }

  @ReactMethod
  fun getInstalledApps(promise: Promise) {
    try {
      val packages = pm().getInstalledApplications(PackageManager.GET_META_DATA)
      val arr: WritableArray = Arguments.createArray()
      for (info in packages) {
        if ((info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
          (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
        ) continue
        val map: WritableMap = Arguments.createMap()
        map.putString("packageName", info.packageName)
        map.putString("name", pm().getApplicationLabel(info).toString())
        map.putString("iconBase64", drawableToBase64(pm().getApplicationIcon(info)))
        arr.pushMap(map)
      }
      promise.resolve(arr)
    } catch (e: Exception) {
      promise.reject("ERR_LIST", e)
    }
  }
}
