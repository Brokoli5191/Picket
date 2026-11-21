package app.brokoli5191.picket.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.brokoli5191.picket.R
import app.brokoli5191.picket.ui.components.SectionHeader
import app.brokoli5191.picket.ui.components.segmentedColors

@Composable
fun InfoScaffold(
    title: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
            content = content,
        )
    }
}

/** "How Picket works": Shizuku, the helper process, typed operations, no INTERNET. */
@Composable
fun HowItWorksScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sections =
        listOf(
            R.string.how_shizuku_title to R.string.how_shizuku_body,
            R.string.how_helper_title to R.string.how_helper_body,
            R.string.how_typed_title to R.string.how_typed_body,
            R.string.how_offline_title to R.string.how_offline_body,
            R.string.how_reboot_title to R.string.how_reboot_body,
            R.string.how_restricted_title to R.string.how_restricted_body,
        )
    InfoScaffold(R.string.settings_how_it_works, onBack, modifier) {
        sections.forEach { (title, body) ->
            item {
                SectionHeader(stringResource(title))
                Text(
                    stringResource(body),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** Third-party code shipped in the APK. */
@Composable
fun LicensesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries =
        listOf(
            "AndroidX, Jetpack Compose, Material 3" to "Apache License 2.0",
            "Kotlin, kotlinx.coroutines, kotlinx.serialization" to "Apache License 2.0",
            "Shizuku API (RikkaApps)" to "MIT License",
            "material-color-utilities (MaterialKolor port)" to "MIT License / Apache License 2.0",
            "Material Symbols (Google)" to "Apache License 2.0",
        )
    InfoScaffold(R.string.settings_licenses, onBack, modifier) {
        item {
            Text(
                stringResource(R.string.licenses_intro),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        itemsIndexed(entries) { index, (name, license) ->
            SegmentedListItem(
                shapes = ListItemDefaults.segmentedShapes(index = index, count = entries.size),
                colors = segmentedColors(),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 1.dp),
                supportingContent = { Text(license) },
            ) { Text(name) }
        }
    }
}
