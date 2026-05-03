# 高级模式页面重构 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 重新构建高级模式页面，使用 Vue 3 + Vite + Vant 4 实现所有 51 个配置项的完整支持。

**Architecture:** 单页应用 + 标签页架构，使用 Vue 3 Composition API，Vite 构建，输出到 Android assets 目录。

**Tech Stack:** Vue 3.4+, Vite 5+, Vant 4, TypeScript 5+, dayjs

---

## File Structure

| File | Action | Responsibility |
|------|--------|---------------|
| `tv/src/main/assets/remote-configs-source/package.json` | Create | 项目依赖配置 |
| `tv/src/main/assets/remote-configs-source/vite.config.ts` | Create | Vite 构建配置 |
| `tv/src/main/assets/remote-configs-source/tsconfig.json` | Create | TypeScript 配置 |
| `tv/src/main/assets/remote-configs-source/tsconfig.node.json` | Create | Node TypeScript 配置 |
| `tv/src/main/assets/remote-configs-source/index.html` | Create | HTML 入口 |
| `tv/src/main/assets/remote-configs-source/src/main.ts` | Create | Vue 应用入口 |
| `tv/src/main/assets/remote-configs-source/src/App.vue` | Create | 主应用组件 |
| `tv/src/main/assets/remote-configs-source/src/types/config.ts` | Create | 配置类型定义 |
| `tv/src/main/assets/remote-configs-source/src/utils/api.ts` | Create | API 客户端 |
| `tv/src/main/assets/remote-configs-source/src/utils/auth.ts` | Create | 认证工具 |
| `tv/src/main/assets/remote-configs-source/src/composables/useConfig.ts` | Create | 配置管理组合函数 |
| `tv/src/main/assets/remote-configs-source/src/composables/useApi.ts` | Create | API 请求组合函数 |
| `tv/src/main/assets/remote-configs-source/src/composables/useAuth.ts` | Create | 认证管理组合函数 |
| `tv/src/main/assets/remote-configs-source/src/components/ConfigSection.vue` | Create | 配置分组组件 |
| `tv/src/main/assets/remote-configs-source/src/views/LiveView.vue` | Create | 直播源配置视图 |
| `tv/src/main/assets/remote-configs-source/src/views/PlayerView.vue` | Create | 播放器配置视图 |
| `tv/src/main/assets/remote-configs-source/src/views/UiView.vue` | Create | 界面配置视图 |
| `tv/src/main/assets/remote-configs-source/src/views/SyncView.vue` | Create | 同步调试视图 |
| `tv/src/main/assets/remote-configs-source/src/views/LogsView.vue` | Create | 日志视图 |
| `tv/src/main/assets/remote-configs-source/src/styles/main.css` | Create | 全局样式 |
| `tv/src/main/java/top/yogiczy/mytv/tv/utlis/HttpServer.kt` | Modify | 更新 /advance 路由 |

---

### Task 1: 创建项目基础结构

**Files:**
- Create: `tv/src/main/assets/remote-configs-source/package.json`
- Create: `tv/src/main/assets/remote-configs-source/vite.config.ts`
- Create: `tv/src/main/assets/remote-configs-source/tsconfig.json`
- Create: `tv/src/main/assets/remote-configs-source/tsconfig.node.json`
- Create: `tv/src/main/assets/remote-configs-source/index.html`

- [ ] **Step 1: 创建 package.json**

```json
{
  "name": "remote-configs",
  "version": "1.0.0",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vue-tsc -b && vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "vue": "^3.4.38",
    "vant": "^4.8.0",
    "dayjs": "^1.11.10"
  },
  "devDependencies": {
    "@types/node": "^20.14.0",
    "@vitejs/plugin-vue": "^5.1.0",
    "typescript": "~5.5.0",
    "vite": "^5.4.0",
    "vue-tsc": "^2.1.0"
  }
}
```

- [ ] **Step 2: 创建 vite.config.ts**

```typescript
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  base: '/remote-configs/',
  build: {
    outDir: '../remote-configs',
    emptyOutDir: true,
    sourcemap: false,
    minify: 'terser',
    terserOptions: {
      compress: {
        drop_console: true,
        drop_debugger: true,
      },
    },
    rollupOptions: {
      output: {
        assetFileNames: 'assets/[name]-[hash][extname]',
        chunkFileNames: 'assets/[name]-[hash].js',
        entryFileNames: 'assets/[name]-[hash].js',
      },
    },
  },
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
})
```

- [ ] **Step 3: 创建 tsconfig.json**

```json
{
  "compilerOptions": {
    "target": "ES2020",
    "useDefineForClassFields": true,
    "module": "ESNext",
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "resolveJsonModule": true,
    "isolatedModules": true,
    "noEmit": true,
    "jsx": "preserve",
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true,
    "paths": {
      "@/*": ["./src/*"]
    }
  },
  "include": ["src/**/*.ts", "src/**/*.tsx", "src/**/*.vue"],
  "references": [{ "path": "./tsconfig.node.json" }]
}
```

- [ ] **Step 4: 创建 tsconfig.node.json**

```json
{
  "compilerOptions": {
    "composite": true,
    "skipLibCheck": true,
    "module": "ESNext",
    "moduleResolution": "bundler",
    "allowSyntheticDefaultImports": true,
    "strict": true
  },
  "include": ["vite.config.ts"]
}
```

- [ ] **Step 5: 创建 index.html**

```html
<!DOCTYPE html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>天光云影 - 高级设置</title>
    <script>
      (function () {
        const prefersDark =
          window.matchMedia &&
          window.matchMedia('(prefers-color-scheme: dark)').matches
        const setting = localStorage.getItem('vueuse-color-scheme') || 'auto'
        if (setting === 'dark' || (prefersDark && setting !== 'light'))
          document.documentElement.classList.toggle('dark', true)
      })()
    </script>
  </head>
  <body class="font-sans">
    <div id="app"></div>
    <script type="module" src="/src/main.ts"></script>
  </body>
</html>
```

- [ ] **Step 6: 创建 src 目录结构**

创建以下空目录：
```
tv/src/main/assets/remote-configs-source/src/
├── types/
├── utils/
├── composables/
├── components/
├── views/
└── styles/
```

---

### Task 2: 创建类型定义和工具函数

**Files:**
- Create: `tv/src/main/assets/remote-configs-source/src/types/config.ts`
- Create: `tv/src/main/assets/remote-configs-source/src/utils/auth.ts`
- Create: `tv/src/main/assets/remote-configs-source/src/utils/api.ts`

- [ ] **Step 1: 创建类型定义 src/types/config.ts**

