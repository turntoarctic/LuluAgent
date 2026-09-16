package com.lulu.agent.agent.evaluator

import com.lulu.agent.data.pref.AppSettings
import com.lulu.agent.llm.model.response.JDEvalResponse

/**
 * 评分策略中枢：四维分数加权合成 + 本地信号微调
 *
 * 总分不再直接采信 LLM 的单一 match_score，而是本地按用户偏好加权合成，
 * 保证分数可解释、权重可配置，并对 LLM 输出越界值免疫。
 */
object ScorePolicy {

    /**
     * 四维权重配比 (百分制)
     */
    data class Weights(
        val tech: Int,
        val experience: Int,
        val salary: Int,
        val stability: Int
    ) {
        val total: Int get() = tech + experience + salary + stability
    }

    val DEFAULT_WEIGHTS = Weights(50, 20, 15, 15)

    /**
     * 按用户评分偏好取权重：未知偏好回退均衡档
     */
    fun weightsFor(preference: String): Weights {
        return when (preference) {
            AppSettings.SCORE_PREF_TECH_FIRST -> Weights(65, 15, 10, 10)
            AppSettings.SCORE_PREF_SALARY_FIRST -> Weights(40, 15, 30, 15)
            AppSettings.SCORE_PREF_STABILITY_FIRST -> Weights(40, 15, 15, 30)
            AppSettings.SCORE_PREF_BALANCED -> DEFAULT_WEIGHTS
            else -> DEFAULT_WEIGHTS
        }
    }

    /**
     * 权重配比的人类可读描述 (设置页展示用)
     */
    fun describeWeights(preference: String): String {
        val w = weightsFor(preference)
        return "技术${w.tech}% · 经验${w.experience}% · 薪资${w.salary}% · 稳定${w.stability}%"
    }

    /**
     * 四维加权合成基础分；维度缺失时回退到 LLM 综合直觉分 match_score (兼容旧缓存)
     */
    fun composeBaseScore(response: JDEvalResponse, weights: Weights): Int {
        if (!response.hasFullDimensions()) {
            return response.matchScore.coerceIn(0, 100)
        }
        val weighted = response.techMatch * weights.tech +
            response.experienceMatch * weights.experience +
            response.salaryMatch * weights.salary +
            response.stability * weights.stability
        return Math.round(weighted.toFloat() / weights.total).coerceIn(0, 100)
    }

    /**
     * 最终得分 = 四维加权基础分 + 本地信号加分 (如 HR 高活跃度)，封顶 100
     */
    fun composeFinalScore(response: JDEvalResponse, weights: Weights, localBonus: Int): Int {
        return (composeBaseScore(response, weights) + localBonus).coerceIn(0, 100)
    }
}
