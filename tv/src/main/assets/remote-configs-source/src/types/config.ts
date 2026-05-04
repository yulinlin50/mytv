export interface EpgSource {
  name: string
  url: string
  isLocal: boolean
  expireHours: number | null
}

export interface IptvSource {
  name: string
  url: string
  isLocal: boolean
  transformJs: string | null
  userAgent: string | null
  cacheTime: number | null
  epgSource: EpgSource | null
  enabled: boolean
  id: string
}

export interface AppThemeDef {
  name: string
  background: string
  texture?: string
  textureAlpha?: number
}

export interface LogHistoryItem {
  time: number
  tag: string
  level: string
  message: string
  cause?: string
}

export interface AppInfo {
  appTitle: string
  logHistory: LogHistoryItem[]
}

export interface Config {
  appBootLaunch: boolean
  appPipEnable: boolean
  appStartupScreen: 'Dashboard' | 'Live'
  debugDeveloperMode: boolean
  debugShowFps: boolean
  debugShowVideoPlayerMetadata: boolean
  debugShowLayoutGrids: boolean
  iptvSourceList: IptvSource[]
  epgSourceList: EpgSource[]
  iptvChannelGroupHiddenList: string[]
  iptvHybridMode: 'DISABLE' | 'IPTV_FIRST' | 'HYBRID_FIRST'
  iptvSimilarChannelMerge: boolean
  iptvChannelLogoProvider: string
  iptvChannelLogoOverride: boolean
  iptvChannelFavoriteEnable: boolean
  iptvChannelChangeFlip: boolean
  iptvChannelNoSelectEnable: boolean
  iptvChannelChangeListLoop: boolean
  iptvSourceCacheEnable: boolean
  iptvSourceCacheTimeHours: number
  epgEnable: boolean
  epgSourceFollowIptv: boolean
  epgCacheTimeHours: number
  epgCacheEnable: boolean
  audioTrackSortMode: 'LANGUAGE' | 'CHANNELS' | 'BITRATE'
  videoPlayerCore: 'MEDIA3' | 'IJK'
  videoPlayerRenderMode: 'SURFACE_VIEW' | 'TEXTURE_VIEW'
  videoPlayerUserAgent: string
  videoPlayerHeaders: string
  videoPlayerLoadTimeout: number
  videoPlayerDisplayMode:
    | 'ORIGINAL'
    | 'FILL'
    | 'CROP'
    | 'FOUR_THREE'
    | 'SIXTEEN_NINE'
    | 'TWO_THIRTY_FIVE_ONE'
  videoPlayerForceAudioSoftDecode: boolean
  videoPlayerStopPreviousMediaItem: boolean
  videoPlayerSkipMultipleFramesOnSameVSync: boolean
  uiShowEpgProgrammeProgress: boolean
  uiShowEpgProgrammePermanentProgress: boolean
  uiShowChannelLogo: boolean
  uiShowChannelPreview: boolean
  uiUseClassicPanelScreen: boolean
  uiDensityScaleRatio: number
  uiFontScaleRatio: number
  uiTimeShowMode: 'HIDDEN' | 'ALWAYS' | 'EVERY_HOUR' | 'HALF_HOUR'
  uiFocusOptimize: boolean
  uiScreenAutoCloseDelay: number
  uiLowPerformanceMode: boolean
  uiChannelGridColumns: number
  uiEpgUpdateIntervalMs: number
  uiSimplifyChannelItem: boolean
  channelLogoCacheEnable: boolean
  themeAppCurrent: AppThemeDef | null
  themeTextureAlpha: number
  cloudSyncAutoPull: boolean
  cloudSyncProvider: 'GITHUB_GIST' | 'GITEE_GIST' | 'NETWORK_URL' | 'LOCAL_FILE' | 'WEBDAV'
  cloudSyncGithubGistId: string
  cloudSyncGithubGistToken: string
  cloudSyncGiteeGistId: string
  cloudSyncGiteeGistToken: string
  cloudSyncNetworkUrl: string
  cloudSyncLocalFilePath: string
  cloudSyncWebDavUrl: string
  cloudSyncWebDavUsername: string
  cloudSyncWebDavPassword: string
  feiyangAllInOneFilePath: string
}

export interface ThemeGroup {
  name: string
  description?: string
  list: AppThemeDef[]
}
