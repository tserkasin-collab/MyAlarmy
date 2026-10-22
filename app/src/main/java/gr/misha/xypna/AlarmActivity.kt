package gr.misha.xypna

import android.animation.ObjectAnimator
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/**
 * Η οθόνη που ανοίγει όταν χτυπάει.
 * Επιλογές: «Λύση προβλήματος» (σιγή 2 λεπτών όσο λύνεις) ή «Αναβολή 5 λεπτά» (έως 3 φορές).
 */
class AlarmActivity : AppCompatActivity() {

    private var alarmId = -1
    private var difficulty = 1
    private var total = 2
    private var solved = 0
    private var answer = 0
    private var input = ""
    private var solving = false

    private lateinit var clockTv: TextView

    // Οθόνη επιλογής
    private lateinit var choiceBox: LinearLayout
    private lateinit var choiceMsgTv: TextView
    private lateinit var snoozeBtn: MaterialButton
    private lateinit var snoozeInfoTv: TextView

    // Οθόνη πράξεων
    private lateinit var solveBox: LinearLayout
    private lateinit var countdownTv: TextView
    private lateinit var progressTv: TextView
    private lateinit var problemTv: TextView
    private lateinit var inputTv: TextView
    private lateinit var msgTv: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            clockTv.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            if (solving) updateCountdown()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        alarmId = intent.getIntExtra(EXTRA_ID, -1)
        val alarm = AlarmStore.get(this, alarmId)
        difficulty = alarm?.difficulty ?: 1
        total = (alarm?.problems ?: 2).coerceIn(1, 10)
        val label = alarm?.label?.takeIf { it.isNotBlank() }
            ?: if (alarmId == AlarmScheduler.TEST_ID) "Δοκιμή" else "Ξύπνα!"