```typescript
export interface IptvSource {
  name: string
  url: string
  isEnabled: boolean
  isLocalFile: boolean
}

export interface EpgSource {
  name: string
  url: string
  isEnabled: boolean
}

export interface AppThemeDef {
  name: string
  background: string
  texture: string
  textureAlpha: number
}

export interface LogHistoryItem {
  time: number
  tag: string
  level: string
  message: string
  cause?: string
}

export interface AppInfo {
  appTitle: string
  logHistory: LogHistoryItem[]
}

export interface Config {
  appBootLaunch: boolean
  appPipEnable: boolean
  appStartupScreen: 'Dashboard' | 'Live'
  debugDeveloperMode: boolean
  debugShowFps: boolean
  debugShowVideoPlayerMetadata: boolean
  debugShowLayoutGrids: boolean
  iptvSourceList: IptvSource[]
  iptvChannelGroupHiddenList: string[]
  iptvHybridMode: 'DISABLE' | 'IPTV_FIRST' | 'HYBRID_FIRST'
  iptvSimilarChannelMerge: boolean
  iptvChannelLogoProvider: string
  iptvChannelLogoOverride: boolean
  iptvChannelFavoriteEnable: boolean
  iptvChannelChangeFlip: boolean
  iptvChannelNoSelectEnable: boolean
  iptvChannelChangeListLoop: boolean
  iptvSourceCacheEnable: boolean
  iptvSourceCacheTimeHours: number
  epgEnable: boolean
  epgSourceFollowIptv: boolean
  epgCacheTimeHours: number
  epgCacheEnable: boolean
  audioTrackSortMode: 'LANGUAGE' | 'CHANNELS' | 'BITRATE'
  videoPlayerCore: 'MEDIA3' | 'IJK'
  videoPlayerRenderMode: 'SURFACE_VIEW' | 'TEXTURE_VIEW'
  videoPlayerUserAgent: string
  videoPlayerHeaders: string
  videoPlayerLoadTimeout: number
  videoPlayerDisplayMode:
    | 'ORIGINAL'
    | 'FILL'
    | 'CROP'
    | 'FOUR_THREE'
    | 'SIXTEEN_NINE'
    | 'TWO_THIRTY_FIVE_ONE'
  videoPlayerForceAudioSoftDecode: boolean
  videoPlayerStopPreviousMediaItem: boolean
  videoPlayerSkipMultipleFramesOnSameVSync: boolean
  uiShowEpgProgrammeProgress: boolean
  uiShowEpgProgrammePermanentProgress: boolean
  uiShowChannelLogo: boolean
  uiShowChannelPreview: boolean
  uiUseClassicPanelScreen: boolean
  uiDensityScaleRatio: number
  uiFontScaleRatio: number
  uiTimeShowMode: 'HIDDEN' | 'ALWAYS' | 'EVERY_HOUR' | 'HALF_HOUR'
  uiFocusOptimize: boolean
  uiScreenAutoCloseDelay: number
  uiLowPerformanceMode: boolean
  uiChannelGridColumns: number
  uiEpgUpdateIntervalMs: number
  uiSimplifyChannelItem: boolean
  channelLogoCacheEnable: boolean
  themeAppCurrent: AppThemeDef | null
  themeTextureAlpha: number
  cloudSyncAutoPull: boolean
  cloudSyncProvider: 'GITHUB_GIST' | 'GITEE_GIST' | 'NETWORK_URL' | 'LOCAL_FILE' | 'WEBDAV'
  cloudSyncGithubGistId: string
  cloudSyncGithubGistToken: string
  cloudSyncGiteeGistId: string
  cloudSyncGiteeGistToken: string
  cloudSyncNetworkUrl: string
  cloudSyncLocalFilePath: string
  cloudSyncWebDavUrl: string
  cloudSyncWebDavUsername: string
  cloudSyncWebDavPassword: string
  feiyangAllInOneFilePath: string
}

export interface ThemeGroup {
  name: string
  list: AppThemeDef[]
}
```

- [ ] **Step 2: 创建认证工具 src/utils/auth.ts**

```typescript
const TOKEN_KEY = 'http_server_token'
const TOKEN_TIME_KEY = 'http_server_token_time'
const TOKEN_EXPIRE_MS = 3600000

export function getAccessToken(): string | null {
  const hash = window.location.hash
  if (hash && hash.startsWith('#token=')) {
    const token = hash.substring(7)
    if (token) {
      localStorage.setItem(TOKEN_KEY, token)
      localStorage.setItem(TOKEN_TIME_KEY, Date.now().toString())
      window.location.hash = ''
      return token
    }
  }

  const savedToken = localStorage.getItem(TOKEN_KEY)
  const savedTime = parseInt(localStorage.getItem(TOKEN_TIME_KEY) || '0')

  if (savedToken && Date.now() - savedTime < TOKEN_EXPIRE_MS) {
    return savedToken
  }

  clearToken()
  return null
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(TOKEN_TIME_KEY)
}
```

- [ ] **Step 3: 创建 API 客户端 src/utils/api.ts**

```typescript
import { getAccessToken, clearToken } from './auth'

const BASE_URL = ''

export class ApiError extends Error {
  constructor(
    message: string,
    public status: number
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

export async function requestApi(
  url: string,
  config: RequestInit = {}
): Promise<Response> {
  const headers: Record<string, string> = {
    ...(config.headers as Record<string, string>),
  }

  if (
    config.body &&
    typeof config.body === 'string' &&
    !headers['Content-Type']
  ) {
    headers['Content-Type'] = 'application/json'
  }

  const token = getAccessToken()
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }

  const resp = await fetch(`${BASE_URL}${url}`, { ...config, headers })

  if (resp.status === 401) {
    clearToken()
    throw new ApiError('Unauthorized: Token expired', 401)
  }

  if (resp.status !== 200) {
    throw new ApiError(`请求失败：${resp.status}`, resp.status)
  }

  return resp
}

export async function getJson<T>(url: string): Promise<T> {
  const resp = await requestApi(url)
  return resp.json()
}

export async function postJson<T>(url: string, data: unknown): Promise<T> {
  const resp = await requestApi(url, {
    method: 'POST',
    body: JSON.stringify(data),
    headers: { 'Content-Type': 'application/json' },
  })
  return resp.json()
}

export async function getText(url: string): Promise<string> {
  const resp = await requestApi(url)
  return resp.text()
}

export async function postText(url: string, data: string): Promise<void> {
  await requestApi(url, {
    method: 'POST',
    body: data,
  })
}
```

---

### Task 3: 创建组合式函数

**Files:**
- Create: `tv/src/main/assets/remote-configs-source/src/composables/useAuth.ts`
- Create: `tv/src/main/assets/remote-configs-source/src/composables/useApi.ts`
- Create: `tv/src/main/assets/remote-configs-source/src/composables/useConfig.ts`

- [ ] **Step 1: 创建 useAuth.ts**

```typescript
import { ref, computed } from 'vue'
import { getAccessToken, clearToken } from '@/utils/auth'

const token = ref<string | null>(getAccessToken())

export function useAuth() {
  const isAuthenticated = computed(() => token.value !== null)

  function setToken(newToken: string | null) {
    token.value = newToken
    if (newToken === null) {
      clearToken()
    }
  }

  function logout() {
    setToken(null)
  }

  return {
    token,
    isAuthenticated,
    setToken,
    logout,
  }
}
```

- [ ] **Step 2: 创建 useApi.ts**

