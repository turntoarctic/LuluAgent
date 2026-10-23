package com.jobmate.app

import android.app.Application
import android.util.Log
import com.jobmate.app.agent.communicator.CommunicatorAgent
import com.jobmate.app.agent.evaluator.EvaluatorAgent
import com.jobmate.app.agent.scout.ScoutAgent
import com.jobmate.app.agent.supervisor.SupervisorAgent
import com.jobmate.app.data.local.AppDatabase
import com.jobmate.app.data.pref.AppSettings
import com.jobmate.app.data.pref.EncryptedDataStore
import com.jobmate.app.data.repository.ConfigRepository
import com.jobmate.app.data.repository.JobRepository
import com.jobmate.app.dispatcher.TaskDispatcher
import com.jobmate.app.dispatcher.fsm.StateMachine
import com.jobmate.app.llm.DeepSeekClient
import com.jobmate.app.llm.filter.LocalPreFilter
import com.jobmate.app.llm.limiter.TokenUsageTracker
import com.jobmate.app.llm.prompt.PromptManager

/**
 * 全局 Application：统一依赖图谱装配
 */
class JobmateApp : Application() {

   private val tag = "JobmateApp"

    // 全局持久化与仓储单例
    lateinit var database: AppDatabase private set
    lateinit var encryptedDataStore: EncryptedDataStore private set
    lateinit var appSettings: AppSettings private set
    lateinit var configRepository: ConfigRepository private set
    lateinit var jobRepository: JobRepository private set

    // 大模型认知层单例
    lateinit var deepSeekClient: DeepSeekClient private set
    lateinit var tokenUsageTracker: TokenUsageTracker private set
    lateinit var promptManager: PromptManager private set
    lateinit var localPreFilter: LocalPreFilter private set

    // 多智能体集群单例
    lateinit var stateMachine: StateMachine private set
    lateinit var supervisorAgent: SupervisorAgent private set
    lateinit var scoutAgent: ScoutAgent private set
    lateinit var evaluatorAgent: EvaluatorAgent private set
    lateinit var communicatorAgent: CommunicatorAgent private set

    // 调度总指挥中枢
    lateinit var taskDispatcher: TaskDispatcher private set

    companion object {
        lateinit var instance: JobmateApp private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(tag, "JobmateApp 进程启动，开始全链路依赖装配...")
        initStorageLayer()
        initCognitiveLayer()
        initAgentCluster()
        initDispatcher()

        Log.i(tag, "✅ JobmateApp 全系统依赖注入装配完成！")
    }

    private fun initStorageLayer() {
        database = AppDatabase.getInstance(this)
        encryptedDataStore = EncryptedDataStore.getInstance(this)
        appSettings = AppSettings.getInstance(this)
        configRepository = ConfigRepository(encryptedDataStore, appSettings)
        jobRepository = JobRepository(database)
    }

    private fun initCognitiveLayer() {
        tokenUsageTracker = TokenUsageTracker(jobRepository)
        promptManager = PromptManager(configRepository)
        localPreFilter = LocalPreFilter(jobRepository, configRepository)
        deepSeekClient = DeepSeekClient.getInstance(
            configRepository,
            jobRepository,
            tokenUsageTracker,
            promptManager
        )
    }

    private fun initAgentCluster() {
        stateMachine = StateMachine()
        supervisorAgent = SupervisorAgent(this)
        scoutAgent = ScoutAgent(this)
        evaluatorAgent = EvaluatorAgent(
            this,
            localPreFilter,
            deepSeekClient,
            jobRepository,
            configRepository
        )
        communicatorAgent = CommunicatorAgent(
            this,
            deepSeekClient,
            jobRepository,
            configRepository
        )
    }

    private fun initDispatcher() {
        taskDispatcher = TaskDispatcher(
            context = this,
            stateMachine = stateMachine,
            supervisorAgent = supervisorAgent,
            scoutAgent = scoutAgent,
            evaluatorAgent = evaluatorAgent,
            communicatorAgent = communicatorAgent,
            configRepository = configRepository,
            jobRepository = jobRepository
        )
    }
}
