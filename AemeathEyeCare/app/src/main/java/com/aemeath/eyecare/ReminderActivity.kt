package com.aemeath.eyecare

import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ReminderActivity : AppCompatActivity() {

    private lateinit var tvContent: TextView
    private lateinit var btnDismiss: Button
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        setContentView(R.layout.activity_reminder)

        tvContent = findViewById(R.id.tv_content)
        btnDismiss = findViewById(R.id.btn_dismiss)

        // 通知服务：进入休息状态
        startService(Intent(this, ScreenMonitorService::class.java).apply { putExtra("CMD", "REST_START") })

        // 播放音频
        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.music)
            mediaPlayer?.start()
        } catch (e: Exception) { e.printStackTrace() }

        btnDismiss.isEnabled = false

        // 20秒倒计时
        object : CountDownTimer(20000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                tvContent.text = "远眺休息中，听听音乐...\n倒计时: ${millisUntilFinished / 1000}s"
            }

            override fun onFinish() {
                tvContent.text = "休息完成！✨"
                btnDismiss.isEnabled = true
                btnDismiss.text = "完成"
                stopMusic() // 倒计时结束自动停止
                
                // 通知服务：休息结束，重置计时
                startService(Intent(this@ReminderActivity, ScreenMonitorService::class.java).apply { putExtra("CMD", "REST_DONE") })
            }
        }.start()

        btnDismiss.setOnClickListener { finish() }
    }

    private fun stopMusic() {
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null
    }

    override fun onDestroy() {
        stopMusic()
        super.onDestroy()
    }
}
