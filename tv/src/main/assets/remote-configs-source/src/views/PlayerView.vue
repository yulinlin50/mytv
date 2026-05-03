<template>
  <div class="page-container">
    <div class="page-header">
      <van-icon name="arrow-left" class="back-icon" @click="$router.push('/')" />
      <span class="page-title">播放器</span>
      <span class="placeholder"></span>
    </div>
    
    <div class="page-content">
      <ConfigSection title="播放器" show-push-button @push="pushConfig">
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
        <van-cell title="全局显示模式">
          <template #right-icon>
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
        <van-cell title="加载超时">
          <template #label>
            <span class="text-gray text-12px">影响超时换源、断线重连</span>
          </template>
          <template #right-icon>
            <van-stepper
              v-model="config.videoPlayerLoadTimeout"
              min="1000"
              max="60000"
              step="1000"
            />
          </template>
          <template #value>
            <span>{{ formatTimeout(config.videoPlayerLoadTimeout) }}</span>
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
      </ConfigSection>

      <ConfigSection title="音轨">
        <van-cell title="音轨排序">
          <template #label>
            <van-space class="w-full" direction="vertical" size="small">
              <span>每行一个音轨名称，按优先级排序</span>
              <van-field
                class="!p-0"
                rows="5"
                type="textarea"
                v-model="audioTrackSort"
                placeholder="音轨名称1&#10;音轨名称2"
              />
              <div class="flex justify-end">
                <van-button size="small" type="primary" @click="updateAudioTrackSort">推送</van-button>
              </div>
            </van-space>
          </template>
        </van-cell>
        <template #footer>
          <div class="flex justify-end">
            <van-button size="small" type="primary" @click="pushConfig">推送</van-button>
          </div>
        </template>
      </ConfigSection>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useConfig } from '@/composables/useConfig'
import { getText, postText } from '@/utils/api'
import { showSuccessToast, showFailToast, showLoadingToast, closeToast } from 'vant'
import ConfigSection from '@/components/ConfigSection.vue'

const { config, pushConfig } = useConfig()

const videoPlayerHeadersExample =
  'Header-Name-1: Header-Value-1\nHeader-Name-2: Header-Value-2'

const audioTrackSort = ref('')

function formatTimeout(ms: number): string {
  if (ms >= 1000) {
    return `${ms / 1000}秒`
  }
  return `${ms}毫秒`
}

async function fetchAudioTrackSort() {
  try {
    audioTrackSort.value = await getText('/api/audio-track-sort')
  } catch (e) {
    console.error(e)
  }
}

async function updateAudioTrackSort() {
  showLoadingToast({ message: '加载中...', forbidClick: true, duration: 0 })
  try {
    await postText('/api/audio-track-sort', audioTrackSort.value)
    showSuccessToast('更新音轨排序成功')
    await fetchAudioTrackSort()
  } catch (e) {
    showFailToast('更新音轨排序失败')
    console.error(e)
  } finally {
    closeToast()
  }
}

onMounted(() => {
  fetchAudioTrackSort()
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

.flex {
  display: flex;
}

.justify-end {
  justify-content: flex-end;
}
</style>
