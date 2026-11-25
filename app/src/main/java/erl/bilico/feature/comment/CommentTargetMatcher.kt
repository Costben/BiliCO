package erl.bilico.feature.comment

import erl.bilico.data.model.Comment
import erl.bilico.data.model.CommentSort
import erl.bilico.data.repository.CommentRepository

internal data class MatchedCommentTarget(
    val root: Comment,
    val subReplyId: Long = 0L,
)

/**
 * 根据动态卡片外露的推荐评论（作者昵称 + 正文预览）在评论列表里反查定位目标评论。
 * 优先匹配主楼（作者+正文 → 纯正文），其次匹配楼中楼预览回复；首屏未命中时最多再翻两页热评。
 */
internal suspend fun resolvePreviewMatchedComment(
    repository: CommentRepository,
    oid: Long,
    type: Int,
    upMid: Long,
    firstPageComments: List<Comment>,
    firstPageNextOffset: String?,
    firstPageEnd: Boolean,
    matchAuthor: String,
    matchText: String,
): MatchedCommentTarget? {
    val normQuery = normalizeCommentPreview(matchText)
    if (normQuery.isEmpty() && matchAuthor.isBlank()) return null
    val normAuthor = matchAuthor.trim()

    findCommentMatchInList(firstPageComments, normAuthor, normQuery)?.let { return it }

    var offset = firstPageNextOffset
    var isEnd = firstPageEnd
    var pagesChecked = 0
    while (!isEnd && offset != null && pagesChecked < 2) {
        pagesChecked++
        val next = repository.mainComments(oid, offset, type, CommentSort.Hot, fallbackUpMid = upMid).getOrNull()
            ?: break
        findCommentMatchInList(next.comments, normAuthor, normQuery)?.let { return it }
        offset = next.nextOffset
        isEnd = next.isEnd || next.comments.isEmpty()
    }
    return null
}

private fun findCommentMatchInList(
    comments: List<Comment>,
    author: String,
    normQuery: String,
): MatchedCommentTarget? {
    // 1. 强匹配：作者名一致 + 正文匹配（主楼）
    if (author.isNotEmpty() && normQuery.isNotEmpty()) {
        comments.firstOrNull { c ->
            c.userName.trim().equals(author, ignoreCase = true) &&
                previewContentMatches(c.content, normQuery)
        }?.let { return MatchedCommentTarget(it) }

        // 2. 强匹配：作者名一致 + 正文匹配（楼中楼预览）
        for (root in comments) {
            val sub = root.replies.firstOrNull { r ->
                r.userName.trim().equals(author, ignoreCase = true) &&
                    previewContentMatches(r.content, normQuery)
            }
            if (sub != null) return MatchedCommentTarget(root, sub.id)
        }
    }

    // 3. 正文匹配（主楼）
    if (normQuery.isNotEmpty()) {
        comments.firstOrNull { previewContentMatches(it.content, normQuery) }
            ?.let { return MatchedCommentTarget(it) }

        // 4. 正文匹配（楼中楼预览）
        for (root in comments) {
            val sub = root.replies.firstOrNull { previewContentMatches(it.content, normQuery) }
            if (sub != null) return MatchedCommentTarget(root, sub.id)
        }
    }

    // 5. 兜底：若评论是纯图片/表情导致文本为空，按作者名匹配
    if (author.isNotEmpty()) {
        comments.firstOrNull { it.userName.trim().equals(author, ignoreCase = true) }
            ?.let { return MatchedCommentTarget(it) }
    }
    return null
}

private fun previewContentMatches(commentContent: String, normQuery: String): Boolean {
    val normContent = normalizeCommentPreview(commentContent)
    if (normContent.isEmpty() || normQuery.isEmpty()) return false
    if (normContent == normQuery) return true
    val probe = if (normQuery.length > 24) normQuery.substring(0, 24) else normQuery
    return normContent.contains(probe, ignoreCase = true) ||
        (normContent.length >= 4 && normQuery.contains(normContent, ignoreCase = true))
}

private fun normalizeCommentPreview(raw: String): String {
    var s = raw.trim()
    // 去掉楼中楼自带的「回复 @xxx :」前缀
    if (s.startsWith("回复 @")) {
        val idx = s.indexOf('：').takeIf { it > 0 } ?: s.indexOf(':')
        if (idx in 4..40) s = s.substring(idx + 1).trim()
    }
    s = s.replace(Regex("\\s+"), " ")
    while (s.endsWith("…") || s.endsWith("...")) {
        s = s.removeSuffix("…").removeSuffix("...").trimEnd()
    }
    return s
}
