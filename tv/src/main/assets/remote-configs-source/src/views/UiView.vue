<template>
  <div class="page-container">
    <div class="page-header">
      <van-icon name="arrow-left" class="back-icon" @click="$router.push('/')" />
      <span class="page-title">界面</span>
      <span class="placeholder"></span>
    </div>
    
    <div class="page-content">
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

      <ConfigSection title="缩放">
        <van-cell title="界面密度缩放">
          <template #label>
            <van-slider v-model="config.uiDensityScaleRatio" :min="0" :max="2" :step="0.1" />
          </template>
        </van-cell>
        <van-cell title="界面字体缩放">
          <template #label>
            <van-slider v-model="config.uiFontScaleRatio" :min="0.5" :max="2" :step="0.1" />
          </template>
        </van-cell>
        <template #footer>
          <div class="flex justify-end">
            <van-button size="small" type="primary" @click="pushConfig">推送</van-button>
          </div>
        </template>
      </ConfigSection>

      <ConfigSection title="交互">
        <van-cell title="焦点优化">
          <template #right-icon>
            <van-switch v-model="config.uiFocusOptimize" size="20px" />
          </template>
        </van-cell>
        <van-cell title="自动关闭延时">
          <template #right-icon>
            <van-stepper v-model="config.uiScreenAutoCloseDelay" min="1000" max="60000" step="1000" />
          </template>
          <template #label>
            <span class="text-gray text-12px">毫秒</span>
          </template>
        </van-cell>
        <template #footer>
          <div class="flex justify-end">
            <van-button size="small" type="primary" @click="pushConfig">推送</van-button>
          </div>
        </template>
      </ConfigSection>

      <ConfigSection title="性能">
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
            <van-stepper v-model="config.uiEpgUpdateIntervalMs" :min="5000" :max="300000" :step="5000" />
          </template>
          <template #label>
            <span class="text-gray text-12px">毫秒</span>
          </template>
        </van-cell>
        <template #footer>
          <div class="flex justify-end">
            <van-button size="small" type="primary" @click="pushConfig">推送</van-button>
          </div>
        </template>
      </ConfigSection>

      <ConfigSection title="主题">
        <van-cell title="主题选择" is-link @click="showThemePicker = true">
          <template #value>{{ config.themeAppCurrent?.name || '默认' }}</template>
        </van-cell>
        <van-popup v-model:show="showThemePicker" position="bottom" round class="theme-picker-popup">
          <div class="theme-picker-header">
            <span class="theme-picker-cancel" @click="showThemePicker = false">取消</span>
            <span class="theme-picker-title">选择主题</span>
            <span class="theme-picker-confirm" @click="onThemeConfirm">确定</span>
          </div>
          <div class="theme-picker-content">
            <div 
              v-for="(group, groupIndex) in themeList" 
              :key="group.name"
              class="theme-group"
            >
              <div class="theme-group-title">{{ group.name }}</div>
              <div class="theme-list">
                <div 
                  v-for="(theme, themeIndex) in group.list" 
                  :key="theme.name"
                  class="theme-item"
                  :class="{ active: selectedGroupIndex === groupIndex && selectedThemeIndex === themeIndex }"
                  @click="selectedGroupIndex = groupIndex; selectedThemeIndex = themeIndex"
                >
                  {{ theme.name }}
                </div>
              </div>
            </div>
          </div>
        </van-popup>
        <van-cell title="纹理透明度">
          <template #label>
            <van-slider v-model="config.themeTextureAlpha" :min="0" :max="1" :step="0.1" />
          </template>
        </van-cell>
        <template #footer>
          <div class="flex justify-end">
            <van-button size="small" type="primary" @click="pushConfig">推送</van-button>
          </div>
        </template>
      </ConfigSection>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useConfig } from '@/composables/useConfig'
import { getJson } from '@/utils/api'
import ConfigSection from '@/components/ConfigSection.vue'

import type { ThemeGroup } from '@/types/config'

const { config, pushConfig, fetchConfig } = useConfig()

const showThemePicker = ref(false)
const themeList = ref<ThemeGroup[]>([])
const selectedGroupIndex = ref(0)
const selectedThemeIndex = ref(0)

async function fetchThemes() {
  try {
    themeList.value = await getJson('/api/themes')
    const currentTheme = config.value.themeAppCurrent
    if (currentTheme) {
      for (let i = 0; i < themeList.value.length; i++) {
        const themeIndex = themeList.value[i].list.findIndex(t => t.name === currentTheme.name)
        if (themeIndex !== -1) {
          selectedGroupIndex.value = i
          selectedThemeIndex.value = themeIndex
          break
        }
      }
    }
  } catch (e) {
    console.error(e)
  }
}

function onThemeConfirm() {
  const selectedTheme = themeList.value[selectedGroupIndex.value]?.list[selectedThemeIndex.value]
  if (selectedTheme) {
    config.value.themeAppCurrent = selectedTheme
    if (selectedTheme.textureAlpha !== undefined) {
      config.value.themeTextureAlpha = selectedTheme.textureAlpha
    }
  }
  showThemePicker.value = false
}

onMounted(() => {
  fetchConfig()
  fetchThemes()
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

.theme-picker-popup {
  max-height: 70vh;
}

.theme-picker-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-bottom: 1px solid #ebedf0;
}

.theme-picker-cancel {
  color: #646566;
  font-size: 16px;
}

.theme-picker-title {
  font-size: 16px;
  font-weight: 600;
}

.theme-picker-confirm {
  color: #1989fa;
  font-size: 16px;
}

.theme-picker-content {
  max-height: 50vh;
  overflow-y: auto;
  padding: 12px;
}

.theme-group {
  margin-bottom: 16px;
}

.theme-group-title {
  font-size: 14px;
  color: #969799;
  margin-bottom: 12px;
}

.theme-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 8px;
}

.theme-item {
  padding: 12px 16px;
  background: #f7f8fa;
  border-radius: 8px;
  text-align: center;
  cursor: pointer;
  font-size: 14px;
  color: #323233;
  transition: all 0.2s;
}

.theme-item:hover {
  background: #ebedf0;
}

.theme-item.active {
  background: #e8f3ff;
  color: #1989fa;
  border: 1px solid #1989fa;
}
</style>
