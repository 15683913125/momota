package com.example.imagegen

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.PackageInfoCompat
import com.example.imagegen.databinding.ActivityVerifyBinding
import kotlin.concurrent.thread

/**
 * 启动验证页：打开应用时拉取远程配置，根据结果决定：
 *   1. 维护/下架   -> 显示维护提示，无法进入
 *   2. 强制更新    -> 显示更新提示，跳浏览器下载
 *   3. 网络错误    -> 显示错误提示，可重试（阻断进入）
 *   4. 验证通过    -> 跳转 MainActivity
 *
 * 入口 Activity，禁止返回退出（强制更新/维护/断网时不能绕过）。
 */
class VerifyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVerifyBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVerifyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        check()
    }

    private fun check() {
        showLoading()
        thread {
            try {
                val config = RemoteConfigFetcher.fetch()
                runOnUiThread { handle(config) }
            } catch (e: Exception) {
                runOnUiThread { showError() }
            }
        }
    }

    private fun handle(config: RemoteConfig) {
        if (config.maintenance) {
            showMaintenance(config.maintenanceMessage)
            return
        }
        val info = packageManager.getPackageInfo(packageName, 0)
        val currentCode = PackageInfoCompat.getLongVersionCode(info).toInt()
        if (config.latestVersionCode > currentCode && config.forceUpdate) {
            showForceUpdate(config)
            return
        }
        // 验证通过，进入主界面
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun showLoading() {
        binding.progress.visibility = View.VISIBLE
        binding.tvTitle.visibility = View.GONE
        binding.tvMessage.visibility = View.GONE
        binding.btnAction.visibility = View.GONE
    }

    private fun showError() {
        binding.progress.visibility = View.GONE
        binding.tvTitle.apply {
            visibility = View.VISIBLE
            text = getString(R.string.verify_network_error)
        }
        binding.tvMessage.apply {
            visibility = View.VISIBLE
            text = getString(R.string.verify_network_error_msg)
        }
        binding.btnAction.apply {
            visibility = View.VISIBLE
            text = getString(R.string.verify_retry)
            setOnClickListener { check() }
        }
    }

    private fun showMaintenance(message: String) {
        binding.progress.visibility = View.GONE
        binding.tvTitle.apply {
            visibility = View.VISIBLE
            text = getString(R.string.verify_maintenance)
        }
        binding.tvMessage.apply {
            visibility = View.VISIBLE
            text = if (message.isNotBlank()) message
                   else getString(R.string.verify_maintenance_msg)
        }
        binding.btnAction.apply {
            visibility = View.VISIBLE
            text = getString(R.string.verify_retry)
            setOnClickListener { check() }
        }
    }

    private fun showForceUpdate(config: RemoteConfig) {
        binding.progress.visibility = View.GONE
        binding.tvTitle.apply {
            visibility = View.VISIBLE
            text = getString(R.string.verify_force_update_title, config.latestVersionName)
        }
        binding.tvMessage.apply {
            visibility = View.VISIBLE
            text = if (config.updateMessage.isNotBlank()) config.updateMessage
                   else getString(R.string.verify_force_update_msg)
        }
        binding.btnAction.apply {
            visibility = View.VISIBLE
            text = getString(R.string.verify_update_now)
            setOnClickListener { openUrlInBrowser(this@VerifyActivity, config.downloadUrl) }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // 强制更新/维护/断网时不允许返回退出，保持阻断
        // 仅在加载阶段也不允许直接退出，避免跳过验证
    }
}
