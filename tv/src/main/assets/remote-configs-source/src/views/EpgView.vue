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
      <van-cell title="直播源节目单配置">
        <template #label>
          <van-space class="w-full" direction="vertical" size="small">
            <span>为每个直播源配置对应的节目单源</span>
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

    <van-popup v-model:show="showAddPopup" position="bottom" round style="height: 70%">
      <div class="p-4">
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
import {
  showFailToast,
  showConfirmDialog,
} from 'vant'
import ConfigSection from '@/components/ConfigSection.vue'
import type { EpgSource } from '@/types/config'

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
