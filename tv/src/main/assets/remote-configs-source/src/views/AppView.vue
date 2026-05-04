<template>
  <div class="page-container">
    <div class="page-header">
      <van-icon name="arrow-left" class="back-icon" @click="$router.push('/')" />
      <span class="page-title">应用</span>
      <span class="placeholder"></span>
    </div>
    
    <div class="page-content">
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
      </ConfigSection>

      <ConfigSection title="缓存">
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

      <ConfigSection title="上传">
        <van-cell title="上传APK">
          <template #label>
            <van-space class="w-full" direction="vertical" size="small">
              <van-uploader :after-read="uploadApk" accept=".apk" />
            </van-space>
          </template>
        </van-cell>
        <van-cell title="上传二进制文件">
          <template #label>
            <van-space class="w-full" direction="vertical" size="small">
              <van-field class="!pl-0" input-align="right" label="文件路径" placeholder="二进制文件地址" v-model="binaryFilePath" />
              <van-uploader :after-read="uploadBinary" />
            </van-space>
          </template>
        </van-cell>
      </ConfigSection>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useConfig } from '@/composables/useConfig'
import { requestApi, getJson } from '@/utils/api'
import { showSuccessToast, showFailToast, showLoadingToast, closeToast, showConfirmDialog } from 'vant'
import type { UploaderFileListItem } from 'vant'
import ConfigSection from '@/components/ConfigSection.vue'

const { config, pushConfig, fetchConfig } = useConfig()

const cacheSize = ref('计算中...')
const binaryFilePath = ref('')

const appStartupScreenLive = computed({
  get: () => config.value.appStartupScreen === 'Live',
  set: (val: boolean) => {
    config.value.appStartupScreen = val ? 'Live' : 'Dashboard'
  }
})

async function fetchCacheSize() {
  try {
    const data = await getJson<{ size: string }>('/api/cache-size')
    cacheSize.value = data.size || '0 B'
  } catch (e) {
    cacheSize.value = '未知'
    console.error(e)
  }
}

async function clearCache() {
  try {
    await showConfirmDialog({
      title: '确认清除缓存',
      message: '这将清除所有缓存数据，包括直播源、节目单、频道图标等。是否继续？',
    })
    showLoadingToast({ message: '清除中...', forbidClick: true, duration: 0 })
    await requestApi('/api/clear-cache', { method: 'POST' })
    showSuccessToast('缓存清除成功')
    fetchCacheSize()
  } catch (e) {
    if (e !== 'cancel') {
      showFailToast('清除缓存失败')
      console.error(e)
    }
  } finally {
    closeToast()
  }
}

async function resetApp() {
  try {
    await showConfirmDialog({
      title: '确认恢复初始化',
      message: '这将重置所有设置和数据到初始状态，此操作不可撤销。是否继续？',
    })
    showLoadingToast({ message: '重置中...', forbidClick: true, duration: 0 })
    await requestApi('/api/reset', { method: 'POST' })
    showSuccessToast('恢复初始化成功，请重新扫码')
  } catch (e) {
    if (e !== 'cancel') {
      showFailToast('恢复初始化失败')
      console.error(e)
    }
  } finally {
    closeToast()
  }
}

async function uploadApk(items: UploaderFileListItem | UploaderFileListItem[]) {
  const item = Array.isArray(items) ? items[0] : items
  if (!item?.file) return
  
  showLoadingToast({ message: '上传中...', forbidClick: true, duration: 0 })
  try {
    const formData = new FormData()
    formData.append('file', item.file)
    const resp = await requestApi('/api/upload/apk', {
      method: 'POST',
      body: formData,
    })
    const data = await resp.json()
    showSuccessToast(`上传成功: ${data.path || 'APK已安装'}`)
  } catch (e) {
    showFailToast('上传APK失败')
    console.error(e)
  } finally {
    closeToast()
  }
}

async function uploadBinary(items: UploaderFileListItem | UploaderFileListItem[]) {
  const item = Array.isArray(items) ? items[0] : items
  if (!item?.file) return
  if (!binaryFilePath.value) {
    showFailToast('请填写二进制文件地址')
    return
  }
  
  showLoadingToast({ message: '上传中...', forbidClick: true, duration: 0 })
  try {
    const formData = new FormData()
    formData.append('file', item.file)
    formData.append('path', binaryFilePath.value)
    const resp = await requestApi('/api/file/content', {
      method: 'POST',
      body: formData,
    })
    const data = await resp.json()
    showSuccessToast(`上传成功: ${data.path}`)
  } catch (e) {
    showFailToast('上传二进制文件失败')
    console.error(e)
  } finally {
    closeToast()
  }
}

onMounted(() => {
  fetchConfig()
  fetchCacheSize()
})
</script>

<style scoped>
.back-icon {
  font-size: 20px;
  cursor: pointer;
  color: #323233;
}

.placeholder {
  width: 20px;
}
</style>
