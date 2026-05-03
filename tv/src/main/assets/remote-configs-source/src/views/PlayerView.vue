<template>
  <div>
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
  </div>
</template>

<script setup lang="ts">
import { useConfig } from '@/composables/useConfig'
import ConfigSection from '@/components/ConfigSection.vue'

const { config, pushConfig } = useConfig()

const videoPlayerHeadersExample =
  'Header-Name-1: Header-Value-1\nHeader-Name-2: Header-Value-2'

function formatTimeout(ms: number): string {
  if (ms >= 1000) {
    return `${ms / 1000}秒`
  }
  return `${ms}毫秒`
}
</script>
