import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/HomeView.vue'),
  },
  {
    path: '/app',
    name: 'App',
    component: () => import('@/views/AppView.vue'),
  },
  {
    path: '/iptv',
    name: 'Iptv',
    component: () => import('@/views/IptvView.vue'),
  },
  {
    path: '/epg',
    name: 'Epg',
    component: () => import('@/views/EpgView.vue'),
  },
  {
    path: '/player',
    name: 'Player',
    component: () => import('@/views/PlayerView.vue'),
  },
  {
    path: '/ui',
    name: 'Ui',
    component: () => import('@/views/UiView.vue'),
  },
  {
    path: '/sync',
    name: 'Sync',
    component: () => import('@/views/CloudSyncView.vue'),
  },
  {
    path: '/debug',
    name: 'Debug',
    component: () => import('@/views/DebugView.vue'),
  },
  {
    path: '/logs',
    name: 'Logs',
    component: () => import('@/views/LogsView.vue'),
  },
]

const router = createRouter({
  history: createWebHistory('/'),
  routes,
})

export default router
