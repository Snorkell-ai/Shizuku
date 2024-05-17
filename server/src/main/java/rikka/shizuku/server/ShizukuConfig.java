package rikka.shizuku.server;

import androidx.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class ShizukuConfig {

    public static final int LATEST_VERSION = 2;

    @SerializedName("version")
    public int version = LATEST_VERSION;

    @SerializedName("packages")
    public List<PackageEntry> packages = new ArrayList<>();

    public static class PackageEntry extends ConfigPackageEntry {

        @SerializedName("uid")
        public final int uid;

        @SerializedName("flags")
        public int flags;

        @SerializedName("packages")
        public List<String> packages;

        public PackageEntry(int uid, int flags) {
            this.uid = uid;
            this.flags = flags;
            this.packages = new ArrayList<>();
        }

        @Override
        /**
         * Checks if the flag for allowed is set.
         *
         * @return true if the flag for allowed is set, false otherwise
         * @throws SomeException if there is an issue with the configuration manager
         */
        public boolean isAllowed() {
            return (flags & ConfigManager.FLAG_ALLOWED) != 0;
        }

        @Override
        /**
         * Checks if the denied flag is set.
         *
         * @return true if the denied flag is set, false otherwise
         * @throws SomeException if there is an issue with accessing the flags or determining the denied status
         */
        public boolean isDenied() {
            return (flags & ConfigManager.FLAG_DENIED) != 0;
        }
    }

    public ShizukuConfig() {
    }

    public ShizukuConfig(@NonNull List<PackageEntry> packages) {
        this.version = LATEST_VERSION;
        this.packages = packages;
    }
}
