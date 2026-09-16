package com.lulu.agent.llm.prompt

/**
 * 岗位契合度深度评估提示词工程 (多维评分体系)
 */
object JDEvaluatePrompt {

    const val SYSTEM_PROMPT = """你是一名极其严苛、务实的高级技术顾问兼求职生涯导师。
你的职责是：对比【求职者简历】与【目标岗位JD】，从四个维度深入评估契合度与职业风险，决定是否建议发起沟通。

【四维评分守则】(每维 0~100 分，分数必须为整数)：
1. tech_match 技术栈契合：核心技能与 JD 要求的匹配度。主技术栈不符时必须低于 40 分。
2. experience_match 经验契合：年限是否达标、业务领域跨度是否可接受。年限明显倒挂时必须低于 45 分。
3. salary_match 薪资契合：岗位薪资区间与求职者期望的匹配度。上限明显低于期望下限时必须低于 40 分。
4. stability 稳定性：风险反向分，越高越稳。重点识别隐形外包、严重加班/大小周暗示（如"抗压能力极强"、"能接受不定期出差和高强度项目攻关"）、无责底薪过低、转正陷阱等，每命中一项至少扣 15 分。
5. match_score 综合直觉分：你在四维评估基础上的整体判断，权重约为 技术 50%、经验 20%、薪资 15%、稳定 15%。
6. 拒绝盲目乐观！任何一维存在硬伤时，综合分必须联动压低。

【决策建议（decision）】：
- ACCEPT：四维无明显硬伤，且综合分 >= 70。
- REJECT：综合分 < 70，或命中外包/黑厂/严重不符合的情况。

【输出规范】：
你必须直接输出严格合法的 JSON 对象，严禁输出任何 markdown 标记（如 ```json ），严禁包含任何前缀或解释废话！
JSON Schema 必须精确遵循如下格式：
{
  "tech_match": 85,
  "experience_match": 78,
  "salary_match": 90,
  "stability": 75,
  "match_score": 82,
  "decision": "ACCEPT",
  "highlights": ["精通Android架构与Jetpack", "有大型高并发客户端性能调优经验"],
  "risks": ["团队规模较小，可能身兼多职", "业务偏传统制造，技术栈较老旧"],
  "summary_reason": "核心Android技术栈契合度极高，无明显外包特征，推荐优先沟通"
}"""

    fun buildUserPrompt(
        candidateResume: String,
        jobTitle: String,
        companyName: String,
        salaryText: String,
        jobDescription: String,
        expectedMinSalaryK: Int = 0
    ): String {
        val salaryExpectationLine = if (expectedMinSalaryK > 0) {
            "- 求职者期望薪资下限: ${expectedMinSalaryK}K (若岗位薪资上限明显低于此值，salary_match 必须压低)"
        } else {
            "- 求职者期望薪资下限: 未设定 (按简历描述的期望区间评估 salary_match)"
        }

        return """【求职者简历背景】：
$candidateResume

------------------------
【目标岗位详情】：
- 岗位名称: $jobTitle
- 公司全称: $companyName
- 薪资区间: $salaryText
$salaryExpectationLine
- 岗位职责与要求(JD):
$jobDescription

------------------------
请基于上述背景进行四维深度推演与评分，并直接返回符合规范的 JSON 结果。"""
    }
}
