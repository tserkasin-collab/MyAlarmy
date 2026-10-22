package gr.misha.xypna

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object AlarmScheduler {
    /** id για το κουμπί «Δοκιμή» — δεν αποθηκεύεται στη λίστα. */
    const val TEST_ID = 0

    fun nextTrigger(a: Alarm, now: Long = System.currentTimeMillis()): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, a.hour)
            set(Calendar.MINUTE, a.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        for (i in 0..7) {
            if (c.timeInMillis > now) {
                if (a.days == 0) return c.timeInMillis
                val idx = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Δευτέρα = 0
                if ((a.days and (1 shl idx)) != 0) return c.timeInMillis
            }
            c.add(Calendar.DAY_OF_YEAR, 1)
        }
        return c.timeInMillis
    }

    private fun pending(ctx: Context, id: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, id,
            Intent(ctx, AlarmReceiver::class.java).putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** Προγραμματίζει το ξυπνητήρι. Επιστρέφει την ώρα που θα χτυπήσει ή null αν είναι κλειστό. */
    fun schedule(ctx: Context, a: Alarm): Long? {
        cancel(ctx, a.id)
        if (!a.enabled) return null
        val t = nextTrigger(a)
        setAt(ctx, a.id, t)
        return t
    }

    fun setAt(ctx: Context, id: Int, triggerAt: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val show = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, show), pending(ctx, id))
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending(ctx, id))
        }
    }

    fun cancel(ctx: Context, id: Int) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.cancel(pending(ctx, id))
        am.cancel(snoozePending(ctx, id))
    }

    // ---------- Αναβολή ----------

    const val SNOOZE_MINUTES = 5
    const val MAX_SNOOZES = 3
    private const val SNOOZE_REQUEST_BASE = 100_000
    private const val SNOOZE_PREFS = "snooze"

    private fun snoozePending(ctx: Context, id: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, SNOOZE_REQUEST_BASE + id,
            Intent(ctx, AlarmReceiver::class.java)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_SNOOZE, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** Ξαναχτυπάει σε 5 λεπτά και μετράει μία αναβολή. */
    fun snooze(ctx: Context, id: Int) {
        val prefs = ctx.getSharedPreferences(SNOOZE_PREFS, Context.MODE_PRIVATE)
        prefs.edit().putInt("count_$id", snoozesUsed(ctx, id) + 1).commit()

        val at = System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L
        val am = ctx.getSystemService(AlarmManager::class.java)
        val show = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), snoozePending(ctx, id))
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, snoozePending(ctx, id))
        }
    }

    fun snoozesUsed(ctx: Context, id: Int): Int =
        ctx.getSharedPreferences(SNOOZE_PREFS, Context.MODE_PRIVATE).getInt("count_$id", 0)

    fun resetSnoozes(ctx: Context, id: Int) {
        ctx.getSharedPreferences(SNOOZE_PREFS, Context.MODE_PRIVATE).edit().remove("count_$id").commit()
    }

    fun rescheduleAll(ctx: Context) {
        AlarmStore.all(ctx).forEach { schedule(ctx, it) }
    }

    fun canScheduleExact(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
}
