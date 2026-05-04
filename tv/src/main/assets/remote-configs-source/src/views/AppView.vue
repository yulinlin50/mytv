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
import { requestApi } from '@/utils/api'
import { showSuccessToast, showFailToast, showLoadingToast, closeToast, showConfirmDialog } from 'vant'
import type { UploaderFileListItem } from 'vant'
import ConfigSection from '@/components/ConfigSection.vue'

const { config, pushConfig, fetchConfig } = useConfig()

const cacheSize = ref('0 B')
const binaryFilePath = ref('')

const appStartupScreenLive = computed({
  get: () => config.value.appStartupScreen === 'Live',
  set: (val: boolean) => {
    config.value.appStartupScreen = val ? 'Live' : 'Dashboard'
  }
})

async function fetchCacheSize() {
  try {
    await requestApi('/api/about')
    cacheSize.value = '计算中...'
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
    showSuccessToast('缓存清除功能暂未实现')
  } catch (e) {
    if (e !== 'cancel') {
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
    showSuccessToast('恢复初始化功能暂未实现')
  } catch (e) {
    if (e !== 'cancel') {
      console.error(e)
    }
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
.page-container {
  min-height: 100vh;
  background: #f7f8fa;
}

.page-header {
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  background: white;
  border-bottom: 1px solid #ebedf0;
}

.back-icon {
  font-size: 20px;
  cursor: pointer;
  color: #323233;
}

.page-title {
  font-size: 16px;
  font-weight: 600;
}

.placeholder {
  width: 20px;
}

.page-content {
  padding-bottom: 20px;
}

.text-gray {
  color: #969799;
}

.text-12px {
  font-size: 12px;
}

.w-full {
  width: 100%;
}
</style>
