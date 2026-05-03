<template>
  <div>
    <ConfigSection title="控制" show-push-button @push="pushConfig">
      <van-cell title="数字选台">
        <template #label>
          <span class="text-gray text-12px">通过数字键选择频道</span>
        </template>
        <template #right-icon>
          <van-switch v-model="config.iptvChannelNoSelectEnable" size="20px" />
        </template>
      </van-cell>
      <van-cell title="换台反转">
        <template #label>
          <span class="text-gray text-12px">{{ config.iptvChannelChangeFlip ? '方向键上：下一个频道；方向键下：上一个频道' : '方向键上：上一个频道；方向键下：下一个频道' }}</span>
        </template>
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
  </div>
</template>

<script setup lang="ts">
import { useConfig } from '@/composables/useConfig'
import ConfigSection from '@/components/ConfigSection.vue'

const { config, pushConfig } = useConfig()
</script>
