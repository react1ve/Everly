package app.platform

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.writeToURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * Save: writes a temp file and opens the share sheet ("Save to Files", AirDrop, mail…).
 * Open: the Files document picker, JSON only, as a copy.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosBackupFiles : BackupFiles {
    private var delegate: PickerDelegate? = null // keep a strong reference while the picker is up

    override suspend fun save(suggestedName: String, content: String): Boolean {
        val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + suggestedName)
        val text = NSString.create(string = content)
        val ok = text.writeToURL(url, atomically = true, encoding = NSUTF8StringEncoding, error = null)
        if (!ok) return false
        val top = topController() ?: return false
        return suspendCancellableCoroutine { cont ->
            val sheet = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
            sheet.completionWithItemsHandler = { _, completed, _, _ -> if (cont.isActive) cont.resume(completed) }
            sheet.popoverPresentationController?.sourceView = top.view // iPad
            top.presentViewController(sheet, animated = true, completion = null)
        }
    }

    override suspend fun open(): String? {
        val top = topController() ?: return null
        return suspendCancellableCoroutine { cont ->
            val d = PickerDelegate { url ->
                delegate = null
                val text = url?.let {
                    val scoped = it.startAccessingSecurityScopedResource()
                    val s = NSString.create(contentsOfURL = it, encoding = NSUTF8StringEncoding, error = null)?.toString()
                    if (scoped) it.stopAccessingSecurityScopedResource()
                    s
                }
                if (cont.isActive) cont.resume(text)
            }
            delegate = d
            val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeJSON), asCopy = true)
            picker.delegate = d
            top.presentViewController(picker, animated = true, completion = null)
        }
    }

    private fun topController(): UIViewController? {
        var vc = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (vc?.presentedViewController != null) vc = vc.presentedViewController
        return vc
    }
}

private class PickerDelegate(private val onResult: (NSURL?) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        onResult(didPickDocumentsAtURLs.firstOrNull() as? NSURL)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onResult(null)
    }
}
