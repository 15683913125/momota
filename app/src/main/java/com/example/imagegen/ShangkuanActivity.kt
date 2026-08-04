package com.example.imagegen

import android.Manifest
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.imagegen.databinding.ActivityShangkuanBinding
import com.example.imagegen.generator.ShangkuanGenerator
import com.example.imagegen.util.ImageSaver

/**
 * 商宽表单生成界面
 *  - 输入：用 + 分隔的 6/7 字段文本
 *  - 输出：宽带业务确认单 + 感谢信两张图片
 */
class ShangkuanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityShangkuanBinding
    private var confirmBitmap: Bitmap? = null
    private var thankBitmap: Bitmap? = null
    private var currentSaveTarget: Bitmap? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val target = currentSaveTarget
        if (granted && target != null) doSave(target)
        else Toast.makeText(this, R.string.msg_save_failed, Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShangkuanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnGenerate.setOnClickListener { onGenerateClick() }
        binding.btnSaveConfirm.setOnClickListener { onSaveClick(confirmBitmap) }
        binding.btnSaveThank.setOnClickListener { onSaveClick(thankBitmap) }
    }

    private fun onGenerateClick() {
        val text = binding.editShangkuan.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) {
            Toast.makeText(this, R.string.msg_input_empty, Toast.LENGTH_SHORT).show()
            return
        }

        Thread {
            val result = ShangkuanGenerator.generate(this, text)
            runOnUiThread {
                if (result == null || result.isEmpty()) {
                    Toast.makeText(this, R.string.msg_no_template, Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                confirmBitmap = result.getOrNull(0)
                thankBitmap = result.getOrNull(1)
                confirmBitmap?.let { binding.imgConfirm.setImageBitmap(it) }
                thankBitmap?.let { binding.imgThank.setImageBitmap(it) }
                binding.layoutResults.visibility = View.VISIBLE
                Toast.makeText(this, R.string.msg_success, Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun onSaveClick(bitmap: Bitmap?) {
        if (bitmap == null) {
            Toast.makeText(this, R.string.msg_no_template, Toast.LENGTH_SHORT).show()
            return
        }
        currentSaveTarget = bitmap
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                doSave(bitmap)
            } else {
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        } else {
            doSave(bitmap)
        }
    }

    private fun doSave(bitmap: Bitmap) {
        val prefix = if (bitmap === confirmBitmap) "宽带业务确认单" else "感谢信"
        Thread {
            val uri = ImageSaver.saveToGallery(this, bitmap, prefix)
            runOnUiThread {
                val msg = if (uri != null) R.string.msg_save_success else R.string.msg_save_failed
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }.start()
    }
}
