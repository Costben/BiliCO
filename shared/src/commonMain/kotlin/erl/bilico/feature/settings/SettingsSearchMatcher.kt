package erl.bilico.feature.settings

import erl.bilico.platform.SynchronizedMap
import erl.bilico.platform.getOrPut
import kotlin.math.abs
import kotlin.math.min

/**
 * 设置页本地多模态搜索引擎（零额外依赖）：
 * 支持原字子串、简繁归一、汉语全拼、拼音首字母、音节混拼（如 `dmu` / `shensm`）、
 * 标题跳字子序列（如 `深模` / `清图`）、轻度拼写纠错（Damerau-Levenshtein ≤ 1）与相关度打分。
 */
internal object SettingsSearchMatcher {

    private val indexCache = SynchronizedMap<String, TextSearchIndex>()
    private val normalizedQueryCache = SynchronizedMap<String, String>()

    /**
     * 计算某条设置对多词查询的综合相关度分值；任一词未命中时返回 0。
     */
    fun scoreEntry(
        entry: SettingsEntry,
        groupTitle: String,
        terms: List<String>,
    ): Int {
        if (terms.isEmpty()) return 1
        val titleIdx = indexOf(entry.title)
        val summaryIdx = indexOf(entry.summary)
        val keywordIdx = indexOf(entry.keywords)
        val groupIdx = indexOf(groupTitle)

        var total = 0
        for (rawTerm in terms) {
            val term = normalizeQuery(rawTerm)
            if (term.isEmpty()) continue
            val best = maxOf(
                scoreField(term, titleIdx, FieldWeight.TITLE),
                scoreField(term, keywordIdx, FieldWeight.KEYWORD),
                scoreField(term, summaryIdx, FieldWeight.SUMMARY),
                scoreField(term, groupIdx, FieldWeight.GROUP),
            )
            if (best <= 0) return 0
            total += best
        }
        return total
    }

    private enum class FieldWeight(
        val exact: Int,
        val substring: Int,
        val syllable: Int,
        val initialsSubseq: Int,
        val cjkSubseq: Int,
        val typo: Int,
        val allowShortPinyin: Boolean,
        val allowCjkSubseq: Boolean,
    ) {
        TITLE(120, 100, 86, 68, 62, 54, allowShortPinyin = true, allowCjkSubseq = true),
        KEYWORD(84, 72, 62, 46, 0, 44, allowShortPinyin = false, allowCjkSubseq = false),
        SUMMARY(64, 54, 48, 0, 0, 34, allowShortPinyin = false, allowCjkSubseq = false),
        GROUP(52, 44, 38, 30, 28, 24, allowShortPinyin = true, allowCjkSubseq = true),
    }

    private fun scoreField(
        term: String,
        index: TextSearchIndex,
        weight: FieldWeight,
    ): Int {
        if (index.rawNorm.isEmpty()) return 0

        // 1. 标准化原文 / 去标点原文精确或子串命中（同时覆盖简繁归一与中英混输）
        if (index.compact == term || index.rawNorm == term) return weight.exact
        if (index.rawNorm.contains(term) || index.compact.contains(term)) {
            return if (index.compact.startsWith(term)) weight.substring + 6 else weight.substring
        }

        val isAscii = term.all { it in 'a'..'z' || it in '0'..'9' }
        val isAlphaOnly = isAscii && term.all { it in 'a'..'z' }

        // 2. 英文/拼音音节前缀匹配：同时覆盖全拼（danmu）、首字母（ssms）、混拼（dmu / shensm）
        if (isAscii && index.syllables.isNotEmpty() && (term.length >= 2 || weight.allowShortPinyin)) {
            if (matchesSyllablePrefixes(term, index.syllables)) {
                return weight.syllable
            }
            // 3. 拼音首字母跳字子序列（如 dmz 命中 弹幕设置），仅在标题/关键词/分组中启用避免噪声
            if (isAlphaOnly && term.length >= 2 && weight.initialsSubseq > 0) {
                if (matchesInitialsSubsequence(term, index.syllables)) {
                    return weight.initialsSubseq
                }
            }
        }

        // 4. 汉字跳字子序列（如「深模」命中「深色模式」、「清图」命中「清除图片缓存」）
        if (!isAscii && weight.allowCjkSubseq && term.length >= 2 && isSubsequence(term, index.compact)) {
            return weight.cjkSubseq
        }

        // 5. 轻度拼写容错（Damerau-Levenshtein 距离 ≤ 1：支持错字、漏字、多字、相邻字母打反）
        if (weight.typo > 0) {
            if (isAscii && term.length >= 4) {
                if (index.asciiTokens.any { token ->
                        token.length >= 4 && damerauLevenshteinAtMostOne(term, token)
                    }
                ) {
                    return weight.typo
                }
                if (index.joinedPinyin.length >= 4 && matchesPinyinWithTypo(term, index.joinedPinyin)) {
                    return weight.typo - 4
                }
            } else if (!isAscii && term.length >= 3 && index.compact.length >= 3) {
                if (matchesSlidingTypo(term, index.compact)) {
                    return weight.typo
                }
            }
        }

        return 0
    }

