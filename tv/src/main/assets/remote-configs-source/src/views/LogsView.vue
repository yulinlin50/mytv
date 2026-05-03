<template>
  <div class="page-container">
    <div class="page-header">
      <van-icon name="arrow-left" class="back-icon" @click="$router.push('/')" />
      <span class="page-title">日志</span>
      <span class="placeholder"></span>
    </div>
    
    <div class="page-content">
      <van-list>
        <van-cell
          v-for="item in logs"
          :key="item.time"
          :label="item.cause"
        >
          <template #title>
            <div class="flex flex-col gap-1">
              <div class="flex gap-1 items-center">
                <van-tag plain>{{ item.tag }}</van-tag>
                <van-tag plain>{{ item.level }}</van-tag>
              </div>
              <span>{{ item.message }}</span>
            </div>
          </template>
          <template #extra>
            <span class="text-gray">{{ formatTime(item.time) }}</span>
          </template>
        </van-cell>
      </van-list>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getJson } from '@/utils/api'
import type { LogHistoryItem } from '@/types/config'
import dayjs from 'dayjs'

const logs = ref<LogHistoryItem[]>([])

async function fetchLogs() {
  try {
    const data = await getJson<LogHistoryItem[]>('/api/logs')
    logs.value = data.reverse()
  } catch (e) {
    console.error(e)
  }
}

function formatTime(time: number): string {
  return dayjs(time).format('HH:mm:ss')
}

onMounted(() => {
  fetchLogs()
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

.flex-col {
  flex-direction: column;
}

.gap-1 {
  gap: 4px;
}

.items-center {
  align-items: center;
}

.text-gray {
  color: #969799;
}
</style>
