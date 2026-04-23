package top.yogiczy.mytv.core.data.repositories.epg

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yogiczy.mytv.core.data.entities.epg.Epg
import top.yogiczy.mytv.core.data.entities.epg.EpgList
import top.yogiczy.mytv.core.data.entities.epgsource.EpgSource

object EpgRepository {
    suspend fun clearAllCache() = withContext(Dispatchers.IO) {
        EpgSource.cacheDir.deleteRecursively()
        EpgList.clearCache()
        Epg.clearRecentProgrammeCache()
    }
}
