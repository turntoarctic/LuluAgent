package com.lulu.agent.llm.model.response

import com.google.gson.annotations.SerializedName

/**
 * DeepSeek 评估岗位匹配度的结构化输出模型
 *
 * 多维评分体系：LLM 输出 技术栈/经验年限/薪资/稳定性 四个维度分 (0~100，越高越优)，
 * match_score 保留为 LLM 的综合直觉分，最终总分由本地 ScorePolicy 加权合成（可解释、可调权）。
 */
data class JDEvalResponse(
    @SerializedName("match_score")
    val matchScore: Int = 0,

    @SerializedName("tech_match")
    val techMatch: Int = -1,

    @SerializedName("experience_match")
    val experienceMatch: Int = -1,

    @SerializedName("salary_match")
    val salaryMatch: Int = -1,

    @SerializedName("stability")
    val stability: Int = -1,

    @SerializedName("decision")
    val decision: String = DECISION_REJECT,

    @SerializedName("highlights")
    val highlights: List<String> = emptyList(),

    @SerializedName("risks")
    val risks: List<String> = emptyList(),

    @SerializedName("summary_reason")
    val summaryReason: String = ""
) {
    /**
     * 四个维度分是否齐全（缺失时本地合成会回退到 match_score）
     */
    fun hasFullDimensions(): Boolean {
        return techMatch in 0..100 && experienceMatch in 0..100 &&
            salaryMatch in 0..100 && stability in 0..100
    }

    /**
     * 清洗 LLM 输出的越界分数：match_score 强制夹取到 [0,100]，维度分越界视为缺失(-1)
     */
    fun sanitized(): JDEvalResponse {
        return copy(
            matchScore = matchScore.coerceIn(0, 100),
            techMatch = if (techMatch in 0..100) techMatch else -1,
            experienceMatch = if (experienceMatch in 0..100) experienceMatch else -1,
            salaryMatch = if (salaryMatch in 0..100) salaryMatch else -1,
            stability = if (stability in 0..100) stability else -1
        )
    }

    /**
     * 综合判定：决策为 ACCEPT 且分数达到门槛（默认 70 分）
     */
    fun isApproved(minScoreThreshold: Int = 70): Boolean {
        return decision.equals(DECISION_ACCEPT, ignoreCase = true) && matchScore >= minScoreThreshold
    }

    companion object {
        const val DECISION_ACCEPT = "ACCEPT"
        const val DECISION_REJECT = "REJECT"
    }
}
