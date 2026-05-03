<template>
  <van-config-provider :theme="isDark ? 'dark' : undefined">
    <div class="app-container">
      <header class="app-header">
        <h1>{{ appInfo?.appTitle || '天光云影' }}</h1>
      </header>

      <main class="app-content">
        <van-empty v-if="!appInfo" image="network" />
        <template v-else>
          <AppView />
          <IptvView />
          <EpgView />
          <UiView />
          <ControlView />
          <PlayerView />
          <ThemeView />
          <CloudSyncView />
          <DebugView />
          <LogsView />
        </template>
      </main>
    </div>
  </van-config-provider>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getJson } from '@/utils/api'
import type { AppInfo } from '@/types/config'
import { useConfig } from '@/composables/useConfig'
import { showLoadingToast, closeToast, showFailToast } from 'vant'
import AppView from '@/views/AppView.vue'
import IptvView from '@/views/IptvView.vue'
import EpgView from '@/views/EpgView.vue'
import UiView from '@/views/UiView.vue'
import ControlView from '@/views/ControlView.vue'
import PlayerView from '@/views/PlayerView.vue'
import ThemeView from '@/views/ThemeView.vue'
import CloudSyncView from '@/views/CloudSyncView.vue'
import DebugView from '@/views/DebugView.vue'
import LogsView from '@/views/LogsView.vue'

const isDark =
  window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches

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
