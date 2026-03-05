package com.mrshudson.android.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================
// MrsHudson 品牌色 - 暖色调 (琥珀色/橙色系列)
// ============================================

// 品牌主色 - 琥珀色
val AmberPrimary = Color(0xFFFF8F00)
val AmberOnPrimary = Color(0xFFFFFFFF)
val AmberPrimaryContainer = Color(0xFFFFE082)
val AmberOnPrimaryContainer = Color(0xFF5D4037)

// 品牌次色 - 深橙色
val DeepOrangeSecondary = Color(0xFFE65100)
val DeepOrangeOnSecondary = Color(0xFFFFFFFF)
val DeepOrangeSecondaryContainer = Color(0xFFFFCC80)
val DeepOrangeOnSecondaryContainer = Color(0xFF3E2723)

// 第三色 - 暖棕色
val WarmBrownTertiary = Color(0xFF8D6E63)
val WarmBrownOnTertiary = Color(0xFFFFFFFF)
val WarmBrownTertiaryContainer = Color(0xFFD7CCC8)
val WarmBrownOnTertiaryContainer = Color(0xFF3E2723)

// 错误色
val Error = Color(0xFFB3261E)
val OnError = Color(0xFFFFFFFF)
val ErrorContainer = Color(0xFFF9DEDC)
val OnErrorContainer = Color(0xFF410E0B)

// 表面色 (Light) - 暖白色背景
val SurfaceLight = Color(0xFFFFFBF8)
val OnSurfaceLight = Color(0xFF1C1B1A)
val SurfaceVariantLight = Color(0xFFF5E6D3)
val OnSurfaceVariantLight = Color(0xFF4E4540)
val OutlineLight = Color(0xFF85736B)
val OutlineVariantLight = Color(0xFFD7C2B4)

// 背景色 (Light) - 暖白色
val BackgroundLight = Color(0xFFFFFBF8)
val OnBackgroundLight = Color(0xFF1C1B1A)

// 表面色 (Dark) - 深暖色
val SurfaceDark = Color(0xFF1C1B1A)
val OnSurfaceDark = Color(0xFFE6E1DE)
val SurfaceVariantDark = Color(0xFF4E4540)
val OnSurfaceVariantDark = Color(0xFFD7C2B4)
val OutlineDark = Color(0xFF9E8C82)
val OutlineVariantDark = Color(0xFF4E4540)

// 背景色 (Dark) - 深暖色
val BackgroundDark = Color(0xFF1C1B1A)
val OnBackgroundDark = Color(0xFFE6E1DE)

// ============================================
// 兼容旧代码的别名 (使用新的品牌色)
// ============================================

// 品牌主色
val Primary = AmberPrimary
val OnPrimary = AmberOnPrimary
val PrimaryContainer = AmberPrimaryContainer
val OnPrimaryContainer = AmberOnPrimaryContainer

// 品牌次色
val Secondary = DeepOrangeSecondary
val OnSecondary = DeepOrangeOnSecondary
val SecondaryContainer = DeepOrangeSecondaryContainer
val OnSecondaryContainer = DeepOrangeOnSecondaryContainer

// 第三色
val Tertiary = WarmBrownTertiary
val OnTertiary = WarmBrownOnTertiary
val TertiaryContainer = WarmBrownTertiaryContainer
val OnTertiaryContainer = WarmBrownOnTertiaryContainer
