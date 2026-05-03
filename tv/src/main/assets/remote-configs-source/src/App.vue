<template>
  <van-config-provider :theme="isDark ? 'dark' : undefined">
    <router-view />
  </van-config-provider>
</template>

<script setup lang="ts">
const isDark =
  window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches
</script>

<style>
body {
  margin: 0;
  padding: 0;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}
</style>
