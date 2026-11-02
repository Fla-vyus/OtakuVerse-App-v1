package com.example.ui.screens.profile

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ProfileAchievementsSection(achievements: List<OtakuAchievement>) {
    SectionTitle(title = "CONQUISTAS")
    LazyRow(
        contentPadding = PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(achievements, key = { it.id }) { ach ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (ach.isUnlocked) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                elevation = CardDefaults.cardElevation(if (ach.isUnlocked) 2.dp else 0.dp),
                modifier = Modifier
                    .width(140.dp)
                    .height(110.dp)
                    .border(
                        1.dp,
                        if (ach.isUnlocked) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent,
                        RoundedCornerShape(14.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = ach.iconEmoji, fontSize = 20.sp)
                        if (ach.isUnlocked) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Desbloqueada", tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                        } else {
                            Icon(imageVector = Icons.Default.Lock, contentDescription = "Bloqueada", tint = Color.Gray.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                        }
                    }

                    Column {
                        Text(
                            text = ach.title,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (ach.isUnlocked) MaterialTheme.colorScheme.onSurface else Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = ach.description,
                            fontSize = 9.sp,
                            color = if (ach.isUnlocked) MaterialTheme.colorScheme.onSurfaceVariant else Color.Gray.copy(alpha = 0.7f),
                            lineHeight = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
