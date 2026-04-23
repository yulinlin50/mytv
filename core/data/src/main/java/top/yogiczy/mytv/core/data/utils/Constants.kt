package top.yogiczy.mytv.core.data.utils

import top.yogiczy.mytv.core.data.entities.epgsource.EpgSourceList
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSourceList

object Constants {
    const val APP_TITLE = "天光云影"

    val IPTV_SOURCE_LIST = IptvSourceList(emptyList())

    const val IPTV_SOURCE_CACHE_TIME = 1000 * 60 * 60 * 24L

    const val IPTV_SOURCE_CACHE_TIME_DEFAULT_HOURS = 24

    const val EPG_CACHE_TIME_DEFAULT_HOURS = 24

    val EPG_SOURCE_LIST = EpgSourceList(emptyList())

    const val EPG_REFRESH_TIME_THRESHOLD = 2

    const val CHANNEL_LOGO_PROVIDER = "https://live.fanmingming.com/tv/{name|uppercase}.png"

    const val NETWORK_RETRY_COUNT = 10L

    const val NETWORK_RETRY_INTERVAL = 3000L

    const val VIDEO_PLAYER_USER_AGENT = "okhttp"

    const val VIDEO_PLAYER_LOAD_TIMEOUT = 1000L * 15

    const val LOG_HISTORY_MAX_SIZE = 100

    const val UI_TEMP_CHANNEL_SCREEN_SHOW_DURATION = 2000L

    const val UI_SCREEN_AUTO_CLOSE_DELAY = 1000L * 15

    const val UI_TIME_SCREEN_SHOW_DURATION = 1000L * 30
}