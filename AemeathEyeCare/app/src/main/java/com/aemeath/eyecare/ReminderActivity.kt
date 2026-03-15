package com.aemeath.eyecare

import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ReminderActivity : AppCompatActivity() {

    private lateinit var tvContent: TextView
    private lateinit var btnDismiss: Button
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 必须在 setContentView 之前设置
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        
        setContentView(R.layout.activity_reminder)

        tvContent = findViewById(R.id.tv_content)
        btnDismiss = findViewById(R.id.btn_dismiss)

        // 1. 发送开始休息指令给 Service
        val startIntent = Intent(this, ScreenMonitorService::class.java).apply {
            putExtra("CMD", "REST_START")
        }
        startService(startIntent)

        // 2. 播放音频逻辑 (music.mp3 放在 res/raw/music.mp3)
        playMusic()

        btnDismiss.isEnabled = false

        object : CountDownTimer(20000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                tvContent.text = "远眺休息中，听听音乐...\n倒计时: ${millisUntilFinished / 1000}s"
            }

            override fun onFinish() {
                tvContent.text = "休息完成！✨"
                btnDismiss.isEnabled = true
                btnDismiss.text = "完成"
                stopMusic()
                
                // 3. 告诉 Service 休息结束
                val doneIntent = Intent(this@ReminderActivity, ScreenMonitorService::class.java).apply {
                    putExtra("CMD", "REST_DONE")
                }
                startService(doneIntent)
            }
        }.start()

        btnDismiss.setOnClickListener { finish() }
    }

    private fun playMusic() {
        try {
            // R.raw.music 对应 res/raw/music.mp3
            mediaPlayer = MediaPlayer.create(this, R.raw.music)
            mediaPlayer?.apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setOnErrorListener { _, what, extra ->
                    Log.e("EyeCare", "Music Error: $what, $extra")
                    false
                }
                start()
            }
        } catch (e: Exception) {
            Log.e("EyeCare", "Play Music Failed", e)
        }
    }

    private fun stopMusic() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
            mediaPlayer = null
        } catch (e: Exception) { e.printStackTrace() }
    }

    override fun onPause() {
        super.onPause()
        // 如果 Activity 意外被切走，也应停止音乐
        if (isFinishing) stopMusic()
    }

    override fun onDestroy() {
        stopMusic()
        super.onDestroy()
    }
}
