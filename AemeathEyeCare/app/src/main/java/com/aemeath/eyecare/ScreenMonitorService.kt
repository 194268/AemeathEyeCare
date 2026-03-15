package com.aemeath.eyecare

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.*

class ScreenMonitorService : Service() {

    // 定义两个不同的频道 ID
    private val TIMER_CHANNEL_ID = "EyeCareTimerChannel"   // 用于每分钟静默计时的频道
    private val REMIND_CHANNEL_ID = "EyeCareRemindChannel" // 用于20分钟到期弹横幅的频道
    
    private val NOTIFICATION_ID_TIMER = 1
    private val NOTIFICATION_ID_REMIND = 2
    private val REMIND_THRESHOLD = 20

    private var thisSessionMinutes = 0
    private var totalDailyMinutes = 0
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
        loadDailyStats()
        createNotificationChannels()

        val filter = IntentFilter(ACTION_TICK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(tickReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(tickReceiver, filter)
        }

        // 启动时使用静默计时频道
        startForeground(NOTIFICATION_ID_TIMER, buildTimerNotification())
        scheduleNextTick()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.getStringExtra("CMD")) {
            "REST_START" -> {
                isResting = true
                updateTimerNotification()
            }
            "REST_DONE" -> {
                isResting = false
                thisSessionMinutes = 0
                updateTimerNotification()
            }
        }
        return START_STICKY
    }

    private fun checkUsage() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isScreenOn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            pm.isInteractive
        } else {
            pm.isScreenOn
        }

        if (isScreenOn && !isResting) {
            thisSessionMinutes++
            totalDailyMinutes++
            saveDailyStats()

            if (thisSessionMinutes >= REMIND_THRESHOLD) {
                showHeadsUpNotification()
            }
        } else if (!isScreenOn) {
            thisSessionMinutes = 0
        }
        updateTimerNotification()
    }

    private fun scheduleNextTick() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(ACTION_TICK)
        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAt = System.currentTimeMillis() + 60 * 1000
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    // --- 核心：20分钟到期后的横幅提醒 (使用高优先级频道) ---
    private fun showHeadsUpNotification() {
        val intent = Intent(this, ReminderActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, REMIND_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("✨ 爱弥斯提醒：休息时间到！")
            .setContentText("已经连续用眼 20 分钟，请点击进入休息模式")
            .setPriority(NotificationCompat.PRIORITY_HIGH) // 允许横幅弹出
            .setDefaults(Notification.DEFAULT_ALL)
            .setFullScreenIntent(pendingIntent, true) 
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID_REMIND, builder.build())
    }

    // --- 核心：每分钟更新的通知栏计时 (使用低优先级频道，保持静默) ---
    private fun updateTimerNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID_TIMER, buildTimerNotification())
    }

    private fun buildTimerNotification(): Notification {
        val nextRemindIn = (REMIND_THRESHOLD - thisSessionMinutes).coerceAtLeast(0)
        val status = if (isResting) "休息模式中" else "守护中"
        val content = "本次: ${thisSessionMinutes}分 | 当日累计: ${totalDailyMinutes}分 | 剩余: ${nextRemindIn}分"

        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, TIMER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("爱弥斯 $status ✨")
            .setContentText(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true) // 更新时绝不发出声音或振动
            .setPriority(NotificationCompat.PRIORITY_LOW) // 绝不弹出横幅
            .setContentIntent(pendingIntent)
            .build()
    }

    // --- 数据处理与频道创建 ---

    private fun saveDailyStats() {
        val sp = getSharedPreferences("EyeCareStats", Context.MODE_PRIVATE)
        sp.edit().putInt("minutes_${getTodayDate()}", totalDailyMinutes).apply()
    }

    private fun loadDailyStats() {
        val sp = getSharedPreferences("EyeCareStats", Context.MODE_PRIVATE)
        totalDailyMinutes = sp.getInt("minutes_${getTodayDate()}", 0)
    }

    private fun getTodayDate(): String = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            // 1. 计时器进度频道 (LOW: 静默)
            val timerChannel = NotificationChannel(
                TIMER_CHANNEL_ID, "用眼计时进度", NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }

            // 2. 护眼到期提醒频道 (HIGH: 弹窗)
            val remindChannel = NotificationChannel(
                REMIND_CHANNEL_ID, "护眼到期提醒", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableLights(true)
                enableVibration(true)
            }

            manager.createNotificationChannel(timerChannel)
            manager.createNotificationChannel(remindChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(ACTION_TICK)
        val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.cancel(pendingIntent)
        try { unregisterReceiver(tickReceiver) } catch (e: Exception) {}
        super.onDestroy()
    }
}
