package com.aemeath.eyecare

import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tv_status)
        btnStart = findViewById(R.id.btn_start)
        btnStop = findViewById(R.id.btn_stop)

        // 按钮点击：启动逻辑
        btnStart.setOnClickListener {
            if (checkAllPermissions()) {
                startMonitoring()
            }
        }

        // 按钮点击：停止逻辑
        btnStop.setOnClickListener {
            stopMonitoring()
        }
    }

    override fun onResume() {
        super.onResume()
        // 每次回到页面，更新按钮和文字状态
        updateUIState(isServiceRunning(ScreenMonitorService::class.java))
    }

    private fun startMonitoring() {
        val serviceIntent = Intent(this, ScreenMonitorService::class.java)
        // 适配 Android 8.0+ 的前台服务启动
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        updateUIState(true)
        Toast.makeText(this, "爱弥斯开始守护你的眼睛 ✨", Toast.LENGTH_SHORT).show()
    }

    private fun stopMonitoring() {
        val serviceIntent = Intent(this, ScreenMonitorService::class.java)
        stopService(serviceIntent)
        updateUIState(false)
        Toast.makeText(this, "守护已停止", Toast.LENGTH_SHORT).show()
    }

    private fun updateUIState(isRunning: Boolean) {
        if (isRunning) {
            tvStatus.text = "爱弥斯正在守护你的眼睛... ✨"
            btnStart.isEnabled = false
            btnStop.isEnabled = true
        } else {
            tvStatus.text = "守护已暂停"
            btnStart.isEnabled = true
            btnStop.isEnabled = false
        }
    }

    // --- 权限检查逻辑 ---

    private fun checkAllPermissions(): Boolean {
        // 1. 检查使用情况统计权限 (Usage Stats)
        if (!hasUsageStatsPermission()) {
            Toast.makeText(this, "请开启使用统计权限", Toast.LENGTH_SHORT).show()
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            return false
        }

        // 2. 检查悬浮窗权限 (Overlay) - ReminderActivity 弹出必备
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请开启悬浮窗权限以显示提醒", Toast.LENGTH_SHORT).show()
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            return false
        }

        // 3. Android 13+ 需要额外申请通知权限 (Notification)
        // 篇幅原因此处建议在实际运行中动态申请，或在 Manifest 声明

        return true
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    // 判断服务是否正在运行的辅助函数
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
}
