package com.carlos.miflujo.ui.movement

import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.Movement
import com.carlos.miflujo.domain.model.MovementCategory
import com.carlos.miflujo.domain.model.MovementSubcategory
import com.carlos.miflujo.domain.model.MovementType
import java.time.LocalDate
import java.time.YearMonth

enum class MovementFilter(val label: String) {
    All(label = "Todos"),
    Income(label = "Ingresos"),
    Expense(label = "Egresos"),
}

data class MovementUiState(
    val selectedMonth: YearMonth = YearMonth.now(),
    val selectedFilter: MovementFilter = MovementFilter.All,
    val movements: List<Movement> = emptyList(),
)

data class AddMovementInput(
    val type: MovementType,
    val amountMinor: Long,
    val currency: Currency,
    val date: LocalDate,
    val category: MovementCategory,
    val subcategory: MovementSubcategory?,
    val detail: String?,
)
