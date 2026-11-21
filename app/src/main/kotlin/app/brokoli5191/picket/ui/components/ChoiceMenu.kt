package app.brokoli5191.picket.ui.components

import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import app.brokoli5191.picket.R

/**
 * A single choice as an Expressive menu: one rounded group, the selected item tinted and marked
 * with a check. Place it next to its anchor inside a Box, like a DropdownMenu.
 */
@Composable
fun <T> ChoiceMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    DropdownMenuPopup(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 1)) {
            options.forEachIndexed { index, option ->
                SelectableDropdownMenuItem(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    text = { Text(label(option)) },
                    shapes = MenuDefaults.itemShape(index = index, count = options.size),
                    selectedLeadingIcon = { Icon(painterResource(R.drawable.ic_check), contentDescription = null) },
                )
            }
        }
    }
}
