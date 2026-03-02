package com.fizzzli.listapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fizzzli.listapp.data.local.entity.ListTemplateEntity
import com.fizzzli.listapp.data.repository.ListRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ListUiState(
    val templates: List<ListTemplateEntity> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class CreateListViewModel @Inject constructor(
    private val repository: ListRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ListUiState())
    val uiState: StateFlow<ListUiState> = _uiState.asStateFlow()

    init {
        loadTemplates()
    }

    private fun loadTemplates() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                repository.getSystemTemplates().collect { templates ->
                    _uiState.value = _uiState.value.copy(
                        templates = templates,
                        isLoading = false
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

    fun createList(title: String, templateId: String, ownerId: String = "local-user") {
        viewModelScope.launch {
            try {
                val newList = com.fizzzli.listapp.data.local.entity.UserListEntity(
                    id = UUID.randomUUID().toString(),
                    templateId = templateId,
                    title = title,
                    isPublic = false,
                    forkedFrom = null,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    ownerId = ownerId
                )
                repository.insertList(newList)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }
}
