@file:OptIn(ExperimentalMaterial3Api::class)

package app.brokoli5191.picket.ui.expressive

import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.AppBarWithSearch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarScrollBehavior
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarScrollState
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import app.brokoli5191.picket.R
import kotlinx.coroutines.launch

/** Lets [SearchTopBar] slide away while the content scrolls down and return on the way up. */
@Stable
class SearchTopBarScroll internal constructor(
    internal val behavior: SearchBarScrollBehavior,
) {
    val nestedScrollConnection: NestedScrollConnection get() = behavior.nestedScrollConnection
}

@Composable
fun rememberSearchTopBarScroll(): SearchTopBarScroll {
    val behavior = SearchBarDefaults.enterAlwaysSearchBarScrollBehavior(scrollState = rememberSearchBarScrollState())
    return remember(behavior) { SearchTopBarScroll(behavior) }
}

/**
 * A screen's top bar as an Expressive search field that filters in place: typing narrows the content
 * below, there is no separate results page. [query] and [onQueryChange] mirror the field into the
 * view model, which owns the filter.
 */
@Composable
fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    clearLabel: String,
    scroll: SearchTopBarScroll,
    modifier: Modifier = Modifier,
) {
    val text = rememberTextFieldState(query)
    val latestOnQueryChange by rememberUpdatedState(onQueryChange)
    LaunchedEffect(text) { snapshotFlow { text.text.toString() }.collect { latestOnQueryChange(it) } }
    LaunchedEffect(query) { if (query != text.text.toString()) text.setTextAndPlaceCursorAtEnd(query) }

    // Focus moves the state to "expanded". Nothing expands here, so collapse again when focus leaves;
    // otherwise screen readers keep announcing search suggestions that do not exist.
    val searchState = rememberSearchBarState()
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    AppBarWithSearch(
        state = searchState,
        inputField = {
            SearchBarDefaults.InputField(
                textFieldState = text,
                searchBarState = searchState,
                onSearch = { focus.clearFocus() },
                modifier =
                    Modifier.onFocusChanged {
                        if (!it.isFocused && searchState.currentValue == SearchBarValue.Expanded) {
                            scope.launch { searchState.animateToCollapsed() }
                        }
                    },
                placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                trailingIcon =
                    if (text.text.isEmpty()) {
                        null
                    } else {
                        {
                            IconButton(onClick = { text.clearText() }) {
                                Icon(painterResource(R.drawable.ic_close), contentDescription = clearLabel)
                            }
                        }
                    },
            )
        },
        modifier = modifier,
        scrollBehavior = scroll.behavior,
    )
}
