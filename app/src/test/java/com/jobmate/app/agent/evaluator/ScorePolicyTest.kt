package com.jobmate.app.agent.evaluator

import com.jobmate.app.data.pref.AppSettings
import com.jobmate.app.llm.model.response.JDEvalResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScorePolicyTest {

    private fun fullResponse(
        tech: Int = 80,
        experience: Int = 70,
        salary: Int = 60,
        stability: Int = 90,
        matchScore: Int = 55
    ) = JDEvalResponse(
        matchScore = matchScore,
        techMatch = tech,
        experienceMatch = experience,
        salaryMatch = salary,
        stability = stability,
        summaryReason = "测试"
    )

    // ==================== 权重选择 ====================

    @Test
    fun `均衡偏好返回默认权重`() {
        val w = ScorePolicy.weightsFor(AppSettings.SCORE_PREF_BALANCED)
        assertEquals(ScorePolicy.DEFAULT_WEIGHTS, w)
    }

    @Test
    fun `四档预设权重总和均为100`() {
        listOf(
            AppSettings.SCORE_PREF_BALANCED,
            AppSettings.SCORE_PREF_TECH_FIRST,
            AppSettings.SCORE_PREF_SALARY_FIRST,
            AppSettings.SCORE_PREF_STABILITY_FIRST
        ).forEach { pref ->
            assertEquals("预设 $pref 权重总和应为 100", 100, ScorePolicy.weightsFor(pref).total)
        }
    }

    @Test
    fun `未知偏好回退均衡档`() {
        assertEquals(ScorePolicy.DEFAULT_WEIGHTS, ScorePolicy.weightsFor("不存在的偏好"))
    }

    @Test
    fun `自定义档优先使用传入权重`() {
        val custom = ScorePolicy.Weights(10, 20, 30, 40)
        assertEquals(custom, ScorePolicy.weightsFor(AppSettings.SCORE_PREF_CUSTOM, custom))
    }

    @Test
    fun `自定义档缺失传入权重时回退默认`() {
        assertEquals(ScorePolicy.DEFAULT_WEIGHTS, ScorePolicy.weightsFor(AppSettings.SCORE_PREF_CUSTOM, null))
    }

    // ==================== 加权合成 ====================

    @Test
    fun `四维加权计算正确`() {
        // tech=80*50 + exp=70*20 + salary=60*15 + stability=90*15 = 4000+1400+900+1350 = 7650 / 100 = 76.5 -> 77 (四舍五入)
        val score = ScorePolicy.composeBaseScore(fullResponse(), ScorePolicy.DEFAULT_WEIGHTS)
        assertEquals(77, score)
    }

    @Test
    fun `维度缺失时回退LLM直觉分`() {
        val partial = JDEvalResponse(matchScore = 66)
        assertEquals(66, ScorePolicy.composeBaseScore(partial, ScorePolicy.DEFAULT_WEIGHTS))
    }

    @Test
    fun `LLM越界分数被免疫_维度缺失时钳制到0到100`() {
        val wild = JDEvalResponse(matchScore = 250)
        assertEquals(100, ScorePolicy.composeBaseScore(wild, ScorePolicy.DEFAULT_WEIGHTS))

        val negative = JDEvalResponse(matchScore = -30)
        assertEquals(0, ScorePolicy.composeBaseScore(negative, ScorePolicy.DEFAULT_WEIGHTS))
    }

    @Test
    fun `本地加分累加并封顶100`() {
        val base = fullResponse(tech = 100, experience = 100, salary = 100, stability = 100)
        assertEquals(100, ScorePolicy.composeFinalScore(base, ScorePolicy.DEFAULT_WEIGHTS, localBonus = 5))

        val mid = fullResponse()
        assertEquals(79, ScorePolicy.composeFinalScore(mid, ScorePolicy.DEFAULT_WEIGHTS, localBonus = 2))
    }

    @Test
    fun `权重描述包含四个维度`() {
        val desc = ScorePolicy.describeWeights(AppSettings.SCORE_PREF_BALANCED)
        assertTrue(desc.contains("技术"))
        assertTrue(desc.contains("经验"))
        assertTrue(desc.contains("薪资"))
        assertTrue(desc.contains("稳定"))
    }
}
