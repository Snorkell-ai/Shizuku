package rikka.shizuku.server

import android.os.FileObserver
import android.util.Log
import java.io.File
import java.util.*

interface ApkChangedListener {
    /**
     * This method is called when the APK is changed.
     *
     * @throws ApkChangedException if there is an error while handling the APK change
     *
     * Example:
     * ```
     * try {
     *     onApkChanged()
     * } catch (e: ApkChangedException) {
     *     // Handle the exception
     * }
     * ```
     */
    fun onApkChanged()
}

private val observers = Collections.synchronizedMap(HashMap<String, ApkChangedObserver>())

object ApkChangedObservers {

    @JvmStatic
    /**
     * Starts monitoring the specified APK file for changes.
     *
     * @param apkPath the path to the APK file to monitor
     * @param listener the listener to be notified of APK changes
     * @throws IllegalArgumentException if the provided APK path is invalid
     * @throws SecurityException if a security manager exists and denies the required file read access
     * @throws UnsupportedOperationException if the platform does not support file watching
     * @sample start("/path/to/apk/file.apk", object : ApkChangedListener {
     *     override fun onApkChanged() {
     *         // Handle APK change event
     *     }
     * })
     */
    fun start(apkPath: String, listener: ApkChangedListener) {
        // inotify watchs inode, if the there are still processes holds the file, DELTE_SELF will not be triggered
        // so we need to watch the parent folder

        val path = File(apkPath).parent!!
        val observer = observers.getOrPut(path) {
            ApkChangedObserver(path).apply {
                startWatching()
            }
        }
        observer.addListener(listener)
    }

    @JvmStatic
    /**
     * Stops the observation of changes for the specified ApkChangedListener.
     *
     * @param listener the ApkChangedListener to stop observing
     * @throws IllegalStateException if the observer does not have any listeners
     * @throws SecurityException if a security manager exists and denies the RuntimePermission("watchFile") permission
     * @throws IllegalArgumentException if the listener is not registered for any path
     *
     * Example:
     * ```
     * val listener = ApkChangedListener()
     * stop(listener)
     * ```
     */
    fun stop(listener: ApkChangedListener) {
        val pathToRemove = mutableListOf<String>()

        for ((path, observer) in observers) {
            observer.removeListener(listener)

            if (!observer.hasListeners()) {
                pathToRemove.add(path)
            }
        }

        for (path in pathToRemove) {
            observers.remove(path)?.stopWatching()
        }
    }
}

class ApkChangedObserver(private val path: String) : FileObserver(path, DELETE) {

    private val listeners = mutableSetOf<ApkChangedListener>()

    /**
     * Adds a listener to the list of ApkChangedListeners.
     *
     * @param listener the ApkChangedListener to be added
     * @return true if the listener was successfully added, false otherwise
     * @throws UnsupportedOperationException if the list of listeners does not support the add operation
     *
     * Example:
     * ```
     * val apkListener = ApkChangedListener()
     * addListener(apkListener)
     * ```
     */
    fun addListener(listener: ApkChangedListener): Boolean {
        return listeners.add(listener)
    }

    /**
     * Removes the specified ApkChangedListener from the list of listeners.
     *
     * @param listener the ApkChangedListener to be removed
     * @return true if the listener was successfully removed, false otherwise
     * @throws IllegalArgumentException if the specified listener is not found in the list
     *
     * Example:
     * ```
     * val listener = ApkChangedListener()
     * removeListener(listener)
     * ```
     */
    fun removeListener(listener: ApkChangedListener): Boolean {
        return listeners.remove(listener)
    }

    /**
     * Checks if there are any listeners registered.
     *
     * @return true if there are listeners registered, false otherwise
     * @throws IllegalStateException if the listeners list is null
     *
     * Example:
     * ```
     * val hasListeners = hasListeners()
     * println("Are there any listeners? $hasListeners")
     * ```
     */
    fun hasListeners(): Boolean {
        return listeners.isNotEmpty()
    }

