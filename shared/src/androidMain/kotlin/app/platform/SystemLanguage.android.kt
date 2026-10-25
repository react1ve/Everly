package app.platform

import java.util.Locale

actual fun systemLanguageCode(): String = Locale.getDefault().language
