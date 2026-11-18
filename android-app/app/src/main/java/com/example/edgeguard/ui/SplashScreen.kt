package com.example.edgeguard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.edgeguard.R
import com.example.edgeguard.ui.theme.EdgeGuardColors
import kotlinx.coroutines.delay

/** Splash from the design: blue background, logo, "EDGEGUARD: DEEPFAKE DETECTION". */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    StatusBarIcons(darkIcons = false)
    LaunchedEffect(Unit) {
        delay(1500)
        onDone()
    }

    // splash_title is one line "EDGEGUARD: DEEPFAKE DETECTION"; the design shows two lines
    val full = stringResource(R.string.splash_title)
    val first = full.substringBefore(":") + ":"
    val second = full.substringAfter(":").trim()

    Column(
        Modifier
            .fillMaxSize()
            .background(EdgeGuardColors.SplashBlue),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        EdgeGuardLogo(size = 150.dp, onDark = true)
        Spacer(Modifier.height(24.dp))
        Text(
            text = first,
            color = Color.White,
            fontSize = 30.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        Text(
            text = second,
            color = Color.White,
            fontSize = 30.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
    }
}
