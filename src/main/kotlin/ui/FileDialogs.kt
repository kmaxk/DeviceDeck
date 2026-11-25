package ui

import java.awt.FileDialog
import java.awt.Frame

/** The app's main window, set from `Main.kt`; native dialogs use it as owner. */
object AppWindow {
    @Volatile
    var frame: Frame? = null
}

/**
 * A native file dialog owned by the main window. An unowned dialog leaves keyboard focus
 * outside the Compose window after it closes (macOS), so text fields stop receiving keys.
 */
fun fileDialog(title: String, mode: Int): FileDialog = FileDialog(AppWindow.frame, title, mode)

/** Shows the dialog (blocks until closed, call on the Swing thread) and hands focus back to the main window. */
fun FileDialog.showAndRestoreFocus() {
    isVisible = true
    AppWindow.frame?.let {
        it.toFront()
        it.requestFocus()
    }
}
