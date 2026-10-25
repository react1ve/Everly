package app.platform

/**
 * OS-level switches that affect reminders and that only the user can change.
 * Each is null when it doesn't apply on this platform/version.
 */
interface SystemSettings {
    /** Android 12+: exact alarms allowed? null = not applicable. */
    fun exactAlarmsAllowed(): Boolean?
    /** Android: is the app exempt from battery optimisation? null = not applicable. */
    fun ignoringBatteryOptimisations(): Boolean?
    fun openExactAlarmSettings()
    fun openBatterySettings()
    /** The app's page in system settings (notifications etc.). */
    fun openAppSettings()
}
