package app.brokoli5191.picket.ui.components

import android.content.Context
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import app.brokoli5191.picket.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val IconSize = 40.dp

/**
 * The launcher icon of [packageName]; a neutral placeholder until it has loaded, or if there is none.
 * Icons are rasterised once, off the main thread, at the pixel size they are drawn, and kept in
 * [AppIconCache]: a row that scrolls back into view shows its icon at once instead of the
 * placeholder, and the icon is as sharp as the screen allows.
 */
@Composable
fun AppIcon(
    packageName: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val preview = LocalInspectionMode.current
    val sizePx = with(LocalDensity.current) { IconSize.roundToPx() }
    var icon by remember(packageName, sizePx) { mutableStateOf(packageName?.let { AppIconCache.peek(it, sizePx) }) }
    LaunchedEffect(packageName, sizePx) {
        if (icon == null && packageName != null && !preview) icon = AppIconCache.load(context, packageName, sizePx)
    }
    Box(modifier.size(IconSize).clip(CircleShape), contentAlignment = Alignment.Center) {
        Crossfade(icon, animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(), label = "icon") { bitmap ->
            if (bitmap != null) {
                Image(bitmap, contentDescription = null, modifier = Modifier.size(IconSize))
            } else {
                Placeholder()
            }
        }
    }
}

@Composable
private fun Placeholder() {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(IconSize)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_memory), contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

/** Rasterised launcher icons by package and pixel size, within a fixed memory budget. */
internal object AppIconCache {
    /** About 140 icons of 40 dp on a 3x screen: more than a process list shows at once. */
    private const val BUDGET_BYTES = 8 * 1024 * 1024

    private val cache =
        object : LruCache<String, ImageBitmap>(BUDGET_BYTES) {
            override fun sizeOf(
                key: String,
                value: ImageBitmap,
            ) = value.width * value.height * 4
        }

    fun peek(
        packageName: String,
        sizePx: Int,
    ): ImageBitmap? = cache.get(key(packageName, sizePx))

    /** The icon from the cache, or loaded and rasterised on the IO dispatcher; null if there is none. */
    suspend fun load(
        context: Context,
        packageName: String,
        sizePx: Int,
    ): ImageBitmap? =
        withContext(Dispatchers.IO) {
            val key = key(packageName, sizePx)
            cache.get(key) ?: runCatching {
                context.packageManager
                    .getApplicationIcon(packageName)
                    .toBitmap(sizePx, sizePx)
                    .asImageBitmap()
            }.getOrNull()?.also { cache.put(key, it) }
        }

    private fun key(
        packageName: String,
        sizePx: Int,
    ) = "$packageName@$sizePx"
}
