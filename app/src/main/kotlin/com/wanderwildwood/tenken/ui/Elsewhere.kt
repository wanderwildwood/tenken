package com.wanderwildwood.tenken.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract

/**
 * Where a scan can go from here: a reminder in the calendar, and the codes kept in Notes. Each
 * is offered only when an app on the phone answers it.
 */
object Elsewhere {

    /** Notes — oboegaki — which takes shared text with a subject as a new note of that title. */
    private const val NOTES_APP = "com.wanderwildwood.oboegaki"

    /** The reminder is for this time tomorrow, half an hour long: a calendar that insists on a time gets one. */
    fun reminderStart(now: Long): Long = now + DAY_MS

    fun reminder(title: String, description: String, now: Long = System.currentTimeMillis()): Intent =
        Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, title)
            .putExtra(CalendarContract.Events.DESCRIPTION, description)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, reminderStart(now))
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, reminderStart(now) + HALF_HOUR_MS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val HALF_HOUR_MS = 30L * 60 * 1000

    fun note(title: String, text: String): Intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .setPackage(NOTES_APP)
        .putExtra(Intent.EXTRA_SUBJECT, title)
        .putExtra(Intent.EXTRA_TEXT, text)

    fun answered(context: Context, intent: Intent): Boolean =
        context.packageManager.queryIntentActivities(intent, 0).isNotEmpty()

    fun open(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    } catch (_: RuntimeException) {
        // Another app's calendar can fall over on what it is handed; ours must not go with it.
        false
    }
}
