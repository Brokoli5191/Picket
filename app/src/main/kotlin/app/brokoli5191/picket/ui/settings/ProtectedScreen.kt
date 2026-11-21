package app.brokoli5191.picket.ui.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.brokoli5191.picket.R
import app.brokoli5191.picket.core.action.ProtectedPackages
import app.brokoli5191.picket.privileged.client.PrivilegedClient
import app.brokoli5191.picket.ui.components.NoticeCard
import app.brokoli5191.picket.ui.components.SectionHeader
import app.brokoli5191.picket.ui.components.segmentedColors

/** A package protected because of what it currently is (launcher, keyboard, ...). */
data class DynamicProtection(
    val role: Int,
    val packageName: String?,
)

/**
 * Read-only view of what Picket refuses to stop, restrict or disable. The service enforces the same
 * file plus the dynamic entries; this screen only mirrors them.
 */
@Composable
fun ProtectedScreen(
    fixed: ProtectedPackages?,
    dynamic: List<DynamicProtection>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = fixed?.packages.orEmpty() + fixed?.prefixes.orEmpty().map { "$it*" }
    InfoScaffold(R.string.settings_protected, onBack, modifier) {
        item(key = "intro") {
            Text(
                stringResource(R.string.protected_intro),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        item(key = "dynamic-header") { SectionHeader(stringResource(R.string.protected_dynamic)) }
        itemsIndexed(dynamic, key = { _, d -> "dynamic-${d.role}" }) { index, d ->
            SegmentedListItem(
                shapes = ListItemDefaults.segmentedShapes(index = index, count = dynamic.size),
                colors = segmentedColors(),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 1.dp),
                supportingContent = { Text(d.packageName ?: stringResource(R.string.unknown)) },
            ) { Text(stringResource(d.role)) }
        }
        item(key = "fixed-header") { SectionHeader(stringResource(R.string.protected_fixed)) }
        if (fixed == null) {
            item(key = "missing") {
                NoticeCard(
                    stringResource(R.string.error_title),
                    stringResource(R.string.protected_missing),
                    error = true,
                )
            }
        }
        itemsIndexed(entries, key = { _, pkg -> pkg }) { index, pkg ->
            SegmentedListItem(
                shapes = ListItemDefaults.segmentedShapes(index = index, count = entries.size),
                colors = segmentedColors(),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 1.dp),
            ) { Text(pkg) }
        }
    }
}

/** Reads the bundled list and resolves the dynamic entries the same way the service does. */
@Composable
fun ProtectedDestination(onBack: () -> Unit) {
    val context = LocalContext.current
    val fixed = remember { loadProtected(context) }
    val dynamic = remember { dynamicProtections(context) }
    ProtectedScreen(fixed, dynamic, onBack)
}

private fun loadProtected(context: Context): ProtectedPackages? =
    runCatching {
        context.assets
            .open("protected.json")
            .bufferedReader()
            .use { ProtectedPackages.parse(it.readText()) }
    }.getOrNull()

private fun dynamicProtections(context: Context): List<DynamicProtection> {
    val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val launcher =
        runCatching {
            context.packageManager
                .resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo
                ?.packageName
        }.getOrNull()
    val keyboard =
        runCatching {
            Settings.Secure
                .getString(
                    context.contentResolver,
                    Settings.Secure.DEFAULT_INPUT_METHOD,
                )?.substringBefore('/')
        }.getOrNull()
    return listOf(
        DynamicProtection(R.string.protected_launcher, launcher),
        DynamicProtection(R.string.protected_keyboard, keyboard),
        DynamicProtection(R.string.protected_shizuku, PrivilegedClient.SHIZUKU_PACKAGE),
        DynamicProtection(R.string.protected_self, context.packageName),
    )
}
