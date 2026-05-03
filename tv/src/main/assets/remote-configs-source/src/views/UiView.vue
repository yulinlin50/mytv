<template>
  <div>
    <ConfigSection title="显示" show-push-button @push="pushConfig">
      <van-cell title="节目进度">
        <template #right-icon>
          <van-switch v-model="config.uiShowEpgProgrammeProgress" size="20px" />
        </template>
      </van-cell>
      <van-cell title="常驻节目进度">
        <template #right-icon>
          <van-switch v-model="config.uiShowEpgProgrammePermanentProgress" size="20px" />
        </template>
      </van-cell>
      <van-cell title="台标显示">
        <template #right-icon>
          <van-switch v-model="config.uiShowChannelLogo" size="20px" />
        </template>
      </van-cell>
      <van-cell title="频道预览">
        <template #right-icon>
          <van-switch v-model="config.uiShowChannelPreview" size="20px" />
        </template>
      </van-cell>
      <van-cell title="经典选台界面">
        <template #right-icon>
          <van-switch v-model="config.uiUseClassicPanelScreen" size="20px" />
        </template>
      </van-cell>
      <van-cell title="时间显示模式">
        <template #right-icon>
          <van-radio-group direction="horizontal" v-model="config.uiTimeShowMode">
            <van-radio name="HIDDEN">隐藏</van-radio>
            <van-radio name="ALWAYS">常显</van-radio>
            <van-radio name="EVERY_HOUR">整点</van-radio>
            <van-radio name="HALF_HOUR">半点</van-radio>
          </van-radio-group>
        </template>
      </van-cell>
    </ConfigSection>

    <ConfigSection title="缩放" show-push-button @push="pushConfig">
      <van-cell title="界面密度缩放">
        <template #label>
          <van-slider
            v-model="config.uiDensityScaleRatio"
            :min="0"
            :max="2"
            :step="0.1"
          />
        </template>
      </van-cell>
      <van-cell title="界面字体缩放">
        <template #label>
          <van-slider
            v-model="config.uiFontScaleRatio"
            :min="0.5"
            :max="2"
            :step="0.1"
          />
        </template>
      </van-cell>
    </ConfigSection>

    <ConfigSection title="交互" show-push-button @push="pushConfig">
      <van-cell title="焦点优化">
        <template #right-icon>
          <van-switch v-model="config.uiFocusOptimize" size="20px" />
        </template>
      </van-cell>
      <van-cell title="自动关闭延时">
        <template #right-icon>
          <van-stepper
            v-model="config.uiScreenAutoCloseDelay"
            min="1000"
            max="60000"
            step="1000"
          />
        </template>
        <template #label>
          <span class="text-gray text-12px">毫秒</span>
        </template>
      </van-cell>
    </ConfigSection>

    <ConfigSection title="性能" show-push-button @push="pushConfig">
      <van-cell title="低性能模式">
        <template #right-icon>
          <van-switch v-model="config.uiLowPerformanceMode" size="20px" />
        </template>
      </van-cell>
      <van-cell title="简化频道项">
        <template #right-icon>
          <van-switch v-model="config.uiSimplifyChannelItem" size="20px" />
        </template>
      </van-cell>
      <van-cell title="频道网格列数">
        <template #right-icon>
          <van-stepper v-model="config.uiChannelGridColumns" :min="3" :max="10" />
        </template>
      </van-cell>
      <van-cell title="EPG更新间隔">
        <template #right-icon>
          <van-stepper
            v-model="config.uiEpgUpdateIntervalMs"
            :min="5000"
            :max="300000"
            :step="5000"
          />
        </template>
        <template #label>
          <span class="text-gray text-12px">毫秒</span>
        </template>
      </van-cell>
    </ConfigSection>

    <ConfigSection title="主题">
      <van-cell title="主题选择" is-link @click="showThemePicker = true">
        <template #value>
          {{ config.themeAppCurrent?.name || '默认' }}
        </template>
      </van-cell>
      <van-popup v-model:show="showThemePicker" position="bottom" round>
        <van-picker
          :columns="themeColumns"
          @confirm="onThemeConfirm"
          @cancel="showThemePicker = false"
        />
      </van-popup>
      <van-cell title="纹理透明度">
        <template #label>
          <van-slider
            v-model="config.themeTextureAlpha"
            :min="0"
            :max="1"
            :step="0.1"
          />
        </template>
      </van-cell>
      <template #footer>
        <div class="flex justify-end">
          <van-button size="small" type="primary" @click="pushConfig">推送</van-button>
        </div>
      </template>
    </ConfigSection>

    <ConfigSection title="缓存" show-push-button @push="pushConfig">
      <van-cell title="节目单缓存启用">
        <template #right-icon>
          <van-switch v-model="config.epgCacheEnable" size="20px" />
        </template>
      </van-cell>
      <van-cell title="频道图标缓存启用">
        <template #right-icon>
          <van-switch v-model="config.channelLogoCacheEnable" size="20px" />
        </template>
      </van-cell>
    </ConfigSection>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useConfig } from '@/composables/useConfig'
import { getJson } from '@/utils/api'
import ConfigSection from '@/components/ConfigSection.vue'
import type { ThemeGroup, AppThemeDef } from '@/types/config'

const { config, pushConfig } = useConfig()

const showThemePicker = ref(false)
const themeList = ref<ThemeGroup[]>([])

const themeColumns = computed(() => {
  const groups: Record<string, { text: string; value: AppThemeDef }[]> = {}
  themeList.value.forEach((group) => {
    groups[group.name] = group.list.map((t) => ({ text: t.name, value: t }))
  })
  return [
    Object.keys(groups).map((g) => ({
      text: g,
      children: groups[g],
    })),
  ]
})

async function fetchThemes() {
  try {
    themeList.value = await getJson<ThemeGroup[]>('/api/themes')
  } catch (e) {
    console.error(e)
  }
}

function onThemeConfirm({ selectedOptions }: { selectedOptions: { value: AppThemeDef }[] }) {
  const theme = selectedOptions[1]?.value
  if (theme) {
    config.value.themeAppCurrent = theme
  }
  showThemePicker.value = false
}

onMounted(() => {
  fetchThemes()
})
</script>
