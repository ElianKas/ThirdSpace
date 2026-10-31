package com.example.thirdspace.data

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

/** iOS actual: NSDate's seconds-since-1970 converted to millis. */
actual fun currentEpochMillis(): Long =
    (NSDate().timeIntervalSince1970 * 1000.0).toLong()
