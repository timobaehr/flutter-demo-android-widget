# flutter_app_android_widget

A plain Flutter application with Android widget.

## Getting Started

This project is a starting point for a Flutter application.

A few resources to get you started if this is your first Flutter project:

- [Lab: Write your first Flutter app](https://flutter.dev/docs/get-started/codelab)
- [Cookbook: Useful Flutter samples](https://flutter.dev/docs/cookbook)

For help getting started with Flutter, view our
[online documentation](https://flutter.dev/docs), which offers tutorials,
samples, guidance on mobile development, and a full API reference.

## Flutter Android communication

### Dart setup

Go to `main.dart` and add the following top-level function:

~~~dart
void initializeAndroidWidgets() {
  if (Platform.isAndroid) {
    // Initialize flutter
    WidgetsFlutterBinding.ensureInitialized();

    const MethodChannel channel = MethodChannel('com.example.app/widget');

    final CallbackHandle callback = PluginUtilities.getCallbackHandle(onWidgetUpdate);
    final handle = callback.toRawHandle();

    channel.invokeMethod('initialize', handle);
  }
}
~~~

then call this function before running your app

~~~dart
void main() {
  initializeAndroidWidgets();
  runApp(MyApp());
}
~~~

this will ensure that we can get a callback handle on the native side for our entry point. 

Now add an entry point like so:

~~~dart
void onWidgetUpdate() {
  // Initialize flutter
  WidgetsFlutterBinding.ensureInitialized();

  const MethodChannel channel = MethodChannel('com.example.app/widget');

  // If you use dependency injection you will need to inject
  // your objects before using them.

  channel.setMethodCallHandler(
    (call) async {
      final id = call.arguments;

      print('on Dart ${call.method}!');

      // Do your stuff here...
      final result = Random().nextDouble();

      return {
        // Pass back the id of the widget so we can update it later
        'id': id,
        // Some data of type double
        'value': result,
      };
    },
  );
}
~~~

This function will be the entry point for our widgets and gets called when our widgets `onUpdate` method is called. We can then pass back some data (for example after calling an api).

### Android setup

The samples here are in Kotlin but should work with some minor adjustments also in Java.

Create a `WidgetHelper` class that will help us in storing and getting a handle to our entry point:

~~~kotlin
class WidgetHelper {
    companion object  {
        private const val WIDGET_PREFERENCES_KEY = "widget_preferences"
        private const val WIDGET_HANDLE_KEY = "handle"

        const val CHANNEL = "com.example.app/widget"
        const val NO_HANDLE = -1L

        fun setHandle(context: Context, handle: Long) {
            context.getSharedPreferences(
                WIDGET_PREFERENCES_KEY,
                Context.MODE_PRIVATE
            ).edit().apply {
                putLong(WIDGET_HANDLE_KEY, handle)
                apply()
            }
        }

        fun getRawHandle(context: Context): Long {
            return context.getSharedPreferences(
                WIDGET_PREFERENCES_KEY,
                Context.MODE_PRIVATE
            ).getLong(WIDGET_HANDLE_KEY, NO_HANDLE)
        }
    }
}
~~~

Replace your `MainActivity` with this:

~~~kotlin
class MainActivity : FlutterActivity(), MethodChannel.MethodCallHandler {
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
~~~

This will simply ensure that we store the handle (the hash of the entry point) to `SharedPreferences` to be able to retrieve it later in the widget.

Now modify your `AppWidgetProvider` to look something similar to this:

~~~kotlin
class CustomAppWidgetProvider : AppWidgetProvider(), MethodChannel.Result {

    private val TAG = this::class.java.simpleName

    companion object {
        private var channel: MethodChannel? = null;
    }

    private lateinit var context: Context

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        this.context = context

        initializeFlutter()

        for (appWidgetId in appWidgetIds) {
            updateWidget("onUpdate ${Math.random()}", appWidgetId, context)
            // Pass over the id so we can update it later...
            channel?.invokeMethod("update", appWidgetId, this)
        }
    }

    private fun initializeFlutter() {
        if (channel == null) {
            FlutterMain.startInitialization(context)
            FlutterMain.ensureInitializationComplete(context, arrayOf())

            val handle = WidgetHelper.getRawHandle(context)
            if (handle == WidgetHelper.NO_HANDLE) {
                Log.w(TAG, "Couldn't update widget because there is no handle stored!")
                return
            }

            val callbackInfo = FlutterCallbackInformation.lookupCallbackInformation(handle)
            // You could also use a hard coded value to save you from all
            // the hassle with SharedPreferences, but alas when running your
            // app in release mode this would fail.
            val entryPointFunctionName = callbackInfo.callbackName

            // Instantiate a FlutterEngine.
            val engine = FlutterEngine(context.applicationContext)
            val entryPoint = DartEntrypoint(FlutterMain.findAppBundlePath(), entryPointFunctionName)
            engine.dartExecutor.executeDartEntrypoint(entryPoint)

            // Register Plugins when in background. When there 
            // is already an engine running, this will be ignored (although there will be some
            // warnings in the log).
            GeneratedPluginRegistrant.registerWith(engine)

            channel = MethodChannel(engine.dartExecutor.binaryMessenger, WidgetHelper.CHANNEL)
        }
    }

    override fun success(result: Any?) {
        Log.d(TAG, "success $result")

        val args = result as HashMap<*, *>
        val id = args["id"] as Int
        val value = args["value"] as Double

        updateWidget("onDart $value", id, context)
    }

    override fun notImplemented() {
        Log.d(TAG, "notImplemented")
    }

    override fun error(errorCode: String?, errorMessage: String?, errorDetails: Any?) {
        Log.d(TAG, "onError $errorCode")
    }

    override fun onDisabled(context: Context?) {
        super.onDisabled(context)
        channel = null
    }
}

internal fun updateWidget(text: String, id: Int, context: Context) {
    val views = RemoteViews(context.packageName, R.layout.small_widget).apply {
        setTextViewText(R.id.appwidget_text, text)
    }

    val manager = AppWidgetManager.getInstance(context)
    manager.updateAppWidget(id, views)
}
~~~

The important thing here is `initializeFlutter` that will make sure we can get a handle to our entry point. In `onUpdate` we are then calling `channel?.invokeMethod("update", appWidgetId, this)` that will trigger the callback in our `MethodChannel` on the dart side defined earlier. Then we handle the result later in `success` (at least when the call is successful).