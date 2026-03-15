package com.aemeath.eyecare

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ReminderActivity : AppCompatActivity() {

    private lateinit var tvContent: TextView
    private lateinit var btnDismiss: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 确保在锁屏上也能显示
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        
        setContentView(R.layout.activity_reminder)

        tvContent = findViewById(R.id.tv_content)
        btnDismiss = findViewById(R.id.btn_dismiss)

        // 1. 告诉 Service：用户已经看到提醒，开始进入“休息模式”（停止增加用眼时长）
        val startIntent = Intent(this, ScreenMonitorService::class.java)
        startIntent.putExtra("CMD", "REST_START")
        startService(startIntent)

        // 2. 初始状态：倒计时期间不能关闭，防止用户秒关
        btnDismiss.isEnabled = false
        btnDismiss.text = "休息中..."

        // 3. 开启 20 秒倒计时 (20000 毫秒)
        object : CountDownTimer(20000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = millisUntilFinished / 1000
                tvContent.text = "爱弥斯提醒你：\n请向 6 米外远眺\n\n倒计时: ${secondsRemaining}s"
            }

            override fun onFinish() {
                tvContent.text = "休息完成！\n眼睛感觉好些了吗？ ✨"
                btnDismiss.isEnabled = true
                btnDismiss.text = "休息好了"
                
                // 4. 告诉 Service：休息结束，可以开始新的一轮 20 分钟计时了
                val doneIntent = Intent(this@ReminderActivity, ScreenMonitorService::class.java)
                doneIntent.putExtra("CMD", "REST_DONE")
                startService(doneIntent)
            }
        }.start()

        btnDismiss.setOnClickListener {
            finish()
        }
    }

    // 防止用户通过返回键跳过休息
    override fun onBackPressed() {
        if (btnDismiss.isEnabled) {
            super.onBackPressed()
        }
    }
}
