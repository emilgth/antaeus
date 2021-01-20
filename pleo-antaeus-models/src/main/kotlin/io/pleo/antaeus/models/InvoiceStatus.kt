package io.pleo.antaeus.models

enum class InvoiceStatus {
    PENDING,
    PAID,
    CUSTOMER_NOT_FOUND,
    CURRENCY_MISMATCH,
    NETWORK_EXCEPTION,
    INSUFFICIENT_BALANCE
}
