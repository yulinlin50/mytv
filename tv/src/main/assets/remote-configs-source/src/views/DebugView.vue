<template>
  <div>
    <ConfigSection title="调试" show-push-button @push="pushConfig">
      <van-cell title="开发者模式">
        <template #right-icon>
          <van-switch v-model="config.debugDeveloperMode" size="20px" />
        </template>
      </van-cell>
      <van-cell title="显示FPS">
        <template #label>
          <span class="text-gray text-12px">在屏幕左上角显示fps和柱状图</span>
        </template>
        <template #right-icon>
          <van-switch v-model="config.debugShowFps" size="20px" />
        </template>
      </van-cell>
      <van-cell title="显示播放器信息">
        <template #label>
          <span class="text-gray text-12px">显示播放器详细信息（编码、解码器、采样率等）</span>
        </template>
        <template #right-icon>
          <van-switch v-model="config.debugShowVideoPlayerMetadata" size="20px" />
        </template>
      </van-cell>
      <van-cell title="显示布局网格">
        <template #right-icon>
          <van-switch v-model="config.debugShowLayoutGrids" size="20px" />
        </template>
      </van-cell>
      <van-cell title="上传APK">
        <template #label>
          <span class="text-gray text-12px">上传APK文件以进行调试</span>
        </template>
        <template #right-icon>
          <van-uploader :after-read="uploadApk" accept=".apk" />
        </template>
      </van-cell>
      <van-cell title="上传二进制">
        <template #label>
          <span class="text-gray text-12px">上传AllInOne二进制文件</span>
        </template>
        <template #right-icon>
          <van-uploader :after-read="uploadAllInOne" accept="*/*" />
        </template>
      </van-cell>
    </ConfigSection>
  </div>
</template>

<script setup lang="ts">
import { useConfig } from '@/composables/useConfig'
import { requestApi } from '@/utils/api'
import {
  showFailToast,
  showLoadingToast,
  closeToast,
  type UploaderFileListItem,
} from 'vant'
import ConfigSection from '@/components/ConfigSection.vue'

const { config, pushConfig } = useConfig()

async function uploadApk(items: UploaderFileListItem | UploaderFileListItem[]) {
  const item = Array.isArray(items) ? items[0] : items
  if (!item?.file) return
  showLoadingToast({ message: '上传中...', forbidClick: true, duration: 0 })
  try {
    const formData = new FormData()
    formData.append('filename', item.file)
    await requestApi('/api/upload/apk', { method: 'POST', body: formData })
    closeToast()
  } catch (e) {
    showFailToast('上传apk失败')
    console.error(e)
  }
}

async function uploadAllInOne(items: UploaderFileListItem | UploaderFileListItem[]) {
  const item = Array.isArray(items) ? items[0] : items
  if (!item?.file) return
  showLoadingToast({ message: '上传中...', forbidClick: true, duration: 0 })
  try {
    const formData = new FormData()
    formData.append('filename', item.file)
    await requestApi('/api/upload/allinone', { method: 'POST', body: formData })
    closeToast()
  } catch (e) {
    showFailToast('上传AllInOne失败')
    console.error(e)
  }
}
</script>
