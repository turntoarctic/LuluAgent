package com.lulu.agent.dispatcher.fsm

/**
 * 自动化引擎有限状态机 (FSM) 核心状态
 */
enum class EngineState(
    val title: String,
    val isHalted: Boolean,
    val indicatorColorHex: String
) {
    /** 初始就绪/空闲状态 (灰灯) */
    IDLE("待命中", false, "#9E9E9E"),

    /** 侦察中：在推荐列表滚动翻页搜寻 (蓝灯) */
    SCANNING("正在寻找好岗位", false, "#2196F3"),

    /** 详查中：已进入详情页，正在抓取全量 JD (青灯) */
    INSPECTING("正在细看岗位要求", false, "#00BCD4"),

    /** 思考中：DeepSeek 大模型正在进行语义打分与话术构建 (紫灯) */
    THINKING("AI 正在研判匹配度", false, "#9C27B0"),

    /** 交互中：正在自动点击沟通并填入破冰语 (绿灯) */
    COMMUNICATING("正在帮你打招呼", false, "#4CAF50"),

    /** 暂停态：求职者主动点击暂停或等待冷却 (蓝灰灯) */
    PAUSED("已暂停休息", true, "#78909C"),

    /** 熔断急停：命中滑块验证码或风控限制 (玫红灯闪烁) */
    EMERGENCY_STOP("风控急刹 · 待确认", true, "#E11D48");

    fun isOperating(): Boolean = !isHalted && this != IDLE
}
