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

    private val CHANNEL_ID = "EyeCareServiceChannel"
    private val NOTIFICATION_ID = 1
    private val REMIND_THRESHOLD = 20

    private var thisSessionMinutes = 0
    private var totalDailyMinutes = 0
    private var isResting = false // 是否处于休息状态

    companion object {
        var isRunning = false
        const val ACTION_TICK = "com.aemeath.eyecare.TICK"
    }

    // 接收精准闹钟广播，确保即使在休眠模式下也能每分钟唤醒一次逻辑
    private val tickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_TICK) {
                checkUsage()
                scheduleNextTick() // 安排下一分钟的唤醒
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        loadDailyStats()
        createNotificationChannel()

        // 注册闹钟广播接收器
        val filter = IntentFilter(ACTION_TICK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(tickReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(tickReceiver, filter)
        }

        // 启动前台服务
        startForeground(NOTIFICATION_ID, buildTimerNotification())
        scheduleNextTick()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 处理来自 ReminderActivity 的回调指令
        when (intent?.getStringExtra("CMD")) {
            "REST_START" -> {
                isResting = true
                updateNotification()
            }
            "REST_DONE" -> {
                isResting = false
                thisSessionMinutes = 0 // 休息完成后重置本次计时
                updateNotification()
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
            // 熄屏时自动重置本次计时，因为用户已经停止用眼
            thisSessionMinutes = 0
        }
        updateNotification()
    }

    // 使用系统闹钟实现精准的一分钟唤醒，不受 Doze 模式限制
    private fun scheduleNextTick() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(ACTION_TICK)
        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAt = System.currentTimeMillis() + 60 * 1000
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // 允许在系统待机（Idle）时唤醒
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    // 发送类似短信的横幅通知
    private fun showHeadsUpNotification() {
        val intent = Intent(this, ReminderActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher) // 确保 drawable 中有此图标
            .setContentTitle("✨ 爱弥斯提醒：该休息啦！")
            .setContentText("已连续用眼20分钟，点击进入20s休息模式")
            .setPriority(NotificationCompat.PRIORITY_HIGH) // 必须设为 HIGH 才会弹出横幅
            .setDefaults(Notification.DEFAULT_ALL)
            .setFullScreenIntent(pendingIntent, true) // 锁屏时也能显示
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(2, builder.build())
    }

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildTimerNotification())
    }

    private fun buildTimerNotification(): Notification {
        val nextRemindIn = (REMIND_THRESHOLD - thisSessionMinutes).coerceAtLeast(0)
        val status = if (isResting) "休息模式中" else "用眼守护中"
        val content = "本次: ${thisSessionMinutes}分 | 当日累计: ${totalDailyMinutes}分 | 剩余: ${nextRemindIn}分"

        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("爱弥斯 $status ✨")
            .setContentText(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    // --- 数据持久化与工具方法 ---

    private fun saveDailyStats() {
        val sp = getSharedPreferences("EyeCareStats", Context.MODE_PRIVATE)
        val today = getTodayDate()
        sp.edit().putInt("minutes_$today", totalDailyMinutes).apply()
    }

    private fun loadDailyStats() {
        val sp = getSharedPreferences("EyeCareStats", Context.MODE_PRIVATE)
        val today = getTodayDate()
        // 自动读取当天的累计时间，如果是新的一天则从0开始
        totalDailyMinutes = sp.getInt("minutes_$today", 0)
    }

    private fun getTodayDate(): String {
        val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "爱弥斯护眼提醒服务",
                NotificationManager.IMPORTANCE_HIGH // 设为 HIGH 以支持横幅弹窗
            ).apply {
                description = "显示实时用眼计时与休息提醒"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        // 取消闹钟，防止服务停止后继续唤醒
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(ACTION_TICK)
        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        
        unregisterReceiver(tickReceiver)
        super.onDestroy()
    }
}
