package top.yogiczy.mytv.core.data.entities.epg

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import top.yogiczy.mytv.core.data.entities.channel.Channel

class EpgListMatchTest {
    
    private lateinit var epgList: EpgList
    
    @BeforeEach
    fun setup() {
        epgList = EpgList(listOf(
            Epg(
                channelList = listOf("CCTV1", "cctv1"),
                sourceId = "source1",
                programmeList = EpgProgrammeList(listOf(
                    EpgProgramme(title = "新闻联播")
                ))
            ),
            Epg(
                channelList = listOf("CCTV2", "cctv2"),
                sourceId = "source1",
                programmeList = EpgProgrammeList(listOf(
                    EpgProgramme(title = "经济半小时")
                ))
            ),
            Epg(
                channelList = listOf("湖南卫视", "hunantv"),
                sourceId = "source2",
                programmeList = EpgProgrammeList(listOf(
                    EpgProgramme(title = "快乐大本营")
                ))
            ),
            Epg(
                channelList = listOf("浙江卫视", "zjtv"),
                sourceId = null,
                programmeList = EpgProgrammeList(listOf(
                    EpgProgramme(title = "奔跑吧")
                ))
            )
        ))
    }
    
    @Nested
    @DisplayName("同源匹配测试")
    inner class SourceMatchTest {
        
        @Test
        @DisplayName("精确匹配频道名称")
        fun testExactMatchInSource() {
            val channel = Channel(
                name = "CCTV1",
                iptvSourceId = "source1"
            )
            
            val result = epgList.match(channel)
            assertNotNull(result)
            assertEquals("CCTV1", result?.channelList?.first())
        }
        
        @Test
        @DisplayName("源不存在时返回 null")
        fun testSourceNotFound() {
            val channel = Channel(
                name = "CCTV1",
                iptvSourceId = "unknown_source"
            )
            
            val result = epgList.match(channel)
            assertNull(result)
        }
        
        @Test
        @DisplayName("源中无匹配时不回退到全局匹配")
        fun testNoFallbackToGlobal() {
            val channel = Channel(
                name = "浙江卫视",
                iptvSourceId = "source1"
            )
            
            val result = epgList.match(channel)
            assertNull(result)
        }
    }
    
    @Nested
    @DisplayName("全局匹配测试")
    inner class GlobalMatchTest {
        
        @Test
        @DisplayName("无源 ID 时使用全局匹配")
        fun testGlobalMatch() {
            val channel = Channel(
                name = "浙江卫视",
                iptvSourceId = null
            )
            
            val result = epgList.match(channel)
            assertNotNull(result)
            assertEquals("浙江卫视", result?.channelList?.first())
        }
        
        @Test
        @DisplayName("EPG ID 匹配")
        fun testEpgIdMatch() {
            val channel = Channel(
                name = "其他名称",
                epgId = "CCTV1",
                iptvSourceId = null
            )
            
            val result = epgList.match(channel)
            assertNotNull(result)
        }
    }
    
    @Nested
    @DisplayName("空列表测试")
    inner class EmptyListTest {
        
        @Test
        @DisplayName("空 EPG 列表返回 null")
        fun testEmptyEpgList() {
            val emptyList = EpgList()
            val channel = Channel(name = "CCTV1")
            
            val result = emptyList.match(channel)
            assertNull(result)
        }
    }
}
