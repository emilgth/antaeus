package io.pleo.antaeus.core.services

import io.mockk.*
import io.pleo.antaeus.core.exceptions.BillingNotScheduledException
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
import org.junit.jupiter.api.assertThrows
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
    private val invoice5 = Invoice(5, 5, money1, InvoiceStatus.PENDING)

    private val invoices =
        listOf(invoice1, invoice2, invoice3, invoice4, invoice5)

    private val processedInvoice1 = Invoice(1, 1, money1, InvoiceStatus.PAID)
    private val processedInvoice2 = Invoice(2, 404, money1, InvoiceStatus.CUSTOMER_NOT_FOUND)
    private val processedInvoice3 = Invoice(3, 3, money1, InvoiceStatus.CURRENCY_MISMATCH)
    private val processedInvoice4 = Invoice(4, 4, money1, InvoiceStatus.NETWORK_EXCEPTION)
    private val processedInvoice5 = Invoice(5, 5, money1, InvoiceStatus.INSUFFICIENT_BALANCE)

    private val processedInvoicesList =
        listOf(processedInvoice1, processedInvoice2, processedInvoice3, processedInvoice4, processedInvoice5)

    private val date: LocalDate = LocalDate.of(2021, 12, 1)
    private val expectedDate: Date = Date.from(LocalDate.of(2022, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC))
    private val pastDate: LocalDate = LocalDate.of(2011, 12, 1)

    private val dal = mockk<AntaeusDal> {
        every { fetchPendingInvoices() } returns invoices
        every { updateInvoice(processedInvoice1) } returns 1
        every { updateInvoice(processedInvoice2) } returns 404
        every { updateInvoice(processedInvoice3) } returns 3
        every { updateInvoice(processedInvoice4) } returns 4
        every { updateInvoice(processedInvoice5) } returns 5
    }

    private val paymentProvider = mockk<PaymentProvider> {
        every { charge(invoice1) } returns true
        every { charge(invoice2) } throws CustomerNotFoundException(invoice2.customerId)
        every { charge(invoice3) } throws CurrencyMismatchException(invoice3.id, invoice3.customerId)
        every { charge(invoice4) } throws NetworkException()
        every { charge(invoice5) } returns false
    }

    private val billingService = BillingService(dal = dal, paymentProvider = paymentProvider)

    @Test
    fun `will correctly roll over month and add year if it is december`() {
        val result = billingService.scheduleBilling(date)
        assertEquals(expectedDate, result)
    }

    @Test
    fun `will return the list of correctly processed invoices`() {
        val result = billingService.processPendingInvoices(invoices)
        assertTrue(result.containsAll(processedInvoicesList))
    }

    @Test
    fun `will return the correct date string`() {
        billingService.scheduleBilling(date)
        val result = billingService.getNextBillingDate()
        assertEquals(expectedDate, result)
    }

    @Test
    fun `will throw if no billing scheduled`() {
        assertThrows<BillingNotScheduledException> {
            billingService.getNextBillingDate()
        }
    }

    @Test
    fun `will throw if scheduled billing has been run`() {
        billingService.scheduleBilling(pastDate)
        // Wait for the scheduled task to be run
        Thread.sleep(1000)
        assertThrows<BillingNotScheduledException> {
            billingService.getNextBillingDate()
        }
    }

    @Test
    fun `will return true if scheduled task is cancelled successfully`() {
        billingService.scheduleBilling()
        val result = billingService.cancel()
        assertTrue(result)
    }

    @Test
    fun `will return false if scheduled task has already run`() {
        billingService.scheduleBilling(pastDate)
        Thread.sleep(1000)
        val result = billingService.cancel()
        assertFalse(result)
    }

    @Test
    fun `will successfully reschedule task after canceling`() {
        billingService.scheduleBilling(date)
        billingService.cancel()
        val result = billingService.scheduleBilling(date)
        assertEquals(result, expectedDate)
    }
}