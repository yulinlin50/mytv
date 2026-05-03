<template>
  <div>
    <ConfigSection title="直播源">
      <van-cell title="自定义直播源">
        <template #label>
          <van-space class="w-full" direction="vertical" size="small">
            <span>支持多订阅源同时使用、本地文件、自定义UA</span>
            <van-field class="!pl-0" input-align="right" label="类型">
              <template #input>
                <van-radio-group direction="horizontal" v-model="iptvSource.type">
                  <van-radio name="url">远程</van-radio>
                  <van-radio name="file">文件</van-radio>
                  <van-radio name="content">静态</van-radio>
                </van-radio-group>
              </template>
            </van-field>
            <van-field
              class="!pl-0"
              input-align="right"
              label="名称"
              placeholder="直播源名称"
              v-model="iptvSource.name"
            />
            <van-field
              v-if="iptvSource.type === 'url'"
              class="!pl-0"
              input-align="right"
              label="链接"
              placeholder="直播源链接"
              v-model="iptvSource.url"
            />
            <van-field
              v-else-if="iptvSource.type === 'file'"
              class="!pl-0"
              input-align="right"
              label="文件路径"
              placeholder="直播源文件路径"
              v-model="iptvSource.filePath"
            />
            <template v-else-if="iptvSource.type === 'content'">
              <van-field
                class="!pl-0"
                :input-align="iptvSource.content ? 'left' : 'right'"
                label="内容"
                placeholder="直播源内容"
                rows="5"
                type="textarea"
                v-model="iptvSource.content"
              />
              <van-field class="!pl-0" input-align="right" label="上传">
                <template #input>
                  <van-uploader
                    :after-read="uploadIptvSourceContent"
                    accept=".txt,.m3u"
                  />
                </template>
              </van-field>
            </template>
            <div class="flex justify-end">
              <van-button size="small" type="primary" @click="pushIptvSource">
                推送直播源
              </van-button>
            </div>
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
              <van-button size="small" type="primary" @click="updateChannelAlias">
                推送
              </van-button>
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
import { postJson, getText, postText } from '@/utils/api'
import {
  showSuccessToast,
  showFailToast,
  showLoadingToast,
  closeToast,
  type UploaderFileListItem,
} from 'vant'
import ConfigSection from '@/components/ConfigSection.vue'
import dayjs from 'dayjs'

const { config, hiddenGroupText, pushConfig } = useConfig()

const iptvSource = ref({
  name: `添加于${dayjs().format('YYYY-MM-DD HH:mm:ss')}`,
  type: 'url' as 'url' | 'file' | 'content',
  url: '',
  filePath: '',
  content: '',
})

const channelAlias = ref('')
const channelAliasExample = `{\n    "__suffix": ["高码", "HD"],\n    "频道1": ["别名1", "别名2"]\n}`

async function pushIptvSource() {
  if (!iptvSource.value.name) {
    showFailToast('请填写直播源名称')
    return
  }
  if (iptvSource.value.type === 'url' && !iptvSource.value.url) {
    showFailToast('请填写直播源链接')
    return
  }
  if (iptvSource.value.type === 'file' && !iptvSource.value.filePath) {
    showFailToast('请填写直播源文件路径')
    return
  }
  if (iptvSource.value.type === 'content' && !iptvSource.value.content) {
    showFailToast('请填写直播源内容')
    return
  }

  showLoadingToast({ message: '加载中...', forbidClick: true, duration: 0 })
  try {
    await postJson('/api/iptv-source/push', iptvSource.value)
    showSuccessToast('推送直播源成功')
  } catch (e) {
    showFailToast('推送直播源失败')
    console.error(e)
  } finally {
    closeToast()
  }
}

function uploadIptvSourceContent(items: UploaderFileListItem | UploaderFileListItem[]) {
  const item = Array.isArray(items) ? items[0] : items
  if (!item?.file) return
  const reader = new FileReader()
  reader.onload = (e) => {
    iptvSource.value.content = e.target?.result as string
  }
  reader.readAsText(item.file)
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

onMounted(() => {
  fetchChannelAlias()
})
</script>
