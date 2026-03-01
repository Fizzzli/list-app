package com.fizzzli.listapp.ui.screens.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fizzzli.listapp.data.local.entity.ListItemEntity
import com.fizzzli.listapp.ui.viewmodel.ListDetailViewModel
import com.fizzzli.listapp.ui.viewmodel.ListDetailUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListDetailScreen(
    listId: String,
    onNavigateBack: () -> Unit,
    viewModel: ListDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddItemDialog by remember { mutableStateOf(false) }
    var selectedItemFilter by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(listId) {
        viewModel.loadList(listId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.list?.title ?: "列表详情") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { /* TODO: Edit list */ }) {
                        Icon(Icons.Default.Edit, "编辑")
                    }
                    IconButton(onClick = {
                        viewModel.deleteList()
                        onNavigateBack()
                    }) {
                        Icon(Icons.Default.Delete, "删除")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddItemDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, "添加条目")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedItemFilter == null,
                    onClick = { selectedItemFilter = null },
                    label = { Text("全部") }
                )
                FilterChip(
                    selected = selectedItemFilter == "PENDING",
                    onClick = { selectedItemFilter = "PENDING" },
                    label = { Text("待办") }
                )
                FilterChip(
                    selected = selectedItemFilter == "COMPLETED",
                    onClick = { selectedItemFilter = "COMPLETED" },
                    label = { Text("已完成") }
                )
            }

            // Items list
            val filteredItems = if (selectedItemFilter == null) {
                uiState.items
            } else {
                uiState.items.filter { it.status == selectedItemFilter }
            }

            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无条目",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredItems) { item ->
                        ListItemCard(
                            item = item,
                            onStatusChange = { newStatus ->
                                viewModel.updateItemStatus(item.id, newStatus)
                            },
                            onDelete = {
                                viewModel.deleteItem(item)
                            }
                        )
                    }
                }
            }
        }

        // Add item dialog
        if (showAddItemDialog) {
            AddItemDialog(
                onAddItem = { name ->
                    viewModel.addItem(name)
                    showAddItemDialog = false
                },
                onDismiss = { showAddItemDialog = false }
            )
        }
    }
}

@Composable
private fun ListItemCard(
    item: ListItemEntity,
    onStatusChange: (String) -> Unit,
    onDelete: () -> Unit
) {
    val isCompleted = item.status == "COMPLETED"
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isCompleted,
                    onCheckedChange = {
                        onStatusChange(if (it) "COMPLETED" else "PENDING")
                    }
                )
                Column {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isCompleted) {
                        Text(
                            text = "已完成",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun AddItemDialog(
    onAddItem: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加条目") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("条目名称") },
                placeholder = { Text("例如：告五人演唱会") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onAddItem(name)
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
