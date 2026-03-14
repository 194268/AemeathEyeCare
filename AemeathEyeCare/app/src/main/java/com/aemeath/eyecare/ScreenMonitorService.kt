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

    override fun onCreate() {
        super.onCreate()
        // 1. 必须创建通知渠道 (Android 8.0+)
        createNotificationChannel()
        
        // 2. 启动前台服务，展示持久化通知
        val notification = buildNotification("爱弥斯正在守护中...", "已经开启用眼监测")
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 3. 启动定时监测任务
        startMonitoringTask()
        
        // START_STICKY 表示如果服务被系统杀掉，系统会尝试重新创建它
        return START_STICKY
    }

    private fun startMonitoringTask() {
        timer?.cancel()
        timer = Timer()
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                // 这里编写你的核心逻辑：比如检查屏幕使用时长
                checkUsageAndNotify()
            }
        }, 0, 60 * 1000) // 每分钟检查一次
    }

    private fun checkUsageAndNotify() {
        // TODO: 使用 UsageStatsManager 获取应用使用情况
        // 如果连续使用超过20分钟，可以发出一个新的通知或弹窗提醒
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "护眼监测服务",
                NotificationManager.IMPORTANCE_LOW // 设置为 LOW 避免一直叮当响
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_eye) // 确保你有这个图标资源
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }
}
