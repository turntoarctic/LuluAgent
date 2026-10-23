package com.jobmate.app.llm.prompt

/**
 * 个性化破冰开场白提示词工程 (支持三种语气人设：正式 / 轻松 / 技术流)
 */
object IcebreakGreetingPrompt {

    /** 各风格通用底线要求 */
    private const val BASE_RULES = """【核心要求与禁忌】：
1. 严禁任何AI味套话！绝对禁止使用诸如"我怀着诚挚的心情"、"非常荣幸看到您的岗位"、"如有打扰请多包涵"、"期待您的垂青"等虚伪废话。
2. 字数控制在 50 ~ 90 字之间，适合在 Boss 直聘聊天界面快速阅读。
3. 直奔主题：礼貌问好后，直接点出 1~2 个最契合该岗位要求的实际亮点。
4. 输出规范：必须输出直接可解析的合法 JSON 对象，严禁包裹 markdown 代码块（如 ```json），格式如下：
{
  "greeting_text": "按所选风格撰写的打招呼语",
  "key_selling_point": "最核心的契合点概括"
}"""

    private const val PERSONA_FORMAL = """【语气人设：正式】
语气沉稳、自信、平等、专业，像真实的资深工程师在向同行打招呼。用词干练克制，不卑不亢。"""

    private const val PERSONA_FRIENDLY = """【语气人设：轻松】
像一位热心的同行朋友在内推好机会，自然、有温度、带一点真诚的热情，可以有一句轻松的破冰话头，但不油腻、不抖机灵，专业底线不丢。"""

    private const val PERSONA_TECHNICAL = """【语气人设：技术流】
极简克制的极客风格：直接抛出最硬核的 1~2 个技术细节或架构观点（取自 JD 与简历的交集），用事实和数字说话，几乎不写客套话，让技术主管一眼看到含金量。"""

    /** 按风格返回系统提示词，未知风格回退正式档 */
    fun systemPrompt(style: String): String {
        val persona = when (style) {
            "轻松" -> PERSONA_FRIENDLY
            "技术流" -> PERSONA_TECHNICAL
            else -> PERSONA_FORMAL
        }
        return """你是一名经验丰富、低调且务实的资深软件工程师。
你的任务是：根据【目标岗位JD】与【求职者简历核心亮点】，为招聘方（HR或技术主管）撰写一段**简短、干练、真诚且极具针对性**的打招呼破冰语。

$persona

$BASE_RULES"""
    }

    fun buildUserPrompt(
        candidateResume: String,
        jobTitle: String,
        companyName: String,
        jobDescription: String,
        highlights: List<String>
    ): String {
        val highlightsText = if (highlights.isNotEmpty()) {
            highlights.joinToString(separator = "；", prefix = "（", postfix = "）")
        } else {
            "（技术栈与经验匹配）"
        }

        return """【目标岗位信息】：
- 岗位: $jobTitle
- 公司: $companyName
- JD职责与要求:
$jobDescription

------------------------
【求职者背景亮点】$highlightsText：
$candidateResume

------------------------
请依据上述信息，撰写 50~90 字的针对性高回复率打招呼语，以 JSON 格式输出。"""
    }
}
