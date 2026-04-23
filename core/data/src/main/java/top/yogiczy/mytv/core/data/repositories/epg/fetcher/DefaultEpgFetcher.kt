package top.yogiczy.mytv.core.data.repositories.epg.fetcher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import java.io.InputStream

/**
 * 缺省节目单数据获取
 */
class DefaultEpgFetcher : EpgFetcher {
    override fun isSupport(url: String): Boolean {
        return true
    }

    override suspend fun fetch(body: ResponseBody) = withContext(Dispatchers.IO) {
        body.byteStream()
    }
}