        // Το «πίσω» δεν το σβήνει.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (solving) showMsg("Λύσε τις πράξεις πρώτα 😉", Palette.MUTED)
            }
        })

        buildUi(label)

        // Αν η σιγή τρέχει ήδη (π.χ. ξαναμπήκες στην οθόνη), συνέχισε στις πράξεις.
        if (AlarmService.silencedUntil > System.currentTimeMillis()) startSolving(silenceAlready = true)
        else showChoice(null)
    }

    // ---------------- UI ----------------

    private fun buildUi(label: String) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Palette.BG)
            setPadding(dp(20), dp(36), dp(20), dp(20))
        }

        clockTv = text(64f, Palette.TEXT, bold = true)
        root.addView(clockTv)
        root.addView(text(18f, Palette.MUTED).apply { text = label })

        val content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        // --- Επιλογή ---
        choiceBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        choiceMsgTv = text(17f, Palette.RED, bold = true).apply { setPadding(0, 0, 0, dp(24)) }
        choiceBox.addView(choiceMsgTv)

        choiceBox.addView(bigButton("🧮  Λύση προβλήματος", Palette.ACCENT, Palette.BG) {
            startSolving(silenceAlready = false)
        }, LinearLayout.LayoutParams(MATCH_PARENT, dp(84)).apply { bottomMargin = dp(16) })

        snoozeBtn = bigButton("💤  Αναβολή ${AlarmScheduler.SNOOZE_MINUTES} λεπτά", Palette.CARD, Palette.TEXT) {
            snooze()
        }
        choiceBox.addView(snoozeBtn, LinearLayout.LayoutParams(MATCH_PARENT, dp(84)))

        snoozeInfoTv = text(14f, Palette.MUTED).apply { setPadding(0, dp(10), 0, 0) }
        choiceBox.addView(snoozeInfoTv)

        content.addView(choiceBox, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        // --- Πράξεις ---
        solveBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        countdownTv = text(16f, Palette.ACCENT, bold = true).apply { setPadding(0, dp(16), 0, 0) }
        solveBox.addView(countdownTv)

        progressTv = text(15f, Palette.MUTED, bold = true).apply { setPadding(0, dp(8), 0, dp(8)) }
        solveBox.addView(progressTv)

        problemTv = text(42f, Palette.TEXT, bold = true)
        solveBox.addView(problemTv)

        inputTv = text(40f, Palette.ACCENT, bold = true).apply {
            minWidth = dp(200)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Palette.CARD)
            }
        }
        solveBox.addView(inputTv, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
            topMargin = dp(16)
        })

        msgTv = text(16f, Palette.MUTED).apply { setPadding(0, dp(10), 0, 0) }
        solveBox.addView(msgTv)

        solveBox.addView(View(this), LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        val keys = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("⌫", "0", "✓")
        )
        keys.forEach { row ->
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.forEach { k ->
                val b = MaterialButton(this).apply {
                    text = k
                    textSize = 26f
                    insetTop = 0
                    insetBottom = 0
                    cornerRadius = dp(18)
                    if (k == "✓") {
                        setBackgroundColor(Palette.ACCENT)
                        setTextColor(Palette.BG)
                    } else {
                        setBackgroundColor(Palette.CARD)
                        setTextColor(Palette.TEXT)
                    }
                    setOnClickListener { onKey(k) }
                }
                r.addView(b, LinearLayout.LayoutParams(0, dp(68), 1f).apply {
                    setMargins(dp(5), dp(5), dp(5), dp(5))
                })
            }
            solveBox.addView(r, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }

        content.addView(solveBox, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        setContentView(root)
    }

    private fun bigButton(label: String, bg: Int, fg: Int, onClick: () -> Unit) =
        MaterialButton(this).apply {
            text = label
            textSize = 22f
            insetTop = 0
            insetBottom = 0
            cornerRadius = dp(22)
            setBackgroundColor(bg)
            setTextColor(fg)
            setOnClickListener { onClick() }
        }

    private fun text(size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    // ---------------- Επιλογή ----------------

    private fun showChoice(message: String?) {
        solving = false
        choiceBox.visibility = View.VISIBLE
        solveBox.visibility = View.GONE

        choiceMsgTv.text = message ?: ""
        choiceMsgTv.visibility = if (message == null) View.GONE else View.VISIBLE

        val left = AlarmScheduler.MAX_SNOOZES - AlarmScheduler.snoozesUsed(this, alarmId)
        if (left > 0) {
            snoozeBtn.visibility = View.VISIBLE
            snoozeInfoTv.text = if (left == 1) "Απομένει 1 αναβολή" else "Απομένουν $left αναβολές"
        } else {
            snoozeBtn.visibility = View.GONE
            snoozeInfoTv.text = "Δεν απομένουν αναβολές — μόνο λύση!"
        }
    }

    private fun snooze() {
        if (AlarmScheduler.snoozesUsed(this, alarmId) >= AlarmScheduler.MAX_SNOOZES) return
        AlarmScheduler.snooze(this, alarmId)
        AlarmService.stop(this)
        finishAndRemoveTask()
    }

    // ---------------- Πράξεις ----------------

    private fun startSolving(silenceAlready: Boolean) {
        if (!silenceAlready) AlarmService.silence(this)
        solving = true
        solved = 0
        choiceBox.visibility = View.GONE
        solveBox.visibility = View.VISIBLE
        showMsg("", Palette.MUTED)
        newProblem()
        updateCountdown()
    }

    private fun updateCountdown() {
        val until = AlarmService.silencedUntil
        val left = until - System.currentTimeMillis()
        if (until == 0L || left <= 0) {
            // Πέρασαν τα 2 λεπτά → ξαναχτυπάει, πίσω στις επιλογές.
            showChoice("⏰ Πέρασαν τα 2 λεπτά!")
            return
        }
        val secs = ((left + 999) / 1000).toInt()
        countdownTv.text = "⏱ %d:%02d".format(secs / 60, secs % 60)
        countdownTv.setTextColor(if (secs <= 30) Palette.RED else Palette.ACCENT)
    }

    private fun onKey(k: String) {
        when (k) {
            "⌫" -> input = input.dropLast(1)
            "✓" -> { check(); return }
            else -> if (input.length < 6) input += k
        }
        inputTv.text = input.ifEmpty { " " }
    }

    private fun check() {
        if (input.isEmpty()) return
        if (input.toIntOrNull() == answer) {
            solved++
            if (solved >= total) {
                done()
            } else {
                showMsg("Σωστό! ✔", Palette.GREEN)
                newProblem()
            }
        } else {
            showMsg("Λάθος — ξαναδοκίμασε", Palette.RED)
            input = ""
            inputTv.text = " "
            ObjectAnimator.ofFloat(problemTv, "translationX", 0f, 25f, -25f, 15f, -15f, 0f)
                .setDuration(400).start()
        }
    }

    private fun newProblem() {
        val text: String
        when (difficulty) {
            0 -> {
                val x = Random.nextInt(10, 60); val y = Random.nextInt(10, 40)
                text = "$x + $y"; answer = x + y
            }
            1 -> {
                val x = Random.nextInt(3, 10); val y = Random.nextInt(11, 30); val z = Random.nextInt(10, 100)
                text = "$x × $y + $z"; answer = x * y + z
            }
            else -> {
                val x = Random.nextInt(11, 20); val y = Random.nextInt(11, 50); val z = Random.nextInt(100, 1000)
                text = "$x × $y + $z"; answer = x * y + z
            }
        }
        problemTv.text = "$text = ?"
        progressTv.text = "ΠΡΑΞΗ ${solved + 1} ΑΠΟ $total"
        input = ""
        inputTv.text = " "
    }

    private fun showMsg(m: String, color: Int) {
        msgTv.text = m
        msgTv.setTextColor(color)
    }

    private fun done() {
        solving = false
        AlarmScheduler.resetSnoozes(this, alarmId)
        AlarmService.stop(this)
        finishAndRemoveTask()
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
        // αν το ξυπνητήρι σταμάτησε από αλλού, κλείσε την οθόνη
        if (!AlarmService.isRinging) finishAndRemoveTask()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }
}
