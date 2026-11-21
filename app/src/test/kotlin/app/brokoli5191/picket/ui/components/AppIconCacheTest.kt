package app.brokoli5191.picket.ui.components

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AppIconCacheTest {
    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun installApp() {
        val packages = shadowOf(context.packageManager)
        packages.installPackage(
            PackageInfo().apply {
                packageName = PKG
                applicationInfo = ApplicationInfo().apply { packageName = PKG }
            },
        )
        packages.setApplicationIcon(PKG, ColorDrawable(Color.RED))
    }

    @Test
    fun anIconIsRasterisedOnceAtTheRequestedSize() =
        runBlocking {
            val pkg = PKG
            assertThat(AppIconCache.peek(pkg, 123)).isNull()
            val first = checkNotNull(AppIconCache.load(context, pkg, 123))
            assertThat(first.width).isEqualTo(123)
            assertThat(first.height).isEqualTo(123)
            assertThat(AppIconCache.peek(pkg, 123)).isSameInstanceAs(first)
            assertThat(AppIconCache.load(context, pkg, 123)).isSameInstanceAs(first)
        }

    @Test
    fun eachPixelSizeHasItsOwnEntry() =
        runBlocking {
            val pkg = PKG
            val small = checkNotNull(AppIconCache.load(context, pkg, 61))
            val large = checkNotNull(AppIconCache.load(context, pkg, 89))
            assertThat(small.width).isEqualTo(61)
            assertThat(large.width).isEqualTo(89)
        }

    @Test
    fun anUnknownPackageHasNoIconAndIsNotCached() =
        runBlocking {
            assertThat(AppIconCache.load(context, "com.example.not.installed", 96)).isNull()
            assertThat(AppIconCache.peek("com.example.not.installed", 96)).isNull()
        }

    private companion object {
        const val PKG = "com.example.icon"
    }
}
