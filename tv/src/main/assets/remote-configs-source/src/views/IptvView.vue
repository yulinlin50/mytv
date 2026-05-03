<template>
  <div>
    <ConfigSection title="直播源">
      <van-cell title="直播源管理">
        <template #label>
          <van-space class="w-full" direction="vertical" size="small">
            <span>管理已添加的直播源（点击编辑，左滑删除）</span>
            <van-list>
              <van-swipe-cell v-for="(source, index) in config.iptvSourceList" :key="source.id || index">
                <van-cell :title="source.name" :label="source.url" is-link @click="editIptvSource(index)">
                  <template #value>
                    <van-tag v-if="source.isLocal" type="primary">本地</van-tag>
                    <van-tag v-if="source.userAgent" type="success">自定义UA</van-tag>
                    <van-tag v-if="source.epgSource" type="warning">节目单</van-tag>
                  </template>
                  <template #right-icon>
                    <van-switch v-model="source.enabled" size="20px" @change="onSourceChange" @click.stop />
                  </template>
                </van-cell>
                <template #right>
                  <van-button square type="danger" text="删除" @click="deleteIptvSource(index)" />
                </template>
              </van-swipe-cell>
            </van-list>
            <van-button size="small" type="primary" block @click="showAddPopup = true">添加直播源</van-button>
          </van-space>
        </template>
      </van-cell>
      <van-cell title="频道分组管理">
        <template #label>
          <van-space class="w-full" direction="vertical" size="small">
            <span>每行一个分组名称，隐藏的分组将不会显示</span>
            <van-field
              class="!p-0"
              rows="3"
              type="textarea"
              v-model="hiddenGroupText"
            />
          </van-space>
        </template>
      </van-cell>
      <van-cell title="频道别名">
        <template #label>
          <van-space class="w-full" direction="vertical" size="small">
            <van-field
              :placeholder="channelAliasExample"
              class="!p-0"
              rows="5"
              type="textarea"
              v-model="channelAlias"
            />
            <div class="flex justify-end">
              <van-button size="small" type="primary" @click="updateChannelAlias">推送</van-button>
            </div>
          </van-space>
        </template>
      </van-cell>
      <van-cell title="相似频道合并">
        <template #label>
          <span class="text-gray text-12px">相同频道别名将进行合并</span>
        </template>
        <template #right-icon>
          <van-switch v-model="config.iptvSimilarChannelMerge" size="20px" />
        </template>
      </van-cell>
      <van-cell title="频道图标提供">
        <template #label>
          <van-space class="w-full" direction="vertical" size="small">
            <span>格式：{name} - 保持不变，{name|lowercase} - 小写，{name|uppercase} - 大写</span>
            <van-field
              class="!p-0"
              placeholder="https://live.fanmingming.com/tv/{name}.png"
              v-model="config.iptvChannelLogoProvider"
            />
          </van-space>
        </template>
      </van-cell>
      <van-cell title="频道图标覆盖">
        <template #label>
          <span class="text-gray text-12px">使用频道图标提供覆盖直播源中定义的频道图标</span>
        </template>
        <template #right-icon>
          <van-switch v-model="config.iptvChannelLogoOverride" size="20px" />
        </template>
      </van-cell>
      <van-cell title="混合模式">
        <template #right-icon>
          <van-radio-group direction="horizontal" v-model="config.iptvHybridMode">
            <van-radio name="DISABLE">禁用</van-radio>
            <van-radio name="IPTV_FIRST">直播源优先</van-radio>
            <van-radio name="HYBRID_FIRST">混合优先</van-radio>
          </van-radio-group>
        </template>
      </van-cell>
      <van-cell title="直播源缓存">
        <template #label>
          <span class="text-gray text-12px">缓存直播源数据以加快加载速度</span>
        </template>
        <template #right-icon>
          <van-switch v-model="config.iptvSourceCacheEnable" size="20px" />
        </template>
      </van-cell>
      <van-cell title="直播源缓存时间">
        <template #right-icon>
          <van-stepper v-model="config.iptvSourceCacheTimeHours" min="1" max="168" />
        </template>
        <template #label>
          <span class="text-gray text-12px">小时</span>
        </template>
      </van-cell>
      <van-cell title="频道图标缓存">
        <template #label>
          <span class="text-gray text-12px">缓存频道图标以加快加载速度</span>
        </template>
        <template #right-icon>
          <van-switch v-model="config.channelLogoCacheEnable" size="20px" />
        </template>
      </van-cell>
      <van-cell title="清除频道图标缓存" is-link @click="clearImageCache">
        <template #label>
          <span class="text-gray text-12px">清除所有已缓存的频道图标</span>
        </template>
      </van-cell>
      <template #footer>
        <div class="flex justify-end">
          <van-button size="small" type="primary" @click="pushConfig">推送</van-button>
        </div>
      </template>
    </ConfigSection>

    <van-popup v-model:show="showAddPopup" position="bottom" round style="height: 90%">
      <div class="p-4">
        <h3 class="text-center mb-4">{{ editingIndex >= 0 ? '编辑直播源' : '添加直播源' }}</h3>
        <van-form>
          <van-field label="名称" placeholder="直播源名称" v-model="editingSource.name" required />
          <van-field label="类型">
            <template #input>
              <van-radio-group direction="horizontal" v-model="editingSource.isLocal">
                <van-radio :value="false">远程</van-radio>
                <van-radio :value="true">本地</van-radio>
              </van-radio-group>
            </template>
          </van-field>
          <van-field
            v-if="!editingSource.isLocal"
            label="地址"
            placeholder="直播源地址"
            v-model="editingSource.url"
            required
          />
          <van-field
            v-else
            label="文件路径"
            placeholder="本地文件路径"
            v-model="editingSource.url"
            required
          />
          <van-field label="自定义UA" placeholder="可选，留空使用默认UA" v-model="editingSource.userAgent" />
          <van-field label="缓存时间" placeholder="毫秒，可选，留空使用全局设置" type="number" v-model="editingSource.cacheTime" />
          <van-field label="转换JS" placeholder="可选" type="textarea" rows="2" v-model="editingSource.transformJs" />
          <van-cell title="关联节目单">
            <template #value>
              <van-dropdown-menu>
                <van-dropdown-item v-model="editingSource.epgSourceIndex" :options="epgSourceOptions" />
              </van-dropdown-menu>
            </template>
          </van-cell>
          <van-field label="节目单名称" placeholder="手动输入节目单名称" v-model="editingSource.epgSourceName" />
          <van-field label="节目单地址" placeholder="手动输入节目单地址" v-model="editingSource.epgSourceUrl" />
        </van-form>
        <div class="flex gap-2 mt-4">
          <van-button block @click="cancelEdit">取消</van-button>
          <van-button block type="primary" @click="saveIptvSource">保存</van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useConfig } from '@/composables/useConfig'
