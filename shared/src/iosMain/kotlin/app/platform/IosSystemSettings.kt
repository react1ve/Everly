package app.platform

import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

class IosSystemSettings : SystemSettings {
    override fun exactAlarmsAllowed(): Boolean? = null
    override fun ignoringBatteryOptimisations(): Boolean? = null
    override fun openExactAlarmSettings() = openAppSettings()
    override fun openBatterySettings() = openAppSettings()
    override fun openAppSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
    }
}
