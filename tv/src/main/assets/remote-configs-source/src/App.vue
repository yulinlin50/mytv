<template>
  <van-config-provider :theme="isDark ? 'dark' : undefined">
    <div class="app-container">
      <header class="app-header">
        <h1>{{ appInfo?.appTitle || '天光云影' }}</h1>
      </header>

      <main class="app-content">
        <van-empty v-if="!appInfo" image="network" />
        <template v-else>
          <AppView v-if="activeTab === 'app'" />
          <IptvView v-else-if="activeTab === 'iptv'" />
          <EpgView v-else-if="activeTab === 'epg'" />
          <UiView v-else-if="activeTab === 'ui'" />
          <ControlView v-else-if="activeTab === 'control'" />
          <PlayerView v-else-if="activeTab === 'player'" />
          <ThemeView v-else-if="activeTab === 'theme'" />
          <CloudSyncView v-else-if="activeTab === 'cloudSync'" />
          <DebugView v-else-if="activeTab === 'debug'" />
          <LogsView v-else-if="activeTab === 'logs'" />
        </template>
      </main>

      <van-tabbar v-model="activeTab" :fixed="false">
        <van-tabbar-item name="app" icon="apps">应用</van-tabbar-item>
        <van-tabbar-item name="iptv" icon="tv-o">直播源</van-tabbar-item>
        <van-tabbar-item name="epg" icon="notes-o">节目单</van-tabbar-item>
        <van-tabbar-item name="ui" icon="setting-o">界面</van-tabbar-item>
        <van-tabbar-item name="control" icon="guide-o">控制</van-tabbar-item>
        <van-tabbar-item name="player" icon="play-circle-o">播放器</van-tabbar-item>
        <van-tabbar-item name="theme" icon="brush-o">主题</van-tabbar-item>
        <van-tabbar-item name="cloudSync" icon="cloud-o">云同步</van-tabbar-item>
        <van-tabbar-item name="debug" icon="bug-o">调试</van-tabbar-item>
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

const activeTab = ref('app')
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
