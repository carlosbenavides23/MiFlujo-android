package com.carlos.miflujo.domain.validation

import com.carlos.miflujo.domain.model.MovementCategory
import com.carlos.miflujo.domain.model.MovementSubcategory
import com.carlos.miflujo.domain.model.MovementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementBusinessRuleValidatorTest {
    @Test
    fun `accepts every valid movement combination`() {
        val validCombinations = listOf(
            combination(
                type = MovementType.INCOME,
                category = MovementCategory.GENERAL_INCOME,
            ),
            combination(
                type = MovementType.EXPENSE,
                category = MovementCategory.FIXED_COST,
                subcategory = MovementSubcategory.WATER,
            ),
            combination(
                type = MovementType.EXPENSE,
                category = MovementCategory.FIXED_COST,
                subcategory = MovementSubcategory.ELECTRICITY,
            ),
            combination(
                type = MovementType.EXPENSE,
                category = MovementCategory.FIXED_COST,
                subcategory = MovementSubcategory.INTERNET,
            ),
            combination(
                type = MovementType.EXPENSE,
                category = MovementCategory.MAINTENANCE,
            ),
            combination(
                type = MovementType.EXPENSE,
                category = MovementCategory.OTHER,
            ),
        )

        validCombinations.forEach { combination ->
            assertTrue(validate(combination).isEmpty())
        }
    }

    @Test
    fun `rejects non-positive amounts`() {
        listOf(0L, -1L).forEach { amountMinor ->
            assertEquals(
                setOf(MovementBusinessRuleViolation.AMOUNT_NOT_POSITIVE),
                MovementBusinessRuleValidator.validate(
                    amountMinor = amountMinor,
                    type = MovementType.EXPENSE,
                    category = MovementCategory.OTHER,
                    subcategory = null,
                ),
            )
        }
    }

    @Test
    fun `rejects invalid income combinations`() {
        assertEquals(
            setOf(MovementBusinessRuleViolation.INCOME_CATEGORY_NOT_GENERAL_INCOME),
            validate(
                combination(
                    type = MovementType.INCOME,
                    category = MovementCategory.OTHER,
                ),
            ),
        )
        assertEquals(
            setOf(MovementBusinessRuleViolation.INCOME_HAS_SUBCATEGORY),
            validate(
                combination(
                    type = MovementType.INCOME,
                    category = MovementCategory.GENERAL_INCOME,
                    subcategory = MovementSubcategory.WATER,
                ),
            ),
        )
    }

    @Test
    fun `rejects invalid expense combinations`() {
        val invalidCombinations = listOf(
            combination(
                type = MovementType.EXPENSE,
                category = MovementCategory.GENERAL_INCOME,
            ) to MovementBusinessRuleViolation.EXPENSE_USES_GENERAL_INCOME_CATEGORY,
            combination(
                type = MovementType.EXPENSE,
                category = MovementCategory.FIXED_COST,
            ) to MovementBusinessRuleViolation.FIXED_COST_HAS_NO_SUBCATEGORY,
            combination(
                type = MovementType.EXPENSE,
                category = MovementCategory.MAINTENANCE,
                subcategory = MovementSubcategory.ELECTRICITY,
            ) to MovementBusinessRuleViolation.NON_FIXED_COST_HAS_SUBCATEGORY,
            combination(
                type = MovementType.EXPENSE,
                category = MovementCategory.OTHER,
                subcategory = MovementSubcategory.INTERNET,
            ) to MovementBusinessRuleViolation.NON_FIXED_COST_HAS_SUBCATEGORY,
        )

        invalidCombinations.forEach { (combination, expectedViolation) ->
            assertEquals(setOf(expectedViolation), validate(combination))
        }
    }

    private fun validate(combination: MovementCombination): Set<MovementBusinessRuleViolation> =
        MovementBusinessRuleValidator.validate(
            amountMinor = combination.amountMinor,
            type = combination.type,
            category = combination.category,
            subcategory = combination.subcategory,
        )

    private fun combination(
        type: MovementType,
        category: MovementCategory,
        subcategory: MovementSubcategory? = null,
        amountMinor: Long = 1L,
    ) = MovementCombination(
        amountMinor = amountMinor,
        type = type,
        category = category,
        subcategory = subcategory,
    )
}

private data class MovementCombination(
    val amountMinor: Long,
    val type: MovementType,
    val category: MovementCategory,
    val subcategory: MovementSubcategory?,
)
