package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import mu.KotlinLogging
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.*

private val logger = KotlinLogging.logger { }

class BillingService(
    private val paymentProvider: PaymentProvider,
    private val dal: AntaeusDal
) {

    /*
        Schedules billing function to be called on the first of the next month

        Returns:
            A Date object, containing the scheduled execution time
    */
    fun scheduleBilling(date: LocalDate = LocalDate.now()): Date {
        val scheduleDate: Date = getFirstOfNextMonth(date)
        // Timer to handle scheduling
        val timer = Timer()

        // TimerTask to be scheduled by timer
        val timerTask = object : TimerTask() {
            override fun run() {
                billPendingInvoices()
            }
        }
        // Schedule the TimerTask to be run at scheduleDate
        timer.schedule(
            timerTask,
            scheduleDate
        )
        logger.info { "Billing scheduled for ${Date.from(Instant.ofEpochMilli(timerTask.scheduledExecutionTime()))}." }
        return Date.from(Instant.ofEpochMilli(timerTask.scheduledExecutionTime()))
    }

    /*
        Gets the first day of the next month

        Returns:
            A Date object of the first day of the next month
    */
    private fun getFirstOfNextMonth(currentDate: LocalDate): Date {
        // The date where the billing is to be scheduled
        return Date.from(
            LocalDate.of(
                // If the current month is December, add 1 to current year
                if (currentDate.month.value == 12) {
                    currentDate.year + 1
                } else {
                    currentDate.year
                },
                // Adds 1 to current month, the "plus" method handles rollover from 12 to 1
                currentDate.month.plus(1),
                1
            )
                // Convert to LocalDateTime
                .atStartOfDay()
                // Convert to Instant
                .toInstant(ZoneOffset.UTC)
        )
    }

    /*
        Attempts to bill all pending invoices from DB
        Calls method to schedule next billing time

        Returns:
            List of processed invoices
    */
    fun billPendingInvoices(): List<Invoice> {
        // Get all pending invoices
        val pendingInvoices = dal.fetchPendingInvoices()
        // List to store processed invoices
        val processedInvoices = mutableListOf<Invoice>()
        // Loop through all pending invoices
        pendingInvoices.forEach { invoice ->
            val processedInvoice = billInvoice(invoice)
            processedInvoices.add(processedInvoice)
        }
        logger.info { "${processedInvoices.size} invoices were processed" }
        // Schedule next billing date
        scheduleBilling()
        return processedInvoices.toList()
    }

    /*
        Charges an invoice

        Returns:
            Invoice object with status set to paid
    */
    private fun billInvoice(
        invoice: Invoice
    ): Invoice {
        // Attempt to charge the customer
        var result: Invoice
        try {
            val success = paymentProvider.charge(invoice)
            if (!success) {
                logger.error { "Customer ${invoice.customerId} account balance did not allow the charge, when attempting to charge Invoice ${invoice.id}" }
                result = invoice.copy(status = InvoiceStatus.INSUFFICIENT_BALANCE)
                dal.updateInvoice(result)
            }
            // Create copy of invoice with status set to paid
            result = invoice.copy(status = InvoiceStatus.PAID)
            // Update the invoice in the DB
            dal.updateInvoice(result)
        } catch (e: CustomerNotFoundException) {
            logger.error { "${e.message} when attempting to charge Invoice ${invoice.id}" }
            result = invoice.copy(status = InvoiceStatus.CUSTOMER_NOT_FOUND)
            dal.updateInvoice(result)
        } catch (e: CurrencyMismatchException) {
            logger.error { "${e.message} when attempting to charge Invoice ${invoice.id}" }
            result = invoice.copy(status = InvoiceStatus.CURRENCY_MISMATCH)
            dal.updateInvoice(result)
        } catch (e: NetworkException) {
            logger.error { "${e.message} when attempting to charge Invoice ${invoice.id}" }
            result = invoice.copy(status = InvoiceStatus.NETWORK_EXCEPTION)
            dal.updateInvoice(result)
        }
        return result
    }

}
