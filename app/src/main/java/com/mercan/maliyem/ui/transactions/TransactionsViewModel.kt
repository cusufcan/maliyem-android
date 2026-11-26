package com.mercan.maliyem.ui.transactions

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.mercan.maliyem.data.local.AccountEntity
import com.mercan.maliyem.data.local.CategoryEntity
import com.mercan.maliyem.data.local.DefaultAccounts
import com.mercan.maliyem.data.local.DefaultCategories
import com.mercan.maliyem.data.repository.AccountRepository
import com.mercan.maliyem.data.repository.CategoryRepository
import com.mercan.maliyem.data.repository.TransactionRepository
import com.mercan.maliyem.data.repository.TransferRepository
import com.mercan.maliyem.ui.CalendarDays
import com.mercan.maliyem.ui.MonthSelection
import com.mercan.maliyem.ui.budgets.OVERALL_BUDGET
import com.mercan.maliyem.ui.navigation.BudgetTransactions
import com.mercan.maliyem.ui.summary.adjacentMonths
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn

/** Aynı süreçte yeniden oluşturulan ekran durumu korunur. */
internal val transactionsSession = UUID.randomUUID().toString()

private const val KEY_SESSION = "session"
private const val KEY_FILTERS = "filters"
private const val KEY_SEARCH = "search"

internal data class TransactionsPeriod(
    val month: YearMonth,
    val today: LocalDate,
    val previous: YearMonth?,
    val next: YearMonth?,
)

internal data class TransactionsOptions(val categories: List<CategoryEntity>, val accounts: List<AccountEntity>)

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val months: MonthSelection,
    repository: TransactionRepository,
    transfers: TransferRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
    calendar: CalendarDays,
) : ViewModel() {

    init {
        if (savedState.get<String>(KEY_SESSION) != transactionsSession) {
            val budgetFilters = if (savedState.contains("budgetCategoryId")) {
                val id = savedState.toRoute<BudgetTransactions>().budgetCategoryId
                TransactionsFilters(
                    type = TransactionFilter.EXPENSE,
                    categoryIds = if (id == OVERALL_BUDGET) emptySet() else setOf(id),
                ).toBundle()
            } else null
            savedState[KEY_FILTERS] = budgetFilters
            savedState.remove<String>(KEY_SEARCH)
            savedState[KEY_SESSION] = transactionsSession
        }
    }

    val search = savedState.getStateFlow<String?>(KEY_SEARCH, null)

    private val options = combine(categories.observeAll(), accounts.observeAll(), ::TransactionsOptions)
    private val savedFilters = savedState.getStateFlow<Bundle?>(KEY_FILTERS, null).map(::readFilters)

    /** Silinmiş kategori ve hesaplar süzgeçte tutulmaz; arşivli hesaplar geçmişe dahildir. */
    internal val selection = combine(savedFilters, options) { filters, choices ->
        val categoryIds = choices.categories.map { it.id }.toSet()
        val accountIds = choices.accounts.map { it.id }.toSet()
        filters.copy(
            categoryIds = filters.categoryIds.intersect(categoryIds),
            accountIds = if (accountIds.size >= 2) filters.accountIds.intersect(accountIds) else emptySet(),
        ) to choices
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val dates = combine(months.month, calendar.days) { month, today -> month to today }

    internal val period = combine(
        dates, repository.observeMonthsWithData(), transfers.observeMonthsWithData(),
    ) { (month, today), records, moves ->
        val (previous, next) = adjacentMonths(month, records + moves, YearMonth.from(today))
        TransactionsPeriod(month, today, previous, next)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private data class Query(
        val dates: ClosedRange<LocalDate>,
        val type: TransactionFilter,
        val categories: Set<Long>,
        val accounts: Set<Long>,
        val includesTransfers: Boolean,
    )

    private val localizedNames = MutableStateFlow(emptyMap<Int, String>())
    private val inputs = combine(selection, dates, search, localizedNames) { selected, (month, today), query, names ->
        selected?.let { (filters, choices) ->
            val bounds = filters.dates(month, today)
            Query(bounds, filters.type, filters.categoryIds, filters.accountIds, filters.includesTransfers) to
                TransactionSearch(
                    query.orEmpty(),
                    filters,
                    choices.categories.associate { it.id to (it.name ?: names[DefaultCategories.nameRes(it.key)].orEmpty()) },
                    choices.accounts.associate { it.id to (it.name ?: names[DefaultAccounts.nameRes(it.key)].orEmpty()) },
                )
        }
    }.flowOn(Dispatchers.Default).shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    @OptIn(ExperimentalCoroutinesApi::class)
    val result: StateFlow<TransactionsResult?> = inputs.map { it?.first }.distinctUntilChanged()
        .flatMapLatest { query ->
            if (query == null) return@flatMapLatest flowOf(null)
            val (bounds, type, categories, accounts, includesTransfers) = query
            val rows = if (type == TransactionFilter.TRANSFER) flowOf(emptyList()) else
                repository.observeFiltered(bounds.start, bounds.endInclusive, type.transactionType, categories, accounts)
            val moves = if (includesTransfers) transfers.observeFiltered(bounds.start, bounds.endInclusive, accounts) else
                flowOf(emptyList())
            combine(rows, moves, inputs) { records, transfers, input ->
                val matcher = input?.takeIf { it.first == query }?.second ?: return@combine null
                TransactionsResult(groupByDay(records.filter(matcher::matches), transfers.filter(matcher::matches)))
            }.onStart { emit(null) }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    internal fun setLocalizedNames(names: Map<Int, String>) { localizedNames.value = names }
    fun selectMonth(month: YearMonth) { months.select(month) }
    fun setSearch(text: String?) { savedState[KEY_SEARCH] = text }
    fun setFilters(filters: TransactionsFilters) { savedState[KEY_FILTERS] = filters.toBundle() }
}

internal fun TransactionsFilters.toBundle() = Bundle().apply {
    putString("type", type.name)
    putLongArray("categories", categoryIds.toLongArray())
    putLongArray("accounts", accountIds.toLongArray())
    putString("range", range.name)
    customStart?.let { putLong("start", it.toEpochDay()) }
    customEnd?.let { putLong("end", it.toEpochDay()) }
    minimum?.let { putLong("min", it) }
    maximum?.let { putLong("max", it) }
}

internal fun readFilters(bundle: Bundle?): TransactionsFilters = bundle?.run {
    TransactionsFilters(
        type = getString("type")?.let(TransactionFilter::valueOf) ?: TransactionFilter.ALL,
        categoryIds = getLongArray("categories")?.toSet().orEmpty(),
        accountIds = getLongArray("accounts")?.toSet().orEmpty(),
        range = getString("range")?.let(TransactionsRange::valueOf) ?: TransactionsRange.MONTH,
        customStart = getLong("start").takeIf { containsKey("start") }?.let(LocalDate::ofEpochDay),
        customEnd = getLong("end").takeIf { containsKey("end") }?.let(LocalDate::ofEpochDay),
        minimum = getLong("min").takeIf { containsKey("min") },
        maximum = getLong("max").takeIf { containsKey("max") },
    )
} ?: TransactionsFilters()
