package com.aemeath.eyecare

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.util.*

class ScreenMonitorService : Service() {

    private val CHANNEL_ID = "EyeCareServiceChannel"
    private val NOTIFICATION_ID = 1
    private var timer: Timer? = null

    // 计时变量
    private var thisSessionMinutes = 0  // 本次使用时长
    private var totalDailyMinutes = 0    // 当日累计时长
    private val REMIND_THRESHOLD = 20    // 提醒阈值

    companion object {
        var isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        loadDailyStats() // 加载已保存的当日数据
        createNotificationChannel()
        // 初始显示通知
        startForeground(NOTIFICATION_ID, buildTimerNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startMonitoringTask()
        return START_STICKY
    }

    private fun startMonitoringTask() {
        timer?.cancel()
        timer = Timer()
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                val displayManager = getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
                val isScreenOn = displayManager.displays.any { it.state == android.view.Display.STATE_ON }

                if (isScreenOn) {
                    thisSessionMinutes++
                    totalDailyMinutes++
                    saveDailyStats() // 实时保存数据
                    
                    // 检查是否达到提醒阈值
                    if (thisSessionMinutes >= REMIND_THRESHOLD) {
                        showReminder()
                        thisSessionMinutes = 0 // 提醒后重新开始本次计时
                    }
                } else {
                    // 屏幕熄灭时，重置“本次使用时间”，但不重置“当日累计”
                    thisSessionMinutes = 0
                }

                // 刷新通知栏显示
                updateNotification()
            }
        }, 0, 60 * 1000) // 每分钟执行一次
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildTimerNotification())
    }

    private fun buildTimerNotification(): Notification {
        val nextRemindIn = REMIND_THRESHOLD - thisSessionMinutes
        val contentText = "本次已用: ${thisSessionMinutes}分 | 当日累计: ${totalDailyMinutes}分\n距离下次提醒还有: ${nextRemindIn}分"

        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("爱弥斯护眼计时中 ✨")
            .setContentText("距离下次提醒还有 $nextRemindIn 分钟")
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText)) // 使用大布局显示更多文字
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true) // 重要：更新时不发出声音或震动
            .setOngoing(true)       // 设置为持久通知
            .build()
    }

    // --- 数据持久化逻辑 ---

    private fun saveDailyStats() {
        val sp = getSharedPreferences("EyeCareStats", Context.MODE_PRIVATE)
        val today = getTodayDate()
        sp.edit().putInt("minutes_$today", totalDailyMinutes).apply()
    }

    private fun loadDailyStats() {
        val sp = getSharedPreferences("EyeCareStats", Context.MODE_PRIVATE)
        val today = getTodayDate()
        totalDailyMinutes = sp.getInt("minutes_$today", 0)
    }

    private fun getTodayDate(): String {
        val sdf = java.text.SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun showReminder() {
        val intent = Intent(this, ReminderActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        startActivity(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID, "护眼计时通知", NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        timer?.cancel()
        super.onDestroy()
    }
}
