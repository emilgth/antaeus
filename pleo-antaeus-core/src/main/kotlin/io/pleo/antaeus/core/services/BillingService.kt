package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.*

class BillingService(
    private val paymentProvider: PaymentProvider,
    private val antaeusDal: AntaeusDal
) {

    // Schedules billing function to be called on the first of the next month
    fun scheduleBilling() {
        val currentDate = LocalDate.now()
        // The date where the function is to be called
        val scheduleDate = Date.from(
            LocalDate.of(
                // If the current month is December, add 1 to current year
                if (currentDate.month.value == 12) {
                    currentDate.year
                } else {
                    currentDate.year + 1
                },
                // Adds 1 to current month, the method handles rollover from 12 to 1
                currentDate.month.plus(1),
                1
            )
                // Convert to LocalDateTime
                .atStartOfDay()
                // Convert to Instant
                .toInstant(ZoneOffset.UTC)
        )

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
    }

    fun billPendingInvoices() {
        // Get all pending invoices
        val pendingInvoices = antaeusDal.fetchPendingInvoices()
        // List to store processed invoices
        val processedInvoices = mutableListOf<Invoice>()
        // Loop through all pending invoices
        pendingInvoices.forEach { invoice ->
            try {
                // Attempt to charge the customer
                val success = paymentProvider.charge(invoice)
                if (!success) {
                    // todo handle customer account balance did not allow the charge
                }
                // Create copy of invoice with status set to paid
                val paidInvoice = invoice.copy(status = InvoiceStatus.PAID)
                // Update the invoice in the DB
                antaeusDal.updateInvoice(paidInvoice)
                // Add to list of processed invoices
                processedInvoices.add(paidInvoice)
            } catch (e: CustomerNotFoundException) {
                // todo handle exception
            } catch (e: CurrencyMismatchException) {
                // todo handle exception
            } catch (e: NetworkException) {
                // todo handle exception
            }
        }
    }

}
