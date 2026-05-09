package com.carlos.miflujo.domain.model

data class ExpenseBreakdown(
    val fixedCostMinor: Long = 0,
    val maintenanceMinor: Long = 0,
    val otherMinor: Long = 0,
    val waterMinor: Long = 0,
    val electricityMinor: Long = 0,
    val internetMinor: Long = 0
)
