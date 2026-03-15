package com.aemeath.eyecare

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.*

class ScreenMonitorService : Service() {

    private val TIMER_CHANNEL_ID = "EyeCareTimerChannel"
    private val REMIND_CHANNEL_ID = "EyeCareRemindChannel"
    private val NOTIFICATION_ID_TIMER = 1
    private val NOTIFICATION_ID_REMIND = 2
    
    // 测试时请将此改为 1
    private var REMIND_THRESHOLD_MINS = 20

    private var sessionStartTimeMillis: Long = 0
    private var thisSessionMinutes = 0
    private var totalDailyMinutesAtStart = 0
    private var isResting = false

    companion object {
        var isRunning = false
        const val ACTION_TICK = "com.aemeath.eyecare.TICK"
    }

    private val tickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_TICK) {
                checkUsage()
                scheduleNextTick()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        resetSession()
        createNotificationChannels()

        val filter = IntentFilter(ACTION_TICK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(tickReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(tickReceiver, filter)
        }

        startForeground(NOTIFICATION_ID_TIMER, buildTimerNotification())
        scheduleNextTick()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 关键修复：处理来自各处的 CMD 指令
        val cmd = intent?.getStringExtra("CMD")
        if (cmd == "REST_START") {
            isResting = true
        } else if (cmd == "REST_DONE") {
            isResting = false
            resetSession()
            // 休息完关闭提醒横幅
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIFICATION_ID_REMIND)
        }
        updateTimerNotification()
        return START_STICKY
    }

    private fun resetSession() {
        val sp = getSharedPreferences("EyeCareStats", Context.MODE_PRIVATE)
        val today = getTodayDate()
        totalDailyMinutesAtStart = sp.getInt("minutes_$today", 0)
        sessionStartTimeMillis = System.currentTimeMillis()
        thisSessionMinutes = 0
    }

    private fun checkUsage() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isScreenOn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) pm.isInteractive else pm.isScreenOn

        if (isScreenOn && !isResting) {
            val elapsedMillis = System.currentTimeMillis() - sessionStartTimeMillis
            thisSessionMinutes = (elapsedMillis / (1000 * 60)).toInt()
            saveDailyStats(totalDailyMinutesAtStart + thisSessionMinutes)

            if (thisSessionMinutes >= REMIND_THRESHOLD_MINS) {
                triggerAlert()
            }
        } else if (!isScreenOn) {
            resetSession()
        }
        updateTimerNotification()
    }

    private fun triggerAlert() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(1000)
        }
        showHeadsUpNotification()
    }

    private fun scheduleNextTick() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(ACTION_TICK).setPackage(packageName)
        val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val triggerAt = System.currentTimeMillis() + 60 * 1000
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
    }

    private fun showHeadsUpNotification() {
        val intent = Intent(this, ReminderActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        // 使用不同的 RequestCode (1) 区分不同的 PendingIntent
        val pendingIntent = PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, REMIND_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("✨ 休息时间到！")
            .setContentText("已连续用眼 ${thisSessionMinutes} 分钟，点击休息")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(Notification.DEFAULT_ALL)
            .setFullScreenIntent(pendingIntent, true) // 强制弹出的核心
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID_REMIND, builder.build())
    }

    private fun buildTimerNotification(): Notification {
        val currentTotal = totalDailyMinutesAtStart + thisSessionMinutes
        val nextIn = (REMIND_THRESHOLD_MINS - thisSessionMinutes).coerceAtLeast(0)
        
        return NotificationCompat.Builder(this, TIMER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("爱弥斯护眼守护中")
            .setContentText("当日累计: ${currentTotal}分 | 剩余: ${nextIn}分")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .build()
    }

    private fun updateTimerNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID_TIMER, buildTimerNotification())
    }

    private fun saveDailyStats(total: Int) {
        val sp = getSharedPreferences("EyeCareStats", Context.MODE_PRIVATE)
        sp.edit().putInt("minutes_${getTodayDate()}", total).apply()
    }

    private fun getTodayDate(): String = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val timerCh = NotificationChannel(TIMER_CHANNEL_ID, "计时进度", NotificationManager.IMPORTANCE_LOW)
            val remindCh = NotificationChannel(REMIND_CHANNEL_ID, "到期提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(timerCh)
            manager.createNotificationChannel(remindCh)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }
}
