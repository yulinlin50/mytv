<template>
  <div>
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
      <van-cell title="恢复默认">
        <template #right-icon>
          <van-button size="small" @click="resetTheme">恢复</van-button>
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

function resetTheme() {
  config.value.themeAppCurrent = null
}

onMounted(() => {
  fetchThemes()
})
</script>