```typescript
import { ref } from 'vue'
import { requestApi, ApiError } from '@/utils/api'
import { useAuth } from './useAuth'
import { showSuccessToast, showFailToast, showLoadingToast, closeToast } from 'vant'

export function useApi() {
  const loading = ref(false)
  const { logout } = useAuth()

  async function withLoading<T>(fn: () => Promise<T>): Promise<T | null> {
    loading.value = true
    showLoadingToast({ message: '加载中...', forbidClick: true, duration: 0 })
    try {
      return await fn()
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        showFailToast('授权已过期，请重新扫描二维码')
        logout()
      }
      throw e
    } finally {
      loading.value = false
      closeToast()
    }
  }

  async function withToast<T>(
    fn: () => Promise<T>,
    successMsg: string,
    errorMsg: string
  ): Promise<T | null> {
    try {
      const result = await withLoading(fn)
      showSuccessToast(successMsg)
      return result
    } catch (e) {
      showFailToast(errorMsg)
      console.error(e)
      return null
    }
  }

  return {
    loading,
    withLoading,
    withToast,
    requestApi,
  }
}
```

- [ ] **Step 3: 创建 useConfig.ts**

```typescript
import { ref, computed } from 'vue'
import { getJson, postJson } from '@/utils/api'
import type { Config } from '@/types/config'
import { useApi } from './useApi'

const defaultConfig: Config = {
  appBootLaunch: false,
  appPipEnable: false,
  appStartupScreen: 'Dashboard',
  debugDeveloperMode: false,
  debugShowFps: false,
  debugShowVideoPlayerMetadata: false,
  debugShowLayoutGrids: false,
  iptvSourceList: [],
  iptvChannelGroupHiddenList: [],
  iptvHybridMode: 'DISABLE',
  iptvSimilarChannelMerge: false,
  iptvChannelLogoProvider: '',
  iptvChannelLogoOverride: false,
  iptvChannelFavoriteEnable: true,
  iptvChannelChangeFlip: false,
  iptvChannelNoSelectEnable: true,
  iptvChannelChangeListLoop: false,
  iptvSourceCacheEnable: true,
  iptvSourceCacheTimeHours: 24,
  epgEnable: true,
  epgSourceFollowIptv: false,
  epgCacheTimeHours: 24,
  epgCacheEnable: true,
  audioTrackSortMode: 'LANGUAGE',
  videoPlayerCore: 'MEDIA3',
  videoPlayerRenderMode: 'SURFACE_VIEW',
  videoPlayerUserAgent: '',
  videoPlayerHeaders: '',
  videoPlayerLoadTimeout: 10000,
  videoPlayerDisplayMode: 'ORIGINAL',
  videoPlayerForceAudioSoftDecode: false,
  videoPlayerStopPreviousMediaItem: true,
  videoPlayerSkipMultipleFramesOnSameVSync: true,
  uiShowEpgProgrammeProgress: true,
  uiShowEpgProgrammePermanentProgress: false,
  uiShowChannelLogo: true,
  uiShowChannelPreview: false,
  uiUseClassicPanelScreen: false,
  uiDensityScaleRatio: 0,
  uiFontScaleRatio: 1,
  uiTimeShowMode: 'HIDDEN',
  uiFocusOptimize: true,
  uiScreenAutoCloseDelay: 5000,
  uiLowPerformanceMode: false,
  uiChannelGridColumns: 5,
  uiEpgUpdateIntervalMs: 30000,
  uiSimplifyChannelItem: false,
  channelLogoCacheEnable: true,
  themeAppCurrent: null,
  themeTextureAlpha: 0.8,
  cloudSyncAutoPull: false,
  cloudSyncProvider: 'GITHUB_GIST',
  cloudSyncGithubGistId: '',
  cloudSyncGithubGistToken: '',
  cloudSyncGiteeGistId: '',
  cloudSyncGiteeGistToken: '',
  cloudSyncNetworkUrl: '',
  cloudSyncLocalFilePath: '',
  cloudSyncWebDavUrl: '',
  cloudSyncWebDavUsername: '',
  cloudSyncWebDavPassword: '',
  feiyangAllInOneFilePath: '',
}

const config = ref<Config>({ ...defaultConfig })
const hiddenGroupText = ref('')

export function useConfig() {
  const { withLoading, withToast } = useApi()

  const iptvChannelGroupHiddenListArray = computed({
    get: () => hiddenGroupText.value.split('\n').filter((s) => s.trim()),
    set: (val: string[]) => {
      hiddenGroupText.value = val.join('\n')
    },
  })

  async function fetchConfig(): Promise<void> {
    await withLoading(async () => {
      const data = await getJson<Config>('/api/configs')
      config.value = { ...defaultConfig, ...data }
      if (config.value.iptvChannelGroupHiddenList) {
        hiddenGroupText.value = Array.from(
          config.value.iptvChannelGroupHiddenList
        ).join('\n')
      }
    })
  }

  async function pushConfig(): Promise<boolean> {
    const result = await withToast(
      async () => {
        const payload = {
          ...config.value,
          iptvChannelGroupHiddenList: iptvChannelGroupHiddenListArray.value,
        }
        await postJson('/api/configs', payload)
        await fetchConfig()
        return true
      },
      '推送配置成功',
      '推送配置失败'
    )
    return result !== null
  }

  return {
    config,
    hiddenGroupText,
    iptvChannelGroupHiddenListArray,
    fetchConfig,
    pushConfig,
  }
}
```

---

### Task 4: 创建组件

**Files:**
- Create: `tv/src/main/assets/remote-configs-source/src/components/ConfigSection.vue`
- Create: `tv/src/main/assets/remote-configs-source/src/styles/main.css`

- [ ] **Step 1: 创建全局样式 src/styles/main.css**

```css
:root {
  --van-tabbar-item-active-color: #1989fa;
}

.dark {
  color-scheme: dark;
}

body {
  margin: 0;
  padding: 0;
  background-color: #f7f8fa;
}

.dark body {
  background-color: #1a1a1a;
  color: #f5f5f5;
}

.app-container {
  min-height: 100vh;
  padding-top: 46px;
  padding-bottom: 66px;
}

.app-header {
  padding: 16px 20px 0 20px;
}

.app-header h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
}

.app-content {
  padding-bottom: 16px;
}

.w-full {
  width: 100%;
}

.text-gray {
  color: #969799;
}

.text-12px {
  font-size: 12px;
}

.flex {
  display: flex;
}

.justify-end {
  justify-content: flex-end;
}

.gap-1 {
  gap: 4px;
}

.items-center {
  align-items: center;
}
```

- [ ] **Step 2: 创建 ConfigSection.vue**

```vue
<template>
  <van-cell-group :title="title" inset>
    <slot></slot>
    <van-cell v-if="showPushButton">
      <div class="flex justify-end">
        <van-button size="small" type="primary" @click="$emit('push')">
          推送
        </van-button>
      </div>
    </van-cell>
  </van-cell-group>
</template>

<script setup lang="ts">
defineProps<{
  title?: string
  showPushButton?: boolean
}>()

defineEmits<{
  push: []
}>()
</script>
```