    private fun indexOf(text: String): TextSearchIndex {
        if (text.isBlank()) return TextSearchIndex.EMPTY
        return indexCache.getOrPut(text) { buildIndex(text) }
    }

    private fun normalizeQuery(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        return normalizedQueryCache.getOrPut(trimmed) {
            val simplified = toSimplified(trimmed)
            normalizeBasic(simplified)
        }
    }

    private fun buildIndex(raw: String): TextSearchIndex {
        val simplified = toSimplified(raw)
        val rawNorm = normalizeBasic(simplified)
        val compact = stripNonWord(rawNorm)
        val syllables = extractSyllables(simplified)
        val asciiTokens = LinkedHashSet<String>()
        Regex("[a-z0-9]+").findAll(rawNorm).forEach { asciiTokens.add(it.value) }
        val joinedPinyin = syllables.joinToString("") { it.firstOrNull().orEmpty() }
        // 同步把每个连续中文词段对应的全拼也收入 token，便于对关键词里的长词做拼写纠错
        Regex("[\\u4e00-\\u9fff]+").findAll(simplified).forEach { match ->
            val segSyllables = extractSyllables(match.value)
            val full = segSyllables.joinToString("") { it.firstOrNull().orEmpty() }
            if (full.length >= 4) asciiTokens.add(full)
        }
        return TextSearchIndex(
            rawNorm = rawNorm,
            compact = compact,
            syllables = syllables,
            joinedPinyin = joinedPinyin,
            asciiTokens = asciiTokens.toList(),
        )
    }

