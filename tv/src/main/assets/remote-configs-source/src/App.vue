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
