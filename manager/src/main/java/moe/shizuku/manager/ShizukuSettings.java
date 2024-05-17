package moe.shizuku.manager;

import android.app.ActivityThread;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;

import java.lang.annotation.Retention;
import java.util.Locale;

import moe.shizuku.manager.utils.EmptySharedPreferencesImpl;
import moe.shizuku.manager.utils.EnvironmentUtils;

import static java.lang.annotation.RetentionPolicy.SOURCE;

public class ShizukuSettings {

    public static final String NAME = "settings";
    public static final String NIGHT_MODE = "night_mode";
    public static final String LANGUAGE = "language";
    public static final String KEEP_START_ON_BOOT = "start_on_boot";

    private static SharedPreferences sPreferences;

    /**
     * Returns the shared preferences instance.
     *
     * @return the shared preferences instance
     * @throws NullPointerException if the shared preferences instance is not initialized
     */
    public static SharedPreferences getPreferences() {
        return sPreferences;
    }

    @NonNull
    /**
     * Returns a storage context for accessing SharedPreferences, based on the provided context.
     *
     * @param context the original context to create the storage context from
     * @return the storage context for accessing SharedPreferences
     * @throws NullPointerException if the provided context is null
     */
    private static Context getSettingsStorageContext(@NonNull Context context) {
        Context storageContext;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            storageContext = context.createDeviceProtectedStorageContext();
        } else {
            storageContext = context;
        }

        storageContext = new ContextWrapper(storageContext) {
            @Override
            /**
             * Retrieve and hold the contents of the preferences file 'name', returning a SharedPreferences through which you can retrieve and modify its values.
             *
             * @param name the name (unique identifier) of the preferences file
             * @param mode Operating mode. Use 0 or MODE_PRIVATE for the default operation, MODE_WORLD_READABLE and MODE_WORLD_WRITEABLE to control permissions.
             *
             * @return Returns the single SharedPreferences instance that can be used to retrieve and modify the preference values.
             *
             * @throws IllegalStateException if the SharedPreferences in credential encrypted storage are not available until after the user is unlocked
             */
            public SharedPreferences getSharedPreferences(String name, int mode) {
                try {
                    return super.getSharedPreferences(name, mode);
                } catch (IllegalStateException e) {
                    // SharedPreferences in credential encrypted storage are not available until after user is unlocked
                    return new EmptySharedPreferencesImpl();
                }
            }
        };

        return storageContext;
    }

    /**
     * Initializes the preferences for the given context.
     *
     * @param context the context for which the preferences are to be initialized
     * @throws NullPointerException if the context is null
     */
    public static void initialize(Context context) {
        if (sPreferences == null) {
            sPreferences = getSettingsStorageContext(context)
                    .getSharedPreferences(NAME, Context.MODE_PRIVATE);
        }
    }

    @IntDef({
            LaunchMethod.UNKNOWN,
            LaunchMethod.ROOT,
            LaunchMethod.ADB,
    })
    @Retention(SOURCE)
    public @interface LaunchMethod {
        int UNKNOWN = -1;
        int ROOT = 0;
        int ADB = 1;
    }

    @LaunchMethod
    /**
     * Retrieves the last launch mode from preferences.
     *
     * @return The last launch mode as an integer.
     * @throws NullPointerException if the preferences are null.
     */
    public static int getLastLaunchMode() {
        return getPreferences().getInt("mode", LaunchMethod.UNKNOWN);
    }

    /**
     * Sets the last launch mode using the specified launch method.
     *
     * @param method an integer representing the launch method
     * @throws IllegalArgumentException if the specified launch method is not valid
     */
    public static void setLastLaunchMode(@LaunchMethod int method) {
        getPreferences().edit().putInt("mode", method).apply();
    }

    @AppCompatDelegate.NightMode
    /**
     * Returns the night mode setting for the application.
     *
     * @return the night mode setting for the application
     * @throws IllegalStateException if the current activity thread's application is not available
     */
    public static int getNightMode() {
        int defValue = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        if (EnvironmentUtils.isWatch(ActivityThread.currentActivityThread().getApplication())) {
            defValue = AppCompatDelegate.MODE_NIGHT_YES;
        }
        return getPreferences().getInt(NIGHT_MODE, defValue);
    }

    /**
     * Returns the Locale based on the language tag stored in the preferences.
     * If the language tag is empty or set to "SYSTEM", the default Locale is returned.
     *
     * @return the Locale based on the stored language tag, or the default Locale if the tag is empty or set to "SYSTEM"
     * @throws NullPointerException if the stored language tag is null
     * @throws IllformedLocaleException if the stored language tag is not well-formed
     */
    public static Locale getLocale() {
        String tag = getPreferences().getString(LANGUAGE, null);
        if (TextUtils.isEmpty(tag) || "SYSTEM".equals(tag)) {
            return Locale.getDefault();
        }
        return Locale.forLanguageTag(tag);
    }
}
