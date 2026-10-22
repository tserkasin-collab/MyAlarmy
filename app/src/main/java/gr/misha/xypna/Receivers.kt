package gr.misha.xypna

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, -1)
        val alarm = AlarmStore.get(context, id)
        if (alarm == null && id != AlarmScheduler.TEST_ID) return

        if (alarm != null) {
            if (!alarm.enabled) return
            if (alarm.days == 0) {
                alarm.enabled = false            // μία φορά → κλείνει
                AlarmStore.upsert(context, alarm)
            } else {
                AlarmScheduler.schedule(context, alarm) // επόμενη μέρα
            }
        }

        ContextCompat.startForegroundService(
            context,
            Intent(context, AlarmService::class.java).putExtra(EXTRA_ID, id)
        )
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmScheduler.rescheduleAll(context)
    }
}
