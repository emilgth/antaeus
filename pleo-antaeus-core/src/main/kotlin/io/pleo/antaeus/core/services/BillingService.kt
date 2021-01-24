package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.BillingNotScheduledException
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

/**
 * Class to handle scheduling of billing of pending invoices in the database
 * @property paymentProvider
 * @property dal
 */
class BillingService(
    private val paymentProvider: PaymentProvider,
    private val dal: AntaeusDal
) {
    /**
     * Shows if a billing is scheduled or not
     */
    private var billingScheduled = false

    /**
     * TimerTask to be scheduled. When run it will fetch pending invoices, attempt to process them and sets billingScheduled to false
     */
    private lateinit var timerTask: TimerTask

    /**
     * Schedules processPendingInvoices function to be called on the first of the next month
     * @see processPendingInvoices
     * @param date If this parameter is passed, this date will be used instead of the current date
     * @return A Date object of the scheduled execution time
     */
    fun scheduleBilling(date: LocalDate = LocalDate.now()): Date {
        val scheduleDate = getFirstOfNextMonth(date)
        val timer = Timer()
        timerTask = object : TimerTask() {
            override fun run() {
                val pendingInvoices = dal.fetchPendingInvoices()
                processPendingInvoices(pendingInvoices)
                billingScheduled = false
            }
        }
        timer.schedule(
            timerTask,
            scheduleDate
        )
        billingScheduled = true
        logger.info { "Billing scheduled for $scheduleDate." }
        return scheduleDate
    }

    /**
     * Gets the first day of the next month.
     * If the month is December, rolls over to next year and sets month to January
     * @param currentDate The date that the result will be calculated from
     * @return A Date object of the first day of the next month
     */
    private fun getFirstOfNextMonth(currentDate: LocalDate): Date {
        return Date.from(
            LocalDate.of(
                if (currentDate.month.value == 12) {
                    currentDate.year + 1
                } else {
                    currentDate.year
                },
                currentDate.month.plus(1),
                1
            )
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC)
        )
    }

    /**
     * Attempts to process list of invoices
     * @param invoices The list of invoices to process
     * @return List of processed invoices
     */
    fun processPendingInvoices(invoices: List<Invoice>): List<Invoice> {
        val processedInvoices = mutableListOf<Invoice>()
        invoices.forEach { invoice ->
            val processedInvoice = billInvoice(invoice)
            processedInvoices.add(processedInvoice)
        }
        logger.info { "${processedInvoices.size} invoices were processed" }
        return processedInvoices.toList()
    }

    /**
     * Attempts to charge an invoice.
     * If charge is a success, invoice status will be set to PAID.
     * If charge fails, invoice status will be set to INSUFFICIENT_BALANCE.
     * If any exception is thrown, the invoice status will be set to exception cause.
     * @param invoice invoice to attempt to charge
     * @return invoice with status set to result of attempt to charge
     */
    private fun billInvoice(invoice: Invoice): Invoice {
        var result: Invoice
        try {
            val success = paymentProvider.charge(invoice)
            result = if (!success) {
                logger.error { "Customer ${invoice.customerId} account balance did not allow the charge, when attempting to charge Invoice ${invoice.id}" }
                updateInvoiceStatus(invoice, InvoiceStatus.INSUFFICIENT_BALANCE)
            } else {
                updateInvoiceStatus(invoice, InvoiceStatus.PAID)
            }
        } catch (e: CustomerNotFoundException) {
            logError(e, invoice.id)
            result = updateInvoiceStatus(invoice, InvoiceStatus.CUSTOMER_NOT_FOUND)
        } catch (e: CurrencyMismatchException) {
            logError(e, invoice.id)
            result = updateInvoiceStatus(invoice, InvoiceStatus.CURRENCY_MISMATCH)
        } catch (e: NetworkException) {
            logError(e, invoice.id)
            result = updateInvoiceStatus(invoice, InvoiceStatus.NETWORK_EXCEPTION)
        }
        return result
    }

    /**
     * Updates invoice status to given parameter, and persists to DB
     * @param invoice the invoice to update
     * @param status the status to update the invoice with
     * @return The updated invoice
     */
    private fun updateInvoiceStatus(invoice: Invoice, status: InvoiceStatus): Invoice {
        val result = invoice.copy(status = status)
        dal.updateInvoice(result)
        return result
    }

    /**
     * Returns the next billing date, throws BillingNotScheduledException if no billing is scheduled
     * @return next scheduled billing date
     * @throws BillingNotScheduledException
     */
    fun getNextBillingDate(): Date {
        if (!billingScheduled) {
            throw BillingNotScheduledException()
        }
        return Date.from(Instant.ofEpochMilli(timerTask.scheduledExecutionTime()))
    }

    /**
     * Cancels any scheduled billing tasks
     * @return true if the the task is successfully canceled, false if the task has already run or non was scheduled
     */
    fun cancel(): Boolean {
        if (!billingScheduled) {
            return false
        }
        val result = timerTask.cancel()
        billingScheduled = false
        return result
    }

    /**
     * Utility function for logging billing exceptions
     * @param e the thrown exception
     * @param invoiceId id of the invoice that caused the error
     */
    private fun logError(e: Exception, invoiceId: Int) {
        logger.error(e) { "${e.message} when attempting to charge Invoice $invoiceId" }
    }
}
