package com.tahoshi.cloudstream_jvm.core

import com.tahoshi.cloudstream_jvm.core.utils.DataStore

/* TODO: Bir şeyleri reportlamaya gerek yok
class CustomReportSender : ReportSender {
    // Sends all your crashes to google forms
    override fun send(context: Context, errorContent: CrashReportData) {
        /*println("Sending report")
        val url =
            "https://docs.google.com/forms/d/e/$id/formResponse"
        val data = mapOf(
            "entry.$entry" to errorContent.toJSON()
        )

        thread { // to not run it on main thread
            runBlocking {
                suspendSafeApiCall {
                    app.post(url, data = data)
                    //println("Report response: $post")
                }
            }
        }

        runOnMainThread { // to run it on main looper
            normalSafeApiCall {
                Toast.makeText(context, R.string.acra_report_toast, Toast.LENGTH_SHORT).show()
            }
        }*/
    }
}

 */

/* TODO: Reportlamaya ihtiyacımız yok
class CustomSenderFactory : ReportSenderFactory {
    override fun create(context: Context, config: CoreConfiguration): ReportSender {
        return CustomReportSender()
    }

    override fun enabled(config: CoreConfiguration): Boolean {
        return true
    }
}

class ExceptionHandler(val errorFile: File, val onError: (() -> Unit)) :
    Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, error: Throwable) {
        ACRA.errorReporter.handleException(error)
        try {
            PrintStream(errorFile).use { ps ->
                ps.println("Currently loading extension: ${PluginManager.currentlyLoading ?: "none"}")
                ps.println("Fatal exception on thread ${thread.name} (${thread.id})")
                error.printStackTrace(ps)
            }
        } catch (ignored: FileNotFoundException) {
        }
        try {
            onError.invoke()
        } catch (ignored: Exception) {
        }
        exitProcess(1)
    }

}

 */

class AcraApplication // TODO:Application ve coil android spesifik : Application(), SingletonImageLoader.Factory
{

    /* TODO:Acraya ve coile ihtiyacımız yok
    override fun onCreate() {
        super.onCreate()
        // if we want to initialise coil at earliest
        // (maybe when loading an image or gif using in splash screen activity)
        //ImageLoader.buildImageLoader(applicationContext)

        ExceptionHandler(filesDir.resolve("last_error")) {
            val intent = context!!.packageManager.getLaunchIntentForPackage(context!!.packageName)
            startActivity(Intent.makeRestartActivityTask(intent!!.component))
        }.also {
            exceptionHandler = it
            Thread.setDefaultUncaughtExceptionHandler(it)
        }
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        context = base

        initAcra {
            //core configuration:
            buildConfigClass = BuildConfig::class.java
            reportFormat = StringFormat.JSON

            reportContent = listOf(
                ReportField.BUILD_CONFIG, ReportField.USER_CRASH_DATE,
                ReportField.ANDROID_VERSION, ReportField.PHONE_MODEL,
                ReportField.STACK_TRACE,
            )

            // removed this due to bug when starting the app, moved it to when it actually crashes
            //each plugin you chose above can be configured in a block like this:
            /*toast {
                text = getString(R.string.acra_report_toast)
                //opening this block automatically enables the plugin.
            }*/
        }
    }

    override fun newImageLoader(context: PlatformContext): coil3.ImageLoader {
        // Coil Module will be initialized & setSafe globally when first loadImage() is invoked
        return ImageLoader.buildImageLoader(applicationContext)
    }

     */

    companion object {
        /*TODO: ExceptionHandler, context, activity, bunlara ihtiyacımız yok
        var exceptionHandler: ExceptionHandler? = null

        /** Use to get activity from Context */
        tailrec fun Context.getActivity(): Activity? = this as? Activity
            ?: (this as? ContextWrapper)?.baseContext?.getActivity()

        private var _context: WeakReference<Context>? = null
        var context
            get() = _context?.get()
            private set(value) {
                _context = WeakReference(value)
                setContext(WeakReference(value))
            }

         */

        fun <T : Any> getKeyClass(path: String, valueType: Class<T>): T? {
            return DataStore.getKey(path, valueType)
        }

        fun <T : Any> setKeyClass(path: String, value: T) {
            DataStore.setKey(path, value)
        }

        fun removeKeys(folder: String): Int? {
            return DataStore.removeKeys(folder)
        }

        fun <T> setKey(path: String, value: T) {
            DataStore.setKey(path, value)
        }

        fun <T> setKey(folder: String, path: String, value: T) {
            DataStore.setKey(folder, path, value)
        }

        inline fun <reified T : Any> getKey(path: String, defVal: T?): T? {
            return DataStore.getKey(path, defVal)
        }

        inline fun <reified T : Any> getKey(path: String): T? {
            return DataStore.getKey(path)
        }

        inline fun <reified T : Any> getKey(folder: String, path: String): T? {
            return DataStore.getKey(folder, path)
        }

        inline fun <reified T : Any> getKey(folder: String, path: String, defVal: T?): T? {
            return DataStore.getKey(folder, path, defVal)
        }

        fun getKeys(folder: String): List<String>? {
            return DataStore.getKeys(folder)
        }

        fun removeKey(folder: String, path: String) {
            DataStore.removeKey(folder, path)
        }

        fun removeKey(path: String) {
            DataStore.removeKey(path)
        }

        /* TODO: Browser açma tarzında bir şeye ihtiyacımız çok muhtemelen olmayacak jvm-core'da
        /**
         * If fallbackWebview is true and a fragment is supplied then it will open a webview with the url if the browser fails.
         * */
        fun openBrowser(url: String, fallbackWebview: Boolean = false, fragment: Fragment? = null) {
            context?.openBrowser(url, fallbackWebview, fragment)
        }

        /** Will fallback to webview if in TV layout */
        fun openBrowser(url: String, activity: FragmentActivity?) {
            openBrowser(
                url,
                isLayout(TV or EMULATOR),
                activity?.supportFragmentManager?.fragments?.lastOrNull()
            )
        }
         */
    }
}
