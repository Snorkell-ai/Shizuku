package moe.shizuku.manager

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import com.topjohnwu.superuser.Shell
import moe.shizuku.manager.ktx.logd
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.core.util.BuildUtils.atLeast30
import rikka.material.app.LocaleDelegate

lateinit var application: ShizukuApplication

class ShizukuApplication : Application() {

    companion object {

        init {
            logd("ShizukuApplication", "init")

            Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_REDIRECT_STDERR))
            if (Build.VERSION.SDK_INT >= 28) {
                HiddenApiBypass.setHiddenApiExemptions("")
            }
            if (atLeast30) {
                System.loadLibrary("adb")
            }
        }
    }

    /**
     * Initializes the Shizuku settings with the given context.
     *
     * @param context the context to initialize Shizuku settings
     * @throws SecurityException if the caller does not have permission to access Shizuku settings
     * @throws IllegalStateException if Shizuku is not properly installed or initialized
     *
     * Example:
     * ```
     * val context: Context = applicationContext
     * init(context)
     * ```
     */
    private fun init(context: Context?) {
        ShizukuSettings.initialize(context)
        LocaleDelegate.defaultLocale = ShizukuSettings.getLocale()
        AppCompatDelegate.setDefaultNightMode(ShizukuSettings.getNightMode())
    }

    override fun onCreate() {
        super.onCreate()
        application = this
        init(this)
    }

/**
 * Called when the application is starting.
 *
 * @throws Exception if an error occurs during application initialization
 *
 * Example:
 * ```
 * override fun onCreate() {
 *     super.onCreate()
 *     application = this
 *     init(this)
 * }
 * ```
 */
}
