<template>
  <div>
    <ConfigSection title="云同步">
      <van-cell title="自动拉取">
        <template #right-icon>
          <van-switch v-model="config.cloudSyncAutoPull" size="20px" />
        </template>
      </van-cell>
      <van-cell title="服务商">
        <template #label>
          <van-radio-group direction="horizontal" v-model="config.cloudSyncProvider">
            <van-radio name="GITHUB_GIST">GitHub Gist</van-radio>
            <van-radio name="GITEE_GIST">Gitee</van-radio>
            <van-radio name="NETWORK_URL">网络链接</van-radio>
            <van-radio name="LOCAL_FILE">本地文件</van-radio>
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
        <van-cell title="Gitee Id">
          <template #label>
            <van-field class="!p-0" v-model="config.cloudSyncGiteeGistId" />
          </template>
        </van-cell>
        <van-cell title="Gitee Token">
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
      <van-cell title="播放器详细信息">
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

    <ConfigSection title="上传">
      <van-cell title="上传APK">
        <template #extra>
          <van-uploader :after-read="uploadApk" accept=".apk" />
        </template>
      </van-cell>
      <van-cell title="上传二进制">
        <template #extra>
          <van-uploader :after-read="uploadAllInOne" accept="*.*" />
        </template>
      </van-cell>
      <van-cell title="二进制文件地址">
        <template #label>
          <van-field class="!p-0" v-model="config.feiyangAllInOneFilePath" />
        </template>
      </van-cell>
      <template #footer>
        <div class="flex justify-end">
          <van-button size="small" type="primary" @click="pushConfig">推送</van-button>
        </div>
      </template>
    </ConfigSection>
  </div>
</template>

<script setup lang="ts">
import { useConfig } from '@/composables/useConfig'
import { requestApi } from '@/utils/api'
import {
  showFailToast,
  showLoadingToast,
  closeToast,
  type UploaderFileListItem,
} from 'vant'
import ConfigSection from '@/components/ConfigSection.vue'

const { config, pushConfig } = useConfig()

async function uploadApk(items: UploaderFileListItem | UploaderFileListItem[]) {
  const item = Array.isArray(items) ? items[0] : items
  if (!item?.file) return
  showLoadingToast({ message: '加载中...', forbidClick: true, duration: 0 })
  try {
    const formData = new FormData()
    formData.append('filename', item.file)
    await requestApi('/api/upload/apk', { method: 'POST', body: formData })
    closeToast()
  } catch (e) {
    showFailToast('上传apk失败')
    console.error(e)
  }
}

async function uploadAllInOne(items: UploaderFileListItem | UploaderFileListItem[]) {
  const item = Array.isArray(items) ? items[0] : items
  if (!item?.file) return
  showLoadingToast({ message: '加载中...', forbidClick: true, duration: 0 })
  try {
    const formData = new FormData()
    formData.append('filename', item.file)
    await requestApi('/api/upload/allinone', { method: 'POST', body: formData })
    closeToast()
  } catch (e) {
    showFailToast('上传AllInOne失败')
    console.error(e)
  }
}
</script>
