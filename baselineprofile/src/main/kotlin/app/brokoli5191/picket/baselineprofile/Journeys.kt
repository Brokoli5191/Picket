package app.brokoli5191.picket.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

/** Package of the APK under test, passed in by the module's build script. */
val targetAppId: String
    get() =
        checkNotNull(InstrumentationRegistry.getArguments().getString("targetAppId")) {
            "targetAppId instrumentation argument missing"
        }

private const val TIMEOUT_MS = 5_000L

/**
 * Taps the element with [text] and waits for the UI to settle. Labels are the English strings from
 * app/src/main/res/values/strings.xml.
 */
fun MacrobenchmarkScope.tap(text: String) {
    val target = device.wait(Until.findObject(By.text(text)), TIMEOUT_MS) ?: scrollTo(text)
    checkNotNull(target) { "\"$text\" not on screen" }.click()
    device.waitForIdle()
}

private fun MacrobenchmarkScope.scrollTo(text: String): UiObject2? {
    val list = device.findObject(By.scrollable(true)) ?: return null
    return list.scrollUntil(Direction.DOWN, Until.findObject(By.text(text)))
}

/** Flings the first scrollable container down and back up, as a user skimming a list would. */
fun MacrobenchmarkScope.flingList() {
    val list = device.wait(Until.findObject(By.scrollable(true)), TIMEOUT_MS) ?: return
    // Keep gestures off the system navigation areas.
    list.setGestureMargin(device.displayWidth / 5)
    repeat(2) { list.fling(Direction.DOWN) }
    device.waitForIdle()
    list.fling(Direction.UP)
    device.waitForIdle()
}

fun MacrobenchmarkScope.back() {
    device.pressBack()
    device.waitForIdle()
}

/**
 * Everything a first session touches. Without Shizuku (the managed emulator) the three privileged
 * tabs show the connection screen and the lists come from Settings; on a device with Shizuku the
 * process list itself is scrolled as well.
 */
fun MacrobenchmarkScope.firstSession() {
    flingList()
    tap("Watchers")
    flingList()
    tap("Wakeups")
    flingList()
    tap("Settings")
    tap("Protected apps")
    flingList()
    back()
    tap("Journal")
    back()
    tap("Appearance")
    flingList()
    back()
    tap("Licences")
    flingList()
    back()
    tap("Processes")
}
