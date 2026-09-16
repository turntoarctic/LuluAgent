package com.lulu.agent.agent.evaluator

import com.lulu.agent.accessibility.model.ScrapedRawJob
import com.lulu.agent.llm.model.response.JDEvalResponse

/**
 * 岗位评估最终决策封装 (多维评分体系)
 */
data class EvaluatorResult(
    val jobId: String,
    val title: String,
    val companyName: String,
    val isApproved: Boolean,
    val matchScore: Int = 0,
    val techScore: Int = -1,
    val experienceScore: Int = -1,
    val salaryScore: Int = -1,
    val stabilityScore: Int = -1,
    val isLocalRejected: Boolean = false,
    val rejectReason: String = "",
    val highlights: List<String> = emptyList(),
    val risks: List<String> = emptyList(),
    val summaryReason: String = ""
) {
    companion object {
        /**
         * 本地 0-Token 规则直接淘汰
         */
        fun fromLocalReject(job: ScrapedRawJob, reason: String): EvaluatorResult {
            return EvaluatorResult(
                jobId = job.resolveJobId(),
                title = job.title,
                companyName = job.companyName,
                isApproved = false,
                matchScore = 0,
                isLocalRejected = true,
                rejectReason = reason,
                summaryReason = "命中本地过滤规则: $reason"
            )
        }

        /**
         * DeepSeek 深度评估成功：录取判定 = LLM 决策为 ACCEPT 且本地合成综合分达到阈值
         */
        fun fromLLMResponse(
            job: ScrapedRawJob,
            response: JDEvalResponse,
            compositeScore: Int,
            scoreThreshold: Int = 70
        ): EvaluatorResult {
            val approved = response.decision.equals(JDEvalResponse.DECISION_ACCEPT, ignoreCase = true) &&
                compositeScore >= scoreThreshold
            return EvaluatorResult(
                jobId = job.resolveJobId(),
                title = job.title,
                companyName = job.companyName,
                isApproved = approved,
                matchScore = compositeScore.coerceIn(0, 100),
                techScore = response.techMatch,
                experienceScore = response.experienceMatch,
                salaryScore = response.salaryMatch,
                stabilityScore = response.stability,
                isLocalRejected = false,
                rejectReason = if (!approved) response.summaryReason else "",
                highlights = response.highlights,
                risks = response.risks,
                summaryReason = response.summaryReason
            )
        }

        /**
         * 网络或解析异常兜底
         */
        fun fromError(job: ScrapedRawJob, errorMsg: String): EvaluatorResult {
            return EvaluatorResult(
                jobId = job.resolveJobId(),
                title = job.title,
                companyName = job.companyName,
                isApproved = false,
                matchScore = 0,
                isLocalRejected = false,
                rejectReason = errorMsg,
                summaryReason = "大模型评估异常: $errorMsg"
            )
        }
    }
}
