package top.yogiczy.mytv.core.data.utils

object ChineseConverter {
    private val traditionalToSimplifiedMap: Map<Char, Char>

    init {
        // 去重后的繁简映射表（原始数据中存在大量重复条目）
        val pairs = listOf(
            '亞' to '亚', '電' to '电', '視' to '视', '頻' to '频',
            '臺' to '台', '灣' to '湾', '國' to '国', '語' to '语',
            '粵' to '粤', '聲' to '声', '龍' to '龙', '鳳' to '凤',
            '衛' to '卫', '東' to '东', '廣' to '广', '蘇' to '苏',
            '貴' to '贵', '雲' to '云', '遼' to '辽', '寧' to '宁',
            '內' to '内', '肅' to '肃', '門' to '门', '際' to '际',
            '時' to '时', '訊' to '讯', '體' to '体', '劇' to '剧',
            '綜' to '综', '藝' to '艺', '兒' to '儿', '動' to '动',
            '紀' to '纪', '錄' to '录', '財' to '财', '經' to '经',
            '樂' to '乐', '遊' to '游', '購' to '购', '標' to '标',
            '準' to '准', '藍' to '蓝', '級' to '级', '獨' to '独',
            '顧' to '顾', '聞' to '闻', '資' to '资', '聯' to '联',
            '網' to '网', '數' to '数', '碼' to '码', '戲' to '戏',
            '場' to '场', '連' to '连', '續' to '续', '線' to '线',
            '陸' to '陆', '韓' to '韩', '歐' to '欧', '來' to '来',
            '爾' to '尔', '羅' to '罗', '麥' to '麦', '納' to '纳',
            '幾' to '几', '韋' to '韦', '達' to '达', '貝' to '贝',
            '維' to '维', '約' to '约', '萊' to '莱', '烏' to '乌',
            '魯' to '鲁', '倫' to '伦', '薩' to '萨', '澤' to '泽',
            '屬' to '属', '勞' to '劳', '庫' to '库', '紐' to '纽',
            '凱' to '凯', '恩' to '恩', '曼' to '曼', '聖' to '圣',
            '赫' to '赫', '勒' to '勒', '喬' to '乔', '領' to '领',
            '迪' to '迪', '蘭' to '兰', '慶' to '庆', '奧' to '奥',
            '臘' to '腊', '剛' to '刚', '贊' to '赞', '茲' to '兹',
            '萬' to '万', '諾' to '诺', '岡' to '冈', '敘' to '叙',
            '緬' to '缅', '撾' to '挝', '賓' to '宾', '華' to '华',
            '興' to '兴', '發' to '发', '豐' to '丰', '會' to '会',
            '長' to '长', '間' to '间', '鄉' to '乡', '風' to '风',
            '飛' to '飞', '飯' to '饭', '館' to '馆', '園' to '园',
            '縣' to '县', '諸' to '诸', '義' to '义', '溫' to '温',
            '麗' to '丽', '陝' to '陕', '蒙' to '蒙', '鮮' to '鲜',
            '坡' to '坡', '汶' to '汶', '朗' to '朗', '拉' to '拉',
            '伯' to '伯', '曼' to '曼', '脫' to '脱', '陶' to '陶',
            '宛' to '宛', '波' to '波', '威' to '威', '典' to '典',
            '冰' to '冰', '森' to '森', '堡' to '堡', '陀' to '陀',
            '梵' to '梵', '蒂' to '蒂', '盧' to '卢', '直' to '直',
            '布' to '布', '大' to '大', '列' to '列', '支' to '支',
            '敦' to '敦', '丹' to '丹', '麥' to '麦', '挪' to '挪',
            '瑞' to '瑞', '芬' to '芬', '愛' to '爱', '沙' to '沙',
            '尼' to '尼', '亞' to '亚', '匈' to '匈', '牙' to '牙',
            '利' to '利', '捷' to '捷', '伐' to '伐', '洛' to '洛',
            '保' to '保', '塞' to '塞', '及' to '及', '肯' to '肯',
            '非' to '非', '果' to '果', '蓬' to '蓬', '巴' to '巴',
            '留' to '留', '汪' to '汪', '求' to '求', '舌' to '舌',
            '茨' to '茨', '瓦' to '瓦', '米' to '米', '托' to '托',
            '圭' to '圭', '阿' to '阿', '根' to '根', '廷' to '廷',
            '智' to '智', '秘' to '秘', '委' to '委', '厄' to '厄',
            '多' to '多', '玻' to '玻', '洪' to '洪', '伯' to '伯',
            '墨' to '墨', '古' to '古', '買' to '买', '哈' to '哈',
            '立' to '立', '提' to '提', '各' to '各', '開' to '开',
            '百' to '百', '慕' to '慕', '格' to '格', '陵' to '陵',
            '道' to '道', '耳' to '耳', '他' to '他', '浦' to '浦',
            '路' to '路', '拜' to '拜', '旦' to '旦',
            '以' to '以', '色' to '色', '嫩' to '嫩', '也' to '也',
            '酋' to '酋', '卡' to '卡', '塔' to '塔', '林' to '林',
            '共' to '共', '和' to '和', '孟' to '孟',
            '不' to '不', '錫' to '锡', '金' to '金', '代' to '代',
            '夫' to '夫', '老' to '老', '柬' to '柬', '埔' to '埔',
            '寨' to '寨', '越' to '越', '文' to '文', '菲' to '菲',
            '律' to '律', '帕' to '帕', '紹' to '绍', '密' to '密',
            '邦' to '邦', '瑙' to '瑙', '所' to '所', '圖' to '图',
            '杜' to '杜', '夸' to '夸', '當' to '当', '部' to '部',
            '喀' to '喀', '皮' to '皮', '礁' to '礁', '洋' to '洋',
            '土' to '土', '島' to '岛'
        )
        traditionalToSimplifiedMap = pairs.filter { it.first != it.second }.toMap()
    }

    private const val CACHE_MAX_SIZE = 1000
    private val simplifyCache = LruMutableCache<String, String>(CACHE_MAX_SIZE)

    fun toSimplified(text: String): String {
        if (text.isEmpty()) return text
        simplifyCache.getTimestamped(text)?.let { return it }

        val result = buildString(text.length) {
            for (char in text) append(traditionalToSimplifiedMap[char] ?: char)
        }

        simplifyCache.putTimestamped(text, result)
        return result
    }

    fun clearCache() = simplifyCache.clearAll()
}
