package com.chartlens

import com.chartlens.apps.AppLauncherModule
import com.chartlens.mediaprojection.MediaProjectionModule
import com.chartlens.overlay.OverlayModule
import com.chartlens.system.SystemModule
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager

class ChartLensPackage : ReactPackage {
  override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> =
    listOf(
      AppLauncherModule(reactContext),
      OverlayModule(reactContext),
      MediaProjectionModule(reactContext),
      SystemModule(reactContext),
    )

  override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> =
    emptyList()
}
