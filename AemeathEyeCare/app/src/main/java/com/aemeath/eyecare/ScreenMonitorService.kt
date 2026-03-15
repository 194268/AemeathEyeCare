package com.aemeath.eyecare

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import java.util.*

class ScreenMonitorService : Service() {

    private val CHANNEL_ID = "EyeCareServiceChannel"
    private val NOTIFICATION_ID = 1
    
    // 使用 Handler 替代 Timer，在 Service 运行更稳定
    private val handler = Handler(Looper.getMainLooper())
    private var monitorRunnable: Runnable? = null

    private var thisSessionMinutes = 0
    private var totalDailyMinutes = 0
    private val REMIND_THRESHOLD = 20

    companion object {
        var isRunning = false
    }

    // 监听屏幕亮灭的广播接收器
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    // 屏幕熄灭：本次计时重置，停止增加，但当日累计保留
                    thisSessionMinutes = 0
                    updateNotification()
                }
                Intent.ACTION_SCREEN_ON -> {
                    // 屏幕点亮：立即刷新一次通知
                    updateNotification()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        loadDailyStats()
        createNotificationChannel()
        
        // 注册屏幕状态广播
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, filter)

        startForeground(NOTIFICATION_ID, buildTimerNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startMonitoring()
        return START_STICKY
    }

    private fun startMonitoring() {
        monitorRunnable = object : Runnable {
            override fun run() {
                // 判断屏幕是否真的亮着
                val displayManager = getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
                val isScreenOn = displayManager.displays.any { it.state == android.view.Display.STATE_ON }

                if (isScreenOn) {
                    thisSessionMinutes++
                    totalDailyMinutes++
                    saveDailyStats()

                    if (thisSessionMinutes >= REMIND_THRESHOLD) {
                        showReminder()
                        thisSessionMinutes = 0
                    }
                }

                updateNotification()
                // 每分钟执行一次，并在后台持续运行
                handler.postDelayed(this, 60 * 1000)
            }
        }
        handler.post(monitorRunnable!!)
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildTimerNotification())
    }

    private fun buildTimerNotification(): Notification {
        val nextRemindIn = (REMIND_THRESHOLD - thisSessionMinutes).coerceAtLeast(0)
        val contentText = "本次: ${thisSessionMinutes}分 | 当日: ${totalDailyMinutes}分 | 下次提醒: ${nextRemindIn}分"

        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("爱弥斯护眼守护中 ✨")
            .setContentText("距离下次提醒还有 $nextRemindIn 分钟")
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true) // 禁止每分钟都响铃
            .setOngoing(true)       // 禁止用户划掉通知
            .setPriority(NotificationCompat.PRIORITY_LOW) // 设为低优先级，不打扰用户
            .build()
    }

    // 数据保存逻辑保持不变...
    private fun saveDailyStats() {
        val sp = getSharedPreferences("EyeCareStats", Context.MODE_PRIVATE)
        sp.edit().putInt("minutes_${getTodayDate()}", totalDailyMinutes).apply()
    }

    private fun loadDailyStats() {
        val sp = getSharedPreferences("EyeCareStats", Context.MODE_PRIVATE)
        totalDailyMinutes = sp.getInt("minutes_${getTodayDate()}", 0)
    }

    private fun getTodayDate(): String = java.text.SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())

    private fun showReminder() {
        val intent = Intent(this, ReminderActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL_ID, "护眼计时通知", NotificationManager.IMPORTANCE_LOW
            ))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        monitorRunnable?.let { handler.removeCallbacks(it) }
        unregisterReceiver(screenReceiver)
        super.onDestroy()
    }
}
