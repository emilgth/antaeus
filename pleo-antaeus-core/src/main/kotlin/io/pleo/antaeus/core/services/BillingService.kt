package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.external.PaymentProvider
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.*

class BillingService(
    private val paymentProvider: PaymentProvider
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
                //todo call billing function here
            }
        }

        // Schedule the TimerTask to be run at scheduleDate
        timer.schedule(
            timerTask,
            scheduleDate
        )
    }

}
