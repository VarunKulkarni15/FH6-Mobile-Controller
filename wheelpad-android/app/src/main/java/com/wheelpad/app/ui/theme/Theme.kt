package com.wheelpad.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.wheelpad.app.R

// Authentic Motorsport Chakra Petch Typography
val ChakraPetch = FontFamily(
    Font(R.font.chakra_petch_medium, FontWeight.Normal),
    Font(R.font.chakra_petch_medium, FontWeight.Medium),
    Font(R.font.chakra_petch_bold, FontWeight.Bold),
    Font(R.font.chakra_petch_bold, FontWeight.ExtraBold),
    Font(R.font.chakra_petch_bold, FontWeight.Black)
)

val HorizonTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = ChakraPetch,
        fontWeight = FontWeight.Black,
        fontSize = 22.sp
    ),
    titleMedium = TextStyle(
        fontFamily = ChakraPetch,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = ChakraPetch,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = ChakraPetch,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp
    ),
    labelSmall = TextStyle(
        fontFamily = ChakraPetch,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp
    )
)
