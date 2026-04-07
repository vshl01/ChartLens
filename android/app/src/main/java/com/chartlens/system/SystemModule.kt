package com.chartlens.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

class SystemModule(reactContext: ReactApplicationContext) :
  ReactContextBaseJavaModule(reactContext) {

  override fun getName(): String = "SystemModule"

  private fun launch(intent: Intent, promise: Promise) {
    try {
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      reactApplicationContext.startActivity(intent)
      promise.resolve(null)
    } catch (e: Exception) {
      promise.reject("ERR_LAUNCH", e)
    }
  }

  @ReactMethod
  fun openOverlaySettings(promise: Promise) {
    val intent = Intent(
      Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
      Uri.parse("package:${reactApplicationContext.packageName}"),
    )
    launch(intent, promise)
  }

  @ReactMethod
  fun openBatteryOptimizationSettings(promise: Promise) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
      data = Uri.parse("package:${reactApplicationContext.packageName}")
    }
    launch(intent, promise)
  }

  @ReactMethod
  fun openNotificationSettings(promise: Promise) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
      putExtra(Settings.EXTRA_APP_PACKAGE, reactApplicationContext.packageName)
    }
    launch(intent, promise)
  }

  @ReactMethod
  fun isIgnoringBatteryOptimizations(promise: Promise) {
    val pm = reactApplicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    promise.resolve(pm.isIgnoringBatteryOptimizations(reactApplicationContext.packageName))
  }
}
