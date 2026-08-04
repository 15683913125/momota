# momota


### 工作流程
打开应用 → VerifyActivity 异步拉取 https://cdn.jsdelivr.net/gh/15683913125/momota@main/config.json ：

- maintenance=true → 显示"软件维护中"+ maintenanceMessage ， 禁止返回 ，只能重试
- latestVersionCode > 当前版本 且 forceUpdate=true → 显示"发现新版本 vX.X"+ updateMessage ，点"立即更新"跳浏览器打开 downloadUrl ， 禁止返回
- 网络失败 → 显示"网络连接失败"，可重试， 阻断进入主界面
- 验证通过 → 跳 MainActivity ，主页顶部展示 announcement 公告卡片
### 你需要做的
1. 把 config.json 和 APK 文件上传到 GitHub 仓库 https://github.com/15683913125/momota 的 main 分支根目录
2. 后续发新版本时：
   - 修改 app/build.gradle.kts 的 versionCode / versionName
   - 更新 GitHub 上的 config.json ：把 latestVersionCode / latestVersionName 改为新值， forceUpdate 设为 true ， downloadUrl 指向新 APK
3. 远程下架：把 config.json 的 maintenance 改为 true 即可立即生效
4. jsDelivr CDN 有几分钟缓存延迟，需立即生效可访问 https://purge.jsdelivr.net/gh/15683913125/momota@main/config.json 主动刷新
在 Android Studio 里 Sync + Run 即可验证。
