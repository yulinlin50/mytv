<template>
  <div>
    <ConfigSection title="应用" show-push-button @push="pushConfig">
      <van-cell title="开机自启">
        <template #label>
          <span class="text-gray text-12px">请确保当前设备支持该功能</span>
        </template>
        <template #right-icon>
          <van-switch v-model="config.appBootLaunch" size="20px" />
        </template>
      </van-cell>
      <van-cell title="打开直接进入直播">
        <template #right-icon>
          <van-switch v-model="appStartupScreenLive" size="20px" />
        </template>
      </van-cell>
      <van-cell title="画中画">
        <template #right-icon>
          <van-switch v-model="config.appPipEnable" size="20px" />
        </template>
      </van-cell>
      <van-cell title="清除缓存" is-link @click="clearCache">
        <template #label>
          <span class="text-gray text-12px">清除所有缓存数据（直播源、节目单、频道图标等）</span>
        </template>
        <template #value>
          <span class="text-gray">约 {{ cacheSize }}</span>
        </template>
      </van-cell>
      <van-cell title="恢复初始化" is-link @click="resetApp">
        <template #label>
          <span class="text-gray text-12px">重置所有设置和数据到初始状态</span>
        </template>
      </van-cell>
    </ConfigSection>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useConfig } from '@/composables/useConfig'
import { requestApi } from '@/utils/api'
import { showSuccessToast, showFailToast, showConfirmDialog } from 'vant'
import ConfigSection from '@/components/ConfigSection.vue'

const { config, pushConfig } = useConfig()

const cacheSize = ref('0 B')

const appStartupScreenLive = computed({
  get: () => config.value.appStartupScreen === 'Live',
  set: (val: boolean) => {
    config.value.appStartupScreen = val ? 'Live' : 'Dashboard'
  }
})

async function fetchCacheSize() {
  try {
    const resp = await requestApi('/api/cache-size')
    const data = await resp.json()
    cacheSize.value = data.size || '0 B'
  } catch (e) {
    console.error(e)
  }
}

async function clearCache() {
  try {
    await showConfirmDialog({
      title: '确认清除缓存',
      message: '这将清除所有缓存数据，包括直播源、节目单、频道图标等。是否继续？',
    })
    await requestApi('/api/clear-cache', { method: 'POST' })
    showSuccessToast('缓存已清除')
    await fetchCacheSize()
  } catch (e) {
    if (e !== 'cancel') {
      showFailToast('清除缓存失败')
      console.error(e)
    }
  }
}

async function resetApp() {
  try {
    await showConfirmDialog({
      title: '确认恢复初始化',
      message: '这将重置所有设置和数据到初始状态，此操作不可撤销。是否继续？',
    })
    await requestApi('/api/reset', { method: 'POST' })
    showSuccessToast('已恢复初始化')
    window.location.reload()
  } catch (e) {
    if (e !== 'cancel') {
      showFailToast('恢复初始化失败')
      console.error(e)
    }
  }
}

onMounted(() => {
  fetchCacheSize()
})
</script>
