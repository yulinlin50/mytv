# 高级模式页面重构设计

## 目标

重新构建 `http://ip:10481/advance` 高级模式页面，使用 Vue 3 + Vite + Vant 4 技术栈，实现所有 51 个配置项的完整支持。

## 背景

当前高级模式页面是一个预编译的 Vue.js 应用，源代码不在仓库中。为了实现完整的配置项支持和后续维护，需要从头创建一个新的 Vue 项目。

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue | 3.4+ | 前端框架 |
| Vite | 5+ | 构建工具 |
| Vant | 4+ | UI 组件库 |
| TypeScript | 5+ | 类型支持 |
| dayjs | - | 日期处理 |

## 项目结构

```
tv/src/main/assets/remote-configs-source/
├── src/
│   ├── App.vue                  # 主应用组件
│   ├── main.ts                  # 应用入口
│   ├── views/                   # 页面视图（标签页内容）
│   │   ├── LiveView.vue        # 直播源配置
│   │   ├── PlayerView.vue      # 播放器配置
│   │   ├── UiView.vue          # 界面配置
│   │   ├── SyncView.vue        # 同步与调试
│   │   └── LogsView.vue        # 日志查看
│   ├── components/              # 共享组件
│   │   ├── ConfigSection.vue   # 配置分组容器
│   │   ├── SwitchField.vue     # 开关字段
│   │   ├── RadioField.vue      # 单选字段
│   │   ├── InputField.vue      # 输入字段
│   │   ├── StepperField.vue    # 步进器字段
│   │   ├── SliderField.vue     # 滑块字段
│   │   └── ThemePicker.vue     # 主题选择器
│   ├── composables/             # 组合式函数
│   │   ├── useConfig.ts        # 配置管理
│   │   ├── useApi.ts           # API 请求
│   │   └── useAuth.ts          # 认证管理
│   ├── utils/                   # 工具函数
│   │   ├── api.ts              # API 客户端
│   │   └── auth.ts             # 认证工具
│   ├── types/                   # TypeScript 类型
│   │   └── config.ts           # 配置类型定义
│   └── styles/                  # 样式
│       └── main.css
├── index.html                   # HTML 入口
├── vite.config.ts              # Vite 配置
├── tsconfig.json               # TypeScript 配置
├── tsconfig.node.json         # Node TypeScript 配置
└── package.json                # 项目依赖
```

## 页面架构

底部标签栏 5 个标签：

| 标签 | 图标 | 视图组件 | 配置内容 |
|------|------|---------|---------|
| 直播 | tv-o | LiveView | 直播源 + 控制 + 节目单 + 音轨 |
| 播放 | play-circle-o | PlayerView | 应用 + 播放器 |
| 界面 | setting-o | UiView | 显示 + 缩放 + 交互 + 性能 + 主题 + 缓存 |
| 同步 | tool-o | SyncView | 云同步 + 调试 + 上传 |
| 日志 | list-switch | LogsView | 日志查看 |

## 配置项详细设计

### 直播标签 (LiveView)

#### 直播源分组

| 配置项 | 字段名 | 控件 | 默认值 |
|--------|--------|------|--------|
| 自定义直播源 | - | 推送表单 | - |
| 混合模式 | iptvHybridMode | Radio | DISABLE |
| 相似频道合并 | iptvSimilarChannelMerge | Switch | false |
| 频道图标提供 | iptvChannelLogoProvider | Input | - |
| 频道图标覆盖 | iptvChannelLogoOverride | Switch | false |
| 频道别名 | - | Textarea + 推送 | - |
| 频道收藏启用 | iptvChannelFavoriteEnable | Switch | true |
| 频道分组隐藏 | iptvChannelGroupHiddenList | Textarea | [] |
| 直播源缓存启用 | iptvSourceCacheEnable | Switch | true |
| 直播源缓存时间 | iptvSourceCacheTimeHours | Stepper | 24 |

#### 控制分组

| 配置项 | 字段名 | 控件 | 默认值 |
|--------|--------|------|--------|
| 数字选台 | iptvChannelNoSelectEnable | Switch | true |
| 换台反转 | iptvChannelChangeFlip | Switch | false |
| 换台列表首尾循环 | iptvChannelChangeListLoop | Switch | false |

#### 节目单分组