---

### Task 5: 创建视图组件 - LiveView

**Files:**
- Create: `tv/src/main/assets/remote-configs-source/src/views/LiveView.vue`

- [ ] **Step 1: 创建 LiveView.vue**

```vue
<template>
  <div>
    <ConfigSection title="直播源">
      <van-cell title="自定义直播源">
        <template #label>
          <van-space class="w-full" direction="vertical" size="small">
            <span>支持m3u、txt格式</span>
            <van-field class="!pl-0" input-align="right" label="类型">
              <template #input>
                <van-radio-group direction="horizontal" v-model="iptvSource.type">
                  <van-radio name="url">远程</van-radio>
                  <van-radio name="file">文件</van-radio>
                  <van-radio name="content">静态</van-radio>
                </van-radio-group>
              </template>
            </van-field>
            <van-field
              class="!pl-0"
              input-align="right"
              label="名称"
              placeholder="直播源名称"
              v-model="iptvSource.name"
            />
            <van-field
              v-if="iptvSource.type === 'url'"
              class="!pl-0"
              input-align="right"
              label="链接"
              placeholder="直播源链接"
              v-model="iptvSource.url"
            />
            <van-field
              v-else-if="iptvSource.type === 'file'"
              class="!pl-0"
              input-align="right"
              label="文件路径"
              placeholder="直播源文件路径"
              v-model="iptvSource.filePath"
            />
            <template v-else-if="iptvSource.type === 'content'">
              <van-field
                class="!pl-0"
                :input-align="iptvSource.content ? 'left' : 'right'"
                label="内容"
                placeholder="直播源内容"
                rows="5"
                type="textarea"
                v-model="iptvSource.content"
              />
              <van-field class="!pl-0" input-align="right" label="上传">
                <template #input>
                  <van-uploader
                    :after-read="uploadIptvSourceContent"
                    accept=".txt,.m3u"
                  />
                </template>
              </van-field>
            </template>
            <div class="flex justify-end">
              <van-button size="small" type="primary" @click="pushIptvSource">
                推送直播源
              </van-button>
            </div>
          </van-space>
        </template>
      </van-cell>
      <van-cell title="混合模式">
        <template #right-icon>
          <van-radio-group direction="horizontal" v-model="config.iptvHybridMode">
            <van-radio name="DISABLE">禁用</van-radio>
            <van-radio name="IPTV_FIRST">直播源优先</van-radio>
            <van-radio name="HYBRID_FIRST">混合优先</van-radio>
          </van-radio-group>
        </template>
      </van-cell>
      <van-cell title="相似频道合并">
        <template #right-icon>
          <van-switch v-model="config.iptvSimilarChannelMerge" size="20px" />
        </template>
      </van-cell>
      <van-cell title="频道图标提供">
        <template #label>
          <van-space class="w-full" direction="vertical" size="small">
            <span>格式：{name} - 保持不变，{name|lowercase} - 小写，{name|uppercase} - 大写</span>
            <van-field
              class="!p-0"
              placeholder="https://live.fanmingming.com/tv/{name}.png"
              v-model="config.iptvChannelLogoProvider"
            />
          </van-space>
        </template>
      </van-cell>
      <van-cell title="频道图标覆盖">
        <template #right-icon>
          <van-switch v-model="config.iptvChannelLogoOverride" size="20px" />
        </template>
      </van-cell>
      <van-cell title="频道别名">
        <template #label>
          <van-space class="w-full" direction="vertical" size="small">
            <van-field
              :placeholder="channelAliasExample"
              class="!p-0"
              rows="5"
              type="textarea"
              v-model="channelAlias"
            />
            <div class="flex justify-end">
              <van-button size="small" type="primary" @click="updateChannelAlias">
                推送
              </van-button>
            </div>
          </van-space>
        </template>
      </van-cell>
      <van-cell title="频道收藏启用">
        <template #right-icon>
          <van-switch v-model="config.iptvChannelFavoriteEnable" size="20px" />
        </template>
      </van-cell>
      <van-cell title="频道分组隐藏">
        <template #label>
          <van-space class="w-full" direction="vertical" size="small">
            <span>每行一个分组名称</span>
            <van-field
              class="!p-0"
              rows="3"
              type="textarea"
              v-model="hiddenGroupText"
            />
          </van-space>
        </template>
      </van-cell>
      <van-cell title="直播源缓存启用">
        <template #right-icon>
          <van-switch v-model="config.iptvSourceCacheEnable" size="20px" />
        </template>
      </van-cell>
      <van-cell title="直播源缓存时间">
        <template #right-icon>
          <van-stepper v-model="config.iptvSourceCacheTimeHours" min="1" max="168" />
        </template>
        <template #label>
          <span class="text-gray text-12px">小时</span>
        </template>
      </van-cell>
    </ConfigSection>

    <ConfigSection title="控制" show-push-button @push="pushConfig">
      <van-cell title="数字选台">
        <template #right-icon>
          <van-switch v-model="config.iptvChannelNoSelectEnable" size="20px" />
        </template>
      </van-cell>
      <van-cell title="换台反转">
        <template #right-icon>
          <van-switch v-model="config.iptvChannelChangeFlip" size="20px" />
        </template>
      </van-cell>
      <van-cell title="换台列表首尾循环">
        <template #right-icon>
          <van-switch v-model="config.iptvChannelChangeListLoop" size="20px" />
        </template>
      </van-cell>
    </ConfigSection>

    <ConfigSection title="节目单">
      <van-cell title="启用节目单">
        <template #right-icon>
          <van-switch v-model="config.epgEnable" size="20px" />
        </template>
      </van-cell>
      <van-cell title="跟随直播源">
        <template #right-icon>
          <van-switch v-model="config.epgSourceFollowIptv" size="20px" />
        </template>
      </van-cell>
      <van-cell title="自定义节目单">
        <template #label>
          <van-space class="w-full" direction="vertical" size="small">
            <span>支持xml、xml.gz格式</span>
            <van-field
              class="!pl-0"
              input-align="right"
              label="名称"
              placeholder="节目单名称"
              v-model="epgSource.name"
            />
            <van-field
              class="!pl-0"
              input-align="right"
              label="链接"
              placeholder="节目单链接"
              v-model="epgSource.url"
            />
            <div class="flex justify-end">
              <van-button size="small" type="primary" @click="pushEpgSource">
                推送节目单
              </van-button>
            </div>
          </van-space>
        </template>
      </van-cell>
      <van-cell title="节目单缓存时间">
        <template #right-icon>
          <van-stepper v-model="config.epgCacheTimeHours" min="1" max="168" />
        </template>
        <template #label>
          <span class="text-gray text-12px">小时</span>
        </template>
      </van-cell>
    </ConfigSection>

    <ConfigSection title="音轨" show-push-button @push="pushConfig">
      <van-cell title="音轨排序">
        <template #right-icon>
          <van-radio-group direction="horizontal" v-model="config.audioTrackSortMode">
            <van-radio name="LANGUAGE">按语言</van-radio>
            <van-radio name="CHANNELS">按声道</van-radio>
            <van-radio name="BITRATE">按比特率</van-radio>
          </van-radio-group>
        </template>
      </van-cell>
    </ConfigSection>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useConfig } from '@/composables/useConfig'
import { postJson, getText, postText } from '@/utils/api'
import {
  showSuccessToast,
  showFailToast,
  showLoadingToast,
  closeToast,
} from 'vant'
import ConfigSection from '@/components/ConfigSection.vue'
import dayjs from 'dayjs'

const { config, hiddenGroupText, pushConfig } = useConfig()

const iptvSource = ref({
  name: `添加于${dayjs().format('YYYY-MM-DD HH:mm:ss')}`,
  type: 'url' as 'url' | 'file' | 'content',
  url: '',
  filePath: '',
  content: '',
})

const epgSource = ref({
  name: `添加于${dayjs().format('YYYY-MM-DD HH:mm:ss')}`,
  url: '',
})

const channelAlias = ref('')
const channelAliasExample = `{\n    "__suffix": ["高码", "HD"],\n    "频道1": ["别名1", "别名2"]\n}`

async function pushIptvSource() {
  if (!iptvSource.value.name) {
    showFailToast('请填写直播源名称')
    return
  }
  if (iptvSource.value.type === 'url' && !iptvSource.value.url) {
    showFailToast('请填写直播源链接')
    return
  }
  if (iptvSource.value.type === 'file' && !iptvSource.value.filePath) {
    showFailToast('请填写直播源文件路径')
    return
  }
  if (iptvSource.value.type === 'content' && !iptvSource.value.content) {
    showFailToast('请填写直播源内容')
    return
  }

  showLoadingToast({ message: '加载中...', forbidClick: true, duration: 0 })
  try {
    await postJson('/api/iptv-source/push', iptvSource.value)
    showSuccessToast('推送直播源成功')
  } catch (e) {
    showFailToast('推送直播源失败')
    console.error(e)
  } finally {
    closeToast()
  }
}

function uploadIptvSourceContent(file: { file: File }) {
  const reader = new FileReader()
  reader.onload = (e) => {
    iptvSource.value.content = e.target?.result as string
  }
  reader.readAsText(file.file)
}

async function pushEpgSource() {
  if (!epgSource.value.name) {
    showFailToast('请填写节目单名称')
    return
  }
  if (!epgSource.value.url) {
    showFailToast('请填写节目单链接')
    return
  }

  showLoadingToast({ message: '加载中...', forbidClick: true, duration: 0 })
  try {
    await postJson('/api/epg-source/push', epgSource.value)
    showSuccessToast('推送节目单成功')
  } catch (e) {
    showFailToast('推送节目单失败')
    console.error(e)
  } finally {
    closeToast()
  }
}

async function fetchChannelAlias() {
  try {
    channelAlias.value = await getText('/api/channel-alias')
  } catch (e) {
    console.error(e)
  }
}

async function updateChannelAlias() {
  showLoadingToast({ message: '加载中...', forbidClick: true, duration: 0 })
  try {
    await postText('/api/channel-alias', channelAlias.value)
    showSuccessToast('更新频道别名成功')
    await fetchChannelAlias()
  } catch (e) {
    showFailToast('更新频道别名失败')
    console.error(e)
  } finally {
    closeToast()
  }
}

onMounted(() => {
  fetchChannelAlias()
})
</script>
```

