package com.example.imagegen

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.imagegen.databinding.ActivityMainBinding

/** 主界面：选择两个功能入口，顶部展示远程公告 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 显示远程公告（由 VerifyActivity 拉取后缓存）
        RemoteConfig.get()?.let { config ->
            if (config.announcement.isNotBlank()) {
                binding.tvAnnouncement.text = config.announcement
                binding.cardAnnouncement.visibility = View.VISIBLE
            }
        }

        binding.cardLabelPort.setOnClickListener {
            startActivity(Intent(this, LabelPortActivity::class.java))
        }
        binding.cardShangkuan.setOnClickListener {
            startActivity(Intent(this, ShangkuanActivity::class.java))
        }
    }
}
