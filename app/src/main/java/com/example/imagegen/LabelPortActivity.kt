package com.example.imagegen

import android.Manifest
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.imagegen.databinding.ActivityLabelPortBinding
import com.example.imagegen.generator.LabelImageGenerator
import com.example.imagegen.generator.MessageParser
import com.example.imagegen.util.ImageSaver

/**
 * 标签端口图片生成界面
 *  - Tab1：手动输入标签 + 端口
 *  - Tab2：粘贴完整消息自动解析
 *  - 生成后在预览区显示，可保存到相册
 */
class LabelPortActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLabelPortBinding
    private var generatedBitmap: Bitmap? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) doSave()
        else Toast.makeText(this, R.string.msg_save_failed, Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLabelPortBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.title = getString(R.string.btn_label_port)

        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.tab_manual))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.tab_paste))

        binding.tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                if (tab.position == 0) {
                    binding.layoutManual.visibility = android.view.View.VISIBLE
                    binding.layoutPaste.visibility = android.view.View.GONE
                } else {
                    binding.layoutManual.visibility = android.view.View.GONE
                    binding.layoutPaste.visibility = android.view.View.VISIBLE
                }
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
        })

        binding.btnGenerate.setOnClickListener { onGenerateClick() }
        binding.btnSave.setOnClickListener { onSaveClick() }
    }

    private fun onGenerateClick() {
        val (tag, port) = readInputs() ?: run {
            Toast.makeText(this, R.string.msg_input_empty, Toast.LENGTH_SHORT).show()
            return
        }

        // 子线程生成图片
        Thread {
            val bmp = LabelImageGenerator.generate(this, tag, port)
            runOnUiThread {
                if (bmp != null) {
                    generatedBitmap = bmp
                    binding.imgPreview.setImageBitmap(bmp)
                    binding.cardPreview.visibility = android.view.View.VISIBLE
                    Toast.makeText(this, R.string.msg_success, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, R.string.msg_no_template, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    /** 返回 (tag, port)；输入不合法返回 null */
    private fun readInputs(): Pair<String, String?>? {
        return if (binding.tabLayout.selectedTabPosition == 0) {
            // 手动输入
            val tag = binding.editTag.text?.toString()?.trim().orEmpty()
            val port = binding.editPort.text?.toString()?.trim().orEmpty()
            if (tag.isEmpty()) null else tag to port.ifEmpty { null }
        } else {
            // 粘贴消息
            val msg = binding.editPaste.text?.toString()?.trim().orEmpty()
            if (msg.isEmpty()) null else {
                val (t, p) = MessageParser.parseTagAndPort(msg)
                if (t.isNullOrEmpty()) null else t to p
            }
        }
    }

    private fun onSaveClick() {
        val bmp = generatedBitmap ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Android 9- 需要运行时申请写权限
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                doSave()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        } else {
            doSave()
        }
    }

    private fun doSave() {
        val bmp = generatedBitmap ?: return
        Thread {
            val uri = ImageSaver.saveToGallery(this, bmp, "label_port")
            runOnUiThread {
                if (uri != null) {
                    Toast.makeText(this, R.string.msg_save_success, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, R.string.msg_save_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
}
