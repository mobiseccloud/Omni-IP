package com.mobisec.omniip.model

import androidx.compose.ui.graphics.ImageBitmap

data class AppMatrixItem(
    val uid: Int,
    val label: String,
    val packageName: String,
    val iconBitmap: ImageBitmap,
    val isSystem: Boolean,
    val wifiBlocked: Boolean,
    val cellularBlocked: Boolean
)
