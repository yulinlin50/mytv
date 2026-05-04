<template>
  <div class="page-container">
    <div class="page-header">
      <van-icon name="arrow-left" class="back-icon" @click="$router.push('/')" />
      <span class="page-title">调试</span>
      <span class="placeholder"></span>
    </div>
    
    <div class="page-content">
      <ConfigSection title="调试" show-push-button @push="pushConfig">
        <van-cell title="开发者模式">
          <template #right-icon>
            <van-switch v-model="config.debugDeveloperMode" size="20px" />
          </template>
        </van-cell>
        <van-cell title="显示FPS">
          <template #right-icon>
            <van-switch v-model="config.debugShowFps" size="20px" />
          </template>
        </van-cell>
        <van-cell title="显示播放器信息">
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
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useConfig } from '@/composables/useConfig'
import ConfigSection from '@/components/ConfigSection.vue'

const { config, pushConfig, fetchConfig } = useConfig()

onMounted(() => {
  fetchConfig()
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
</style>
