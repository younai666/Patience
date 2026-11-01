# Patience

[下载 Android APK](https://github.com/younai666/Patience/releases/download/v0.2.0/Patience-0.2.0.apk)

极简 Android 纯文本记录。主页只编辑当前会话，空白编辑区没有占位文案。右上角手绘图标打开二级会话目录，每条会话占一行横长胶囊，右侧依次为编辑铅笔与红色垃圾桶。点击编辑回到主页切换会话，目录顶部可新建会话。

输入即自动保存到应用私有 SQLite，重新打开直接恢复上次会话。首次使用直接进入空白会话；删除其他会话不打断当前会话。没有账号、网络权限、云同步或统计功能。应用内不提供导出；卸载或清除应用数据会删除全部记录。

Android 8.0+，固定纯黑背景 `#000000`、纯白文字 `#FFFFFF`。UI 唯一标准库为 [open-claude-ui](https://github.com/Caho1/open-claude-ui)，并按用户要求组合会话目录与手绘图标，原生移植范围与 Token 来源见 [设计依据](docs/design.md)。没有引入 Material 组件库。

构建需要 JDK 17、Android SDK 36：

```sh
./gradlew assembleDebug
./gradlew lintDebug
./gradlew connectedDebugAndroidTest
```

调试安装包：`app/build/outputs/apk/debug/app-debug.apk`；Release 构建产物需签名后安装。GitHub Releases 提供 `Patience-0.2.0.apk`，当前为开发签名预览版，与此前本地测试版保持签名兼容。设备测试会清除 **Patience 的测试数据**，应在开发模拟器执行。

项目保持单应用模块；核心源码为 `Notes.kt`（持久化）、`Cds.kt`（标准库原生适配）、`MainActivity.kt`（主页编辑和二级目录）。GitHub Actions 负责构建与静态检查，设备测试需本地模拟器。
