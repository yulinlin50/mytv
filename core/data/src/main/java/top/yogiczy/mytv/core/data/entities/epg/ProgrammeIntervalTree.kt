package top.yogiczy.mytv.core.data.entities.epg

import kotlin.math.max

class ProgrammeIntervalTree(programmes: List<EpgProgramme> = emptyList()) {
    
    private var root: IntervalNode? = null
    private val sortedProgrammes: List<EpgProgramme> = programmes.sortedBy { it.startAt }
    
    private data class IntervalNode(
        val programme: EpgProgramme,
        val maxEnd: Long,
        var left: IntervalNode? = null,
        var right: IntervalNode? = null
    )
    
    init {
        if (programmes.isNotEmpty()) {
            root = buildTree(sortedProgrammes)
        }
    }
    
    private fun buildTree(programmes: List<EpgProgramme>): IntervalNode? {
        if (programmes.isEmpty()) return null
        
        val mid = programmes.size / 2
        val programme = programmes[mid]
        
        val leftProgrammes = programmes.subList(0, mid)
        val rightProgrammes = programmes.subList(mid + 1, programmes.size)
        
        val leftNode = buildTree(leftProgrammes)
        val rightNode = buildTree(rightProgrammes)
        
        val maxEnd = maxOf(
            programme.endAt,
            leftNode?.maxEnd ?: Long.MIN_VALUE,
            rightNode?.maxEnd ?: Long.MIN_VALUE
        )
        
        return IntervalNode(programme, maxEnd, leftNode, rightNode)
    }
    
    fun findProgrammeAt(time: Long): EpgProgramme? {
        return findProgrammeAt(root, time)
    }
    
    private fun findProgrammeAt(node: IntervalNode?, time: Long): EpgProgramme? {
        if (node == null) return null
        
        if (time in node.programme.startAt..<node.programme.endAt) {
            return node.programme
        }
        
        if (node.left != null && time < node.maxEnd) {
            val leftResult = findProgrammeAt(node.left, time)
            if (leftResult != null) return leftResult
        }
        
        if (time >= node.programme.endAt) {
            return findProgrammeAt(node.right, time)
        }
        
        return null
    }
    
    fun findProgrammesInRange(startTime: Long, endTime: Long): List<EpgProgramme> {
        val result = mutableListOf<EpgProgramme>()
        findProgrammesInRange(root, startTime, endTime, result)
        return result.sortedBy { it.startAt }
    }
    
    private fun findProgrammesInRange(
        node: IntervalNode?,
        startTime: Long,
        endTime: Long,
        result: MutableList<EpgProgramme>
    ) {
        if (node == null) return
        
        if (intervalsOverlap(node.programme.startAt, node.programme.endAt, startTime, endTime)) {
            result.add(node.programme)
        }
        
        if (node.left != null && startTime < node.left!!.maxEnd) {
            findProgrammesInRange(node.left, startTime, endTime, result)
        }
        
        if (node.right != null && endTime > node.programme.startAt) {
            findProgrammesInRange(node.right, startTime, endTime, result)
        }
    }
    
    private fun intervalsOverlap(start1: Long, end1: Long, start2: Long, end2: Long): Boolean {
        return start1 < end2 && start2 < end1
    }
    
    fun findLiveProgramme(): EpgProgramme? {
        return findProgrammeAt(System.currentTimeMillis())
    }
    
    fun findUpcomingProgrammes(count: Int): List<EpgProgramme> {
        val now = System.currentTimeMillis()
        val result = mutableListOf<EpgProgramme>()
        findUpcomingProgrammes(root, now, result)
        return result.sortedBy { it.startAt }.take(count)
    }
    
    private fun findUpcomingProgrammes(
        node: IntervalNode?,
        now: Long,
        result: MutableList<EpgProgramme>
    ) {
        if (node == null) return
        
        if (node.programme.startAt >= now) {
            result.add(node.programme)
        }
        
        if (node.left != null && now < node.left!!.maxEnd) {
            findUpcomingProgrammes(node.left, now, result)
        }
        
        if (node.right != null) {
            findUpcomingProgrammes(node.right, now, result)
        }
    }
    
    fun binarySearchByTime(time: Long): Int {
        var low = 0
        var high = sortedProgrammes.size - 1
        var result = -1
        
        while (low <= high) {
            val mid = (low + high) / 2
            val prog = sortedProgrammes[mid]
            
            when {
                time < prog.startAt -> high = mid - 1
                time >= prog.endAt -> low = mid + 1
                else -> {
                    result = mid
                    break
                }
            }
        }
        
        return result
    }
    
    fun getProgrammeAt(index: Int): EpgProgramme? {
        return sortedProgrammes.getOrNull(index)
    }
    
    val size: Int get() = sortedProgrammes.size
    
    fun isEmpty(): Boolean = sortedProgrammes.isEmpty()
    
    companion object {
        fun fromProgrammeList(programmeList: EpgProgrammeList): ProgrammeIntervalTree {
            return ProgrammeIntervalTree(programmeList.value)
        }
    }
}
