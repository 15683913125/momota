package com.example.imagegen

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 远程配置：版本验证 + 公告 + 下架控制，统一由一份 JSON 配置驱动。
 *
 * 配置文件托管于 GitHub，通过 jsDelivr CDN 访问（国内更稳定）。
 * 仓库：https://github.com/15683913125/momota
 * 配置 URL：见 [AppConfig.CONFIG_URL]
 *
 * 配置 JSON 字段：
 *   - latestVersionCode   : Int     最新版本号
 *   - latestVersionName   : String  最新版本名
 *   - forceUpdate         : Boolean 是否强制更新
 *   - updateMessage       : String  更新说明
 *   - maintenance         : Boolean 是否下架/维护中
 *   - maintenanceMessage  : String  维护提示
 *   - announcement        : String  主页顶部公告内容
 *   - downloadUrl         : String  APK 下载链接
 */
object AppConfig {
    const val CONFIG_URL = "https://cdn.jsdelivr.net/gh/15683913125/momota@main/config.json"
}

data class RemoteConfig(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val forceUpdate: Boolean,
    val updateMessage: String,
    val maintenance: Boolean,
    val maintenanceMessage: String,
    val announcement: String,
    val downloadUrl: String
) {
    companion object {
        @Volatile
        private var cached: RemoteConfig? = null

        fun cache(config: RemoteConfig) {
            cached = config
        }

        fun get(): RemoteConfig? = cached
    }
}

/** 阻塞式拉取远程配置，失败抛异常 */
object RemoteConfigFetcher {

    fun fetch(): RemoteConfig {
        val conn = (URL(AppConfig.CONFIG_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            requestMethod = "GET"
            useCaches = false
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            val code = conn.responseCode
            if (code != 200) throw RuntimeException("HTTP $code")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val config = RemoteConfig(
                latestVersionCode = json.optInt("latestVersionCode", 0),
                latestVersionName = json.optString("latestVersionName", ""),
                forceUpdate = json.optBoolean("forceUpdate", false),
                updateMessage = json.optString("updateMessage", ""),
                maintenance = json.optBoolean("maintenance", false),
                maintenanceMessage = json.optString("maintenanceMessage", ""),
                announcement = json.optString("announcement", ""),
                downloadUrl = json.optString("downloadUrl", "")
            )
            RemoteConfig.cache(config)
            return config
        } finally {
            conn.disconnect()
        }
    }
}

/** 用系统浏览器打开指定 URL */
fun openUrlInBrowser(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
