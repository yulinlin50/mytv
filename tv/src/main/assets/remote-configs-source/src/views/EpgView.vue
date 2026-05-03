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
      <van-cell title="节目单源管理">
        <template #label>
          <van-space class="w-full" direction="vertical" size="small">
            <span>管理已添加的节目单源</span>
            <van-list>
              <van-cell
                v-for="(source, index) in epgSourceList"
                :key="index"
                :title="source.name"
                :label="source.url"
              >
                <template #right-icon>
                  <van-switch
                    v-model="source.isEnabled"
                    size="20px"
                    @change="updateEpgSourceList"
                  />
                </template>
              </van-cell>
            </van-list>
          </van-space>
        </template>
      </van-cell>
      <van-cell title="直播源节目单配置">
        <template #label>
          <van-space class="w-full" direction="vertical" size="small">
            <span>为每个直播源配置对应的节目单源</span>
            <van-list>
              <van-cell
                v-for="(mapping, index) in iptvEpgMappings"
                :key="index"
                :title="mapping.iptvName"
              >
                <template #label>
                  <van-field
                    class="!p-0"
                    placeholder="选择节目单源"
                    v-model="mapping.epgSourceName"
                    @blur="updateIptvEpgMappings"
                  />
                </template>
              </van-cell>
            </van-list>
          </van-space>
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
import { ref, onMounted } from 'vue'
import { useConfig } from '@/composables/useConfig'
import { postJson, getJson } from '@/utils/api'
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

const epgSourceList = ref<Array<{ name: string; url: string; isEnabled: boolean }>>([])
const iptvEpgMappings = ref<Array<{ iptvName: string; epgSourceName: string }>>([])

async function fetchEpgSourceList() {
  try {
    const data = await getJson<Array<{ name: string; url: string; isEnabled: boolean }>>('/api/epg-source/list')
    epgSourceList.value = data
  } catch (e) {
    console.error(e)
  }
}

async function updateEpgSourceList() {
  try {
    await postJson('/api/epg-source/list', epgSourceList.value)
    showSuccessToast('节目单源已更新')
  } catch (e) {
    showFailToast('更新节目单源失败')
    console.error(e)
  }
}

async function fetchIptvEpgMappings() {
  try {
    const data = await getJson<Array<{ iptvName: string; epgSourceName: string }>>('/api/iptv-epg-mappings')
    iptvEpgMappings.value = data
  } catch (e) {
    console.error(e)
  }
}

async function updateIptvEpgMappings() {
  try {
    await postJson('/api/iptv-epg-mappings', iptvEpgMappings.value)
    showSuccessToast('直播源节目单配置已更新')
  } catch (e) {
    showFailToast('更新直播源节目单配置失败')
    console.error(e)
  }
}

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
    await fetchEpgSourceList()
  } catch (e) {
    showFailToast('推送节目单失败')
    console.error(e)
  } finally {
    closeToast()
  }
}

onMounted(() => {
  fetchEpgSourceList()
  fetchIptvEpgMappings()
})
</script>
