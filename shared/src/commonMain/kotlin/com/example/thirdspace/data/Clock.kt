package com.example.thirdspace.data

/**
 * Current wall-clock time in epoch milliseconds, used to order the pending
 * spot-insert queue (oldest-first flush). An expect/actual avoids adding a
 * datetime dependency; tests inject a fake clock instead of calling this.
 */
expect fun currentEpochMillis(): Long
