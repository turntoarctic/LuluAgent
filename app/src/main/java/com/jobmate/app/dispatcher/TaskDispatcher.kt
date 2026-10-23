package com.jobmate.app.dispatcher

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.jobmate.app.accessibility.BossAccessibilityService
import com.jobmate.app.accessibility.model.PageScene
import com.jobmate.app.accessibility.model.ScrapedRawJob
import com.jobmate.app.accessibility.util.GestureEngine
import com.jobmate.app.agent.communicator.CommunicatorAgent
import com.jobmate.app.agent.evaluator.EvaluatorAgent
import com.jobmate.app.agent.scout.ScoutAgent
import com.jobmate.app.agent.supervisor.SupervisorAgent
import com.jobmate.app.data.repository.ConfigRepository
import com.jobmate.app.data.repository.JobRepository
import com.jobmate.app.dispatcher.contract.DispatcherBroadcasts
import com.jobmate.app.dispatcher.fsm.EngineState
import com.jobmate.app.dispatcher.fsm.StateMachine
import com.jobmate.app.dispatcher.receiver.AccessibilityFeedbackReceiver
import com.jobmate.app.dispatcher.receiver.SupervisorControlReceiver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ThreadLocalRandom

/**
 * 任务调度中枢大脑 (强化自愈守护、WelcomeActivity显式唤醒与前置去重版)
 */
