package com.ratherbeembed.rbe_chess.pocket

import android.app.Activity
import android.view.WindowManager

/**
 * Activity-scoped side effects for entering / exiting Pocket Mode:
 *
 *   - `FLAG_KEEP_SCREEN_ON` so the OS doesn't sleep the display while the
 *     user is mid-move on the BT keypad.
 *   - Drop screen brightness to a low value so an OLED display is close
 *     to off in a pocket; restore the prior value on exit.
 *
 * Per AGENT_NOTES this is the M1 path — no foreground service, no wake
 * locks beyond Activity-scoped keep-awake.
 */
class PocketModeController(private val activity: Activity) {

    private var savedBrightness: Float = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    private var active: Boolean = false

    fun enter() {
        if (active) return
        val window = activity.window
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val lp = window.attributes
        savedBrightness = lp.screenBrightness
        lp.screenBrightness = POCKET_BRIGHTNESS
        window.attributes = lp
        active = true
    }

    fun exit() {
        if (!active) return
        val window = activity.window
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val lp = window.attributes
        lp.screenBrightness = savedBrightness
        window.attributes = lp
        active = false
    }

    companion object {
        private const val POCKET_BRIGHTNESS = 0.05f
    }
}
