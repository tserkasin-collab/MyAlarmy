package gr.misha.xypna

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider

class MainActivity : AppCompatActivity() {

    private lateinit var listBox: LinearLayout
    private lateinit var bannerBox: LinearLayout

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshBanner() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this).apply { setBackgroundColor(Palette.BG) }
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(120))
        }

        col.addView(TextView(this).apply {
            text = "Ξυπνητήρια"
            textSize = 32f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Palette.TEXT)
            setPadding(dp(4), 0, 0, dp(12))
        })

        bannerBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(bannerBox)

        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(listBox)

        col.addView(
            MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "Δοκιμή σε 10 δευτερόλεπτα"
                setOnClickListener {
                    AlarmScheduler.setAt(
                        this@MainActivity, AlarmScheduler.TEST_ID,
                        System.currentTimeMillis() + 10_000
                    )
                    toast("Κλείδωσε την οθόνη — χτυπάει σε 10″")
                }
            },
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(16) }
        )

        scroll.addView(col)
        root.addView(scroll, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        val fab = FloatingActionButton(this).apply {
            setImageResource(R.drawable.ic_add)
            setOnClickListener { edit(null) }
        }
        root.addView(fab, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.BOTTOM or Gravity.END).apply {
            setMargins(dp(24), dp(24), dp(24), dp(32))
        })

        setContentView(root)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        // Αν χτυπάει, δεν ξεφεύγεις ανοίγοντας την εφαρμογή.
        if (AlarmService.isRinging) {
            startActivity(
                Intent(this, AlarmActivity::class.java)
                    .putExtra(EXTRA_ID, AlarmService.currentId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        render()
        refreshBanner()
    }

    // ---------------- Λίστα ----------------

    private fun render() {
        listBox.removeAllViews()
        val alarms = AlarmStore.all(this)
        if (alarms.isEmpty()) {
            listBox.addView(TextView(this).apply {
                text = "Δεν υπάρχουν ξυπνητήρια.\nΠάτα το + για να προσθέσεις."
                textSize = 16f
                setTextColor(Palette.MUTED)
                gravity = Gravity.CENTER
                setPadding(0, dp(48), 0, dp(48))
            })
            return
        }
        alarms.forEach { listBox.addView(card(it)) }
    }

    private fun card(a: Alarm): View {
        val card = MaterialCardView(this).apply {
            radius = dp(20).toFloat()
            setCardBackgroundColor(Palette.CARD)
            strokeWidth = 0
            cardElevation = 0f
            setOnClickListener { edit(a) }
            setOnLongClickListener { confirmDelete(a); true }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(16), dp(16), dp(16))
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this).apply {
            text = a.timeText()
            textSize = 42f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (a.enabled) Palette.TEXT else Palette.DIM)
        })
        val sub = buildString {
            append(a.daysText())
            if (a.label.isNotBlank()) append(" · ").append(a.label)
        }
        texts.addView(TextView(this).apply {
            text = sub
            textSize = 14f
            setTextColor(if (a.enabled) Palette.MUTED else Palette.DIM)
        })
        texts.addView(TextView(this).apply {
            text = "🧮 ${a.problems} πράξεις · ${Alarm.DIFFICULTY[a.difficulty]}"
            textSize = 13f
            setTextColor(if (a.enabled) Palette.ACCENT else Palette.DIM)
            setPadding(0, dp(4), 0, 0)
        })
        row.addView(texts, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))

        row.addView(MaterialSwitch(this).apply {
            isChecked = a.enabled
            setOnCheckedChangeListener { _, checked ->
                a.enabled = checked
                AlarmStore.upsert(this@MainActivity, a)
                AlarmScheduler.schedule(this@MainActivity, a)?.let { toastUntil(it) }
                listBox.post { render() }
            }
        })

        card.addView(row)
        card.layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            bottomMargin = dp(12)
        }
        return card
    }

    private fun confirmDelete(a: Alarm) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Διαγραφή ${a.timeText()};")
            .setPositiveButton("Διαγραφή") { _, _ -> delete(a) }
            .setNegativeButton("Άκυρο", null)
            .show()
    }

    private fun delete(a: Alarm) {
        AlarmScheduler.cancel(this, a.id)
        AlarmStore.delete(this, a.id)
        render()
    }

    // ---------------- Επεξεργασία ----------------

    private fun edit(existing: Alarm?) {
        val a = existing?.copy() ?: Alarm(id = AlarmStore.nextId(this), hour = 7, minute = 0)

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(8))
        }

        // Ώρα
        val timeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val hourPicker = NumberPicker(this).apply {
            minValue = 0; maxValue = 23
            displayedValues = Array(24) { "%02d".format(it) }
            value = a.hour
        }
        val minutePicker = NumberPicker(this).apply {
            minValue = 0; maxValue = 59
            displayedValues = Array(60) { "%02d".format(it) }
            value = a.minute
        }
        timeRow.addView(hourPicker)
        timeRow.addView(TextView(this).apply {
            text = ":"; textSize = 32f; setTextColor(Palette.TEXT)
            setPadding(dp(12), 0, dp(12), 0)
        })
        timeRow.addView(minutePicker)
        box.addView(timeRow)

        // Μέρες
        box.addView(sectionTitle("Επανάληψη"))
        val daysRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        var days = a.days
        Alarm.DAY_LETTER.forEachIndexed { i, letter ->
            val tv = TextView(this).apply {
                text = letter
                textSize = 15f
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }
            fun paint() {
                val on = (days and (1 shl i)) != 0
                tv.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    if (on) setColor(Palette.ACCENT) else setStroke(dp(1), Palette.DIM)
                }
                tv.setTextColor(if (on) Palette.BG else Palette.MUTED)
            }
            paint()
            tv.setOnClickListener { days = days xor (1 shl i); paint() }
            daysRow.addView(tv, LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                setMargins(dp(2), 0, dp(2), 0)
            })
        }
        box.addView(daysRow)
        box.addView(TextView(this).apply {
            text = "Χωρίς μέρες = χτυπάει μία φορά"
            textSize = 12f; setTextColor(Palette.DIM)
            setPadding(0, dp(6), 0, 0)
        })

        // Ετικέτα
        box.addView(sectionTitle("Ετικέτα"))
        val labelEt = EditText(this).apply {
            setText(a.label)
            hint = "π.χ. Καφεκοπτείο"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine()
        }
        box.addView(labelEt)

        // Δυσκολία
        box.addView(sectionTitle("Αποστολή: Μαθηματικά"))
        val example = TextView(this).apply {
            textSize = 13f; setTextColor(Palette.MUTED)
            setPadding(0, dp(6), 0, 0)
        }
        val group = MaterialButtonToggleGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
        }
        val ids = IntArray(3) { View.generateViewId() }
        Alarm.DIFFICULTY.forEachIndexed { i, name ->
            group.addView(
                MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    id = ids[i]; text = name
                },
                LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            )
        }
        group.check(ids[a.difficulty.coerceIn(0, 2)])
        example.text = Alarm.EXAMPLE[a.difficulty.coerceIn(0, 2)]
        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) example.text = Alarm.EXAMPLE[ids.indexOf(checkedId)]
        }
        box.addView(group, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        box.addView(example)

        // Πλήθος πράξεων
        val countTv = TextView(this).apply {
            textSize = 15f; setTextColor(Palette.TEXT)
            setPadding(0, dp(16), 0, 0)
            text = "Πράξεις για να σβήσει: ${a.problems}"
        }
        box.addView(countTv)
        val slider = Slider(this).apply {
            valueFrom = 1f; valueTo = 10f; stepSize = 1f
            value = a.problems.coerceIn(1, 10).toFloat()
            addOnChangeListener { _, v, _ -> countTv.text = "Πράξεις για να σβήσει: ${v.toInt()}" }
        }
        box.addView(slider)

        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(if (existing == null) "Νέο ξυπνητήρι" else "Επεξεργασία")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("Αποθήκευση") { _, _ ->
                a.hour = hourPicker.value
                a.minute = minutePicker.value
                a.days = days
                a.label = labelEt.text.toString().trim()
                a.difficulty = ids.indexOf(group.checkedButtonId).coerceIn(0, 2)
                a.problems = slider.value.toInt()
                a.enabled = true
                AlarmStore.upsert(this, a)
                AlarmScheduler.schedule(this, a)?.let { toastUntil(it) }
                render()
            }
            .setNegativeButton("Άκυρο", null)
        if (existing != null) builder.setNeutralButton("Διαγραφή") { _, _ -> delete(existing) }
        builder.show()
    }

    private fun sectionTitle(t: String) = TextView(this).apply {
        text = t
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Palette.ACCENT)
        setPadding(0, dp(20), 0, dp(8))
    }

    // ---------------- Άδειες / ρυθμίσεις ----------------

    private fun refreshBanner() {
        bannerBox.removeAllViews()
        val nm = getSystemService(NotificationManager::class.java)
        val pm = getSystemService(PowerManager::class.java)

        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            warn("Οι ειδοποιήσεις είναι κλειστές — χωρίς αυτές το ξυπνητήρι δεν εμφανίζεται.") {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
        }
        if (!AlarmScheduler.canScheduleExact(this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            warn("Χρειάζεται άδεια για «Ξυπνητήρια και υπενθυμίσεις».") {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
            }
        }
        if (Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent()) {
            warn("Χρειάζεται άδεια για ειδοποιήσεις πλήρους οθόνης (για να ανοίγει με κλειδωμένο κινητό).") {
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))
            }
        }
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            warn("Βγάλε την εφαρμογή από την εξοικονόμηση μπαταρίας, για να μην την «κοιμίζει» το κινητό.") {
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            }
        }
    }

    private fun warn(msg: String, intent: () -> Intent) {
        val card = MaterialCardView(this).apply {
            radius = dp(16).toFloat()
            setCardBackgroundColor(Palette.WARN)
            strokeWidth = 0
            cardElevation = 0f
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(8))
        }
        row.addView(TextView(this).apply {
            text = "⚠️ $msg"; textSize = 14f; setTextColor(Palette.TEXT)
        })
        row.addView(MaterialButton(this).apply {
            text = "Ενεργοποίηση"
            setOnClickListener {
                try {
                    startActivity(intent())
                } catch (e: Exception) {
                    startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                    )
                }
            }
        }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { gravity = Gravity.END })
        card.addView(row)
        bannerBox.addView(card, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            bottomMargin = dp(12)
        })
    }

    // ---------------- Βοηθητικά ----------------

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    private fun toastUntil(t: Long) {
        val mins = ((t - System.currentTimeMillis()) + 59_999) / 60_000
        val h = mins / 60
        val m = mins % 60
        val parts = mutableListOf<String>()
        if (h > 0) parts += if (h == 1L) "1 ώρα" else "$h ώρες"
        if (m > 0) parts += if (m == 1L) "1 λεπτό" else "$m λεπτά"
        toast("Θα χτυπήσει σε " + parts.joinToString(" και ").ifEmpty { "λιγότερο από 1 λεπτό" })
    }
}
