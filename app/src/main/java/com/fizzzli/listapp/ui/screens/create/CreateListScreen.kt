package com.fizzzli.listapp.ui.screens.create

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fizzzli.listapp.data.local.entity.ListTemplateEntity
import com.fizzzli.listapp.ui.viewmodel.CreateListViewModel
import com.fizzzli.listapp.ui.viewmodel.ListUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateListScreen(
    onNavigateBack: () -> Unit,
    viewModel: CreateListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var title by remember { mutableStateOf("") }
    var selectedTemplate by remember { mutableStateOf<ListTemplateEntity?>(null) }
    var showTemplateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("创建列表") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("列表标题") },
                placeholder = { Text("例如：2026 Live 计划") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = selectedTemplate?.name ?: "",
                onValueChange = { },
                label = { Text("列表类型") },
                placeholder = { Text("选择模板") },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showTemplateDialog = true },
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = { showTemplateDialog = true }) {
                        Text("▼")
                    }
                }
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    if (title.isNotBlank() && selectedTemplate != null) {
                        viewModel.createList(title, selectedTemplate!!.id)
                        onNavigateBack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = title.isNotBlank() && selectedTemplate != null
            ) {
                Text("创建")
            }
        }

        // Template selection dialog
        if (showTemplateDialog) {
            TemplateSelectionDialog(
                templates = uiState.templates,
                onTemplateSelected = {
                    selectedTemplate = it
                    showTemplateDialog = false
                },
                onDismiss = { showTemplateDialog = false }
            )
        }
    }
}

@Composable
private fun TemplateSelectionDialog(
    templates: List<ListTemplateEntity>,
    onTemplateSelected: (ListTemplateEntity) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择列表类型") },
        text = {
            LazyColumn {
                items(templates) { template ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onTemplateSelected(template) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(template.icon, style = MaterialTheme.typography.headlineSmall)
                            Text(template.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = { },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
