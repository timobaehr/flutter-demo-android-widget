package com.example.flutter_app_android_widget

import androidx.annotation.NonNull
import com.example.immowizard.WidgetHelper
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugins.GeneratedPluginRegistrant

class MainActivity: FlutterActivity(), MethodChannel.MethodCallHandler {
    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        GeneratedPluginRegistrant.registerWith(flutterEngine)

        val channel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, WidgetHelper.CHANNEL)
        channel.setMethodCallHandler(this)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "initialize" -> {
                if (call.arguments == null) return
                WidgetHelper.setHandle(this, call.arguments as Long)
            }
        }
    }
}
