## Introduction

Implementing the actual functionality took me about a days work (8 hours). Messing around with refactoring and testing
took me a lot longer. I worked on it on and off for a week in total. I also spent a few days before I began learning
Kotlin, Docker and Gradle.

From the challenge description, I interpreted the actual scheduling as the main task, and the billing as secondary, so I
spent most of my time making that function correctly.

Right now the program can schedule billing for the first of the next month, and it can process the invoices when the
date is reached. A scheduled billing can be canceled, resumed, and the scheduled time can be checked via REST endpoints.

## Schedule

For the scheduling I decided to use the Timer and TimerTask classes since they made it very simple to do in-app as
opposed to using cron-jobs. They also made it easy to monitor the current status of the schedule, and they made it easy
to test, since it was all handled programmatically.

Using cron-jobs would have been an easy and simple solution, but I would also have lost som control.

## Charging

The actual charging of the invoices is a simple loop, that attempts to charge all the pending invoices in the DB. The
invoices are then updated with the result of the charge, if an exception occurs the invoice status will be set to that
exception. Then it will be easy to find and handle them all later.

I would like to have used concurrency when doing the charging as it's possibly an action that could take a while. I
couldn't really make it work in a way, where I was sure I was done correctly, so it didn't get implemented.

## Testing

I don't really have a lot of experience in testing (there was no testing on the backend at my internship...), so I have
probably missed some test cases. I feel I have tried to test the most common use-cases though.
