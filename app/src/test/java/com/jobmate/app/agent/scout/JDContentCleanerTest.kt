package com.jobmate.app.agent.scout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JDContentCleanerTest {

    @Test
    fun `空与空白输入返回空串`() {
        assertEquals("", JDContentCleaner.clean(null))
        assertEquals("", JDContentCleaner.clean(""))
        assertEquals("", JDContentCleaner.clean("   \n  \n "))
    }

    @Test
    fun `平台垃圾声明被移除`() {
        val raw = "岗位职责：负责App开发。\nBOSS直聘严厉打击虚假招聘\n联系我时请说明是在BOSS直聘上看到的"
        val cleaned = JDContentCleaner.clean(raw)
        assertTrue(!cleaned.contains("严厉打击"))
        assertTrue(!cleaned.contains("联系我时请说明"))
        assertTrue(cleaned.contains("负责App开发"))
    }

    @Test
    fun `CRLF统一为LF`() {
        val cleaned = JDContentCleaner.clean("第一行\r\n第二行\r第三行")
        assertEquals("第一行\n第二行\n第三行", cleaned)
    }

    @Test
    fun `连续空格压缩为单个空格`() {
        val cleaned = JDContentCleaner.clean("Java   开发    工程师")
        assertEquals("Java 开发 工程师", cleaned)
    }

    @Test
    fun `三个以上换行压缩为两个`() {
        val cleaned = JDContentCleaner.clean("段落A\n\n\n\n\n段落B")
        assertEquals("段落A\n\n段落B", cleaned)
    }

    @Test
    fun `岗位职责与任职要求正确分段`() {
        val jd = "岗位描述\n岗位职责\n1. 负责开发\n2. 参与设计\n任职要求\n3. 三年经验\n4. 熟悉Kotlin"
        val (responsibilities, requirements) = JDContentCleaner.extractSections(jd)

        assertTrue(responsibilities.contains("1. 负责开发"))
        assertTrue(responsibilities.contains("2. 参与设计"))
        assertTrue(!responsibilities.contains("三年经验"))

        assertTrue(requirements.contains("3. 三年经验"))
        assertTrue(requirements.contains("4. 熟悉Kotlin"))
        assertTrue(!requirements.contains("负责开发"))
    }

    @Test
    fun `无分段标记时全部归入职责段`() {
        val (responsibilities, requirements) = JDContentCleaner.extractSections("只有一段描述\n没有分段标记")
        assertTrue(responsibilities.contains("只有一段描述"))
        assertEquals("", requirements)
    }
}
