package com.example.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.analytics.AnalyticsManager
import com.example.data.model.Transaction
import com.example.data.model.UserProfile
import com.example.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface HomeUiState {
    object Loading : HomeUiState
    data class Success(
        val transactions: List<Transaction>,
        val filteredTransactions: List<Transaction>,
        val totalIncome: Double,
        val totalExpense: Double,
        val netBalance: Double,
        val selectedFilter: TransactionFilter,
        val selectedCategory: String?,
        val userProfile: UserProfile?
    ) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

enum class TransactionFilter {
    ALL,
    INCOME,
    EXPENSE
}

class HomeViewModel(private val repository: TransactionRepository) : ViewModel() {

    private val _selectedFilter = MutableStateFlow(TransactionFilter.ALL)
    val selectedFilter: StateFlow<TransactionFilter> = _selectedFilter.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    val uiState: StateFlow<HomeUiState> = combine(
        repository.observeTransactions(),
        repository.observeUserProfile(),
        _selectedFilter,
        _selectedCategory
    ) { transactions, userProfile, filter, category ->
        val totalIncome = transactions.filter { it.type == "INCOME" }.sumOf { it.amount }
        val totalExpense = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
        val netBalance = totalIncome - totalExpense

        val filtered = transactions.filter { tx ->
            val matchesType = when (filter) {
                TransactionFilter.ALL -> true
                TransactionFilter.INCOME -> tx.type == "INCOME"
                TransactionFilter.EXPENSE -> tx.type == "EXPENSE"
            }
            val matchesCategory = category == null || tx.category == category
            matchesType && matchesCategory
        }

        val successState: HomeUiState = HomeUiState.Success(
            transactions = transactions,
            filteredTransactions = filtered,
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            netBalance = netBalance,
            selectedFilter = filter,
            selectedCategory = category,
            userProfile = userProfile
        )
        successState
    }.catch { error ->
        emit(HomeUiState.Error(error.localizedMessage ?: "Failed to load transactions"))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState.Loading
    )

    fun setFilter(filter: TransactionFilter) {
        _selectedFilter.value = filter
    }

    fun setCategory(category: String?) {
        _selectedCategory.value = if (_selectedCategory.value == category) null else category
    }

    fun addTransaction(
        title: String,
        amount: Double,
        type: String,
        category: String,
        date: String,
        note: String = "",
        method: String = "manual"
    ) {
        viewModelScope.launch {
            val result = repository.createTransaction(
                title = title,
                amount = amount,
                type = type,
                category = category,
                date = date,
                note = note
            )
            if (result.isSuccess) {
                AnalyticsManager.logAddTransaction(method)
                _snackbarMessage.value = "Transaction recorded successfully"
            } else {
                _snackbarMessage.value = result.exceptionOrNull()?.localizedMessage ?: "Failed to save entry"
            }
        }
    }

    fun updateMonthlyBudget(newBudget: Double) {
        viewModelScope.launch {
            val result = repository.updateMonthlyBudget(newBudget)
            if (result.isSuccess) {
                AnalyticsManager.logSetBudget()
                _snackbarMessage.value = "Monthly budget target saved"
            } else {
                _snackbarMessage.value = result.exceptionOrNull()?.localizedMessage ?: "Failed to update budget"
            }
        }
    }

    fun updateTransaction(
        transactionId: String,
        title: String,
        amount: Double,
        type: String,
        category: String,
        date: String,
        note: String = ""
    ) {
        viewModelScope.launch {
            val result = repository.updateTransaction(
                transactionId = transactionId,
                title = title,
                amount = amount,
                type = type,
                category = category,
                date = date,
                note = note
            )
            if (result.isSuccess) {
                _snackbarMessage.value = "Transaction updated"
            } else {
                _snackbarMessage.value = result.exceptionOrNull()?.localizedMessage ?: "Failed to update entry"
            }
        }
    }

    fun updateCurrency(newCurrency: String) {
        viewModelScope.launch {
            val result = repository.updateCurrency(newCurrency)
            if (result.isSuccess) {
                _snackbarMessage.value = "Currency updated to $newCurrency"
            } else {
                _snackbarMessage.value = result.exceptionOrNull()?.localizedMessage ?: "Failed to update currency"
            }
        }
    }

    fun addCustomCategory(newCategory: String) {
        viewModelScope.launch {
            val result = repository.addCustomCategory(newCategory.trim())
            if (result.isSuccess) {
                _snackbarMessage.value = "Category '$newCategory' added"
            } else {
                _snackbarMessage.value = result.exceptionOrNull()?.localizedMessage ?: "Failed to add category"
            }
        }
    }

    fun deleteTransaction(transactionId: String) {
        viewModelScope.launch {
            val result = repository.deleteTransaction(transactionId)
            if (result.isSuccess) {
                _snackbarMessage.value = "Transaction removed"
            } else {
                _snackbarMessage.value = result.exceptionOrNull()?.localizedMessage ?: "Failed to delete"
            }
        }
    }

    fun clearSnackbarMessage() {
        _snackbarMessage.value = null
    }

    class Factory(private val repository: TransactionRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(repository) as T
        }
    }
}
