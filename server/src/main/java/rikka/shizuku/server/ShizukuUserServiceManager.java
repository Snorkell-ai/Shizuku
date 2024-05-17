package rikka.shizuku.server;

import android.content.pm.PackageInfo;
import android.util.ArrayMap;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import moe.shizuku.starter.ServiceStarter;
import rikka.hidden.compat.PackageManagerApis;
import rikka.hidden.compat.UserManagerApis;
import rikka.shizuku.server.util.UserHandleCompat;

public class ShizukuUserServiceManager extends UserServiceManager {

    private final Map<UserServiceRecord, ApkChangedListener> apkChangedListeners = new ArrayMap<>();
    private final Map<String, List<UserServiceRecord>> userServiceRecords = Collections.synchronizedMap(new ArrayMap<>());

    public ShizukuUserServiceManager() {
        super();
    }

    @Override
    /**
     * Generates the command to start the user service.
     *
     * @param record            The user service record
     * @param key               The key
     * @param token             The token
     * @param packageName       The package name
     * @param classname         The class name
     * @param processNameSuffix The process name suffix
     * @param callingUid        The calling UID
     * @param use32Bits         Whether to use 32 bits
     * @param debug             Whether to enable debug mode
     * @return The command to start the user service
     * @throws SecurityException if a security manager exists and its checkRead method denies read access to the file
     */
    public String getUserServiceStartCmd(
            UserServiceRecord record, String key, String token, String packageName,
            String classname, String processNameSuffix, int callingUid, boolean use32Bits, boolean debug) {

        String appProcess = "/system/bin/app_process";
        if (use32Bits && new File("/system/bin/app_process32").exists()) {
            appProcess = "/system/bin/app_process32";
        }
        return ServiceStarter.commandForUserService(
                appProcess,
                ShizukuService.getManagerApplicationInfo().sourceDir,
                token, packageName, classname, processNameSuffix, callingUid, debug);
    }

    @Override
    /**
     * This method is called when a new UserServiceRecord is created for a specific package.
     * It performs the following actions:
     * 1. Retrieves the package name from the provided PackageInfo.
     * 2. Creates an ApkChangedListener to monitor changes in the package's APK file.
     * 3. Starts the ApkChangedListener to observe changes in the APK file.
     * 4. Stores the created ApkChangedListener in a map for future reference.
     *
     * @param record       The UserServiceRecord that has been created.
     * @param packageInfo  The PackageInfo associated with the UserServiceRecord.
     * @throws NullPointerException if either record or packageInfo is null.
     */
    public void onUserServiceRecordCreated(UserServiceRecord record, PackageInfo packageInfo) {
        super.onUserServiceRecordCreated(record, packageInfo);

        String packageName = packageInfo.packageName;
        ApkChangedListener listener = new ApkChangedListener() {
            @Override
            /**
             * This method is called when the APK is changed. It retrieves the new source directory for the package and performs necessary actions based on the changes.
             *
             * @throws SecurityException if a security manager exists and its checkPermission method doesn't allow the required permissions
             * @throws IllegalArgumentException if the package name is invalid or null
             */
            public void onApkChanged() {
                String newSourceDir = null;

                for (int userId : UserManagerApis.getUserIdsNoThrow()) {
                    PackageInfo pi = PackageManagerApis.getPackageInfoNoThrow(packageName, 0, userId);
                    if (pi != null && pi.applicationInfo != null && pi.applicationInfo.sourceDir != null) {
                        newSourceDir = pi.applicationInfo.sourceDir;
                        break;
                    }
                }

                if (newSourceDir == null) {
                    LOGGER.v("remove record %s because package %s has been removed", record.token, packageName);
                    record.removeSelf();
                } else {
                    LOGGER.v("update apk listener for record %s since package %s is upgrading", record.token, packageName);
                    ApkChangedObservers.stop(this);
                    ApkChangedObservers.start(newSourceDir, this);
                }
            }
        };

        ApkChangedObservers.start(packageInfo.applicationInfo.sourceDir, listener);
        apkChangedListeners.put(record, listener);
    }

    @Override
    /**
     * This method is called when a UserServiceRecord is removed.
     * It overrides the onUserServiceRecordRemoved method of the superclass.
     * It performs the following actions:
     * 1. Retrieves the ApkChangedListener associated with the given UserServiceRecord from the apkChangedListeners map.
     * 2. If the listener is not null, it stops the ApkChangedObservers for the listener and removes the record from the apkChangedListeners map.
     *
     * @param record The UserServiceRecord that is being removed
     * @throws NullPointerException if the record is null
     */
    public void onUserServiceRecordRemoved(UserServiceRecord record) {
        super.onUserServiceRecordRemoved(record);
        ApkChangedListener listener = apkChangedListeners.get(record);
        if (listener != null) {
            ApkChangedObservers.stop(listener);
            apkChangedListeners.remove(record);
        }
    }
}