import { getText, postText, requestApi } from '@/utils/api'
import {
  showSuccessToast,
  showFailToast,
  showLoadingToast,
  closeToast,
  showConfirmDialog,
} from 'vant'
import ConfigSection from '@/components/ConfigSection.vue'
import type { IptvSource, EpgSource } from '@/types/config'

const { config, hiddenGroupText, pushConfig } = useConfig()

const showAddPopup = ref(false)
const editingIndex = ref(-1)
const editingSource = ref<{
  name: string
  url: string
  isLocal: boolean
  userAgent: string
  cacheTime: string
  transformJs: string
  epgSourceIndex: number
  epgSourceName: string
  epgSourceUrl: string
}>({
  name: '',
  url: '',
  isLocal: false,
  userAgent: '',
  cacheTime: '',
  transformJs: '',
  epgSourceIndex: -1,
  epgSourceName: '',
  epgSourceUrl: '',
})

const epgSourceOptions = computed(() => {
  const options = [{ text: '不关联', value: -1 }]
  config.value.epgSourceList.forEach((source, index) => {
    options.push({ text: source.name, value: index })
  })
  return options
})

const channelAlias = ref('')
const channelAliasExample = `{\n    "__suffix": ["高码", "HD"],\n    "频道1": ["别名1", "别名2"]\n}`

function onSourceChange() {
  pushConfig()
}

