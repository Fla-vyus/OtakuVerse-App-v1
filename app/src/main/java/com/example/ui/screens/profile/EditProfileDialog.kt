package com.example.ui.screens.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.LibraryEntity
import com.example.data.model.ImageUrlUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileDialog(
    currentNickname: String,
    currentBio: String,
    currentAccent: String,
    currentFavWorkId: String?,
    libraryWorks: List<LibraryEntity>,
    onPickAvatar: () -> Unit,
    onPickBanner: () -> Unit,
    onRemoveAvatar: () -> Unit,
    onRemoveBanner: () -> Unit,
    onSelectWorkCoverAsBanner: (String) -> Unit,
    onSave: (String, String, String, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var editNickname by remember { mutableStateOf(currentNickname) }
    var editBio by remember { mutableStateOf(currentBio) }
    var editAccent by remember { mutableStateOf(currentAccent) }
    var selectedFavWorkId by remember { mutableStateOf(currentFavWorkId) }
    var showWorkCoverPicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Editar Perfil", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Button(
                    onClick = {
                        onSave(editNickname, editBio, editAccent, selectedFavWorkId)
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Salvar")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Imagem de Avatar & Capa Buttons
            Text(text = "Foto de perfil e capa", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onPickAvatar,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Foto (1:1)", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onPickBanner,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Capa (16:9)", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onRemoveAvatar, modifier = Modifier.weight(1f)) {
                    Text("Remover foto", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onRemoveBanner, modifier = Modifier.weight(1f)) {
                    Text("Remover capa", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            }

            // Usar capa de uma obra da biblioteca
            if (libraryWorks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { showWorkCoverPicker = !showWorkCoverPicker },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Usar capa de uma obra da minha biblioteca", fontSize = 12.sp)
                }

                AnimatedVisibility(visible = showWorkCoverPicker) {
                    LazyRow(
                        contentPadding = PaddingValues(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(libraryWorks, key = { it.id }) { work ->
                            Box(
                                modifier = Modifier
                                    .size(width = 60.dp, height = 80.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        onSelectWorkCoverAsBanner(work.cover)
                                        showWorkCoverPicker = false
                                    }
                            ) {
                                AsyncImage(
                                    model = work.cover,
                                    contentDescription = work.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Apelido (máx 20 caracteres)
            OutlinedTextField(
                value = editNickname,
                onValueChange = { editNickname = it.take(20) },
                label = { Text("Apelido (máx 20)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Bio (máx 120 caracteres)
            OutlinedTextField(
                value = editBio,
                onValueChange = { editBio = it.take(120) },
                label = { Text("Bio / Sobre você (máx 120)") },
                minLines = 2,
                maxLines = 3,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Cor de destaque do perfil
            Text(text = "Cor de destaque do perfil:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("#3B82F6", "#EC4899", "#10B981", "#8B5CF6", "#F59E0B", "#EF4444", "#06B6D4").forEach { hex ->
                    val isSel = hex.equals(editAccent, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(ImageUrlUtils.parseColorHex(hex))
                            .border(if (isSel) 2.dp else 0.dp, Color.White, CircleShape)
                            .clickable { editAccent = hex }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
