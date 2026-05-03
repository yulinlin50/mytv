<template>
  <div>
    <ConfigSection title="云同步">
      <van-cell title="云端数据">
        <template #label>
          <span class="text-gray text-12px">长按应用当前云端数据</span>
        </template>
        <template #value>
          <span v-if="syncData">{{ syncData.version }}</span>
          <span v-else class="text-gray">加载中...</span>
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
      <template #footer>
        <div class="flex justify-end">
          <van-button size="small" type="primary" @click="pushConfig">推送</van-button>
        </div>
      </template>
    </ConfigSection>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useConfig } from '@/composables/useConfig'
import { getJson } from '@/utils/api'
import ConfigSection from '@/components/ConfigSection.vue'

const { config, pushConfig } = useConfig()

const syncData = ref<{ version: string } | null>(null)

async function fetchSyncData() {
  try {
    syncData.value = await getJson('/api/sync-data')
  } catch (e) {
    console.error(e)
  }
}

onMounted(() => {
  fetchSyncData()
})
</script>
