package com.example.serverapp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.serverapp.viewmodel.MainActivityViewModel

@Composable
fun Info(mainActivityViewModel: MainActivityViewModel){
    val rowCount by mainActivityViewModel.rowCount.observeAsState(null)
    val uiServerMode by mainActivityViewModel.uiServerMode.observeAsState(null)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ){
            Row(verticalAlignment = Alignment.CenterVertically) {
                StyledText(text = "Total rows in database:")
                Spacer(Modifier.weight(1f))
                StyledText(text = "$rowCount")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val uiServerModeText="UI requests: ${if (uiServerMode==true) "Next.JS Server" else "Static Assets" }";
                StyledText(text = uiServerModeText)
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = uiServerMode==true,
                    onCheckedChange = { newValue ->
                        mainActivityViewModel.setUIServerMode(newValue)
                    }
                )
            }
        }
    }
}

@Composable
private fun StyledText(text: String) {

    Text(
        text = text,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(5.dp),
        fontWeight = FontWeight.Bold,
        fontSize=15.sp
    )
}
