package com.aemeath.eyecare

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

        btnStart.setOnClickListener {
            if (checkAllPermissions()) {
                startMonitoring()
            }
        }

        btnStop.setOnClickListener {
            stopMonitoring()
        }
    }

    override fun onResume() {
        super.onResume()
        // 回到 App 时，根据 Service 里的静态变量更新 UI
        updateUIState(ScreenMonitorService.isRunning)
    }

    private fun startMonitoring() {
        val intent = Intent(this, ScreenMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        updateUIState(true)
        Toast.makeText(this, "守护已启动，20分钟后见~", Toast.LENGTH_SHORT).show()
    }

    private fun stopMonitoring() {
        stopService(Intent(this, ScreenMonitorService::class.java))
        updateUIState(false)
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

    private fun checkAllPermissions(): Boolean {
        // 1. 检查使用情况统计权限
        if (!hasUsageStatsPermission()) {
            Toast.makeText(this, "请授予使用统计权限", Toast.LENGTH_SHORT).show()
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            return false
        }
        // 2. 检查悬浮窗权限（显示弹窗必备）
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请开启悬浮窗权限以显示提醒", Toast.LENGTH_SHORT).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return false
        }
        return true
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