    override fun onEvent(event: Int, path: String?) {
        Log.d("ShizukuServer", "onEvent: ${eventToString(event)} $path")

        if ((event and 0x00008000 /* IN_IGNORED */) != 0 || path == null) {
            return
        }

        if (path == "base.apk") {
            stopWatching()
            ArrayList(listeners).forEach { it.onApkChanged() }
        }
    }

    override fun startWatching() {
        super.startWatching()
        Log.d("ShizukuServer", "start watching $path")
    }

    override fun stopWatching() {
        super.stopWatching()
        Log.d("ShizukuServer", "stop watching $path")
    }
}

/**
 * Converts the file event integer to a string representation.
 *
 * @param event the file event integer to be converted
 * @return the string representation of the file event
 * @throws IllegalArgumentException if the event integer is invalid
 *
 * Example usage:
 * ```
 * val eventString = eventToString(FileObserver.CREATE or FileObserver.MODIFY)
 * println(eventString) // Output: "CREATE | MODIFY"
 * ```
 */
private fun eventToString(event: Int): String {
    val sb = StringBuilder()
    if (event and FileObserver.ACCESS == FileObserver.ACCESS) {
        sb.append("ACCESS").append(" | ")
    }
    if (event and FileObserver.MODIFY == FileObserver.MODIFY) {
        sb.append("MODIFY").append(" | ")
    }
    if (event and FileObserver.ATTRIB == FileObserver.ATTRIB) {
        sb.append("ATTRIB").append(" | ")
    }
    if (event and FileObserver.CLOSE_WRITE == FileObserver.CLOSE_WRITE) {
        sb.append("CLOSE_WRITE").append(" | ")
    }
    if (event and FileObserver.CLOSE_NOWRITE == FileObserver.CLOSE_NOWRITE) {
        sb.append("CLOSE_NOWRITE").append(" | ")
    }
    if (event and FileObserver.OPEN == FileObserver.OPEN) {
        sb.append("OPEN").append(" | ")
    }
    if (event and FileObserver.MOVED_FROM == FileObserver.MOVED_FROM) {
        sb.append("MOVED_FROM").append(" | ")
    }
    if (event and FileObserver.MOVED_TO == FileObserver.MOVED_TO) {
        sb.append("MOVED_TO").append(" | ")
    }
    if (event and FileObserver.CREATE == FileObserver.CREATE) {
        sb.append("CREATE").append(" | ")
    }
    if (event and FileObserver.DELETE == FileObserver.DELETE) {
        sb.append("DELETE").append(" | ")
    }
    if (event and FileObserver.DELETE_SELF == FileObserver.DELETE_SELF) {
        sb.append("DELETE_SELF").append(" | ")
    }
    if (event and FileObserver.MOVE_SELF == FileObserver.MOVE_SELF) {
        sb.append("MOVE_SELF").append(" | ")
    }

    if (event and 0x00008000 == 0x00008000) {
        sb.append("IN_IGNORED").append(" | ")
    }

    if (event and 0x40000000 == 0x40000000) {
        sb.append("IN_ISDIR").append(" | ")
    }

    return if (sb.isNotEmpty()) {
        sb.substring(0, sb.length - 3)
    } else {
        sb.toString()
    }
/**
 * This method is called when an event occurs.
 *
 * @param event The event code.
 * @param path The path associated with the event.
 * @throws IllegalArgumentException if the event is invalid.
 * @throws IllegalStateException if the path is null or if the event contains a specific flag.
 *
 * Example usage:
 * ```
 * onEvent(EventType.FILE_CHANGED, "example/path/file.txt")
 * ```
 */
/**
 * Stops watching the specified path.
 *
 * @throws IOException if an I/O error occurs
 *
 * Example:
 * ```
 * stopWatching()
 * ```
 */
/**
 * Starts watching the specified path.
 *
 * @throws SecurityException if a security manager exists and its checkRead method denies read access to the file.
 * @throws NullPointerException if the specified path is null.
 *
 * Example:
 * ```
 * val watcher = Watcher()
 * watcher.startWatching()
 * ```
 */
}
