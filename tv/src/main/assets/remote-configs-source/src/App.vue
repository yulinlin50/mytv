<template>
  <van-config-provider :theme="isDark ? 'dark' : undefined">
    <router-view v-if="!loading" />
    <div v-else class="loading-container">
      <van-loading size="24px">加载中...</van-loading>
    </div>
  </van-config-provider>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useConfig } from '@/composables/useConfig'

const isDark =
  window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches

const { fetchConfig } = useConfig()
const loading = ref(true)

onMounted(async () => {
  try {
    await fetchConfig()
  } catch (e) {
    console.error('Failed to fetch config:', e)
  } finally {
    loading.value = false
  }
})
</script>

<style>
body {
  margin: 0;
  padding: 0;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

.loading-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
}
</style>
