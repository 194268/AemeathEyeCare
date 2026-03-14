package com.aemeath.eyecare

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class ReminderActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reminder)

        // 让 Activity 覆盖在锁屏之上（如果 Manifest 已经配置，这里是双重保障）
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        findViewById<Button>(R.id.btn_dismiss).setOnClickListener {
            finish() // 关闭弹窗
        }
    }
}
