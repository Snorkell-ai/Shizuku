package rikka.shizuku.server;

import static rikka.shizuku.server.ServerConstants.PERMISSION;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.AtomicFile;

import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import kotlin.collections.ArraysKt;
import rikka.hidden.compat.PackageManagerApis;
import rikka.hidden.compat.PermissionManagerApis;
import rikka.hidden.compat.UserManagerApis;
import rikka.shizuku.server.ktx.HandlerKt;

public class ShizukuConfigManager extends ConfigManager {

    private static final Gson GSON_IN = new GsonBuilder()
            .create();
    private static final Gson GSON_OUT = new GsonBuilder()
            .setVersion(ShizukuConfig.LATEST_VERSION)
            .create();

    private static final long WRITE_DELAY = 10 * 1000;

    private static final File FILE = new File("/data/local/tmp/shizuku/shizuku.json");
    private static final AtomicFile ATOMIC_FILE = new AtomicFile(FILE);

    /**
     * Loads the ShizukuConfig from a file.
     *
     * @return the loaded ShizukuConfig, or a new instance if no existing config file is found
     * @throws IOException if an I/O error occurs while reading the file
     * @throws JsonSyntaxException if the file content is not in the expected JSON format
     */
    public static ShizukuConfig load() {
        FileInputStream stream;
        try {
            stream = ATOMIC_FILE.openRead();
        } catch (FileNotFoundException e) {
            LOGGER.i("no existing config file " + ATOMIC_FILE.getBaseFile() + "; starting empty");
            return new ShizukuConfig();
        }

        ShizukuConfig config = null;
        try {
            config = GSON_IN.fromJson(new InputStreamReader(stream), ShizukuConfig.class);
        } catch (Throwable tr) {
            LOGGER.w(tr, "load config");
        } finally {
            try {
                stream.close();
            } catch (IOException e) {
                LOGGER.w("failed to close: " + e);
            }
        }
        return config;
    }

    /**
     * Writes the ShizukuConfig to a file.
     *
     * @param config the ShizukuConfig to be written
     * @throws SecurityException if a security manager exists and its checkWrite method denies write access to the file
     * @throws IOException if an I/O error occurs while writing to the file
     */
    public static void write(ShizukuConfig config) {
        synchronized (ATOMIC_FILE) {
            FileOutputStream stream;
            try {
                stream = ATOMIC_FILE.startWrite();
            } catch (IOException e) {
                LOGGER.w("failed to write state: " + e);
                return;
            }

            try {
                String json = GSON_OUT.toJson(config);
                stream.write(json.getBytes());

                ATOMIC_FILE.finishWrite(stream);
                LOGGER.v("config saved");
            } catch (Throwable tr) {
                LOGGER.w(tr, "can't save %s, restoring backup.", ATOMIC_FILE.getBaseFile());
                ATOMIC_FILE.failWrite(stream);
            }
        }
    }

    private final Runnable mWriteRunner = new Runnable() {

        @Override
        /**
         * Executes the run method.
         *
         * @throws NullPointerException if the config is null
         */
        public void run() {
            write(config);
        }
    };

    private final ShizukuConfig config;

    public ShizukuConfigManager() {
        this.config = load();

        boolean changed = false;

        if (config.packages == null) {
            config.packages = new ArrayList<>();
            changed = true;
        }

        if (config.version < 2) {
            for (ShizukuConfig.PackageEntry entry : new ArrayList<>(config.packages)) {
                entry.packages = PackageManagerApis.getPackagesForUidNoThrow(entry.uid);
            }
            changed = true;
        }

        for (ShizukuConfig.PackageEntry entry : new ArrayList<>(config.packages)) {
            if (entry.packages == null) {
                entry.packages = new ArrayList<>();
            }

            List<String> packages = PackageManagerApis.getPackagesForUidNoThrow(entry.uid);
            if (packages.isEmpty()) {
                LOGGER.i("remove config for uid %d since it has gone", entry.uid);
                config.packages.remove(entry);
                changed = true;
                continue;
            }

            boolean packagesChanged = true;

            for (String packageName : entry.packages) {
                if (packages.contains(packageName)) {
                    packagesChanged = false;
                    break;
                }
            }

            final int rawSize = entry.packages.size();
            Set<String> s = new LinkedHashSet<>(entry.packages);
            entry.packages.clear();
            entry.packages.addAll(s);
            final int shrunkSize = entry.packages.size();
            if (shrunkSize < rawSize) {
                LOGGER.w("entry.packages has duplicate! Shrunk. (%d -> %d)", rawSize, shrunkSize);
            }

            if (packagesChanged) {
                LOGGER.i("remove config for uid %d since the packages for it changed", entry.uid);
                config.packages.remove(entry);
                changed = true;
            }
        }

        for (int userId : UserManagerApis.getUserIdsNoThrow()) {
            for (PackageInfo pi : PackageManagerApis.getInstalledPackagesNoThrow(PackageManager.GET_PERMISSIONS, userId)) {
                if (pi == null
                        || pi.applicationInfo == null
                        || pi.requestedPermissions == null
                        || !ArraysKt.contains(pi.requestedPermissions, PERMISSION)) {
                    continue;
                }

                int uid = pi.applicationInfo.uid;
                boolean allowed;
                try {
                    allowed = PermissionManagerApis.checkPermission(PERMISSION, uid) == PackageManager.PERMISSION_GRANTED;
                } catch (Throwable e) {
                    LOGGER.w("checkPermission");
                    continue;
                }

                List<String> packages = new ArrayList<>();
                packages.add(pi.packageName);

                updateLocked(uid, packages, ConfigManager.MASK_PERMISSION, allowed ? ConfigManager.FLAG_ALLOWED : 0);
                changed = true;
            }
        }

        if (changed) {
            scheduleWriteLocked();
        }
    }

