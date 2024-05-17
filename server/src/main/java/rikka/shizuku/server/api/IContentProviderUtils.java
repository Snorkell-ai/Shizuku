package rikka.shizuku.server.api;

import android.content.AttributionSource;
import android.content.IContentProvider;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import rikka.shizuku.server.util.OsUtils;

public class IContentProviderUtils {

    /**
     * Calls the specified method on the given content provider, with compatibility handling for different Android versions.
     *
     * @param provider   The content provider to call the method on.
     * @param callingPkg The package name of the calling application.
     * @param authority  The authority of the content provider.
     * @param method     The method to call on the content provider.
     * @param arg        The argument to pass to the method.
     * @param extras     Additional extras to include in the call.
     * @return The result bundle returned by the content provider.
     * @throws RemoteException If an error occurs while communicating with the content provider.
     */
    public static Bundle callCompat(@NonNull IContentProvider provider, @Nullable String callingPkg, @Nullable String authority, @Nullable String method, @Nullable String arg, @Nullable Bundle extras) throws RemoteException {
        Bundle result;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            result = provider.call((new AttributionSource.Builder(OsUtils.getUid())).setPackageName(callingPkg).build(), authority, method, arg, extras);
        } else if (Build.VERSION.SDK_INT >= 30) {
            result = provider.call(callingPkg, (String) null, authority, method, arg, extras);
        } else if (Build.VERSION.SDK_INT >= 29) {
            result = provider.call(callingPkg, authority, method, arg, extras);
        } else {
            result = provider.call(callingPkg, method, arg, extras);
        }

        return result;
    }
}
