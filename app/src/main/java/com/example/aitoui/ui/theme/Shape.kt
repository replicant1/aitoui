package com.example.aitoui.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// The app's corner-radius scale, following the M3 shape roles. Wired into AitouiTheme so M3
// components (Card, Button, TextField, menus, sheets) pick these up without per-call-site shapes.
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** The rounded-square used for tablet-photo thumbnails across the app (Inventory, Daily Schedule). */
val ThumbnailShape = RoundedCornerShape(6.dp)
