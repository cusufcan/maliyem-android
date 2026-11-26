package com.mercan.maliyem.ui.categories

import android.database.sqlite.SQLiteException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mercan.maliyem.data.local.CategoryEntity
import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.data.repository.CategoryRepository
import com.mercan.maliyem.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val KEY_TYPE = "type"

data class CategoryItem(val category: CategoryEntity, val count: Int)

data class CategoriesUiState(val type: TransactionType, val items: List<CategoryItem>)

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val categories: CategoryRepository,
    transactions: TransactionRepository,
) : ViewModel() {

    private val type = savedState.getStateFlow(KEY_TYPE, TransactionType.EXPENSE.name).map(TransactionType::valueOf)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<CategoriesUiState?> = type
        .flatMapLatest { t -> categories.observeByType(t).map { t to it } }
        .combine(transactions.observeCountsByCategory()) { (t, list), counts ->
            CategoriesUiState(t, list.map { CategoryItem(it, counts[it.id] ?: 0) })
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setType(value: TransactionType) { savedState[KEY_TYPE] = value.name }

    private val _writeFailed = MutableStateFlow(false)
    val writeFailed = _writeFailed.asStateFlow()

    fun add(type: TransactionType, name: String, icon: String) = write {
        categories.add(type, name.trim(), icon)
    }

    fun update(category: CategoryEntity, name: String, icon: String) = write {
        categories.update(category.copy(name = name.trim(), icon = icon))
    }

    fun delete(category: CategoryEntity) = write { categories.delete(category) }

    private fun write(operation: suspend () -> Unit) {
        viewModelScope.launch {
            _writeFailed.value = false
            try {
                operation()
            } catch (_: SQLiteException) {
                _writeFailed.value = true
            }
        }
    }
}
