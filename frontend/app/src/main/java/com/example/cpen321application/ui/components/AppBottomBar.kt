package com.example.cpen321application.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.example.cpen321application.ui.theme.SportsOnPrimaryContainer
import com.example.cpen321application.ui.theme.SportsOnSurfaceVariant
import com.example.cpen321application.ui.theme.SportsPrimaryContainer

// the five main sections of the app shown in the bottom bar
enum class BottomTab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home),
    Discover("Discover", CompassIcon),
    Friends("Friends", PeopleIcon),
    Notifications("Notifications", Icons.Filled.Notifications),
    Profile("Profile", Icons.Filled.Person)
}

/*
    Bottom navigation bar shared by the main screens.
    selectedTab is highlighted and onTabSelected is called when a tab is pressed
 */
@Composable
fun AppBottomBar(
    selectedTab: BottomTab,
    onTabSelected: (BottomTab) -> Unit,
    modifier: Modifier = Modifier
) {
    // insets are already applied by the Scaffold in MainActivity
    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 0.dp,
        windowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier
    ) {
        BottomTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selectedTab,
                onClick = { onTabSelected(tab) },
                icon = { Icon(imageVector = tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
                alwaysShowLabel = false,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = SportsOnPrimaryContainer,
                    selectedTextColor = SportsOnPrimaryContainer,
                    indicatorColor = SportsPrimaryContainer,
                    unselectedIconColor = SportsOnSurfaceVariant,
                    unselectedTextColor = SportsOnSurfaceVariant
                )
            )
        }
    }
}

// compass icon for the Discover tab (Material "Explore" icon, not in the core icon set)
private val CompassIcon: ImageVector = ImageVector.Builder(
    name = "Compass",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).addPath(
    pathData = addPathNodes(
        "M12,10.9c-0.61,0 -1.1,0.49 -1.1,1.1s0.49,1.1 1.1,1.1c0.61,0 1.1,-0.49 1.1,-1.1" +
                "s-0.49,-1.1 -1.1,-1.1zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10" +
                "S17.52,2 12,2zM14.19,14.19L6,18l3.81,-8.19L18,6l-3.81,8.19z"
    ),
    fill = SolidColor(Color.Black)
).build()

// two people icon for the Friends tab (Material "People" icon, not in the core icon set)
private val PeopleIcon: ImageVector = ImageVector.Builder(
    name = "People",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).addPath(
    pathData = addPathNodes(
        "M16,11c1.66,0 2.99,-1.34 2.99,-3S17.66,5 16,5c-1.66,0 -3,1.34 -3,3s1.34,3 3,3z" +
                "M8,11c1.66,0 2.99,-1.34 2.99,-3S9.66,5 8,5C6.34,5 5,6.34 5,8s1.34,3 3,3z" +
                "M8,13c-2.33,0 -7,1.17 -7,3.5L1,19h14v-2.5c0,-2.33 -4.67,-3.5 -7,-3.5z" +
                "M16,13c-0.29,0 -0.62,0.02 -0.97,0.05 1.16,0.84 1.97,1.97 1.97,3.45L17,19h6v-2.5" +
                "c0,-2.33 -4.67,-3.5 -7,-3.5z"
    ),
    fill = SolidColor(Color.Black)
).build()
