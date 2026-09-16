package com.lulu.agent.agent.evaluator

import android.content.Context
import android.util.Log
import com.lulu.agent.accessibility.model.ScrapedRawJob
import com.lulu.agent.agent.base.BaseAgent
import com.lulu.agent.data.local.entity.JobEntity
import com.lulu.agent.data.repository.ConfigRepository
import com.lulu.agent.data.repository.JobRepository
import com.lulu.agent.llm.DeepSeekClient
import com.lulu.agent.llm.filter.LocalPreFilter

/**
 * 决策分析参谋：两级过滤中枢 (本地规则筛查 + DeepSeek深度评估)
 */
class EvaluatorAgent(
    context: Context,
    private val localPreFilter: LocalPreFilter,
    private val deepSeekClient: DeepSeekClient,
    private val jobRepository: JobRepository,
    private val configRepository: ConfigRepository
) : BaseAgent(context, "EvaluatorAgent") {

    override fun start() {
        super.start()
        sendAgentLog("🧠 决策参谋已就位，开启双引擎分析体系")
    }

    /**
     * 全流程深度评估一个岗位
     */
    suspend fun evaluate(rawJob: ScrapedRawJob): EvaluatorResult {
        val jobId = rawJob.resolveJobId()
        val jobTitle = rawJob.title
        val company = rawJob.companyName

        sendAgentLog("正在综合研判: $company - $jobTitle ...")

        // 1. 本地 0-Token 规则初筛
        val preCheck = localPreFilter.check(rawJob)
        if (preCheck is LocalPreFilter.FilterResult.Reject) {
            val rejectReason = preCheck.reason
            sendAgentLog("⛔ 本地规则拦截: $rejectReason (节省Token)")

            // 登记数据库为过滤状态
            jobRepository.recordDiscoveredJob(rawJob.toJobEntity(JobEntity.STATUS_FILTERED_OUT))
            jobRepository.markJobFilteredOut(jobId, rejectReason)

            return EvaluatorResult.fromLocalReject(rawJob, rejectReason)
        }

        // 2. 存入数据库初始状态
        jobRepository.recordDiscoveredJob(rawJob.toJobEntity(JobEntity.STATUS_DISCOVERED))

        // 3. DeepSeek 大模型深度语义打分
        sendAgentLog("🚀 呈送 DeepSeek 进行深度技术栈与风险推演...")
        val llmResult = deepSeekClient.evaluateJob(rawJob)

        return llmResult.fold(
            onSuccess = { rawResponse ->
                // 0. 清洗 LLM 输出的越界分数
                val response = rawResponse.sanitized()

                // 1. 四维加权合成综合分 (用户偏好权重 + HR 高活跃本地加分)
                val weights = ScorePolicy.weightsFor(configRepository.getScorePreference())
                val hrBonus = localPreFilter.computeHrActivityBonus(rawJob.hrActiveStatus)
                val compositeScore = ScorePolicy.composeFinalScore(response, weights, hrBonus)
                val threshold = configRepository.getMatchScoreThreshold()
                val result = EvaluatorResult.fromLLMResponse(rawJob, response, compositeScore, threshold)

                val status = if (result.isApproved) JobEntity.STATUS_EVALUATED else JobEntity.STATUS_REJECTED
                jobRepository.saveEvaluationResult(
                    jobId = jobId,
                    score = result.matchScore,
                    reason = result.summaryReason,
                    suggestedGreeting = "",
                    status = status,
                    techScore = response.techMatch,
                    experienceScore = response.experienceMatch,
                    salaryScore = response.salaryMatch,
                    stabilityScore = response.stability
                )

                val dimsText = if (response.hasFullDimensions()) {
                    val bonusText = if (hrBonus > 0) " +活跃${hrBonus}" else ""
                    " (技${response.techMatch}·验${response.experienceMatch}·薪${response.salaryMatch}·稳${response.stability}$bonusText)"
                } else ""
                val logText = if (result.isApproved) {
                    "🎯 评估通过！综合${result.matchScore}分$dimsText | ${result.summaryReason}"
                } else {
                    "⚠️ 评估放弃。综合${result.matchScore}分$dimsText | 原因: ${result.summaryReason}"
                }
                sendAgentLog(logText, isHighlight = result.isApproved)

                result
            },
            onFailure = { error ->
                val errReason = error.message ?: "网络请求失败"
                sendAgentLog("❌ 评估异常: $errReason")
                EvaluatorResult.fromError(rawJob, errReason)
            }
        )
    }
}
