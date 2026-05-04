<template>
  <div class="home-view">
    <div class="home-header">
      <h1>{{ appInfo?.appTitle || '天光云影' }}</h1>
      <p class="home-subtitle">高级设置</p>
    </div>

    <van-cell-group inset>
      <van-cell title="应用" is-link to="/app">
        <template #label>
          <span class="text-gray">开机自启、画中画、缓存管理</span>
        </template>
        <template #icon>
          <van-icon name="apps-o" class="nav-icon" style="color: #1989fa;" />
        </template>
      </van-cell>
      <van-cell title="直播源" is-link to="/iptv">
        <template #label>
          <span class="text-gray">直播源管理、频道分组、别名设置</span>
        </template>
        <template #icon>
          <van-icon name="tv-o" class="nav-icon" style="color: #07c160;" />
        </template>
      </van-cell>
      <van-cell title="节目单" is-link to="/epg">
        <template #label>
          <span class="text-gray">节目单源管理、直播源关联</span>
        </template>
        <template #icon>
          <van-icon name="orders-o" class="nav-icon" style="color: #ff976a;" />
        </template>
      </van-cell>
      <van-cell title="播放器" is-link to="/player">
        <template #label>
          <span class="text-gray">内核、渲染、显示模式设置</span>
        </template>
        <template #icon>
          <van-icon name="play-circle-o" class="nav-icon" style="color: #7232dd;" />
        </template>
      </van-cell>
      <van-cell title="界面" is-link to="/ui">
        <template #label>
          <span class="text-gray">显示、缩放、交互、性能设置</span>
        </template>
        <template #icon>
          <van-icon name="setting-o" class="nav-icon" style="color: #1989fa;" />
        </template>
      </van-cell>
      <van-cell title="云同步" is-link to="/sync">
        <template #label>
          <span class="text-gray">GitHub、Gitee、WebDAV同步</span>
        </template>
        <template #icon>
          <van-icon name="download" class="nav-icon" style="color: #07c160;" />
        </template>
      </van-cell>
      <van-cell title="调试" is-link to="/debug">
        <template #label>
          <span class="text-gray">开发者模式、FPS显示</span>
        </template>
        <template #icon>
          <van-icon name="info-o" class="nav-icon" style="color: #969799;" />
        </template>
      </van-cell>
      <van-cell title="日志" is-link to="/logs">
        <template #label>
          <span class="text-gray">查看应用日志</span>
        </template>
        <template #icon>
          <van-icon name="description" class="nav-icon" style="color: #969799;" />
        </template>
      </van-cell>
    </van-cell-group>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getJson } from '@/utils/api'
import { useConfig } from '@/composables/useConfig'
import { showLoadingToast, closeToast, showFailToast } from 'vant'
import type { AppInfo } from '@/types/config'

const appInfo = ref<AppInfo | null>(null)
const { fetchConfig } = useConfig()

async function fetchAppInfo() {
  showLoadingToast({ message: '加载中...', forbidClick: true, duration: 0 })
  try {
    appInfo.value = await getJson<AppInfo>('/api/info')
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

<style scoped>
.home-view {
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 20px;
}

.home-header {
  padding: 32px 16px 24px;
  text-align: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  margin-bottom: 12px;
}

.home-header h1 {
  font-size: 24px;
  font-weight: 600;
  margin: 0 0 8px 0;
}

.home-subtitle {
  font-size: 14px;
  opacity: 0.9;
  margin: 0;
}

.nav-icon {
  font-size: 20px;
  margin-right: 12px;
}

.text-gray {
  color: #969799;
  font-size: 12px;
}
</style>