---

### Task 6: 创建视图组件 - PlayerView

**Files:**
- Create: `tv/src/main/assets/remote-configs-source/src/views/PlayerView.vue`

- [ ] **Step 1: 创建 PlayerView.vue**

```vue
<template>
  <div>
    <ConfigSection title="应用" show-push-button @push="pushConfig">
      <van-cell title="开机自启">
        <template #right-icon>
          <van-switch v-model="config.appBootLaunch" size="20px" />
        </template>
      </van-cell>
      <van-cell title="画中画">
        <template #right-icon>
          <van-switch v-model="config.appPipEnable" size="20px" />
        </template>
      </van-cell>
      <van-cell title="起始界面">
        <template #right-icon>
          <van-radio-group direction="horizontal" v-model="config.appStartupScreen">
            <van-radio name="Dashboard">仪表盘</van-radio>
            <van-radio name="Live">直播</van-radio>
          </van-radio-group>
        </template>
      </van-cell>
    </ConfigSection>

    <ConfigSection title="播放器">
      <van-cell title="内核">
        <template #right-icon>
          <van-radio-group direction="horizontal" v-model="config.videoPlayerCore">
            <van-radio name="MEDIA3">Media3</van-radio>
            <van-radio name="IJK">IjkPlayer</van-radio>
          </van-radio-group>
        </template>
      </van-cell>
      <van-cell title="渲染方式">
        <template #right-icon>
          <van-radio-group direction="horizontal" v-model="config.videoPlayerRenderMode">
            <van-radio name="SURFACE_VIEW">SurfaceView</van-radio>
            <van-radio name="TEXTURE_VIEW">TextureView</van-radio>
          </van-radio-group>
        </template>
      </van-cell>
      <van-cell title="显示模式">
        <template #label>
          <van-radio-group direction="horizontal" v-model="config.videoPlayerDisplayMode">
            <van-radio name="ORIGINAL">原始比例</van-radio>
            <van-radio name="FILL">填充</van-radio>
            <van-radio name="CROP">裁剪</van-radio>
            <van-radio name="FOUR_THREE">4:3</van-radio>
            <van-radio name="SIXTEEN_NINE">16:9</van-radio>
            <van-radio name="TWO_THIRTY_FIVE_ONE">2.35:1</van-radio>
          </van-radio-group>
        </template>
      </van-cell>
      <van-cell title="自定义UA">
        <template #label>
          <van-field
            class="!p-0"
            placeholder="播放器自定义UA"
            v-model="config.videoPlayerUserAgent"
          />
        </template>
      </van-cell>
      <van-cell title="自定义Headers">
        <template #label>
          <van-field
            :placeholder="videoPlayerHeadersExample"
            autosize
            class="!p-0"
            rows="3"
            type="textarea"
            v-model="config.videoPlayerHeaders"
          />
        </template>
      </van-cell>
      <van-cell title="加载超时">
        <template #right-icon>
          <van-stepper
            v-model="config.videoPlayerLoadTimeout"
            min="1000"
            max="60000"
            step="1000"
          />
        </template>
        <template #label>
          <span class="text-gray text-12px">毫秒</span>
        </template>
      </van-cell>
      <van-cell title="强制音频软解">
        <template #right-icon>
          <van-switch v-model="config.videoPlayerForceAudioSoftDecode" size="20px" />
        </template>
      </van-cell>
      <van-cell title="停止上一媒体项">
        <template #right-icon>
          <van-switch v-model="config.videoPlayerStopPreviousMediaItem" size="20px" />
        </template>
      </van-cell>
      <van-cell title="跳过多帧渲染">
        <template #right-icon>
          <van-switch v-model="config.videoPlayerSkipMultipleFramesOnSameVSync" size="20px" />
        </template>
      </van-cell>
      <template #footer>
        <div class="flex justify-end">
          <van-button size="small" type="primary" @click="pushConfig">推送</van-button>
        </div>
      </template>
    </ConfigSection>
  </div>
</template>

<script setup lang="ts">
import { useConfig } from '@/composables/useConfig'
import ConfigSection from '@/components/ConfigSection.vue'

const { config, pushConfig } = useConfig()

const videoPlayerHeadersExample =
  'Header-Name-1: Header-Value-1\nHeader-Name-2: Header-Value-2'
</script>
```

