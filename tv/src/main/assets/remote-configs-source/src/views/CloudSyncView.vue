<template>
  <div class="page-container">
    <div class="page-header">
      <van-icon name="arrow-left" class="back-icon" @click="$router.push('/')" />
      <span class="page-title">云同步</span>
      <span class="placeholder"></span>
    </div>
    
    <div class="page-content">
      <ConfigSection title="云同步" show-push-button @push="pushConfig">
        <van-cell title="云端数据">
          <template #label>
            <span class="text-gray text-12px">长按应用当前云端数据</span>
          </template>
          <template #value>
            <span v-if="syncData">{{ syncData.version }}</span>
            <span v-else class="text-gray">加载中...</span>
          </template>
        </van-cell>
        <van-cell>
          <template #label>
            <div class="flex gap-1">
              <van-button size="small" type="primary" @click="pullCloudData">拉取云端</van-button>
              <van-button size="small" type="success" @click="pushCloudData">推送云端</van-button>
            </div>
          </template>
        </van-cell>
        <van-cell title="自动拉取">
          <template #label>
            <span class="text-gray text-12px">应用启动时自动拉取云端数据并应用</span>
          </template>
          <template #right-icon>
            <van-switch v-model="config.cloudSyncAutoPull" size="20px" />
          </template>
        </van-cell>
        <van-cell title="云同步服务商">
          <template #right-icon>
            <van-radio-group direction="horizontal" v-model="config.cloudSyncProvider">
              <van-radio name="GITHUB_GIST">GitHub</van-radio>
              <van-radio name="GITEE_GIST">Gitee</van-radio>
              <van-radio name="NETWORK_URL">网络</van-radio>
              <van-radio name="LOCAL_FILE">本地</van-radio>
              <van-radio name="WEBDAV">WebDAV</van-radio>
            </van-radio-group>
          </template>
        </van-cell>
        <template v-if="config.cloudSyncProvider === 'GITHUB_GIST'">
          <van-cell title="Github Gist Id">
            <template #label>
              <van-field class="!p-0" v-model="config.cloudSyncGithubGistId" />
            </template>
          </van-cell>
          <van-cell title="Github Gist Token">
            <template #label>
              <van-field class="!p-0" v-model="config.cloudSyncGithubGistToken" />
            </template>
          </van-cell>
        </template>
        <template v-if="config.cloudSyncProvider === 'GITEE_GIST'">
          <van-cell title="Gitee 代码片段 Id">
            <template #label>
              <van-field class="!p-0" v-model="config.cloudSyncGiteeGistId" />
            </template>
          </van-cell>
          <van-cell title="Gitee 代码片段 Token">
            <template #label>
              <van-field class="!p-0" v-model="config.cloudSyncGiteeGistToken" />
            </template>
          </van-cell>
        </template>
        <template v-if="config.cloudSyncProvider === 'NETWORK_URL'">
          <van-cell title="网络链接">
            <template #label>
              <van-field class="!p-0" v-model="config.cloudSyncNetworkUrl" />
            </template>
          </van-cell>
        </template>
        <template v-if="config.cloudSyncProvider === 'LOCAL_FILE'">
          <van-cell title="本地文件路径">
            <template #label>
              <van-field class="!p-0" v-model="config.cloudSyncLocalFilePath" />
            </template>
          </van-cell>
        </template>
        <template v-if="config.cloudSyncProvider === 'WEBDAV'">
          <van-cell title="WebDAV 地址">
            <template #label>
              <van-field class="!p-0" v-model="config.cloudSyncWebDavUrl" />
            </template>
          </van-cell>
          <van-cell title="WebDAV 用户名">
            <template #label>
              <van-field class="!p-0" v-model="config.cloudSyncWebDavUsername" />
            </template>
          </van-cell>
          <van-cell title="WebDAV 密码">
            <template #label>
              <van-field class="!p-0" v-model="config.cloudSyncWebDavPassword" />
            </template>
          </van-cell>
        </template>
      </ConfigSection>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useConfig } from '@/composables/useConfig'
import { getJson, requestApi } from '@/utils/api'
import { showSuccessToast, showFailToast, showLoadingToast, closeToast } from 'vant'
import ConfigSection from '@/components/ConfigSection.vue'

const { config, pushConfig, fetchConfig } = useConfig()

const syncData = ref<{ version: string } | null>(null)

async function fetchSyncData() {
  try {
    syncData.value = await getJson<{ version: string }>('/api/cloud-sync/data')
  } catch (e) {
    console.error(e)
  }
}

async function pullCloudData() {
  showLoadingToast({ message: '拉取中...', forbidClick: true, duration: 0 })
  try {
    const data = await getJson<{ version: string }>('/api/cloud-sync/data')
    await requestApi('/api/cloud-sync/data', { 
      method: 'POST',
      body: JSON.stringify(data)
    })
    showSuccessToast('拉取云端数据成功')
    await fetchSyncData()
  } catch (e) {
    showFailToast('拉取云端数据失败')
    console.error(e)
  } finally {
    closeToast()
  }
}

async function pushCloudData() {
  showLoadingToast({ message: '推送中...', forbidClick: true, duration: 0 })
  try {
    const resp = await requestApi('/api/configs')
    const config = await resp.json()
    await requestApi('/api/cloud-sync/data', { 
      method: 'POST',
      body: JSON.stringify(config)
    })
    showSuccessToast('推送云端数据成功')
    await fetchSyncData()
  } catch (e) {
    showFailToast('推送云端数据失败')
    console.error(e)
  } finally {
    closeToast()
  }
}

onMounted(() => {
  fetchConfig()
  fetchSyncData()
})
</script>

<style scoped>
.back-icon {
  font-size: 20px;
  cursor: pointer;
  color: #323233;
}

.placeholder {
  width: 20px;
}
</style>
