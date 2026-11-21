package app.brokoli5191.picket.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

/** Package of the APK under test, passed in by the module's build script. */
val targetAppId: String
    get() =
        checkNotNull(InstrumentationRegistry.getArguments().getString("targetAppId")) {
            "targetAppId instrumentation argument missing"
        }

private const val TIMEOUT_MS = 5_000L
private const val ATTEMPTS = 3

/**
 * Runs [action] on a freshly found object. Compose replaces semantics nodes when a screen changes
 * state (the connection screen settling, a top bar collapsing, content loading), so an object held
 * across gestures goes stale; it is looked up again instead. Null if it never appears.
 */
private fun <T> MacrobenchmarkScope.onFresh(
    selector: BySelector,
    action: (UiObject2) -> T,
): T? {
    repeat(ATTEMPTS) {
        val target = device.wait(Until.findObject(selector), TIMEOUT_MS) ?: return null
        try {
            return action(target)
        } catch (_: StaleObjectException) {
            device.waitForIdle()
        }
    }
    return null
}

/** True once [text] is on screen, waiting up to five seconds. */
fun MacrobenchmarkScope.shows(text: String): Boolean = device.wait(Until.hasObject(By.text(text)), TIMEOUT_MS) == true

/**
 * Taps the element with [text] and waits for the UI to settle. Labels are the English strings from
 * app/src/main/res/values/strings.xml.
 */
fun MacrobenchmarkScope.tap(text: String) {
    val selector = By.text(text)
    if (onFresh(selector) { it.click() } == null) {
        onFresh(By.scrollable(true)) { it.scrollUntil(Direction.DOWN, Until.findObject(selector)) }
        checkNotNull(onFresh(selector) { it.click() }) { "\"$text\" not on screen" }
    }
    device.waitForIdle()
}

/** Flings the first scrollable container down twice and back up, as a user skimming a list would. */
fun MacrobenchmarkScope.flingList() {
    device.waitForIdle()
    if (device.wait(Until.hasObject(By.scrollable(true)), TIMEOUT_MS) != true) return
    for (direction in listOf(Direction.DOWN, Direction.DOWN, Direction.UP)) {
        onFresh(By.scrollable(true)) { list ->
            // Keep gestures off the system navigation areas.
            list.setGestureMargin(device.displayWidth / 5)
            list.fling(direction)
        }
        device.waitForIdle()
    }
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
