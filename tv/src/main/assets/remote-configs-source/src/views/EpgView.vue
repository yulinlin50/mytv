<template>
  <div class="page-container">
    <div class="page-header">
      <van-icon name="arrow-left" class="back-icon" @click="$router.push('/')" />
      <span class="page-title">节目单</span>
      <span class="placeholder"></span>
    </div>
    
    <div class="page-content">
      <ConfigSection title="节目单设置">
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
        <template #footer>
          <div class="flex justify-end">
            <van-button size="small" type="primary" @click="pushConfig">推送</van-button>
          </div>
        </template>
      </ConfigSection>

      <ConfigSection title="节目单源管理">
        <van-cell title="节目单源列表">
          <template #label>
            <van-space class="w-full" direction="vertical" size="small">
              <span>管理已添加的节目单源（点击编辑，左滑删除）</span>
              <van-list>
                <van-swipe-cell v-for="(source, index) in config.epgSourceList" :key="index">
                  <van-cell :title="source.name" :label="source.url" is-link @click="editEpgSource(index)">
                    <template #value>
                      <van-tag v-if="source.isLocal" type="primary">本地</van-tag>
                    </template>
                  </van-cell>
                  <template #right>
                    <van-button square type="danger" text="删除" @click="deleteEpgSource(index)" />
                  </template>
                </van-swipe-cell>
              </van-list>
              <van-button size="small" type="primary" block @click="showAddPopup = true">添加节目单源</van-button>
            </van-space>
          </template>
        </van-cell>
        <van-cell title="快速添加节目单">
          <template #label>
            <van-space class="w-full" direction="vertical" size="small">
              <span>支持xml、xml.gz格式</span>
              <van-field class="!pl-0" input-align="right" label="名称" placeholder="节目单名称" v-model="quickEpgSource.name" />
              <van-field class="!pl-0" input-align="right" label="链接" placeholder="节目单链接" v-model="quickEpgSource.url" />
              <div class="flex justify-end">
                <van-button size="small" type="primary" @click="pushQuickEpgSource">推送节目单</van-button>
              </div>
            </van-space>
          </template>
        </van-cell>
        <template #footer>
          <div class="flex justify-end">
            <van-button size="small" type="primary" @click="pushConfig">推送</van-button>
          </div>
        </template>
      </ConfigSection>

      <ConfigSection title="直播源节目单配置">
        <van-cell title="为每个直播源配置节目单">
          <template #label>
            <van-space class="w-full" direction="vertical" size="small">
              <van-list>
                <van-cell
                  v-for="(iptvSource, index) in config.iptvSourceList"
                  :key="index"
                  :title="iptvSource.name"
                >
                  <template #label>
                    <van-dropdown-menu>
                      <van-dropdown-item 
                        :model-value="getEpgSourceIndex(iptvSource)"
                        :options="epgSourceOptions"
                        @change="(val: number) => setIptvEpgSource(index, val)"
                      />
                    </van-dropdown-menu>
                  </template>
                </van-cell>
              </van-list>
            </van-space>
          </template>
        </van-cell>
        <template #footer>
          <div class="flex justify-end">
            <van-button size="small" type="primary" @click="pushConfig">推送</van-button>
          </div>
        </template>
      </ConfigSection>

      <ConfigSection title="缓存">
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
        <template #footer>
          <div class="flex justify-end">
            <van-button size="small" type="primary" @click="pushConfig">推送</van-button>
          </div>
        </template>
      </ConfigSection>
    </div>

    <van-popup v-model:show="showAddPopup" position="bottom" round style="height: 70%">
      <div class="popup-content">
        <h3 class="text-center mb-4">{{ editingIndex >= 0 ? '编辑节目单源' : '添加节目单源' }}</h3>
        <van-form>
          <van-field label="名称" placeholder="节目单名称" v-model="editingSource.name" required />
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
            placeholder="节目单地址"
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
          <van-field label="过期时间" placeholder="小时，可选，留空使用全局设置" type="number" v-model="editingSource.expireHours" />
        </van-form>
        <div class="flex gap-2 mt-4">
          <van-button block @click="cancelEdit">取消</van-button>
          <van-button block type="primary" @click="saveEpgSource">保存</van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useConfig } from '@/composables/useConfig'
