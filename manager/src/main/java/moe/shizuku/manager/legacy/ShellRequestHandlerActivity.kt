package moe.shizuku.manager.legacy

import android.os.Bundle
import android.widget.Toast
import moe.shizuku.manager.app.AppActivity
import moe.shizuku.manager.shell.ShellBinderRequestHandler

class ShellRequestHandlerActivity : AppActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ShellBinderRequestHandler.handleRequest(this, intent)
        finish()
    }
/**
 * Called when the activity is starting. This is where most initialization should go: calling [super.onCreate],
 * [ShellBinderRequestHandler.handleRequest], and then [finish].
 *
 * @param savedInstanceState If the activity is being re-initialized after previously being shut down then this Bundle contains the data it most recently supplied in [onSaveInstanceState].
 * Note: Otherwise it is null.
 *
 * @throws SomeException if something goes wrong during the handling of the request.
 *
 * Example:
 * ```
 * override fun onCreate(savedInstanceState: Bundle?) {
 *     super.onCreate(savedInstanceState)
 *     ShellBinderRequestHandler.handleRequest(this, intent)
 *     finish()
 * }
 * ```
 */
}
