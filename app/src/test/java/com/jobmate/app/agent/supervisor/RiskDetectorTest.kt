package com.jobmate.app.agent.supervisor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RiskDetectorTest {

    @Test
    fun `滑块验证码关键词命中`() {
        val result = RiskDetector.detectFromText("请完成安全验证，拖动滑块完成拼图")
        assertNotNull(result)
        assertEquals(RiskDetector.RiskType.CAPTCHA_SLIDER, result!!.type)
    }

    @Test
    fun `人脸识别关键词命中`() {
        val result = RiskDetector.detectFromText("根据监管要求，请进行人脸验证")
        assertNotNull(result)
        assertEquals(RiskDetector.RiskType.FACE_RECOGNITION, result!!.type)
    }

    @Test
    fun `操作频繁关键词命中`() {
        val result = RiskDetector.detectFromText("您的操作过于频繁，请稍后再试")
        assertNotNull(result)
        assertEquals(RiskDetector.RiskType.FREQUENT_OPERATION, result!!.type)
    }

    @Test
    fun `账号限制关键词命中`() {
        val result = RiskDetector.detectFromText("很抱歉，账号已被限制登录")
        assertNotNull(result)
        assertEquals(RiskDetector.RiskType.ACCOUNT_RESTRICTED, result!!.type)
    }

    @Test
    fun `普通文本不误报`() {
        assertNull(RiskDetector.detectFromText("高级Java开发工程师，25-35K，双休不加班"))
        assertNull(RiskDetector.detectFromText(""))
        assertNull(RiskDetector.detectFromText("   "))
    }

    @Test
    fun `检测携带命中关键词`() {
        val result = RiskDetector.detectFromText("拖动滑块完成拼图")
        assertEquals("拖动滑块完成拼图", result!!.matchedKeyword)
    }
}