| 配置项 | 字段名 | 控件 | 默认值 |
|--------|--------|------|--------|
| 启用节目单 | epgEnable | Switch | true |
| 跟随直播源 | epgSourceFollowIptv | Switch | false |
| 自定义节目单 | - | 推送表单 | - |
| 节目单缓存时间 | epgCacheTimeHours | Stepper | 24 |

#### 音轨分组

| 配置项 | 字段名 | 控件 | 默认值 |
|--------|--------|------|--------|
| 音轨排序 | audioTrackSortMode | Radio | LANGUAGE |

### 播放标签 (PlayerView)

#### 应用分组

| 配置项 | 字段名 | 控件 | 默认值 |
|--------|--------|------|--------|
| 开机自启 | appBootLaunch | Switch | false |
| 画中画 | appPipEnable | Switch | false |
| 起始界面 | appStartupScreen | Radio | Dashboard |

#### 播放器分组

| 配置项 | 字段名 | 控件 | 默认值 |
|--------|--------|------|--------|
| 内核 | videoPlayerCore | Radio | MEDIA3 |
| 渲染方式 | videoPlayerRenderMode | Radio | SURFACE_VIEW |
| 显示模式 | videoPlayerDisplayMode | Radio | ORIGINAL |
| 自定义UA | videoPlayerUserAgent | Input | - |
| 自定义Headers | videoPlayerHeaders | Textarea | - |
| 加载超时 | videoPlayerLoadTimeout | Stepper | 10000 |
| 强制音频软解 | videoPlayerForceAudioSoftDecode | Switch | false |
| 停止上一媒体项 | videoPlayerStopPreviousMediaItem | Switch | true |
| 跳过多帧渲染 | videoPlayerSkipMultipleFramesOnSameVSync | Switch | true |

### 界面标签 (UiView)

#### 显示分组

| 配置项 | 字段名 | 控件 | 默认值 |
|--------|--------|------|--------|
| 节目进度 | uiShowEpgProgrammeProgress | Switch | true |
| 常驻节目进度 | uiShowEpgProgrammePermanentProgress | Switch | false |
| 台标显示 | uiShowChannelLogo | Switch | true |
| 频道预览 | uiShowChannelPreview | Switch | false |
| 经典选台界面 | uiUseClassicPanelScreen | Switch | false |
| 时间显示模式 | uiTimeShowMode | Radio | HIDDEN |

#### 缩放分组

| 配置项 | 字段名 | 控件 | 默认值 |
|--------|--------|------|--------|
| 界面密度缩放 | uiDensityScaleRatio | Slider | 0 |
| 界面字体缩放 | uiFontScaleRatio | Slider | 1 |

#### 交互分组

| 配置项 | 字段名 | 控件 | 默认值 |
|--------|--------|------|--------|
| 焦点优化 | uiFocusOptimize | Switch | true |
| 自动关闭延时 | uiScreenAutoCloseDelay | Stepper | 5000 |

#### 性能分组

| 配置项 | 字段名 | 控件 | 默认值 |
|--------|--------|------|--------|
| 低性能模式 | uiLowPerformanceMode | Switch | false |
| 简化频道项 | uiSimplifyChannelItem | Switch | false |
| 频道网格列数 | uiChannelGridColumns | Stepper | 5 |
| EPG更新间隔 | uiEpgUpdateIntervalMs | Stepper | 30000 |

#### 主题分组

| 配置项 | 字段名 | 控件 | 默认值 |
|--------|--------|------|--------|
| 主题选择 | themeAppCurrent | Picker | null |
| 纹理透明度 | themeTextureAlpha | Slider | 0.8 |

#### 缓存分组

| 配置项 | 字段名 | 控件 | 默认值 |
|--------|--------|------|--------|
| 节目单缓存启用 | epgCacheEnable | Switch | true |
| 频道图标缓存启用 | channelLogoCacheEnable | Switch | true |

### 同步标签 (SyncView)

#### 云同步分组

| 配置项 | 字段名 | 控件 | 默认值 |
|--------|--------|------|--------|
| 自动拉取 | cloudSyncAutoPull | Switch | false |
| 服务商 | cloudSyncProvider | Radio | GITHUB_GIST |
| Github Gist Id | cloudSyncGithubGistId | Input | - |
| Github Gist Token | cloudSyncGithubGistToken | Input | - |
| Gitee Id | cloudSyncGiteeGistId | Input | - |
| Gitee Token | cloudSyncGiteeGistToken | Input | - |
| 网络链接 | cloudSyncNetworkUrl | Input | - |
| 本地文件路径 | cloudSyncLocalFilePath | Input | - |
| WebDAV 地址 | cloudSyncWebDavUrl | Input | - |
| WebDAV 用户名 | cloudSyncWebDavUsername | Input | - |
| WebDAV 密码 | cloudSyncWebDavPassword | Input | - |

