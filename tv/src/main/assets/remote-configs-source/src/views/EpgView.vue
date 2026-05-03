<template>
  <div>
    <ConfigSection title="节目单" show-push-button @push="pushConfig">
      <van-cell title="节目单启用">
        <template #label>
          <span class="text-gray text-12px">首次加载时可能会较为缓慢</span>
        </template>
        <template #right-icon>
          <van-switch v-model="config.epgEnable" size="20px" />
        </template>
      </van-cell>
      <van-cell title="跟随直播源">
        <template #label>
          <span class="text-gray text-12px">优先使用直播源中定义的节目单（每个直播源可单独设置节目单源）</span>
        </template>
        <template #right-icon>
          <van-switch v-model="config.epgSourceFollowIptv" size="20px" />
        </template>
      </van-cell>
      <van-cell title="自定义节目单">
        <template #label>
          <van-space class="w-full" direction="vertical" size="small">
            <span>支持xml、xml.gz格式</span>
            <van-field
              class="!pl-0"
              input-align="right"
              label="名称"
              placeholder="节目单名称"
              v-model="epgSource.name"
            />
            <van-field
              class="!pl-0"
              input-align="right"
              label="链接"
              placeholder="节目单链接"
              v-model="epgSource.url"
            />
            <div class="flex justify-end">
              <van-button size="small" type="primary" @click="pushEpgSource">
                推送节目单
              </van-button>
            </div>
          </van-space>
        </template>
      </van-cell>
      <van-cell title="节目单缓存">
        <template #label>
          <span class="text-gray text-12px">缓存节目单数据以加快加载速度</span>
        </template>
        <template #right-icon>
          <van-switch v-model="config.epgCacheEnable" size="20px" />
        </template>
      </van-cell>
      <van-cell title="节目单缓存时间">
        <template #right-icon>
          <van-stepper v-model="config.epgCacheTimeHours" min="1" max="168" />
        </template>
        <template #label>
          <span class="text-gray text-12px">小时</span>
        </template>
      </van-cell>
    </ConfigSection>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useConfig } from '@/composables/useConfig'
import { postJson } from '@/utils/api'
import {
  showSuccessToast,
  showFailToast,
  showLoadingToast,
  closeToast,
} from 'vant'
import ConfigSection from '@/components/ConfigSection.vue'
import dayjs from 'dayjs'

const { config, pushConfig } = useConfig()

const epgSource = ref({
  name: `添加于${dayjs().format('YYYY-MM-DD HH:mm:ss')}`,
  url: '',
})

async function pushEpgSource() {
  if (!epgSource.value.name) {
    showFailToast('请填写节目单名称')
    return
  }
  if (!epgSource.value.url) {
    showFailToast('请填写节目单链接')
    return
  }

  showLoadingToast({ message: '加载中...', forbidClick: true, duration: 0 })
  try {
    await postJson('/api/epg-source/push', epgSource.value)
    showSuccessToast('推送节目单成功')
  } catch (e) {
    showFailToast('推送节目单失败')
    console.error(e)
  } finally {
    closeToast()
  }
}
</script>
