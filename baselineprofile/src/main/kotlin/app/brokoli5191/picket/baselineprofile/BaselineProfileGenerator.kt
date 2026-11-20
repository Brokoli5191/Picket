package app.brokoli5191.picket.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Collects the baseline and startup profile for the app:
 * `./gradlew :app:generateBaselineProfile` (managed emulator, see the module's build script).
 * The result lands in app/src/release/generated/baselineProfiles/ and is committed.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() =
        rule.collect(packageName = targetAppId, includeInStartupProfile = true) {
            pressHome()
            startActivityAndWait()
            firstSession()
        }
}