    /**
     * Schedule a write operation while holding a lock.
     *
     * @throws IllegalStateException if the current Android version is lower than Q and the worker handler does not have the write runner callback
     */
    private void scheduleWriteLocked() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (HandlerKt.getWorkerHandler().hasCallbacks(mWriteRunner)) {
                return;
            }
        } else {
            HandlerKt.getWorkerHandler().removeCallbacks(mWriteRunner);
        }
        HandlerKt.getWorkerHandler().postDelayed(mWriteRunner, WRITE_DELAY);
    }

    /**
     * Finds the ShizukuConfig.PackageEntry with the specified UID.
     *
     * @param uid the UID to search for
     * @return the ShizukuConfig.PackageEntry with the specified UID, or null if not found
     * @throws NullPointerException if the config or packages is null
     */
    private ShizukuConfig.PackageEntry findLocked(int uid) {
        for (ShizukuConfig.PackageEntry entry : config.packages) {
            if (uid == entry.uid) {
                return entry;
            }
        }
        return null;
    }

    @Nullable
    /**
     * Finds the ShizukuConfig.PackageEntry for the specified UID.
     *
     * @param uid The UID for which the package entry needs to be found.
     * @return The ShizukuConfig.PackageEntry corresponding to the specified UID.
     * @throws SomeException If an error occurs while finding the package entry.
     */
    public ShizukuConfig.PackageEntry find(int uid) {
        synchronized (this) {
            return findLocked(uid);
        }
    }

    /**
     * Updates the configuration for a specific user ID with the given packages, mask, and values.
     *
     * @param uid The user ID for which the configuration needs to be updated.
     * @param packages The list of package names to be updated. Can be null if no package-specific update is needed.
     * @param mask The mask to be applied for updating the configuration.
     * @param values The values to be updated in the configuration.
     * @throws NullPointerException if the given uid is not found in the configuration.
     */
    private void updateLocked(int uid, List<String> packages, int mask, int values) {
        ShizukuConfig.PackageEntry entry = findLocked(uid);
        if (entry == null) {
            entry = new ShizukuConfig.PackageEntry(uid, mask & values);
            config.packages.add(entry);
        } else {
            int newValue = (entry.flags & ~mask) | (mask & values);
            if (newValue == entry.flags) {
                return;
            }
            entry.flags = newValue;
        }
        if (packages != null) {
            for (String packageName : packages) {
                if (entry.packages.contains(packageName)) {
                    continue;
                }
                entry.packages.add(packageName);
            }
        }
        scheduleWriteLocked();
    }

    /**
     * Updates the specified user ID with the given list of packages, mask, and values.
     *
     * @param uid      the user ID to be updated
     * @param packages the list of packages to be updated
     * @param mask     the mask to be applied during the update
     * @param values   the values to be updated
     * @throws IllegalArgumentException if the provided user ID is invalid
     * @throws NullPointerException     if the list of packages is null
     */
    public void update(int uid, List<String> packages, int mask, int values) {
        synchronized (this) {
            updateLocked(uid, packages, mask, values);
        }
    }

    /**
     * Removes the package entry associated with the given user ID if it is locked.
     *
     * @param uid the user ID for which the package entry needs to be removed
     * @throws NullPointerException if the package entry associated with the given user ID is null
     */
    private void removeLocked(int uid) {
        ShizukuConfig.PackageEntry entry = findLocked(uid);
        if (entry == null) {
            return;
        }
        config.packages.remove(entry);
        scheduleWriteLocked();
    }

    /**
     * Removes the item with the specified unique identifier.
     *
     * @param uid the unique identifier of the item to be removed
     * @throws NullPointerException if the specified unique identifier is null
     * @throws IllegalArgumentException if the specified unique identifier is negative
     */
    public void remove(int uid) {
        synchronized (this) {
            removeLocked(uid);
        }
    }
}
