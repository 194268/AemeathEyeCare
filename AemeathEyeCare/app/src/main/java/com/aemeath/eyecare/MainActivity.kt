package com.aemeath.eyecare

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

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
        Toast.makeText(this, "守护已启动，查看通知栏可看进度哦 ✨", Toast.LENGTH_SHORT).show()
    }

    private fun stopMonitoring() {
        stopService(Intent(this, ScreenMonitorService::class.java))
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

    // --- 核心修改：权限检查组合拳 ---

    private fun checkAllPermissions(): Boolean {
        // 1. 检查通知权限 (Android 13+ 必须)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "请允许发送通知以显示计时器", Toast.LENGTH_SHORT).show()
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
                return false
            }
        }

        // 2. 检查使用情况统计权限 (Usage Stats)
        if (!hasUsageStatsPermission()) {
            Toast.makeText(this, "请授予使用统计权限", Toast.LENGTH_SHORT).show()
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            return false
        }

        // 3. 检查悬浮窗权限 (Overlay) - 弹窗必备
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请开启悬浮窗权限以显示提醒", Toast.LENGTH_SHORT).show()
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            return false
        }

        return true
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
