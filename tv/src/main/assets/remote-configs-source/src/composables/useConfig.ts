import { ref, computed } from 'vue'
import { getJson, postJson } from '@/utils/api'
import type { Config } from '@/types/config'
import { useApi } from './useApi'

function getListValue<T>(list: T[] | { value: T[] } | undefined): T[] {
  if (!list) return []
  if (Array.isArray(list)) return list
  if (typeof list === 'object' && 'value' in list) return list.value
  return []
}

const defaultConfig: Config = {
  appBootLaunch: false,
  appPipEnable: false,
  appStartupScreen: 'Dashboard',
  debugDeveloperMode: false,
  debugShowFps: false,
  debugShowVideoPlayerMetadata: false,
  debugShowLayoutGrids: false,
  iptvSourceList: [],
  epgSourceList: [],
  iptvChannelGroupHiddenList: [],
  iptvHybridMode: 'DISABLE',
  iptvSimilarChannelMerge: false,
  iptvChannelLogoProvider: '',
  iptvChannelLogoOverride: false,
  iptvChannelFavoriteEnable: true,
  iptvChannelChangeFlip: false,
  iptvChannelNoSelectEnable: true,
  iptvChannelChangeListLoop: false,
  iptvSourceCacheEnable: true,
  iptvSourceCacheTimeHours: 24,
  epgEnable: true,
  epgSourceFollowIptv: false,
  epgCacheTimeHours: 24,
  epgCacheEnable: true,
  audioTrackSortMode: 'LANGUAGE',
  videoPlayerCore: 'MEDIA3',
  videoPlayerRenderMode: 'SURFACE_VIEW',
  videoPlayerUserAgent: '',
  videoPlayerHeaders: '',
  videoPlayerLoadTimeout: 10000,
  videoPlayerDisplayMode: 'ORIGINAL',
  videoPlayerForceAudioSoftDecode: false,
  videoPlayerStopPreviousMediaItem: true,
  videoPlayerSkipMultipleFramesOnSameVSync: true,
  uiShowEpgProgrammeProgress: true,
  uiShowEpgProgrammePermanentProgress: false,
  uiShowChannelLogo: true,
  uiShowChannelPreview: false,
  uiUseClassicPanelScreen: false,
  uiDensityScaleRatio: 0,
  uiFontScaleRatio: 1,
  uiTimeShowMode: 'HIDDEN',
  uiFocusOptimize: true,
  uiScreenAutoCloseDelay: 5000,
  uiLowPerformanceMode: false,
  uiChannelGridColumns: 5,
  uiEpgUpdateIntervalMs: 30000,
  uiSimplifyChannelItem: false,
  channelLogoCacheEnable: true,
  themeAppCurrent: null,
  themeTextureAlpha: 0.8,
  cloudSyncAutoPull: false,
  cloudSyncProvider: 'GITHUB_GIST',
  cloudSyncGithubGistId: '',
  cloudSyncGithubGistToken: '',
  cloudSyncGiteeGistId: '',
  cloudSyncGiteeGistToken: '',
  cloudSyncNetworkUrl: '',
  cloudSyncLocalFilePath: '',
  cloudSyncWebDavUrl: '',
  cloudSyncWebDavUsername: '',
  cloudSyncWebDavPassword: '',
  feiyangAllInOneFilePath: '',
}

const config = ref<Config>({ ...defaultConfig })
const hiddenGroupText = ref('')
let configLoaded = false
let lastFetchTime = 0
const CACHE_DURATION = 5000

export function useConfig() {
  const { withLoading, withToast } = useApi()

  const iptvChannelGroupHiddenListArray = computed({
    get: () => hiddenGroupText.value.split('\n').filter((s) => s.trim()),
    set: (val: string[]) => {
      hiddenGroupText.value = val.join('\n')
    },
  })

  async function fetchConfig(force = false): Promise<void> {
    const now = Date.now()
    if (!force && configLoaded && (now - lastFetchTime) < CACHE_DURATION) {
      return
    }

    await withLoading(async () => {
      const data = await getJson<Config>('/api/configs')
      config.value = { ...defaultConfig, ...data }
      if (config.value.iptvChannelGroupHiddenList) {
        hiddenGroupText.value = Array.from(
          config.value.iptvChannelGroupHiddenList
        ).join('\n')
      }
      configLoaded = true
      lastFetchTime = now
    })
  }

  async function pushConfig(): Promise<boolean> {
    const result = await withToast(
      async () => {
        const payload = {
          ...config.value,
          iptvChannelGroupHiddenList: iptvChannelGroupHiddenListArray.value,
        }
        await postJson('/api/configs', payload)
        await fetchConfig(true)
        return true
      },
      '推送配置成功',
      '推送配置失败'
    )
    return result !== null
  }

  return {
    config,
    hiddenGroupText,
    iptvChannelGroupHiddenListArray,
    fetchConfig,
    pushConfig,
    getListValue,
  }
}
