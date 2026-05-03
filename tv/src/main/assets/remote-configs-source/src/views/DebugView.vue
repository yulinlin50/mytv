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
    </ConfigSection>
  </div>
</template>

<script setup lang="ts">
import { useConfig } from '@/composables/useConfig'
import ConfigSection from '@/components/ConfigSection.vue'

const { config, pushConfig } = useConfig()
</script>
