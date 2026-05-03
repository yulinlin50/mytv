<template>
  <van-cell-group :title="title" inset>
    <slot></slot>
    <van-cell v-if="showPushButton">
      <div class="flex justify-end">
        <van-button size="small" type="primary" @click="$emit('push')">
          推送
        </van-button>
      </div>
    </van-cell>
  </van-cell-group>
</template>

<script setup lang="ts">
defineProps<{
  title?: string
  showPushButton?: boolean
}>()

defineEmits<{
  push: []
}>()
</script>
