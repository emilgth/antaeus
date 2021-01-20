package io.pleo.antaeus.core.services

import io.mockk.every
import io.mockk.mockk
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.*

internal class BillingServiceTest {
    private val money1 = io.pleo.antaeus.models.Money(BigDecimal.valueOf(100), Currency.DKK)

    private val invoice1 = Invoice(1, 1, money1, InvoiceStatus.PENDING)
    private val invoice2 = Invoice(2, 404, money1, InvoiceStatus.PENDING)
    private val invoice3 = Invoice(3, 3, money1, InvoiceStatus.PENDING)
    private val invoice4 = Invoice(4, 4, money1, InvoiceStatus.PENDING)

    private val processedInvoice1 = Invoice(1, 1, money1, InvoiceStatus.PAID)
    private val processedInvoice2 = Invoice(2, 404, money1, InvoiceStatus.CUSTOMER_NOT_FOUND)
    private val processedInvoice3 = Invoice(3, 3, money1, InvoiceStatus.CURRENCY_MISMATCH)
    private val processedInvoice4 = Invoice(4, 4, money1, InvoiceStatus.NETWORK_EXCEPTION)

    private val processedInvoicesList =
        listOf<Invoice>(processedInvoice1, processedInvoice2, processedInvoice3, processedInvoice4)

    private val dal = mockk<AntaeusDal> {
        every { fetchPendingInvoices() } returns listOf(invoice1, invoice2, invoice3, invoice4)
        every { updateInvoice(processedInvoice1) } returns Unit
        every { updateInvoice(processedInvoice2) } returns Unit
        every { updateInvoice(processedInvoice3) } returns Unit
        every { updateInvoice(processedInvoice4) } returns Unit
    }

    private val paymentProvider = mockk<PaymentProvider> {
        every { charge(invoice1) } returns true
        every { charge(invoice2) } throws CustomerNotFoundException(invoice2.customerId)
        every { charge(invoice3) } throws CurrencyMismatchException(invoice3.id, invoice3.customerId)
        every { charge(invoice4) } throws NetworkException()
    }

    private val billingService = BillingService(dal = dal, paymentProvider = paymentProvider)

    @Test
    fun `will correctly roll over month and add year if it is december`() {
        val date = LocalDate.of(2021, 12, 1)
        val expectedDate = Date.from(LocalDate.of(2022, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC))
        val result = billingService.scheduleBilling(date)
        assertEquals(expectedDate, result)
    }

    @Test
    fun `will return the list of processed invoices`() {
        val result = billingService.billPendingInvoices()
        assertTrue(processedInvoicesList.containsAll(result))
    }
}