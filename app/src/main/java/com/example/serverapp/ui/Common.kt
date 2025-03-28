package com.example.serverapp.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch


@Composable
fun StyledError(text:String){
    Text(
        text = text,
        style = TextStyle(
            color = Color.Red,
            fontWeight = FontWeight.Light
        )
    )
}

@Composable
fun StyledText(text: String) {

    Text(
        text = text,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(5.dp),
        fontWeight = FontWeight.Bold,
        fontSize=15.sp
    )
}

@Composable
fun StyledCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(10.dp),
        elevation = CardDefaults.cardElevation(4.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            content = content
        )
    }
}

@Composable
fun StyledRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.SpaceBetween,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = verticalAlignment,
        content = content
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CommonButton(
    buttonText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 6.dp,
    content: @Composable () -> Unit = { Text(text = buttonText) },
    longPressToastMessage: String = "Long press detected"
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val customPadding = PaddingValues(
        start = 10.dp,
        top = 5.dp,
        end = 10.dp,
        bottom = 5.dp
    )
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(cornerRadius),
        contentPadding = customPadding,
        // Use combinedClickable for long press detection
        modifier = modifier.combinedClickable(
            onClick = onClick,
            onLongClick = {
                scope.launch {
                    Toast.makeText(context, longPressToastMessage, Toast.LENGTH_SHORT).show()
                }
            }
        )
    ) {
        content()
    }
}



