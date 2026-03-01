package com.fizzzli.listapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fizzzli.listapp.data.local.entity.ListItemEntity
import com.fizzzli.listapp.data.local.entity.UserListEntity
import com.fizzzli.listapp.data.repository.ListRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ListDetailUiState(
    val list: UserListEntity? = null,
    val items: List<ListItemEntity> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ListDetailViewModel @Inject constructor(
    private val repository: ListRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ListDetailUiState())
    val uiState: StateFlow<ListDetailUiState> = _uiState.asStateFlow()

    fun loadList(listId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val list = repository.getListById(listId)
                if (list != null) {
                    _uiState.value = _uiState.value.copy(list = list)
                    // Load items
                    repository.getItemsByList(listId).collect { items ->
                        _uiState.value = _uiState.value.copy(
                            items = items,
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "List not found"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun addItem(name: String, fieldsJson: String = "[]") {
        viewModelScope.launch {
            val listId = _uiState.value.list?.id ?: return@launch
            try {
                val newItem = ListItemEntity(
                    id = UUID.randomUUID().toString(),
                    listId = listId,
                    name = name,
                    status = "PENDING",
                    fieldsJson = fieldsJson,
                    createdAt = System.currentTimeMillis(),
                    completedAt = null
                )
                repository.insertItem(newItem)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun updateItemStatus(itemId: String, status: String) {
        viewModelScope.launch {
            try {
                val completedAt = if (status == "COMPLETED") System.currentTimeMillis() else null
                repository.updateItemStatus(itemId, status, completedAt)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun deleteItem(item: ListItemEntity) {
        viewModelScope.launch {
            try {
                repository.deleteItem(item)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun deleteList() {
        viewModelScope.launch {
            val list = _uiState.value.list ?: return@launch
            try {
                repository.deleteList(list)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }
}
