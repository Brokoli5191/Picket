package app.brokoli5191.picket.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold start and list scrolling, each without and with the baseline profile. Emulator numbers are
 * only a smoke test (`androidx.benchmark.suppressErrors=EMULATOR`); real numbers come from a device:
 * `./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest -Ppicket.profileOnDevice=true`.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartWithoutProfile() = start(CompilationMode.None())

    @Test
    fun coldStartWithProfile() = start(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun start(mode: CompilationMode) =
        rule.measureRepeated(
            packageName = targetAppId,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = mode,
            startupMode = StartupMode.COLD,
            iterations = ITERATIONS,
            setupBlock = { pressHome() },
        ) {
            startActivityAndWait()
        }
}

@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun scrollWithoutProfile() = scroll(CompilationMode.None())

    @Test
    fun scrollWithProfile() = scroll(CompilationMode.Partial(BaselineProfileMode.Require))

    /**
     * Scrolls the process list where Shizuku runs; on the emulator the protected-apps list, which
     * uses the same list components.
     */
    private fun scroll(mode: CompilationMode) =
        rule.measureRepeated(
            packageName = targetAppId,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = mode,
            startupMode = StartupMode.WARM,
            iterations = ITERATIONS,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                // The connection screen settles on its final state shortly after start.
                if (shows(SHIZUKU_MISSING)) {
                    tap("Settings")
                    tap("Protected apps")
                }
            },
        ) {
            flingList()
        }

    private companion object {
        /** The connection screen's action when Shizuku is missing; no other state shows it. */
        const val SHIZUKU_MISSING = "Get Shizuku"
    }
}

private const val ITERATIONS = 5
