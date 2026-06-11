package top.yogiczy.mytv.core.data.repositories.iptv.parser

import kotlinx.serialization.Serializable
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroup
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.core.data.entities.channel.ChannelLine
import top.yogiczy.mytv.core.data.entities.channel.ChannelLineList
import top.yogiczy.mytv.core.data.entities.channel.ChannelList
import top.yogiczy.mytv.core.data.utils.ChannelAlias

interface IptvParser {
    fun isSupport(url: String, data: String): Boolean
    suspend fun parse(data: String): List<ChannelItem>
    suspend fun getEpgUrl(data: String): String? = null

    companion object {
        val instances = listOf(
            M3uIptvParser(),
            TxtIptvParser(),
            DefaultIptvParser(),
        )
    }

    @Serializable
    data class ChannelItem(
        val groupName: String,
        val name: String,
        val epgName: String = name,
        val epgId: String? = null,
        val url: String,
        val logo: String? = null,
        val httpUserAgent: String? = null,
        val manifestType: String? = null,
        val licenseType: String? = null,
        val licenseKey: String? = null,
        val catchup: String? = null,
        val catchupSource: String? = null,
        val catchupDays: Int? = null,
        val timeshift: Int? = null,
        val tvgShift: Double? = null,
    ) {
        /** 直接转换为 ChannelLine，避免逐字段映射 */
        fun toChannelLine() = ChannelLine(
            url = url,
            httpUserAgent = httpUserAgent,
            manifestType = manifestType,
            licenseType = licenseType,
            licenseKey = licenseKey,
            catchup = catchup,
            catchupSource = catchupSource,
            catchupDays = catchupDays,
            timeshift = timeshift,
            tvgShift = tvgShift,
        )

        companion object {
            private fun List<ChannelItem>.toChannelList(): ChannelList =
                ChannelList(groupBy { it.name }.map { (channelName, items) ->
                    val first = items.first()
                    Channel(
                        name = channelName,
                        standardName = ChannelAlias.standardChannelName(channelName),
                        epgName = ChannelAlias.standardChannelName(first.epgName),
                        epgId = first.epgId,
                        lineList = ChannelLineList(items.distinctBy { it.url }.map { it.toChannelLine() }),
                        logo = first.logo,
                    )
                })

            fun List<ChannelItem>.toChannelGroupList(): ChannelGroupList =
                ChannelGroupList(groupBy { it.groupName }.map { (groupName, items) ->
                    ChannelGroup(name = groupName, channelList = items.toChannelList())
                })
        }
    }
}