---

### Task 7: 创建视图组件 - UiView

**Files:**
- Create: `tv/src/main/assets/remote-configs-source/src/views/UiView.vue`

- [ ] **Step 1: 创建 UiView.vue**

```vue
<template>
  <div>
    <ConfigSection title="显示" show-push-button @push="pushConfig">
      <van-cell title="节目进度">
        <template #right-icon>
          <van-switch v-model="config.uiShowEpgProgrammeProgress" size="20px" />
        </template>
      </van-cell>
      <van-cell title="常驻节目进度">
        <template #right-icon>
          <van-switch v-model="config.uiShowEpgProgrammePermanentProgress" size="20px" />
        </template>
      </van-cell>
      <van-cell title="台标显示">
        <template #right-icon>
          <van-switch v-model="config.uiShowChannelLogo" size="20px" />
        </template>
      </van-cell>
      <van-cell title="频道预览">
        <template #right-icon>
          <van-switch v-model="config.uiShowChannelPreview" size="20px" />
        </template>
      </van-cell>
      <van-cell title="经典选台界面">
        <template #right-icon>
          <van-switch v-model="config.uiUseClassicPanelScreen" size="20px" />
        </template>
      </van-cell>
      <van-cell title="时间显示模式">
        <template #right-icon>
          <van-radio-group direction="horizontal" v-model="config.uiTimeShowMode">
            <van-radio name="HIDDEN">隐藏</van-radio>
            <van-radio name="ALWAYS">常显</van-radio>
            <van-radio name="EVERY_HOUR">整点</van-radio>
            <van-radio name="HALF_HOUR">半点</van-radio>
          </van-radio-group>
        </template>
      </van-cell>
    </ConfigSection>

    <ConfigSection title="缩放" show-push-button @push="pushConfig">
      <van-cell title="界面密度缩放">
        <template #label>
          <van-slider
            v-model="config.uiDensityScaleRatio"
            :min="0"
            :max="2"
            :step="0.1"
          />
        </template>
      </van-cell>
      <van-cell title="界面字体缩放">
        <template #label>
          <van-slider
            v-model="config.uiFontScaleRatio"
            :min="0.5"
            :max="2"
            :step="0.1"
          />
        </template>
      </van-cell>
    </ConfigSection>

    <ConfigSection title="交互" show-push-button @push="pushConfig">
      <van-cell title="焦点优化">
        <template #right-icon>
          <van-switch v-model="config.uiFocusOptimize" size="20px" />
        </template>
      </van-cell>
      <van-cell title="自动关闭延时">
        <template #right-icon>
          <van-stepper
            v-model="config.uiScreenAutoCloseDelay"
            min="1000"
            max="60000"
            step="1000"
          />
        </template>
        <template #label>
          <span class="text-gray text-12px">毫秒</span>
        </template>
      </van-cell>
    </ConfigSection>

    <ConfigSection title="性能" show-push-button @push="pushConfig">
      <van-cell title="低性能模式">
        <template #right-icon>
          <van-switch v-model="config.uiLowPerformanceMode" size="20px" />
        </template>
      </van-cell>
      <van-cell title="简化频道项">
        <template #right-icon>
          <van-switch v-model="config.uiSimplifyChannelItem" size="20px" />
        </template>
      </van-cell>
      <van-cell title="频道网格列数">
        <template #right-icon>
          <van-stepper v-model="config.uiChannelGridColumns" :min="3" :max="10" />
        </template>
      </van-cell>
      <van-cell title="EPG更新间隔">
        <template #right-icon>
          <van-stepper
            v-model="config.uiEpgUpdateIntervalMs"
            :min="5000"
            :max="300000"
            :step="5000"
          />
        </template>
        <template #label>
          <span class="text-gray text-12px">毫秒</span>
        </template>
      </van-cell>
    </ConfigSection>

    <ConfigSection title="主题">
      <van-cell title="主题选择" is-link @click="showThemePicker = true">
        <template #value>
          {{ config.themeAppCurrent?.name || '默认' }}
        </template>
      </van-cell>
      <van-popup v-model:show="showThemePicker" position="bottom" round>
        <van-picker
          :columns="themeColumns"
          @confirm="onThemeConfirm"
          @cancel="showThemePicker = false"
        />
      </van-popup>
      <van-cell title="纹理透明度">
        <template #label>
          <van-slider
            v-model="config.themeTextureAlpha"
            :min="0"
            :max="1"
            :step="0.1"
          />
        </template>
      </van-cell>
      <template #footer>
        <div class="flex justify-end">
          <van-button size="small" type="primary" @click="pushConfig">推送</van-button>
        </div>
      </template>
    </ConfigSection>

    <ConfigSection title="缓存" show-push-button @push="pushConfig">
      <van-cell title="节目单缓存启用">
        <template #right-icon>
          <van-switch v-model="config.epgCacheEnable" size="20px" />
        </template>
      </van-cell>
      <van-cell title="频道图标缓存启用">
        <template #right-icon>
          <van-switch v-model="config.channelLogoCacheEnable" size="20px" />
        </template>
      </van-cell>
    </ConfigSection>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useConfig } from '@/composables/useConfig'
import { getJson } from '@/utils/api'
import ConfigSection from '@/components/ConfigSection.vue'
import type { ThemeGroup, AppThemeDef } from '@/types/config'

const { config, pushConfig } = useConfig()

const showThemePicker = ref(false)
const themeList = ref<ThemeGroup[]>([])

const themeColumns = computed(() => {
  const groups: Record<string, { text: string; value: AppThemeDef }[]> = {}
  themeList.value.forEach((group) => {
    groups[group.name] = group.list.map((t) => ({ text: t.name, value: t }))
  })
  return [
    Object.keys(groups).map((g) => ({
      text: g,
      children: groups[g],
    })),
  ]
})

async function fetchThemes() {
  try {
    themeList.value = await getJson<ThemeGroup[]>('/api/themes')
  } catch (e) {
    console.error(e)
  }
}

function onThemeConfirm({ selectedOptions }: { selectedOptions: { value: AppThemeDef }[] }) {
  const theme = selectedOptions[1]?.value
  if (theme) {
    config.value.themeAppCurrent = theme
  }
  showThemePicker.value = false
}

onMounted(() => {
  fetchThemes()
})
</script>
```

---

### Task 8: 创建视图组件 - SyncView

**Files:**
- Create: `tv/src/main/assets/remote-configs-source/src/views/SyncView.vue`

