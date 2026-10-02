package org.example.project.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.example.project.database.FolderEntity // <-- Imported the REAL database entity

@Composable
fun TopicListScreen(
    folders: List<FolderEntity>, // <-- Now expects the REAL entity
    onFolderClick: (FolderEntity) -> Unit,
    onCreateFolder: (String) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Top Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Subjects",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Text("+", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimary)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))

            // File Manager Grid
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(folders) { folder ->
                    FolderCard(folder = folder, onClick = { onFolderClick(folder) })
                }
            }
        }

        // Add Folder Dialog
        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("New Folder") },
                text = {
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        placeholder = { Text("e.g. Research, Ideas, Work") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Gray
                        )
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newFolderName.isNotBlank()) {
                                onCreateFolder(newFolderName)
                                newFolderName = ""
                                showAddDialog = false
                            }
                        }
                    ) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
fun FolderCard(folder: FolderEntity, onClick: () -> Unit) { // <-- Uses REAL entity here too
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            NotionStyleFolderIcon(modifier = Modifier.size(42.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = folder.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun NotionStyleFolderIcon(modifier: Modifier = Modifier) {
    val iconColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        val cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())

        drawRoundRect(
            color = iconColor.copy(alpha = 0.5f),
            topLeft = Offset(0f, 0f),
            size = Size(size.width * 0.45f, size.height * 0.3f),
            cornerRadius = cornerRadius
        )

        drawRoundRect(
            color = iconColor,
            topLeft = Offset(0f, size.height * 0.2f),
            size = Size(size.width, size.height * 0.8f),
            cornerRadius = cornerRadius
        )
    }
}