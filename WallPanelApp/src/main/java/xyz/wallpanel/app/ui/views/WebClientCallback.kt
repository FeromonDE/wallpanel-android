package xyz.wallpanel.app.ui.views

import android.webkit.PermissionRequest

interface WebClientCallback {
    fun requestWebkitPermission(request: PermissionRequest)

    fun complete()

    fun pageLoadComplete(url: String)

    var isConnected: Boolean

    fun isFinishing(): Boolean

    fun displayProgress(): Boolean

    fun startReloadDelay()

    fun stopReloadDelay()

    fun certPermissionsShown() : Boolean

}