- [ ] **Step 1: 创建 SyncView.vue**

```vue
<template>
  <div>
    <ConfigSection title="云同步">
      <van-cell title="自动拉取">
        <template #right-icon>
          <van-switch v-model="config.cloudSyncAutoPull" size="20px" />
        </template>
      </van-cell>
      <van-cell title="服务商">
        <template #label>
          <van-radio-group direction="horizontal" v-model="config.cloudSyncProvider">
            <van-radio name="GITHUB_GIST">GitHub Gist</van-radio>
            <van-radio name="GITEE_GIST">Gitee</van-radio>
            <van-radio name="NETWORK_URL">网络链接</van-radio>
            <van-radio name="LOCAL_FILE">本地文件</van-radio>
            <van-radio name="WEBDAV">WebDAV</van-radio>
          </van-radio-group>
        </template>
      </van-cell>
      <template v-if="config.cloudSyncProvider === 'GITHUB_GIST'">
        <van-cell title="Github Gist Id">
          <template #label>
            <van-field class="!p-0" v-model="config.cloudSyncGithubGistId" />
          </template>
        </van-cell>
        <van-cell title="Github Gist Token">
          <template #label>
            <van-field class="!p-0" v-model="config.cloudSyncGithubGistToken" />
          </template>
        </van-cell>
      </template>
      <template v-if="config.cloudSyncProvider === 'GITEE_GIST'">
        <van-cell title="Gitee Id">
          <template #label>
            <van-field class="!p-0" v-model="config.cloudSyncGiteeGistId" />
          </template>
        </van-cell>
        <van-cell title="Gitee Token">
          <template #label>
            <van-field class="!p-0" v-model="config.cloudSyncGiteeGistToken" />
          </template>
        </van-cell>
      </template>
      <template v-if="config.cloudSyncProvider === 'NETWORK_URL'">
        <van-cell title="网络链接">
          <template #label>
            <van-field class="!p-0" v-model="config.cloudSyncNetworkUrl" />
          </template>
        </van-cell>
      </template>
      <template v-if="config.cloudSyncProvider === 'LOCAL_FILE'">
        <van-cell title="本地文件路径">
          <template #label>
            <van-field class="!p-0" v-model="config.cloudSyncLocalFilePath" />
          </template>
        </van-cell>
      </template>
      <template v-if="config.cloudSyncProvider === 'WEBDAV'">
        <van-cell title="WebDAV 地址">
          <template #label>
            <van-field class="!p-0" v-model="config.cloudSyncWebDavUrl" />
          </template>
        </van-cell>
        <van-cell title="WebDAV 用户名">
          <template #label>
            <van-field class="!p-0" v-model="config.cloudSyncWebDavUsername" />
          </template>
        </van-cell>
        <van-cell title="WebDAV 密码">
          <template #label>
            <van-field class="!p-0" v-model="config.cloudSyncWebDavPassword" />
          </template>
        </van-cell>
      </template>
      <template #footer>
        <div class="flex justify-end">
          <van-button size="small" type="primary" @click="pushConfig">推送</van-button>
        </div>
      </template>
    </ConfigSection>

    <ConfigSection title="调试" show-push-button @push="pushConfig">
      <van-cell title="开发者模式">
        <template #right-icon>
          <van-switch v-model="config.debugDeveloperMode" size="20px" />
        </template>
      </van-cell>
      <van-cell title="显示FPS">
        <template #right-icon>
          <van-switch v-model="config.debugShowFps" size="20px" />
        </template>
      </van-cell>
      <van-cell title="播放器详细信息">
        <template #right-icon>
          <van-switch v-model="config.debugShowVideoPlayerMetadata" size="20px" />
        </template>
      </van-cell>
      <van-cell title="显示布局网格">
        <template #right-icon>
          <van-switch v-model="config.debugShowLayoutGrids" size="20px" />
        </template>
      </van-cell>
    </ConfigSection>

    <ConfigSection title="上传">
      <van-cell title="上传APK">
        <template #extra>
          <van-uploader :after-read="uploadApk" accept=".apk" />
        </template>
      </van-cell>
      <van-cell title="上传二进制">
        <template #extra>
          <van-uploader :after-read="uploadAllInOne" accept="*.*" />
        </template>
      </van-cell>
      <van-cell title="二进制文件地址">
        <template #label>
          <van-field class="!p-0" v-model="config.feiyangAllInOneFilePath" />
        </template>
      </van-cell>
      <template #footer>
        <div class="flex justify-end">
          <van-button size="small" type="primary" @click="pushConfig">推送</van-button>
        </div>
      </template>
    </ConfigSection>
  </div>
</template>

<script setup lang="ts">
import { useConfig } from '@/composables/useConfig'
import { requestApi } from '@/utils/api'
import {
  showSuccessToast,
  showFailToast,
  showLoadingToast,
  closeToast,
} from 'vant'
import ConfigSection from '@/components/ConfigSection.vue'

const { config, pushConfig } = useConfig()

async function uploadApk(file: { file: File }) {
  showLoadingToast({ message: '加载中...', forbidClick: true, duration: 0 })
  try {
    const formData = new FormData()
    formData.append('filename', file.file)
    await requestApi('/api/upload/apk', { method: 'POST', body: formData })
    closeToast()
  } catch (e) {
    showFailToast('上传apk失败')
    console.error(e)
  }
}

async function uploadAllInOne(file: { file: File }) {
  showLoadingToast({ message: '加载中...', forbidClick: true, duration: 0 })
  try {
    const formData = new FormData()
    formData.append('filename', file.file)
    await requestApi('/api/upload/allinone', { method: 'POST', body: formData })
    closeToast()
  } catch (e) {
    showFailToast('上传AllInOne失败')
    console.error(e)
  }
}
</script>
```

---

### Task 9: 创建视图组件 - LogsView

**Files:**
- Create: `tv/src/main/assets/remote-configs-source/src/views/LogsView.vue`

- [ ] **Step 1: 创建 LogsView.vue**

```vue
<template>
  <div>
    <van-list>
      <van-cell
        v-for="item in logs"
        :key="item.time"
        :label="item.cause"
      >
        <template #title>
          <div class="flex flex-col gap-1">
            <div class="flex gap-1 items-center">
              <van-tag plain>{{ item.tag }}</van-tag>
              <van-tag plain>{{ item.level }}</van-tag>
            </div>
            <span>{{ item.message }}</span>
          </div>
        </template>
        <template #extra>
          <span class="text-gray">{{ formatTime(item.time) }}</span>
        </template>
      </van-cell>
    </van-list>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getJson } from '@/utils/api'
import type { LogHistoryItem } from '@/types/config'
import dayjs from 'dayjs'

const logs = ref<LogHistoryItem[]>([])

async function fetchLogs() {
  try {
    const data = await getJson<LogHistoryItem[]>('/api/logs')
    logs.value = data.reverse()
  } catch (e) {
    console.error(e)
  }
}

function formatTime(time: number): string {
  return dayjs(time).format('HH:mm:ss')
}

onMounted(() => {
  fetchLogs()
})
</script>

<style scoped>
.flex-col {
  flex-direction: column;
}
</style>
```

