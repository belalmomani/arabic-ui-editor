package com.arabicuieditor.ui.common

import java.text.DateFormat
import java.util.Date

/** تنسيق التاريخ والوقت حسب لغة الجهاز. */
fun formatDateTime(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
