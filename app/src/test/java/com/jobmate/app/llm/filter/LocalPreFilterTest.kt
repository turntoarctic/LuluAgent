package com.jobmate.app.llm.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPreFilterTest {

    // ==================== 关键词解析 ====================

    @Test
    fun `多种分隔符混合解析`() {
        val keywords = LocalPreFilter.parseKeywordList("996，销售驻点;借调、加班多\n长期出差")
        assertEquals(listOf("996", "销售驻点", "借调", "加班多", "长期出差"), keywords)
    }

    @Test
    fun `空白与空段被过滤`() {
        val keywords = LocalPreFilter.parseKeywordList("  996 ,, ，、  外包  ")
        assertEquals(listOf("996", "外包"), keywords)
    }

    @Test
    fun `空输入返回空列表`() {
        assertTrue(LocalPreFilter.parseKeywordList("").isEmpty())
        assertTrue(LocalPreFilter.parseKeywordList("，，、；").isEmpty())
    }

    // ==================== HR 活跃度加分 ====================

    @Test
    fun `高活跃HR加2分`() {
        assertEquals(2, LocalPreFilter.computeHrActivityBonus("刚刚活跃"))
        assertEquals(2, LocalPreFilter.computeHrActivityBonus("在线"))
        assertEquals(2, LocalPreFilter.computeHrActivityBonus("今日活跃"))
    }

    @Test
    fun `普通活跃度不加分`() {
        assertEquals(0, LocalPreFilter.computeHrActivityBonus("本月活跃"))
        assertEquals(0, LocalPreFilter.computeHrActivityBonus(""))
        assertEquals(0, LocalPreFilter.computeHrActivityBonus("3年前活跃"))
    }
}