function editIptvSource(index: number) {
  editingIndex.value = index
  const source = config.value.iptvSourceList[index]
  const epgIndex = source.epgSource
    ? config.value.epgSourceList.findIndex(e => e.url === source.epgSource?.url)
    : -1
  
  editingSource.value = {
    name: source.name,
    url: source.url,
    isLocal: source.isLocal,
    userAgent: source.userAgent || '',
    cacheTime: source.cacheTime?.toString() || '',
    transformJs: source.transformJs || '',
    epgSourceIndex: epgIndex,
    epgSourceName: source.epgSource?.name || '',
    epgSourceUrl: source.epgSource?.url || '',
  }
  showAddPopup.value = true
}

function cancelEdit() {
  showAddPopup.value = false
  editingIndex.value = -1
  editingSource.value = {
    name: '',
    url: '',
    isLocal: false,
    userAgent: '',
    cacheTime: '',
    transformJs: '',
    epgSourceIndex: -1,
    epgSourceName: '',
    epgSourceUrl: '',
  }
}

async function deleteIptvSource(index: number) {
  try {
    await showConfirmDialog({
      title: '确认删除',
      message: `确定要删除直播源 "${config.value.iptvSourceList[index].name}" 吗？`,
    })
    config.value.iptvSourceList.splice(index, 1)
    await pushConfig()
  } catch (e) {
    if (e !== 'cancel') {
      console.error(e)
    }
  }
}

async function saveIptvSource() {
  if (!editingSource.value.name) {
    showFailToast('请填写直播源名称')
    return
  }
  if (!editingSource.value.url) {
    showFailToast('请填写直播源地址')
    return
  }

  let epgSource: EpgSource | null = null
  if (editingSource.value.epgSourceIndex >= 0) {
    const selectedEpg = config.value.epgSourceList[editingSource.value.epgSourceIndex]
    if (selectedEpg) {
      epgSource = {
        name: selectedEpg.name,
        url: selectedEpg.url,
        isLocal: selectedEpg.isLocal,
        expireHours: selectedEpg.expireHours,
      }
    }
  } else if (editingSource.value.epgSourceName && editingSource.value.epgSourceUrl) {
    epgSource = {
      name: editingSource.value.epgSourceName,
      url: editingSource.value.epgSourceUrl,
      isLocal: false,
      expireHours: null,
    }
  }

  const newSource: IptvSource = {
    name: editingSource.value.name,
    url: editingSource.value.url,
    isLocal: editingSource.value.isLocal,
    userAgent: editingSource.value.userAgent || null,
    cacheTime: editingSource.value.cacheTime ? parseInt(editingSource.value.cacheTime) : null,
    transformJs: editingSource.value.transformJs || null,
    epgSource: epgSource,
    enabled: true,
    id: editingIndex.value >= 0 
      ? config.value.iptvSourceList[editingIndex.value].id 
      : crypto.randomUUID(),
  }

  if (editingIndex.value >= 0) {
    config.value.iptvSourceList[editingIndex.value] = newSource
  } else {
    config.value.iptvSourceList.push(newSource)
  }

  await pushConfig()
  showAddPopup.value = false
  cancelEdit()
}

async function fetchChannelAlias() {
  try {
    channelAlias.value = await getText('/api/channel-alias')
  } catch (e) {
    console.error(e)
  }
}

async function updateChannelAlias() {
  showLoadingToast({ message: '加载中...', forbidClick: true, duration: 0 })
  try {
    await postText('/api/channel-alias', channelAlias.value)
    showSuccessToast('更新频道别名成功')
    await fetchChannelAlias()
  } catch (e) {
    showFailToast('更新频道别名失败')
    console.error(e)
  } finally {
    closeToast()
  }
}

async function clearImageCache() {
  try {
    await showConfirmDialog({
      title: '确认清除频道图标缓存',
      message: '这将清除所有已缓存的频道图标。是否继续？',
    })
    await requestApi('/api/clear-image-cache', { method: 'POST' })
    showSuccessToast('频道图标缓存已清除')
  } catch (e) {
    if (e !== 'cancel') {
      showFailToast('清除频道图标缓存失败')
      console.error(e)
    }
  }
}

onMounted(() => {
  fetchChannelAlias()
})
</script>

<style scoped>
.p-4 {
  padding: 16px;
}
.mb-4 {
  margin-bottom: 16px;
}
.mt-4 {
  margin-top: 16px;
}
.gap-2 {
  gap: 8px;
}
</style>
