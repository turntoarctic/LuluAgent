package com.jobmate.app.llm.prompt

import com.jobmate.app.accessibility.model.ScrapedRawJob
import com.jobmate.app.data.repository.ConfigRepository
import com.jobmate.app.llm.model.request.DeepSeekChatRequest

/**
 * 提示词装配中枢
 */
class PromptManager(
    private val configRepository: ConfigRepository
) {

    /**
     * 组装岗位契合度深度评估请求体
     */
    fun buildEvaluationRequest(
        rawJob: ScrapedRawJob,
        model: String = DeepSeekChatRequest.MODEL_CHAT
    ): DeepSeekChatRequest {
        val resume = configRepository.getResumeMarkdown().ifBlank {
            "（求职者未配置简历，请根据一般中高级技术标准严苛评估）"
        }

        val userPrompt = JDEvaluatePrompt.buildUserPrompt(
            candidateResume = resume,
            jobTitle = rawJob.title,
            companyName = rawJob.companyName,
            salaryText = rawJob.salaryText,
            jobDescription = rawJob.jobDescription,
            expectedMinSalaryK = configRepository.getMinSalaryFilterK()
        )

        return DeepSeekChatRequest.buildJsonRequest(
            systemPrompt = JDEvaluatePrompt.SYSTEM_PROMPT,
            userPrompt = userPrompt,
            model = model,
            temperature = 0.2 // 评估阶段使用低温度系数，保证推理严谨稳定
        )
    }

    /**
     * 组装个性化破冰打招呼语请求体
     */
    fun buildGreetingRequest(
        rawJob: ScrapedRawJob,
        highlights: List<String> = emptyList(),
        model: String = DeepSeekChatRequest.MODEL_CHAT
    ): DeepSeekChatRequest {
        val resume = configRepository.getResumeMarkdown()

        val userPrompt = IcebreakGreetingPrompt.buildUserPrompt(
            candidateResume = resume,
            jobTitle = rawJob.title,
            companyName = rawJob.companyName,
            jobDescription = rawJob.jobDescription,
            highlights = highlights
        )

        return DeepSeekChatRequest.buildJsonRequest(
            systemPrompt = IcebreakGreetingPrompt.systemPrompt(configRepository.getGreetingStyle()),
            userPrompt = userPrompt,
            model = model,
            temperature = 0.7 // 生成话术使用较高温度，增强行文灵活性与个性化
        )
    }
}
