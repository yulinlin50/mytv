package top.yogiczy.mytv.tv.ui.screensold.videoplayer

enum class VideoPlayerDisplayMode(val value: Int) {
    ORIGINAL(0),
    FILL(1),
    CROP(2),
    FOUR_THREE(3),
    SIXTEEN_NINE(4),
    TWO_THIRTY_FIVE_ONE(5);

    val label: String
        get() = when (this) {
            ORIGINAL -> "原始比例"
            FILL -> "填充"
            CROP -> "裁剪"
            FOUR_THREE -> "4:3"
            SIXTEEN_NINE -> "16:9"
            TWO_THIRTY_FIVE_ONE -> "2.35:1"
        }

    companion object {
        fun fromValue(value: Int): VideoPlayerDisplayMode {
            return entries.find { it.value == value } ?: ORIGINAL
        }
    }
}
