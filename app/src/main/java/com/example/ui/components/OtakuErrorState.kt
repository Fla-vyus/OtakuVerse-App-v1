package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed class OtakuErrorKind {
    object NoToken : OtakuErrorKind()
    object InvalidToken : OtakuErrorKind() // 401 / 403
    data class NetworkOrServer(val message: String? = null) : OtakuErrorKind() // Timeout / 5xx / IOException
}

@Composable
fun OtakuErrorState(
    kind: OtakuErrorKind,
    onRetry: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val details = when (kind) {
        is OtakuErrorKind.NoToken -> {
            ErrorDetails(
                icon = Icons.Default.KeyOff,
                title = "Chave de acesso não configurada",
                message = "Configure sua chave de acesso em Perfil > Avançado para carregar o catálogo.",
                isProfileRedirect = true,
                buttonText = "Ir para Perfil > Avançado"
            )
        }
        is OtakuErrorKind.InvalidToken -> {
            ErrorDetails(
                icon = Icons.Default.Lock,
                title = "Chave inválida ou expirada",
                message = "A chave de acesso configurada não tem permissão ou expirou. Verifique suas configurações em Perfil > Avançado.",
                isProfileRedirect = true,
                buttonText = "Ir para Perfil > Avançado"
            )
        }
        is OtakuErrorKind.NetworkOrServer -> {
            ErrorDetails(
                icon = Icons.Default.WifiOff,
                title = "Falha de conexão",
                message = kind.message?.takeIf { it.isNotBlank() }
                    ?: "Não foi possível conectar aos servidores. Verifique sua conexão e tente novamente.",
                isProfileRedirect = false,
                buttonText = "Tentar novamente"
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = details.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(52.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = details.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = details.message,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(20.dp))
            if (details.isProfileRedirect) {
                Button(
                    onClick = onOpenProfile,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(details.buttonText)
                }
            } else {
                Button(
                    onClick = onRetry,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(details.buttonText)
                }
            }
        }
    }
}

private data class ErrorDetails(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val message: String,
    val isProfileRedirect: Boolean,
    val buttonText: String
)