import { postJson } from '@/utils/api'
import {
  showSuccessToast,
  showFailToast,
  showLoadingToast,
  closeToast,
  showConfirmDialog,
} from 'vant'
import ConfigSection from '@/components/ConfigSection.vue'
import type { EpgSource } from '@/types/config'
import dayjs from 'dayjs'

const { config, pushConfig } = useConfig()

const showAddPopup = ref(false)
const editingIndex = ref(-1)
const editingSource = ref<{
  name: string
  url: string
  isLocal: boolean
  expireHours: string
}>({
  name: '',
  url: '',
  isLocal: false,
  expireHours: '',
})

const quickEpgSource = ref({
  name: `添加于${dayjs().format('YYYY-MM-DD HH:mm:ss')}`,
  url: '',
})

const epgSourceOptions = computed(() => {
  const options = [{ text: '不关联', value: -1 }]
  config.value.epgSourceList.forEach((source, index) => {
    options.push({ text: source.name, value: index })
  })
  return options
})

function getEpgSourceIndex(iptvSource: { epgSource: EpgSource | null }): number {
  if (!iptvSource.epgSource) return -1
  return config.value.epgSourceList.findIndex(e => e.url === iptvSource.epgSource?.url)
}

function setIptvEpgSource(iptvIndex: number, epgIndex: number) {
  const iptvSource = config.value.iptvSourceList[iptvIndex]
  if (epgIndex >= 0 && config.value.epgSourceList[epgIndex]) {
    const epg = config.value.epgSourceList[epgIndex]
    iptvSource.epgSource = {
      name: epg.name,
      url: epg.url,
      isLocal: epg.isLocal,
      expireHours: epg.expireHours,
    }
  } else {
    iptvSource.epgSource = null
  }
  pushConfig()
}

function editEpgSource(index: number) {
  editingIndex.value = index
  const source = config.value.epgSourceList[index]
  editingSource.value = {
    name: source.name,
    url: source.url,
    isLocal: source.isLocal,
    expireHours: source.expireHours?.toString() || '',
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
    expireHours: '',
  }
}

async function deleteEpgSource(index: number) {
  try {
    await showConfirmDialog({
      title: '确认删除',
      message: `确定要删除节目单源 "${config.value.epgSourceList[index].name}" 吗？`,
    })
    config.value.epgSourceList.splice(index, 1)
    await pushConfig()
  } catch (e) {
    if (e !== 'cancel') {
      console.error(e)
    }
  }
}

async function saveEpgSource() {
  if (!editingSource.value.name) {
    showFailToast('请填写节目单名称')
    return
  }
  if (!editingSource.value.url) {
    showFailToast('请填写节目单地址')
    return
  }

  const newSource: EpgSource = {
    name: editingSource.value.name,
    url: editingSource.value.url,
    isLocal: editingSource.value.isLocal,
    expireHours: editingSource.value.expireHours ? parseInt(editingSource.value.expireHours) : null,
  }

  if (editingIndex.value >= 0) {
    config.value.epgSourceList[editingIndex.value] = newSource
  } else {
    config.value.epgSourceList.push(newSource)
  }

  await pushConfig()
  showAddPopup.value = false
  cancelEdit()
}

async function pushQuickEpgSource() {
  if (!quickEpgSource.value.name) {
    showFailToast('请填写节目单名称')
    return
  }
  if (!quickEpgSource.value.url) {
    showFailToast('请填写节目单链接')
    return
  }

  showLoadingToast({ message: '加载中...', forbidClick: true, duration: 0 })
  try {
    await postJson('/api/epg-source/push', {
      name: quickEpgSource.value.name,
      url: quickEpgSource.value.url,
    })
    showSuccessToast('推送节目单成功')
    quickEpgSource.value = {
      name: `添加于${dayjs().format('YYYY-MM-DD HH:mm:ss')}`,
      url: '',
    }
  } catch (e) {
    showFailToast('推送节目单失败')
    console.error(e)
  } finally {
    closeToast()
  }
}
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

.popup-content {
  padding: 16px;
}

.text-center {
  text-align: center;
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

.text-gray {
  color: #969799;
}

.text-12px {
  font-size: 12px;
}

.flex {
  display: flex;
}

.justify-end {
  justify-content: flex-end;
}

.w-full {
  width: 100%;
}
</style>
