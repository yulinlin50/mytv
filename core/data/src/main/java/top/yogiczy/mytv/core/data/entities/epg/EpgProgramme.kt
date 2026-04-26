package top.yogiczy.mytv.core.data.entities.epg

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.roundToInt

@Serializable
data class EpgProgramme(
    val startAt: Long = 0,
    val endAt: Long = 0,
    val title: String = "",
) {
    companion object {
        fun EpgProgramme.isLive() = System.currentTimeMillis() in startAt..<endAt

        fun EpgProgramme.progress(current: Long = System.currentTimeMillis()): Float {
            val duration = endAt - startAt
            return if (duration <= 0) 0f else (current - startAt).toFloat() / duration
        }

        fun EpgProgramme.remainingMinutes(current: Long = System.currentTimeMillis()) =
            ceil((endAt - current) / 60_000f).roundToInt()

        val EXAMPLE = EpgProgramme(
            startAt = System.currentTimeMillis() - 3600 * 1000,
            endAt = System.currentTimeMillis() + 3600 * 1000,
            title = "节目标题",
        )

        val EMPTY by lazy {
            val todayStart = LocalDate.now()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            
            EpgProgramme(
                startAt = todayStart,
                endAt = todayStart + (24 * 3600 - 1) * 1000,
                title = "精彩节目",
            )
        }
    }
}
