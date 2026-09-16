package com.lulu.agent.llm.limiter

import android.util.LruCache
import com.lulu.agent.accessibility.model.ScrapedRawJob
import com.lulu.agent.llm.model.response.JDEvalResponse
import java.security.MessageDigest

/**
 * 岗位指纹去重缓存中心
 *
 * 指纹由 标题+公司+薪资+JD 全量构成：评估结论依赖这些上下文，
 * 仅按 JD 纯文本缓存会把 A 公司/低薪岗位的结论错误复用给同 JD 的 B 公司/高薪岗位。
 */
object DeduplicationCache {

    // 内存维护最多 500 个近期的岗位指纹评估结论
    private val memoryCache = LruCache<String, JDEvalResponse>(500)

    /**
     * 获取缓存中的评估结果
     */
    fun getCachedEvaluation(job: ScrapedRawJob): JDEvalResponse? {
        val key = computeFingerprint(job) ?: return null
        return memoryCache.get(key)
    }

    /**
     * 写入缓存
     */
    fun putEvaluation(job: ScrapedRawJob, response: JDEvalResponse) {
        val key = computeFingerprint(job) ?: return
        memoryCache.put(key, response)
    }

    fun contains(job: ScrapedRawJob): Boolean {
        return getCachedEvaluation(job) != null
    }

    fun clear() {
        memoryCache.evictAll()
    }

    /**
     * 计算岗位上下文的唯一 MD5 指纹 (核心要素缺失时返回 null 不缓存)
     */
    private fun computeFingerprint(job: ScrapedRawJob): String? {
        if (job.jobDescription.isBlank() && job.title.isBlank()) return null
        val rawKey = "${job.title.trim()}_${job.companyName.trim()}_${job.salaryText.trim()}_${job.jobDescription.trim()}"
        val cleaned = rawKey.replace("\\s+".toRegex(), "")
        return md5(cleaned)
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