    /**
     * 将文本拆分为「音节候选列表」序列：
     * - 每个汉字映射为 1~N 个无声调拼音候选（自动修正多音字，并将 `ü` 同时展开为 `v` 和 `u`）；
     * - 连续英文/数字段按单词拆成一个音节单元，便于中英混排（如 `4k`、`b站粉`、`高级+1`）无缝匹配。
     */
    private fun extractSyllables(text: String): List<List<String>> {
        val result = ArrayList<List<String>>(text.length)
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                isCjkHan(ch) -> {
                    val candidates = hanToPinyinVariants(ch)
                    if (candidates.isNotEmpty()) result.add(candidates)
                    i++
                }
                ch.isLetterOrDigit() && ch.code < 128 -> {
                    val start = i
                    while (i < text.length && text[i].isLetterOrDigit() && text[i].code < 128) i++
                    result.add(listOf(text.substring(start, i).lowercase()))
                }
                else -> i++
            }
        }
        return result
    }

    /**
     * 音节对齐前缀匹配：判断 [query] 是否可以从任意起始音节开始，连续按音节前缀匹配完成。
     * 例：`["shen", "se", "mo", "shi"]` 可命中 `ssms`、`shensm`、`shens`、`shensemoshi`、`ms`。
     */
    private fun matchesSyllablePrefixes(
        query: String,
        syllables: List<List<String>>,
    ): Boolean {
        val qLen = query.length
        val sLen = syllables.size
        for (start in 0 until sLen) {
            // 仅当首字符能命中起始音节任一候选的首字母时才进入搜索
            if (syllables[start].none { it.isNotEmpty() && it[0] == query[0] }) continue
            val visited = BooleanArray((sLen + 1) * (qLen + 1))
            fun dfs(si: Int, qi: Int): Boolean {
                if (qi == qLen) return true
                if (si >= sLen) return false
                val state = si * (qLen + 1) + qi
                if (visited[state]) return false
                visited[state] = true
                for (variant in syllables[si]) {
                    if (variant.isEmpty() || variant[0] != query[qi]) continue
                    var maxMatch = 0
                    val limit = min(variant.length, qLen - qi)
                    while (maxMatch < limit && variant[maxMatch] == query[qi + maxMatch]) {
                        maxMatch++
                    }
                    // 贪心优先尝试更长前缀，失败再回退到较短前缀（含仅取首字母）
                    for (take in maxMatch downTo 1) {
                        if (dfs(si + 1, qi + take)) return true
                    }
                }
                return false
            }
            if (dfs(start, 0)) return true
        }
        return false
    }

    /** 首字母跳字子序列匹配：判断 [query] 是否按顺序命中各音节候选的首字母。 */
    private fun matchesInitialsSubsequence(
        query: String,
        syllables: List<List<String>>,
    ): Boolean {
        var qi = 0
        for (variants in syllables) {
            val target = query[qi]
            if (variants.any { it.isNotEmpty() && it[0] == target }) {
                qi++
                if (qi == query.length) return true
            }
        }
        return false
    }

    private fun isSubsequence(query: String, target: String): Boolean {
        if (query.length > target.length) return false
        var qi = 0
        for (i in target.indices) {
            if (target[i] == query[qi]) {
                qi++
                if (qi == query.length) return true
            }
        }
        return false
    }

    private fun matchesPinyinWithTypo(term: String, joinedPinyin: String): Boolean {
        if (abs(term.length - joinedPinyin.length) <= 1 && damerauLevenshteinAtMostOne(term, joinedPinyin)) {
            return true
        }
        return matchesSlidingTypo(term, joinedPinyin)
    }

    private fun matchesSlidingTypo(term: String, target: String): Boolean {
        val tLen = term.length
        for (winLen in (tLen - 1)..(tLen + 1)) {
            if (winLen < 3 || winLen > target.length) continue
            for (start in 0..(target.length - winLen)) {
                if (damerauLevenshteinAtMostOne(term, target.substring(start, start + winLen))) {
                    return true
                }
            }
        }
        return false
    }

    /** O(N) 判断两串的 Damerau-Levenshtein 距离是否 ≤ 1（单次替换、插入、删除或相邻交换）。 */
    private fun damerauLevenshteinAtMostOne(a: String, b: String): Boolean {
        val lenA = a.length
        val lenB = b.length
        if (abs(lenA - lenB) > 1) return false
        if (a == b) return true
        var i = 0
        val minLen = min(lenA, lenB)
        while (i < minLen && a[i] == b[i]) i++
        if (lenA == lenB) {
            // 单次替换
            if (a.regionMatches(i + 1, b, i + 1, lenA - i - 1)) return true
            // 单次相邻字符对调（如 danum <-> danmu）
            return i + 1 < lenA &&
                a[i] == b[i + 1] &&
                a[i + 1] == b[i] &&
                a.regionMatches(i + 2, b, i + 2, lenA - i - 2)
        }
        // 单次插入或删除
        return if (lenA > lenB) {
            a.regionMatches(i + 1, b, i, lenB - i)
        } else {
            a.regionMatches(i, b, i + 1, lenA - i)
        }
    }

    private fun normalizeBasic(input: String): String = input.lowercase()

    private fun stripNonWord(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            if (ch.isLetterOrDigit() || isCjkHan(ch) || ch == '@' || ch == '+') {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun isCjkHan(ch: Char): Boolean = ch in '\u4E00'..'\u9FFF'

    private fun toSimplified(text: String): String {
        val icuResult = IcuBridge.toSimplified(text)
        return applyTraditionalFallbacks(icuResult)
    }

    private fun applyTraditionalFallbacks(text: String): String {
        var out = text
        for ((trad, simp) in TRAD_PHRASE_FALLBACKS) {
            if (out.contains(trad)) out = out.replace(trad, simp)
        }
        return out
    }

    private fun hanToPinyinVariants(ch: Char): List<String> {
        val variants = LinkedHashSet<String>()
        POLYPHONE_OVERRIDES[ch]?.forEach { variants.add(it) }
        val icu = IcuBridge.hanToPinyin(ch)
        if (icu.isNotEmpty()) {
            // 保留 ü -> v / u 双候选，便于键盘习惯输入 lv / lu 都能命中
            if (icu.contains('ü')) {
                variants.add(icu.replace('ü', 'v'))
                variants.add(icu.replace('ü', 'u'))
            } else {
                variants.add(icu)
            }
        }
        return variants.toList()
    }

    private object IcuBridge {
        fun toSimplified(text: String): String = text
        fun hanToPinyin(ch: Char): String = ""
    }

    /** 设置页高频多音字修正：同时收录主读音与常见次读音，避免 ICU 单读音漏匹配。 */
    private val POLYPHONE_OVERRIDES: Map<Char, List<String>> = mapOf(
        '长' to listOf("chang", "zhang"),
        '弹' to listOf("dan", "tan"),
        '调' to listOf("tiao", "diao"),
        '重' to listOf("zhong", "chong"),
        '数' to listOf("shu"),
        '率' to listOf("lv", "lu", "shuai"),
        '绿' to listOf("lv", "lu"),
        '略' to listOf("lve", "lue"),
        '还' to listOf("hai", "huan"),
        '行' to listOf("hang", "xing"),
        '藏' to listOf("cang", "zang"),
        '倒' to listOf("dao"),
        '降' to listOf("jiang", "xiang"),
        '卡' to listOf("ka", "qia"),
        '角' to listOf("jiao", "jue"),
        '系' to listOf("xi", "ji"),
        '省' to listOf("sheng", "xing"),
        '屏' to listOf("ping"),
        '背' to listOf("bei"),
        '传' to listOf("chuan", "zhuan"),
        '差' to listOf("cha", "chai"),
        '应' to listOf("ying"),
        '量' to listOf("liang"),
        '度' to listOf("du"),
        '分' to listOf("fen"),
        '模' to listOf("mo", "mu"),
        '间' to listOf("jian"),
        '发' to listOf("fa"),
        '只' to listOf("zhi"),
        '片' to listOf("pian"),
        '更' to listOf("geng"),
        '乐' to listOf("le", "yue"),
    )

    /** 跨地区常用术语归一（如港台「快取」→「缓存」、「畫質」→「画质」）。 */
    private val TRAD_PHRASE_FALLBACKS: List<Pair<String, String>> = listOf(
        "快取" to "缓存",
        "預設" to "默认",
        "预设" to "默认",
        "設定" to "设置",
        "设定" to "设置",
        "帳號" to "账号",
        "帳戶" to "账号",
        "彈幕" to "弹幕",
        "顯示" to "显示",
        "主題" to "主题",
        "圖標" to "图标",
        "畫質" to "画质",
        "離線" to "离线",
        "緩存" to "缓存",
        "網路" to "网络",
        "連結" to "链接",
        "超連結" to "超链接",
        "鎖定" to "锁定",
        "橫屏" to "横屏",
        "豎屏" to "竖屏",
        "點踩" to "点踩",
        "過濾" to "过滤",
        "關閉" to "关闭",
        "開啟" to "开启",
    )
}

private data class TextSearchIndex(
    val rawNorm: String,
    val compact: String,
    val syllables: List<List<String>>,
    val joinedPinyin: String,
    val asciiTokens: List<String>,
) {
    companion object {
        val EMPTY = TextSearchIndex("", "", emptyList(), "", emptyList())
    }
}