class TaskDispatcher(
    private val context: Context,
    private val stateMachine: StateMachine,
    private val supervisorAgent: SupervisorAgent,
    private val scoutAgent: ScoutAgent,
    private val evaluatorAgent: EvaluatorAgent,
    private val communicatorAgent: CommunicatorAgent,
    private val configRepository: ConfigRepository,
    private val jobRepository: JobRepository
) {

    private val tag = "TaskDispatcher"
    private var dispatcherScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var orchestratorJob: Job? = null

    private val feedbackReceiver = AccessibilityFeedbackReceiver()
    private val controlReceiver = SupervisorControlReceiver()

    // 会话内存去重集合 (记录 "公司名_职位名")
    private val processedJobKeys = mutableSetOf<String>()

    // 当前感知的视觉场景
    @Volatile
    private var currentScene: PageScene = PageScene.UNKNOWN

    companion object {
        private const val BOSS_PKG = "com.hpbr.bosszhipin"
        // 目标公开导出的 WelcomeActivity
        private const val BOSS_WELCOME_ACTIVITY = "com.hpbr.bosszhipin.module.launcher.WelcomeActivity"

        // 用户通过 HUD 控制面板手动拉停时的 riskType 标记 (非风控熔断，无需驻留 EMERGENCY_STOP)
        private const val RISK_TYPE_MANUAL = "MANUAL"

        // 前置去重键的数据库预载窗口：仅回看近 7 天已终结岗位
        private const val PROCESSED_SEED_WINDOW_MS = 7 * 24 * 60 * 60 * 1000L

        // 真实 UI 锚点 ID
        private const val ID_TAB_JOB = "com.hpbr.bosszhipin:id/cl_tab_1"
        private const val ID_TAB_LABEL = "com.hpbr.bosszhipin:id/tv_tab_label"
    }

    init {
        setupStateListener()
        setupReceivers()
    }

    private fun setupStateListener() {
        stateMachine.addListener { oldState, newState, reason ->
            Log.i(tag, "引擎状态跃迁: $oldState -> $newState ($reason)")
            val intent = Intent(DispatcherBroadcasts.ACTION_ENGINE_STATE_CHANGED).apply {
                putExtra(DispatcherBroadcasts.EXTRA_OLD_STATE, oldState.name)
                putExtra(DispatcherBroadcasts.EXTRA_NEW_STATE, newState.name)
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
        }
    }

    private fun setupReceivers() {
        feedbackReceiver.onSceneChanged = { scene ->
            Log.d(tag, "感知到视觉场景变化: $scene")
            currentScene = scene
        }

        controlReceiver.onStartCommand = { start() }
        controlReceiver.onStopCommand = { stop() }
        controlReceiver.onPauseCommand = { pause() }
        controlReceiver.onResumeCommand = { resume() }
        controlReceiver.onEmergencyStopCommand = { riskType, reason ->
            emergencyStop(riskType, reason)
        }

        val exportFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Context.RECEIVER_NOT_EXPORTED
        } else 0

        context.registerReceiver(feedbackReceiver, AccessibilityFeedbackReceiver.createIntentFilter(), exportFlag)
        context.registerReceiver(controlReceiver, SupervisorControlReceiver.createIntentFilter(), exportFlag)
    }

    fun start(warmUpDelayMs: Long = 4000L) {
        if (stateMachine.getCurrentState() == EngineState.EMERGENCY_STOP) {
            Log.w(tag, "引擎处于风控熔断状态，需人工确认恢复后才能启动")
            broadcastStepLog("风控熔断尚未解除，请在控制台确认恢复后再启动")
            return
        }

        if (stateMachine.getCurrentState().isOperating()) {
            Log.w(tag, "调度器已经在运行中")
            return
        }

        Log.i(tag, "启动 TaskDispatcher 工作流...")
        dispatcherScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        supervisorAgent.start()
        scoutAgent.start()
        evaluatorAgent.start()
        communicatorAgent.start()

        safeTransitionTo(EngineState.SCANNING, "流水线启动")
        configRepository.setPaused(false)

        orchestratorJob = dispatcherScope.launch {
            // 跨会话持久化去重：预载数据库近期已终结岗位，重启后不再重复访问老岗位
            seedProcessedJobKeys()

            // 启动时显式拉起 WelcomeActivity 并复位到 Boss 直聘首页推荐列表
            resetToRecommendFeed()

            if (warmUpDelayMs > 0) {
                val totalSeconds = (warmUpDelayMs / 1000).toInt()
                for (s in totalSeconds downTo 1) {
                    broadcastStepLog("正在就绪 Boss 推荐流，将在 ${s}s 后开启寻岗...")
                    delay(1000L)
                }
            }

            runOrchestrationLoop()
        }
    }

    fun stop() {
        Log.w(tag, "停止 TaskDispatcher 工作流")
        haltWorkflow()
        safeTransitionTo(EngineState.IDLE, "终止任务")
    }

    /**
     * 停掉编排协程与子 Agent，但不改变 FSM 状态 (供 stop 与 emergencyStop 复用)
     */
    private fun haltWorkflow() {
        orchestratorJob?.cancel()
        orchestratorJob = null

        supervisorAgent.stop()
        scoutAgent.stop()
        evaluatorAgent.stop()
        communicatorAgent.stop()

        processedJobKeys.clear()
        dispatcherScope.cancel()
    }

    /**
     * 紧急停止：区分「用户手动拉停」与「风控自动熔断」。
     * - 手动拉停：等同普通终止，直接归位 IDLE；
     * - 风控熔断：驻留 EMERGENCY_STOP 红灯，由用户在控制台人工确认后恢复，
     * 避免 UI 上熔断状态一闪而过、以及风控刚触发就能无冷却继续投递。
     */
    fun emergencyStop(riskType: String, reason: String) {
        if (riskType == RISK_TYPE_MANUAL) {
            stop()
            return
        }

        safeTransitionTo(EngineState.EMERGENCY_STOP, "安全风控熔断: $reason")
        haltWorkflow()
        broadcastStepLog("已触发风控熔断 ($riskType)：$reason。请先到 Boss 直聘确认账号状态，再在控制台解除熔断。")
    }

    /**
     * 人工确认解除风控熔断 (FSM 规则：EMERGENCY_STOP 只能回归 IDLE)
     */
    fun recoverFromEmergencyStop() {
        if (stateMachine.getCurrentState() != EngineState.EMERGENCY_STOP) return
        stateMachine.transitionTo(EngineState.IDLE, "人工确认解除熔断")
        broadcastStepLog("风控熔断已人工确认解除，随时可以重新出发")
    }

    /**
     * 跨会话持久化去重：启动时从数据库预载近期已终结岗位的「公司_职位」键，
     * 让前置秒级去重在重启后依然生效，避免重复点击与重复评估
     */
    private suspend fun seedProcessedJobKeys() {
        runCatching {
            val since = System.currentTimeMillis() - PROCESSED_SEED_WINDOW_MS
            val settledJobs = jobRepository.getRecentlySettledJobs(since)
            settledJobs.forEach { job ->
                processedJobKeys.add(buildJobKey(job.companyName, job.title))
            }
            Log.i(tag, "已从数据库预载 ${settledJobs.size} 条历史岗位用于前置去重")
        }.onFailure {
            Log.w(tag, "预载历史岗位去重键失败: ${it.message}")
        }
    }

    fun pause() {
        if (stateMachine.transitionTo(EngineState.PAUSED, "暂停指令")) {
            configRepository.setPaused(true)
            broadcastStepLog("流水线已挂起")
        }
    }

    fun resume() {
        if (stateMachine.transitionTo(EngineState.SCANNING, "恢复执行")) {
            configRepository.setPaused(false)
            broadcastStepLog("流水线已恢复")
        }
    }

    private suspend fun runOrchestrationLoop() {
        while (dispatcherScope.isActive) {
            if (stateMachine.getCurrentState() == EngineState.PAUSED) {
                delay(1000L)
                continue
            }

            if (!stateMachine.getCurrentState().isOperating()) {
                break
            }

            // 1. 每日配额校验
            val maxLimit = configRepository.getMaxDailyGreetings()
            if (!jobRepository.canGreetMoreToday(maxLimit)) {
                broadcastStepLog("今日打招呼已达最大上限 ($maxLimit 次)，任务圆满完成！")
                stop()
                break
            }

            // 2. 核心守护：扫描前必须确保处于【推荐职位列表】
            ensureInRecommendList()
            safeTransitionTo(EngineState.SCANNING, "扫描当前屏幕卡片")

            // 3. 扫描屏幕可见卡片
            var cardAnchors = emptyList<ScoutAgent.CardAnchor>()
            var waitListSeconds = 0
            while (dispatcherScope.isActive && waitListSeconds < 8) {
                cardAnchors = scoutAgent.scanCurrentFeedCards()
                if (cardAnchors.isNotEmpty()) break
                broadcastStepLog("等待职位列表卡片呈现 (${waitListSeconds + 1}s)...")
                delay(1000L)
                waitListSeconds++
            }

            // 4. 若当前屏幕没有卡片，上滑翻页
            if (cardAnchors.isEmpty()) {
                broadcastStepLog("未发现有效卡片，上滑加载新内容...")
                performScrollFeed(allowTabSwitch = false)
                humanDelay(1500L, 300L)
                continue
            }

            // 5. 前置秒级去重：在点击前直接过滤已看过的岗位
            val unvisitedCards = cardAnchors.filter { card ->
                val key = buildJobKey(card.previewCompany, card.previewTitle)
                !processedJobKeys.contains(key)
            }

            if (unvisitedCards.isEmpty()) {
                broadcastStepLog("当前屏幕卡片均已检视，上滑浏览下一页...")
                performScrollFeed(allowTabSwitch = true)
                humanDelay(1500L, 300L)
                continue
            }

            // 6. 逐个处理未访问的卡片
            for (card in unvisitedCards) {
                if (!dispatcherScope.isActive || !stateMachine.getCurrentState().isOperating()) break

                while (stateMachine.getCurrentState() == EngineState.PAUSED) {
                    delay(1000L)
                }

                val cardKey = buildJobKey(card.previewCompany, card.previewTitle)
                processedJobKeys.add(cardKey)

                ensureInRecommendList()

                // 阶段一：进入详情页抓取 JD
                safeTransitionTo(EngineState.INSPECTING, "查看岗位详情")
                val scrapeDeferred = CompletableDeferred<ScrapedRawJob?>()

                scoutAgent.inspectJobDetail(
                    cardBounds = card.bounds,
                    onSuccess = { scrapedJob -> scrapeDeferred.complete(scrapedJob) },
                    onFailed = { scrapeDeferred.complete(null) }
                )

                val job = scrapeDeferred.await()
                if (job == null) {
                    broadcastStepLog("岗位详情提取失败，安全回退")
                    ensureInRecommendList()
                    continue
                }

                processedJobKeys.add(job.resolveJobId())

                // 阶段二：DeepSeek 评估
                safeTransitionTo(EngineState.THINKING, "DeepSeek 评估中")
                broadcastStepLog("DeepSeek 正在评估【${job.companyName} - ${job.title}】...")
                val evalResult = evaluatorAgent.evaluate(job)

                humanDelay(1000L, 200L)

                // 阶段三：决策闭环分流
                if (evalResult.isApproved) {
                    safeTransitionTo(EngineState.COMMUNICATING, "发起打招呼破冰")
                    broadcastStepLog("契合度达标 (${evalResult.matchScore}分)，立即发起破冰沟通...")

                    val greetDeferred = CompletableDeferred<Boolean>()
                    communicatorAgent.executeGreeting(
                        rawJob = job,
                        evaluatorResult = evalResult,
                        onSuccess = { greetDeferred.complete(true) },
                        onFailed = { greetDeferred.complete(false) }
                    )

                    greetDeferred.await()
                    humanDelay(1500L, 300L)
                } else {
                    broadcastStepLog("契合度不足 (${evalResult.matchScore}分)，跳过该岗位")
                }

                // 阶段四：安全退回推荐列表
                ensureInRecommendList()
                humanDelay(800L, 200L)
            }

            // 7. 当前屏所有新卡片消费完毕，上滑翻页
            if (dispatcherScope.isActive && stateMachine.getCurrentState().isOperating()) {
                broadcastStepLog("当前一屏已巡查完毕，上滑刷新职位流...")
                performScrollFeed(allowTabSwitch = true)
                humanDelay(1500L, 300L)
            }
        }
    }

    /**
     * 自愈场景守卫：优先后退；后退 2 次仍未脱困，直接启动 WelcomeActivity 复位
     */
    private suspend fun ensureInRecommendList(maxAttempts: Int = 3) {
        val service = BossAccessibilityService.instance ?: return
        var attempts = 0

        while (dispatcherScope.isActive && attempts < maxAttempts) {
            if (currentScene == PageScene.RECOMMEND_LIST) {
                return
            }

            Log.w(tag, "检测到未在推荐列表 (当前: $currentScene)，执行安全后退自愈 (第 ${attempts + 1} 次)...")
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            humanDelay(800L, 150L)
            attempts++
        }

        if (currentScene != PageScene.RECOMMEND_LIST) {
            Log.e(tag, "连续后退未恢复，启动 WelcomeActivity 显式复位至推荐首页")
            resetToRecommendFeed()
        }
    }

    /**
     * 【显式组件复位】：通过 ComponentName 直接唤醒 WelcomeActivity 并校准 Tab
     */
    private suspend fun resetToRecommendFeed() {
        broadcastStepLog("正在执行基准线复位：定向拉起 WelcomeActivity...")

        // 显式指定拉起公开的 WelcomeActivity
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(BOSS_PKG, BOSS_WELCOME_ACTIVITY)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(tag, "显式拉起 WelcomeActivity 失败: ${e.message}")
        }

        // 预留 1200ms 等待 Boss 前台就绪
        humanDelay(1200L, 200L)

        val service = BossAccessibilityService.instance ?: return

        // 清理顶层多余的子页面（如详情或聊天）
        var backPopTries = 0
        while (dispatcherScope.isActive && backPopTries < 3) {
            val scene = currentScene
            if (scene == PageScene.JOB_DETAIL || scene == PageScene.CHAT_WINDOW) {
                Log.d(tag, "复位过程清理顶层页面: $scene，执行返回")
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                humanDelay(600L, 100L)
                backPopTries++
            } else {
                break
            }
        }

        // UI 锚点校准：校准底部 Tab 1【职位】
        val root = service.rootInActiveWindow ?: return
        try {
            val tab1Nodes = root.findAccessibilityNodeInfosByViewId(ID_TAB_JOB)
            val tab1 = tab1Nodes?.firstOrNull()
            if (tab1 != null && !tab1.isSelected) {
                Log.d(tag, "校准底部导航：点击【职位】Tab")
                GestureEngine.performClick(service, tab1)
                humanDelay(600L, 100L)
            }
            tab1Nodes?.forEach { it.recycle() }

            // UI 锚点校准：校准顶部子 Tab【推荐】
            val freshRoot = service.rootInActiveWindow ?: return
            val tabLabels = freshRoot.findAccessibilityNodeInfosByViewId(ID_TAB_LABEL)
            val recommendTab = tabLabels?.firstOrNull { it.text?.toString() == "推荐" }
            if (recommendTab != null && !recommendTab.isSelected) {
                Log.d(tag, "校准顶部子导航：点击【推荐】")
                GestureEngine.performClick(service, recommendTab)
                humanDelay(800L, 150L)
            }
            tabLabels?.forEach { it.recycle() }
            freshRoot.recycle()

            Log.i(tag, "成功复位并锚定在 Boss 直聘【职位 -> 推荐】流！")
        } finally {
            root.recycle()
        }
    }

    private suspend fun performScrollFeed(allowTabSwitch: Boolean) {
        val scrollDeferred = CompletableDeferred<Boolean>()
        scoutAgent.scrollNextPage(
            allowTabSwitch = allowTabSwitch,
            onCompleted = { scrollDeferred.complete(true) },
            onFailed = { scrollDeferred.complete(false) }
        )
        scrollDeferred.await()
    }

    private fun safeTransitionTo(targetState: EngineState, reason: String) {
        if (stateMachine.getCurrentState() != targetState) {
            stateMachine.transitionTo(targetState, reason)
        }
    }

    private fun buildJobKey(company: String, title: String): String {
        return "${company.trim()}_${title.trim()}"
    }

    private fun broadcastStepLog(message: String) {
        val intent = Intent(DispatcherBroadcasts.ACTION_TASK_STEP_MESSAGE).apply {
            putExtra(DispatcherBroadcasts.EXTRA_TASK_NAME, "调度中枢")
            putExtra(DispatcherBroadcasts.EXTRA_STEP_INFO, message)
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }

    private suspend fun humanDelay(baseMs: Long, varianceMs: Long = 300L) {
        val speedFactor = configRepository.getSpeedFactor()
        val adjustedBase = (baseMs * speedFactor).toLong()
        val jitter = ThreadLocalRandom.current().nextLong(-varianceMs, varianceMs)
        val finalDelay = maxOf(300L, adjustedBase + jitter)
        delay(finalDelay)
    }

    fun destroy() {
        stop()
        try {
            context.unregisterReceiver(feedbackReceiver)
            context.unregisterReceiver(controlReceiver)
        } catch (e: Exception) {
            Log.e(tag, "注销中枢广播异常: ${e.message}")
        }
        dispatcherScope.cancel()
    }
}