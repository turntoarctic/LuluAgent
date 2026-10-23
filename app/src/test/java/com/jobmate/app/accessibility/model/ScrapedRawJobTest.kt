package com.jobmate.app.accessibility.model

import com.jobmate.app.data.local.entity.JobEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrapedRawJobTest {

    // ==================== 薪资解析 ====================

    @Test
    fun `标准K区间解析`() {
        val job = ScrapedRawJob(salaryText = "25-35K·14薪")
        assertEquals(25, job.parseSalaryMin())
        assertEquals(35, job.parseSalaryMax())
    }

    @Test
    fun `小写k与波浪号区间解析`() {
        val job = ScrapedRawJob(salaryText = "8~13k")
        assertEquals(8, job.parseSalaryMin())
        assertEquals(13, job.parseSalaryMax())
    }

    @Test
    fun `面议或无薪资解析为0`() {
        val job = ScrapedRawJob(salaryText = "面议")
        assertEquals(0, job.parseSalaryMin())
        assertEquals(0, job.parseSalaryMax())
    }

    @Test
    fun `空薪资解析为0`() {
        val job = ScrapedRawJob(salaryText = "")
        assertEquals(0, job.parseSalaryMin())
        assertEquals(0, job.parseSalaryMax())
    }

    // ==================== JobId 指纹 ====================

    @Test
    fun `显式jobId优先于指纹`() {
        val job = ScrapedRawJob(jobId = "boss-12345", title = "Java开发", companyName = "某公司")
        assertEquals("boss-12345", job.resolveJobId())
    }

    @Test
    fun `无jobId时生成稳定指纹`() {
        val a = ScrapedRawJob(title = "Java开发", companyName = "某公司", salaryText = "25-35K")
        val b = ScrapedRawJob(title = "Java开发", companyName = "某公司", salaryText = "25-35K")
        assertEquals(a.resolveJobId(), b.resolveJobId())
    }

    @Test
    fun `不同岗位指纹不同`() {
        val a = ScrapedRawJob(title = "Java开发", companyName = "A公司")
        val b = ScrapedRawJob(title = "Java开发", companyName = "B公司")
        assertNotEquals(a.resolveJobId(), b.resolveJobId())
    }

    @Test
    fun `标题公司首尾空白不影响指纹`() {
        val a = ScrapedRawJob(title = "Java开发", companyName = "某公司")
        val b = ScrapedRawJob(title = " Java开发 ", companyName = " 某公司 ")
        assertEquals(a.resolveJobId(), b.resolveJobId())
    }

    // ==================== 有效性 ====================

    @Test
    fun `标题与公司齐全才算有效`() {
        assertTrue(ScrapedRawJob(title = "开发", companyName = "某公司").isValid())
        assertFalse(ScrapedRawJob(title = "", companyName = "某公司").isValid())
        assertFalse(ScrapedRawJob(title = "开发", companyName = "").isValid())
    }

    // ==================== 转 JobEntity ====================

    @Test
    fun `转JobEntity字段正确映射`() {
        val raw = ScrapedRawJob(
            title = " Android开发 ",
            companyName = " 某科技公司 ",
            salaryText = "25-35K",
            city = " 深圳 ",
            hrActiveStatus = "刚刚活跃",
            jobDescription = " 负责 App 架构 "
        )
        val entity = raw.toJobEntity(JobEntity.STATUS_DISCOVERED)

        assertEquals(raw.resolveJobId(), entity.jobId)
        assertEquals("Android开发", entity.title)
        assertEquals("某科技公司", entity.companyName)
        assertEquals(25, entity.salaryMin)
        assertEquals(35, entity.salaryMax)
        assertEquals("深圳", entity.city)
        assertEquals(JobEntity.STATUS_DISCOVERED, entity.status)
    }
}
