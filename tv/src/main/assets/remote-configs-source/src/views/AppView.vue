<template>
  <div>
    <ConfigSection title="应用" show-push-button @push="pushConfig">
      <van-cell title="开机自启">
        <template #right-icon>
          <van-switch v-model="config.appBootLaunch" size="20px" />
        </template>
      </van-cell>
      <van-cell title="画中画">
        <template #right-icon>
          <van-switch v-model="config.appPipEnable" size="20px" />
        </template>
      </van-cell>
      <van-cell title="起始界面">
        <template #right-icon>
          <van-radio-group direction="horizontal" v-model="config.appStartupScreen">
            <van-radio name="Dashboard">仪表盘</van-radio>
            <van-radio name="Live">直播</van-radio>
          </van-radio-group>
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