---

### Task 10: 创建主应用组件

**Files:**
- Create: `tv/src/main/assets/remote-configs-source/src/App.vue`
- Create: `tv/src/main/assets/remote-configs-source/src/main.ts`

- [ ] **Step 1: 创建 main.ts**

```typescript
import { createApp } from 'vue'
import App from './App.vue'
import {
  Button,
  Cell,
  CellGroup,
  ConfigProvider,
  Field,
  Form,
  List,
  Picker,
  Popup,
  Radio,
  RadioGroup,
  Slider,
  Space,
  Stepper,
  Switch,
  Tabbar,
  TabbarItem,
  Tag,
  Toast,
  Uploader,
  Empty,
} from 'vant'
import 'vant/lib/index.css'
import './styles/main.css'

const app = createApp(App)

app
  .use(Button)
  .use(Cell)
  .use(CellGroup)
  .use(ConfigProvider)
  .use(Field)
  .use(Form)
  .use(List)
  .use(Picker)
  .use(Popup)
  .use(Radio)
  .use(RadioGroup)
  .use(Slider)
  .use(Space)
  .use(Stepper)
  .use(Switch)
  .use(Tabbar)
  .use(TabbarItem)
  .use(Tag)
  .use(Toast)
  .use(Uploader)
  .use(Empty)

app.mount('#app')
```

- [ ] **Step 2: 创建 App.vue**

```vue
<template>
  <van-config-provider :theme="isDark ? 'dark' : undefined">
    <div class="app-container">
      <header class="app-header">
        <h1>{{ appInfo?.appTitle || '天光云影' }}</h1>
      </header>

      <main class="app-content">
        <van-empty v-if="!appInfo" image="network" />
        <template v-else>
          <LiveView v-if="activeTab === 'live'" />
          <PlayerView v-else-if="activeTab === 'player'" />
          <UiView v-else-if="activeTab === 'ui'" />
          <SyncView v-else-if="activeTab === 'sync'" />
          <LogsView v-else-if="activeTab === 'logs'" />
        </template>
      </main>

      <van-tabbar v-model="activeTab">
        <van-tabbar-item name="live" icon="tv-o">直播</van-tabbar-item>
        <van-tabbar-item name="player" icon="play-circle-o">播放</van-tabbar-item>
        <van-tabbar-item name="ui" icon="setting-o">界面</van-tabbar-item>
        <van-tabbar-item name="sync" icon="tool-o">同步</van-tabbar-item>
        <van-tabbar-item name="logs" icon="list-switch">日志</van-tabbar-item>
      </van-tabbar>
    </div>
  </van-config-provider>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getJson } from '@/utils/api'
import type { AppInfo } from '@/types/config'
import { useConfig } from '@/composables/useConfig'
import { showLoadingToast, closeToast, showFailToast } from 'vant'
import LiveView from '@/views/LiveView.vue'
import PlayerView from '@/views/PlayerView.vue'
import UiView from '@/views/UiView.vue'
import SyncView from '@/views/SyncView.vue'
import LogsView from '@/views/LogsView.vue'

const isDark =
  window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches

const activeTab = ref('live')
const appInfo = ref<AppInfo | null>(null)
const { fetchConfig } = useConfig()

async function fetchAppInfo() {
  showLoadingToast({ message: '加载中...', forbidClick: true, duration: 0 })
  try {
    appInfo.value = await getJson<AppInfo>('/api/info')
    if (appInfo.value.logHistory) {
      appInfo.value.logHistory = appInfo.value.logHistory.reverse()
    }
  } catch (e) {
    showFailToast('无法获取信息')
    console.error(e)
  } finally {
    closeToast()
  }
}

onMounted(async () => {
  await fetchAppInfo()
  await fetchConfig()
})
</script>
```

---

### Task 11: 安装依赖并构建

**Files:**
- All project files

- [ ] **Step 1: 安装依赖**

```bash
cd tv/src/main/assets/remote-configs-source
npm install
```

Expected: Dependencies installed successfully

- [ ] **Step 2: 构建项目**

```bash
npm run build
```

Expected: Build successful, files output to `tv/src/main/assets/remote-configs/`

- [ ] **Step 3: 验证构建输出**

检查 `tv/src/main/assets/remote-configs/` 目录：
- index.html 存在
- assets/index-[hash].js 存在
- assets/style-[hash].css 存在

---

### Task 12: 更新后端路由

**Files:**
- Modify: `tv/src/main/java/top/yogiczy/mytv/tv/utlis/HttpServer.kt`

- [ ] **Step 1: 确认现有路由**

现有代码中 `/advance` 路由已经指向 `remote-configs/index.html`：

```kotlin
server.get("/advance") { _, response ->
    handleAssets(response, context, "text/html", "remote-configs/index.html")
}
```

确认此路由存在且正确。

- [ ] **Step 2: 编译验证**

Run: `Set-Location c:\Users\kun\Documents\EP-main; .\gradlew.bat :tv:compileDebugKotlin --no-daemon 2>&1`

Expected: BUILD SUCCESSFUL

---

### Task 13: 最终验证

**Files:**
- All modified files

- [ ] **Step 1: 完整构建**

Run: `Set-Location c:\Users\kun\Documents\EP-main; .\gradlew.bat :tv:assembleDebug --no-daemon 2>&1`

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 提交代码**

```bash
git add tv/src/main/assets/remote-configs-source/
git add tv/src/main/assets/remote-configs/
git commit -m "feat: rebuild advance page with Vue 3 + Vite + Vant 4"
```

---

## 实现范围总结

### 创建的文件

| 文件 | 职责 |
|------|------|
| package.json | 项目依赖 |
| vite.config.ts | Vite 构建配置 |
| tsconfig.json | TypeScript 配置 |
| tsconfig.node.json | Node TypeScript 配置 |
| index.html | HTML 入口 |
| src/main.ts | Vue 应用入口 |
| src/App.vue | 主应用组件 |
| src/types/config.ts | 类型定义 |
| src/utils/auth.ts | 认证工具 |
| src/utils/api.ts | API 客户端 |
| src/composables/useAuth.ts | 认证组合函数 |
| src/composables/useApi.ts | API 组合函数 |
| src/composables/useConfig.ts | 配置组合函数 |
| src/components/ConfigSection.vue | 配置分组组件 |
| src/views/LiveView.vue | 直播源配置 |
| src/views/PlayerView.vue | 播放器配置 |
| src/views/UiView.vue | 界面配置 |
| src/views/SyncView.vue | 同步调试配置 |
| src/views/LogsView.vue | 日志查看 |
| src/styles/main.css | 全局样式 |

### 修改的文件

| 文件 | 变更 |
|------|------|
| HttpServer.kt | 确认 /advance 路由正确 |

### 删除的文件

| 文件 | 原因 |
|------|------|
| tv/src/main/assets/remote-configs/* | 被 Vite 构建输出替换 |
