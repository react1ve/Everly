package app.platform

import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

actual fun systemLanguageCode(): String =
    (NSLocale.preferredLanguages.firstOrNull() as? String)?.take(2) ?: "en"
