package com.aemeath.eyecare

import android.app.*
import android.app.usage.UsageStatsManager
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
    private var continuousUseMinutes = 0 // 记录连续使用分钟数

    companion object {
        var isRunning = false // 方便 MainActivity 快速检查状态
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        val notification = buildNotification("爱弥斯守护中 ✨", "正在监测你的用眼时间...")
        startForeground(NOTIFICATION_ID, notification)
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
                checkUsageAndNotify()
            }
        }, 0, 60 * 1000) // 每 1 分钟检查一次
    }

    private fun checkUsageAndNotify() {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 60 * 1000 

        // 查询过去一分钟内的应用使用情况
        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, startTime, endTime)
        
        // 如果有应用在使用（意味着屏幕亮着且用户在前台操作）
        if (stats != null && stats.isNotEmpty()) {
            continuousUseMinutes++
        } else {
            // 如果一分钟内没有活动，说明用户休息了，重置计时器
            continuousUseMinutes = 0
        }

        // 达到 20 分钟阈值触发提醒
        if (continuousUseMinutes >= 20) {
            showReminder()
            continuousUseMinutes = 0 // 提醒后重置，开始下一轮
        }
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
                CHANNEL_ID, "护眼监测服务", NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // 建议替换为你的 ic_eye
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        timer?.cancel()
        super.onDestroy()
    }
}
