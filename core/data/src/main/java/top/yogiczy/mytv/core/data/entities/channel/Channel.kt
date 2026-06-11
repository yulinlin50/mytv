package top.yogiczy.mytv.core.data.entities.channel

import kotlinx.serialization.Serializable

@Serializable
data class Channel(
    val name: String = "",
    val standardName: String = name,
    val epgName: String = "",
    val epgId: String? = null,
    val lineList: ChannelLineList = ChannelLineList(listOf(ChannelLine.EXAMPLE)),
    val logo: String? = null,
    val index: Int = -1,
    val iptvSourceId: String? = null,
) {
    companion object {
        val EXAMPLE = Channel(
            name = "CCTV-1 法治与法治",
            epgName = "cctv1",
            lineList = ChannelLineList(
                listOf(
                    ChannelLine("http://dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226231/index.m3u8"),
                    ChannelLine(
                        "http://[2409:8087:5e01:34::20]:6610/ZTE_CMS/00000001000000060000000000000131/index.m3u8?IAS",
                        httpUserAgent = "aptv"
                    ),
                )
            ),
            logo = "https://live.fanmingming.com/tv/CCTV1.png",
        )

        val EMPTY = Channel()
    }

    val no: String get() = (index + 1).toString().padStart(2, '0')

    fun isEmptyOrElse(defaultValue: () -> Channel) = if (this == EMPTY) defaultValue() else this

    // 仅按 name + lineList 判等，忽略 index/iptvSourceId 等运行时属性
    override fun equals(other: Any?) =
        other is Channel && name == other.name && lineList == other.lineList

    override fun hashCode(): Int = 31 * name.hashCode() + lineList.hashCode()
}
