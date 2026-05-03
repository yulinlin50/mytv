<template>
  <div>
    <ConfigSection title="直播源">
      <van-cell title="自定义直播源">
        <template #label>
          <van-space class="w-full" direction="vertical" size="small">
            <span>支持m3u、txt格式</span>
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
      <van-cell title="混合模式">
        <template #right-icon>
          <van-radio-group direction="horizontal" v-model="config.iptvHybridMode">
            <van-radio name="DISABLE">禁用</van-radio>
            <van-radio name="IPTV_FIRST">直播源优先</van-radio>
            <van-radio name="HYBRID_FIRST">混合优先</van-radio>
          </van-radio-group>
        </template>
      </van-cell>
      <van-cell title="相似频道合并">
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
        <template #right-icon>
          <van-switch v-model="config.iptvChannelLogoOverride" size="20px" />
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
      <van-cell title="频道收藏启用">
        <template #right-icon>
          <van-switch v-model="config.iptvChannelFavoriteEnable" size="20px" />
        </template>
      </van-cell>
      <van-cell title="频道分组隐藏">
        <template #label>
          <van-space class="w-full" direction="vertical" size="small">
            <span>每行一个分组名称</span>
            <van-field
              class="!p-0"
              rows="3"
              type="textarea"
              v-model="hiddenGroupText"
            />
          </van-space>
        </template>
      </van-cell>
      <van-cell title="直播源缓存启用">
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
    </ConfigSection>

    <ConfigSection title="控制" show-push-button @push="pushConfig">
      <van-cell title="数字选台">
        <template #right-icon>
          <van-switch v-model="config.iptvChannelNoSelectEnable" size="20px" />
        </template>
      </van-cell>
      <van-cell title="换台反转">
        <template #right-icon>
          <van-switch v-model="config.iptvChannelChangeFlip" size="20px" />
        </template>
      </van-cell>
      <van-cell title="换台列表首尾循环">
        <template #right-icon>
          <van-switch v-model="config.iptvChannelChangeListLoop" size="20px" />
        </template>
      </van-cell>
    </ConfigSection>

    <ConfigSection title="节目单">
      <van-cell title="启用节目单">
        <template #right-icon>
          <van-switch v-model="config.epgEnable" size="20px" />
        </template>
      </van-cell>
      <van-cell title="跟随直播源">
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
      <van-cell title="节目单缓存时间">
        <template #right-icon>
          <van-stepper v-model="config.epgCacheTimeHours" min="1" max="168" />
        </template>
        <template #label>
          <span class="text-gray text-12px">小时</span>
        </template>
      </van-cell>
    </ConfigSection>

    <ConfigSection title="音轨" show-push-button @push="pushConfig">
      <van-cell title="音轨排序">
        <template #right-icon>
          <van-radio-group direction="horizontal" v-model="config.audioTrackSortMode">
            <van-radio name="LANGUAGE">按语言</van-radio>
            <van-radio name="CHANNELS">按声道</van-radio>
            <van-radio name="BITRATE">按比特率</van-radio>
          </van-radio-group>
        </template>
      </van-cell>
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

const epgSource = ref({
  name: `添加于${dayjs().format('YYYY-MM-DD HH:mm:ss')}`,
  url: '',
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

function uploadIptvSourceContent(file: { file: File }) {
  const reader = new FileReader()
  reader.onload = (e) => {
    iptvSource.value.content = e.target?.result as string
  }
  reader.readAsText(file.file)
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
  } catch (e) {
    showFailToast('推送节目单失败')
    console.error(e)
  } finally {
    closeToast()
  }
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
