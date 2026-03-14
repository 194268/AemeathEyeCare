package com.aemeath.eyecare

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
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

        // 检查权限
        if (!hasUsageStatsPermission()) {
            requestUsageStatsPermission()
        }

        btnStart.setOnClickListener {
            if (hasUsageStatsPermission()) {
                startMonitoring()
            } else {
                Toast.makeText(this, "请先授予使用统计权限", Toast.LENGTH_SHORT).show()
                requestUsageStatsPermission()
()
            }
        }

        btnStop.setOnClickListener {
            stopMonitoring()
        }
    }

    private fun startMonitoring() {
        val serviceIntent = Intent(this, ScreenMonitorService::class.java)
        startForegroundService(serviceIntent)
        tvStatus.text = "爱弥斯正在守护你的眼睛... ✨"
        btnStart.isEnabled = false
        btnStop.isEnabled = true
        Toast.makeText(this, "守护已启动！20分钟后提醒哦~", Toast.LENGTH_SHORT).show()
    }

    private fun stopMonitoring() {
        val serviceIntent = Intent(this, ScreenMonitorService::class.java)
        stopService(serviceIntent)
        tvStatus.text = "守护已暂停"
        btnStart.isEnabled = true
        btnStop.isEnabled = false
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

    private fun requestUsageStatsPermission() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        startActivity(intent)
    }
}