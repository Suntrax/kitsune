package com.blissless.manga.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

sealed class BottomNavItem(
    val route: String,
    val icon: ImageVector,
    val label: String
) {
    data object Explore : BottomNavItem("explore", Icons.Default.Explore, "Explore")
    data object Home : BottomNavItem("home", Icons.Default.Home, "Home")
    data object Search : BottomNavItem("search", Icons.Default.Search, "Search")
    data object Settings : BottomNavItem("settings", Icons.Default.Settings, "Settings")
}

@Composable
fun BottomNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        BottomNavItem.Explore,
        BottomNavItem.Home,
        BottomNavItem.Search,
        BottomNavItem.Settings
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF1E1E2E).copy(alpha = 0.95f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            val isSelected = currentRoute == item.route
            val tint by animateColorAsState(
                targetValue = if (isSelected) Color(0xFFa855f7) else Color.White.copy(alpha = 0.5f),
                animationSpec = tween(200),
                label = "tint"
            )

            IconButton(
                onClick = { onNavigate(item.route) },
                modifier = Modifier.size(44.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (isSelected) Color(0xFFa855f7).copy(alpha = 0.15f) else Color.Transparent,
                    contentColor = tint
                )
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
