package com.carlos.miflujo.domain.validation

import com.carlos.miflujo.domain.model.MovementCategory
import com.carlos.miflujo.domain.model.MovementSubcategory
import com.carlos.miflujo.domain.model.MovementType

enum class MovementBusinessRuleViolation {
    AMOUNT_NOT_POSITIVE,
    INCOME_CATEGORY_NOT_GENERAL_INCOME,
    INCOME_HAS_SUBCATEGORY,
    EXPENSE_USES_GENERAL_INCOME_CATEGORY,
    FIXED_COST_HAS_NO_SUBCATEGORY,
    NON_FIXED_COST_HAS_SUBCATEGORY,
}

object MovementBusinessRuleValidator {
    fun validate(
        amountMinor: Long,
        type: MovementType,
        category: MovementCategory,
        subcategory: MovementSubcategory?,
    ): Set<MovementBusinessRuleViolation> = buildSet {
        if (amountMinor <= 0L) {
            add(MovementBusinessRuleViolation.AMOUNT_NOT_POSITIVE)
        }

        when (type) {
            MovementType.INCOME -> {
                if (category != MovementCategory.GENERAL_INCOME) {
                    add(MovementBusinessRuleViolation.INCOME_CATEGORY_NOT_GENERAL_INCOME)
                }
                if (subcategory != null) {
                    add(MovementBusinessRuleViolation.INCOME_HAS_SUBCATEGORY)
                }
            }

            MovementType.EXPENSE -> when (category) {
                MovementCategory.GENERAL_INCOME ->
                    add(MovementBusinessRuleViolation.EXPENSE_USES_GENERAL_INCOME_CATEGORY)

                MovementCategory.FIXED_COST -> {
                    if (subcategory == null) {
                        add(MovementBusinessRuleViolation.FIXED_COST_HAS_NO_SUBCATEGORY)
                    }
                }

                MovementCategory.MAINTENANCE,
                MovementCategory.OTHER,
                -> {
                    if (subcategory != null) {
                        add(MovementBusinessRuleViolation.NON_FIXED_COST_HAS_SUBCATEGORY)
                    }
                }
            }
        }
    }
}
