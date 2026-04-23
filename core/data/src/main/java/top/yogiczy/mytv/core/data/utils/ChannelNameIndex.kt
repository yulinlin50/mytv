package top.yogiczy.mytv.core.data.utils

import top.yogiczy.mytv.core.data.entities.epg.Epg
import java.util.concurrent.ConcurrentHashMap

class ChannelNameIndex(epgs: List<Epg> = emptyList()) {
    
    private val invertedIndex: Map<String, List<Epg>> = buildInvertedIndex(epgs)
    private val idIndex: Map<String, Epg> = buildIdIndex(epgs)
    private val allEpgs: List<Epg> = epgs
    
    private fun buildInvertedIndex(epgs: List<Epg>): Map<String, List<Epg>> {
        val index = mutableMapOf<String, MutableList<Epg>>()
        epgs.forEach { epg ->
            epg.channelList.forEach { name ->
                val normalized = normalizeForIndex(name)
                index.getOrPut(normalized) { mutableListOf() }.add(epg)
            }
        }
        return index
    }
    
    private fun buildIdIndex(epgs: List<Epg>): Map<String, Epg> {
        val index = mutableMapOf<String, Epg>()
        epgs.forEach { epg ->
            epg.channelList.forEach { name ->
                index[name] = epg
            }
        }
        return index
    }
    
    fun exactMatch(name: String): Epg? {
        return idIndex[name]
    }
    
    fun normalizedMatch(name: String): List<Epg>? {
        val normalized = normalizeForIndex(name)
        return invertedIndex[normalized]
    }
    
    fun substringMatch(name: String): List<Epg> {
        val normalized = normalizeForIndex(name)
        if (normalized.isBlank()) return emptyList()
        
        substringMatchCache[normalized]?.let { return it }
        
        val candidates = mutableListOf<Pair<Epg, Int>>()
        
        allEpgs.forEach { epg ->
            epg.channelList.forEach { channelName ->
                val normalizedChannel = normalizeForIndex(channelName)
                if (normalizedChannel.isBlank()) return@forEach
                
                val queryDigits = normalized.filter { it.isDigit() }
                val channelDigits = normalizedChannel.filter { it.isDigit() }
                if (queryDigits.isNotEmpty() && channelDigits.isNotEmpty() && queryDigits != channelDigits) {
                    return@forEach
                }
                
                var score = 0
                
                if (normalizedChannel.contains(normalized)) {
                    val lengthDiff = normalizedChannel.length - normalized.length
                    if (lengthDiff <= 3) {
                        score += 100 - lengthDiff * 10
                    }
                }
                else if (normalized.contains(normalizedChannel)) {
                    val lengthDiff = normalized.length - normalizedChannel.length
                    if (lengthDiff <= 3) {
                        score += 80 - lengthDiff * 10
                    }
                }
                else {
                    val commonPrefix = normalized.commonPrefixWith(normalizedChannel)
                    if (commonPrefix.length >= 2) {
                        val queryRemainder = normalized.substring(commonPrefix.length)
                        val channelRemainder = normalizedChannel.substring(commonPrefix.length)
                        
                        if (queryRemainder.length <= 2 && channelRemainder.length <= 2) {
                            score += commonPrefix.length * 5
                        }
                    }
                }
                
                if (score > 0) {
                    val lengthDiff = Math.abs(normalized.length - normalizedChannel.length)
                    candidates.add(epg to (score * 100 - lengthDiff))
                }
            }
        }
        
        val result = candidates
            .distinctBy { it.first }
            .sortedByDescending { it.second }
            .take(5)
            .map { it.first }
        
        if (substringMatchCache.size < MAX_CACHE_SIZE) {
            substringMatchCache[normalized] = result
        }
        
        return result
    }
    
    companion object {
        private const val MAX_CACHE_SIZE = 500
        private val substringMatchCache = ConcurrentHashMap<String, List<Epg>>()
        
        fun clearSubstringMatchCache() {
            substringMatchCache.clear()
        }
        
        private fun replaceChineseNumbers(text: String): String {
            return text
                .replace("一", "1")
                .replace("二", "2")
                .replace("三", "3")
                .replace("四", "4")
                .replace("五", "5")
                .replace("六", "6")
                .replace("七", "7")
                .replace("八", "8")
                .replace("九", "9")
                .replace("零", "0")
        }

        fun normalizeForIndex(name: String): String {
            if (name.isBlank()) return ""
            val simplified = ChineseConverter.toSimplified(name)
            val withoutBrackets = simplified
                .replace(Regex("\\([^)]*\\)"), "")
                .replace(Regex("\\[[^]]*\\]"), "")
                .replace(Regex("\\{[^}]*\\}"), "")
            
            val withArabicNumbers = replaceChineseNumbers(withoutBrackets)
            
            return withArabicNumbers
                .lowercase()
                .replace(Regex("(hd|标清|高清|4k|8k|超清|蓝光|藍光|uhd|sdr|hdr)"), "")
                .replace(Regex("[\\s\\p{Punct}]"), "")
                .trim()
        }
    }
}