#### 调试分组

| 配置项 | 字段名 | 控件 | 默认值 |
|--------|--------|------|--------|
| 开发者模式 | debugDeveloperMode | Switch | false |
| 显示FPS | debugShowFps | Switch | false |
| 播放器详细信息 | debugShowVideoPlayerMetadata | Switch | false |
| 显示布局网格 | debugShowLayoutGrids | Switch | false |

#### 上传分组

| 配置项 | 字段名 | 控件 | 默认值 |
|--------|--------|------|--------|
| 上传APK | - | FileUpload | - |
| 上传二进制 | - | FileUpload | - |
| 二进制文件地址 | feiyangAllInOneFilePath | Input | - |

### 日志标签 (LogsView)

显示最近 100 条日志记录，按时间倒序排列。

## API 接口

### 现有接口（无需修改）

| 接口 | 方法 | 说明 |
|------|------|------|
| /api/info | GET | 获取应用信息 |
| /api/configs | GET | 获取所有配置 |
| /api/configs | POST | 更新配置 |
| /api/themes | GET | 获取主题列表 |
| /api/about | GET | 获取应用版本信息 |
| /api/logs | GET | 获取日志历史 |
| /api/iptv-source/push | POST | 推送直播源 |
| /api/epg-source/push | POST | 推送节目单源 |
| /api/channel-alias | GET/POST | 频道别名 |
| /api/upload/apk | POST | 上传 APK |
| /api/upload/allinone | POST | 上传 AllInOne 文件 |

### 需要新增的接口

无。所有必要接口已存在。

## 枚举值映射

前端使用枚举名称字符串：

| 枚举字段 | 前端值 |
|----------|--------|
| iptvHybridMode | DISABLE / IPTV_FIRST / HYBRID_FIRST |
| videoPlayerCore | MEDIA3 / IJK |
| videoPlayerRenderMode | SURFACE_VIEW / TEXTURE_VIEW |
| videoPlayerDisplayMode | ORIGINAL / FILL / CROP / FOUR_THREE / SIXTEEN_NINE / TWO_THIRTY_FIVE_ONE |
| uiTimeShowMode | HIDDEN / ALWAYS / EVERY_HOUR / HALF_HOUR |
| audioTrackSortMode | LANGUAGE / CHANNELS / BITRATE |
| cloudSyncProvider | GITHUB_GIST / GITEE_GIST / NETWORK_URL / LOCAL_FILE / WEBDAV |
| appStartupScreen | Dashboard / Live |

## 认证机制

使用 Token 认证：
1. 从 URL hash 中获取 token：`#token=xxx`
2. Token 存储在 localStorage，有效期 1 小时
3. 每次请求在 Header 中携带：`Authorization: Bearer <token>`
4. 401 响应时清除 token 并提示重新扫描二维码

## 现有文件处理

### 需要删除的文件

构建前删除现有的编译后文件：
```
tv/src/main/assets/remote-configs/
├── index.html          # 删除
├── manifest.webmanifest # 删除
├── pwa-192x192.png     # 删除
├── safari-pinned-tab.svg # 删除
└── assets/
    ├── index-Bq1XwZez.js  # 删除
    └── style-kB29EKMR.css # 删除
```

### 构建输出

构建产物输出到 `tv/src/main/assets/remote-configs/` 目录：
- index.html
- assets/index-[hash].js
- assets/style-[hash].css

## 后端路由调整

修改 `HttpServer.kt`，将 `/advance` 路由指向新构建的文件：

```kotlin
server.get("/advance") { _, response ->
    handleAssets(response, context, "text/html", "remote-configs/index.html")
}
```

现有通配路由 `/remote-configs/(.*)` 会自动处理 assets 目录下的文件。

## 实现步骤

1. 创建 Vue 项目结构
2. 实现核心组件和工具函数
3. 实现各标签页视图
4. 配置 Vite 构建
5. 修改后端路由
6. 测试验证

## 不修改的文件

- Configs.kt - 无需修改
- HttpServerSecurity.kt - 无需修改
- 其他 API 接口 - 无需修改
