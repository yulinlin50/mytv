package top.yogiczy.mytv.core.data.utils

import top.yogiczy.mytv.core.data.entities.epg.Epg
import java.util.concurrent.ConcurrentHashMap

class ChannelTrieIndex(epgs: List<Epg> = emptyList()) {
    
    private val root = TrieNode()
    private val exactMatchIndex = ConcurrentHashMap<String, Epg>()
    private val normalizedMatchIndex = ConcurrentHashMap<String, MutableList<Epg>>()
    private val allEpgs: List<Epg> = epgs
    private val buildLock = Any()
    
    private class TrieNode {
        val children = ConcurrentHashMap<Int, TrieNode>()
        val epgs = java.util.Collections.synchronizedList(mutableListOf<Epg>())
        var isEnd = false
    }
    
    init {
        buildIndexes(epgs)
    }
    
    private fun buildIndexes(epgs: List<Epg>) {
        synchronized(buildLock) {
            for (epg in epgs) {
                for (name in epg.channelList) {
                    val normalized = normalizeForIndex(name)
                    
                    exactMatchIndex[name] = epg
                    exactMatchIndex[name.lowercase()] = epg
                    
                    normalizedMatchIndex.getOrPut(normalized) { 
                        java.util.Collections.synchronizedList(mutableListOf()) 
                    }.add(epg)
                    
                    insertToTrie(normalized, epg)
                }
            }
        }
    }
    
    private fun insertToTrie(normalized: String, epg: Epg) {
        var node = root
        for (char in normalized) {
            val charCode = char.code
            node = node.children.getOrPut(charCode) { TrieNode() }
        }
        node.isEnd = true
        node.epgs.add(epg)
    }
    
    fun exactMatch(name: String): Epg? {
        return exactMatchIndex[name] ?: exactMatchIndex[name.lowercase()]
    }
    
    fun normalizedMatch(name: String): List<Epg>? {
        val normalized = normalizeForIndex(name)
        return normalizedMatchIndex[normalized]
    }
    
    fun prefixMatch(name: String): List<Epg> {
        val normalized = normalizeForIndex(name)
        if (normalized.isBlank()) return emptyList()
        
        var node = root
        for (char in normalized) {
            node = node.children[char.code] ?: return emptyList()
        }
        
        return collectAllEpgs(node)
    }
    
    private fun collectAllEpgs(node: TrieNode): List<Epg> {
        val result = mutableListOf<Epg>()
        val stack = ArrayDeque<TrieNode>()
        stack.add(node)
        
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            if (current.isEnd) {
                result.addAll(current.epgs)
            }
            for (child in current.children.values) {
                stack.add(child)
            }
        }
        
        return result.distinct()
    }
    
    fun substringMatch(name: String): List<Epg> {
        val normalized = normalizeForIndex(name)
        if (normalized.isBlank()) return emptyList()
        
        val cached: List<Epg>? = UnifiedCacheManager.get(UnifiedCacheManager.CacheNames.SUBSTRING_MATCH, normalized)
        if (cached != null) return cached
        
        val candidates = mutableListOf<Pair<Epg, Int>>()
        
        for (epg in allEpgs) {
            for (channelName in epg.channelList) {
                val normalizedChannel = normalizeForIndex(channelName)
                if (normalizedChannel.isBlank()) continue
                
                val queryDigits = normalized.filter { it.isDigit() }
                val channelDigits = normalizedChannel.filter { it.isDigit() }
                if (queryDigits.isNotEmpty() && channelDigits.isNotEmpty() && queryDigits != channelDigits) {
                    continue
                }
                
                var score = 0
                
                if (normalizedChannel == normalized) {
                    score = 200
                } else if (normalizedChannel.contains(normalized)) {
                    val lengthDiff = normalizedChannel.length - normalized.length
                    score = 100 - lengthDiff * 10
                } else if (normalized.contains(normalizedChannel)) {
                    val lengthDiff = normalized.length - normalizedChannel.length
                    score = 80 - lengthDiff * 10
                } else {
                    val commonPrefix = normalized.commonPrefixWith(normalizedChannel)
                    if (commonPrefix.length >= 2) {
                        score = commonPrefix.length * 5
                    }
                }
                
                if (score > 0) {
                    val lengthDiff = kotlin.math.abs(normalized.length - normalizedChannel.length)
                    candidates.add(epg to (score * 100 - lengthDiff))
                }
            }
        }
        
        val result = candidates
            .distinctBy { it.first }
            .sortedByDescending { it.second }
            .take(5)
            .map { it.first }
        
        UnifiedCacheManager.put(UnifiedCacheManager.CacheNames.SUBSTRING_MATCH, normalized, result)
        
        return result
    }
    
    companion object {
        fun clearCaches() {
            UnifiedCacheManager.clearCache(UnifiedCacheManager.CacheNames.SUBSTRING_MATCH)
            UnifiedCacheManager.clearCache(UnifiedCacheManager.CacheNames.NORMALIZE)
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
            
            return UnifiedCacheManager.getOrPut(UnifiedCacheManager.CacheNames.NORMALIZE, name) {
                val simplified = ChineseConverter.toSimplified(name)
                val withoutBrackets = simplified
                    .replace(Regex("\\([^)]*\\)"), "")
                    .replace(Regex("\\[[^]]*\\]"), "")
                    .replace(Regex("\\{[^}]*\\}"), "")
                
                val withArabicNumbers = replaceChineseNumbers(withoutBrackets)
                
                withArabicNumbers
                    .lowercase()
                    .replace(Regex("(hd|标清|高清|4k|8k|超清|蓝光|藍光|uhd|sdr|hdr)"), "")
                    .replace(Regex("[\\s\\p{Punct}]"), "")
                    .trim()
            }
        }
    }
}
