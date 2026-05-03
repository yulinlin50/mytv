<template>
  <div>
    <ConfigSection title="界面" show-push-button @push="pushConfig">
      <van-cell title="节目进度">
        <template #label>
          <span class="text-gray text-12px">在频道底部显示当前节目进度条</span>
        </template>
        <template #right-icon>
          <van-switch v-model="config.uiShowEpgProgrammeProgress" size="20px" />
        </template>
      </van-cell>
      <van-cell title="常驻节目进度">
        <template #label>
          <span class="text-gray text-12px">在播放器底部显示当前节目进度条</span>
        </template>
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
        <template #label>
          <span class="text-gray text-12px">将选台界面替换为经典三段式结构</span>
        </template>
        <template #right-icon>
          <van-switch v-model="config.uiUseClassicPanelScreen" size="20px" />
        </template>
      </van-cell>
      <van-cell title="时间显示">
        <template #right-icon>
          <van-radio-group direction="horizontal" v-model="config.uiTimeShowMode">
            <van-radio name="HIDDEN">隐藏</van-radio>
            <van-radio name="ALWAYS">常显</van-radio>
            <van-radio name="EVERY_HOUR">整点</van-radio>
            <van-radio name="HALF_HOUR">半点</van-radio>
          </van-radio-group>
        </template>
      </van-cell>
      <van-cell title="超时自动关闭界面">
        <template #right-icon>
          <van-stepper
            v-model="config.uiScreenAutoCloseDelay"
            min="1000"
            max="60000"
            step="1000"
          />
        </template>
        <template #label>
          <span class="text-gray text-12px">毫秒（设为最大值表示不关闭）</span>
        </template>
      </van-cell>
      <van-cell title="界面整体缩放比例">
        <template #label>
          <van-slider
            v-model="config.uiDensityScaleRatio"
            :min="0"
            :max="2"
            :step="0.1"
          />
        </template>
        <template #value>
          <span>{{ config.uiDensityScaleRatio === 0 ? '自适应' : `×${config.uiDensityScaleRatio.toFixed(1)}` }}</span>
        </template>
      </van-cell>
      <van-cell title="界面字体缩放比例">
        <template #label>
          <van-slider
            v-model="config.uiFontScaleRatio"
            :min="0.5"
            :max="2"
            :step="0.1"
          />
        </template>
        <template #value>
          <span>×{{ config.uiFontScaleRatio.toFixed(1) }}</span>
        </template>
      </van-cell>
      <van-cell title="焦点优化">
        <template #label>
          <span class="text-gray text-12px">关闭后可解决触摸设备在部分场景下闪退</span>
        </template>
        <template #right-icon>
          <van-switch v-model="config.uiFocusOptimize" size="20px" />
        </template>
      </van-cell>
      <van-cell title="低性能模式">
        <template #label>
          <span class="text-gray text-12px">为低配置设备优化界面性能，减少动画和渲染开销</span>
        </template>
        <template #right-icon>
          <van-switch v-model="config.uiLowPerformanceMode" size="20px" />
        </template>
      </van-cell>
      <van-cell title="简化频道项">
        <template #label>
          <span class="text-gray text-12px">减少频道项的视觉元素，提升滚动性能</span>
        </template>
        <template #right-icon>
          <van-switch v-model="config.uiSimplifyChannelItem" size="20px" />
        </template>
      </van-cell>
      <van-cell title="频道网格列数">
        <template #label>
          <span class="text-gray text-12px">调整频道网格的列数，减少列数可提升性能</span>
        </template>
        <template #right-icon>
          <van-stepper v-model="config.uiChannelGridColumns" :min="3" :max="10" />
        </template>
      </van-cell>
      <van-cell title="EPG更新间隔">
        <template #label>
          <span class="text-gray text-12px">调整节目信息更新频率，更长的间隔可减少资源消耗</span>
        </template>
        <template #right-icon>
          <van-stepper
            v-model="config.uiEpgUpdateIntervalMs"
            :min="10000"
            :max="300000"
            :step="10000"
          />
        </template>
        <template #value>
          <span>{{ formatEpgInterval(config.uiEpgUpdateIntervalMs) }}</span>
        </template>
      </van-cell>
    </ConfigSection>
  </div>
</template>

<script setup lang="ts">
import { useConfig } from '@/composables/useConfig'
import ConfigSection from '@/components/ConfigSection.vue'

const { config, pushConfig } = useConfig()

function formatEpgInterval(ms: number): string {
  const seconds = ms / 1000
  if (seconds >= 60) {
    return `${seconds / 60}分钟`
  }
  return `${seconds}秒`
}
</script>
