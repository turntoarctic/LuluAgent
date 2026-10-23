package com.jobmate.app.dispatcher.fsm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StateMachineTest {

    private lateinit var machine: StateMachine

    @Before
    fun setUp() {
        machine = StateMachine()
    }

    // ==================== 正向流转 ====================

    @Test
    fun `初始状态为IDLE`() {
        assertEquals(EngineState.IDLE, machine.getCurrentState())
    }

    @Test
    fun `IDLE只能进入SCANNING`() {
        assertFalse(machine.canTransitionTo(EngineState.THINKING))
        assertFalse(machine.canTransitionTo(EngineState.INSPECTING))
        assertTrue(machine.canTransitionTo(EngineState.SCANNING))
    }

    @Test
    fun `核心链路完整流转`() {
        assertTrue(machine.transitionTo(EngineState.SCANNING))
        assertTrue(machine.transitionTo(EngineState.INSPECTING))
        assertTrue(machine.transitionTo(EngineState.THINKING))
        assertTrue(machine.transitionTo(EngineState.COMMUNICATING))
        assertTrue(machine.transitionTo(EngineState.SCANNING))
    }

    @Test
    fun `工作态可随时暂停`() {
        machine.transitionTo(EngineState.SCANNING)
        assertTrue(machine.transitionTo(EngineState.PAUSED))
        assertTrue(machine.transitionTo(EngineState.SCANNING))
    }

    // ==================== 非法流转拦截 ====================

    @Test
    fun `相同状态不重复流转`() {
        machine.transitionTo(EngineState.SCANNING)
        assertFalse(machine.transitionTo(EngineState.SCANNING))
        assertEquals(EngineState.SCANNING, machine.getCurrentState())
    }

    @Test
    fun `跳跃流转被拦截`() {
        machine.transitionTo(EngineState.SCANNING)
        // SCANNING 不能直接到 COMMUNICATING，必须经过 INSPECTING/THINKING
        assertFalse(machine.transitionTo(EngineState.COMMUNICATING))
        assertEquals(EngineState.SCANNING, machine.getCurrentState())
    }

    @Test
    fun `暂停态不能直接进入工作子状态`() {
        machine.transitionTo(EngineState.SCANNING)
        machine.transitionTo(EngineState.PAUSED)
        assertFalse(machine.transitionTo(EngineState.INSPECTING))
        assertTrue(machine.transitionTo(EngineState.SCANNING))
    }

    // ==================== 风控熔断最高优先级 ====================

    @Test
    fun `任意状态可无条件进入熔断`() {
        for (state in listOf(EngineState.SCANNING, EngineState.THINKING, EngineState.PAUSED)) {
            val m = StateMachine()
            m.transitionTo(state)
            assertTrue("从 $state 应可进入熔断", m.transitionTo(EngineState.EMERGENCY_STOP, "测试"))
            assertEquals(EngineState.EMERGENCY_STOP, m.getCurrentState())
        }
    }

    @Test
    fun `熔断后仅允许人工回归IDLE`() {
        machine.transitionTo(EngineState.SCANNING)
        machine.transitionTo(EngineState.EMERGENCY_STOP)

        assertFalse(machine.transitionTo(EngineState.SCANNING))
        assertFalse(machine.transitionTo(EngineState.PAUSED))
        assertTrue(machine.transitionTo(EngineState.IDLE))
        assertEquals(EngineState.IDLE, machine.getCurrentState())
    }

    // ==================== 监听器 ====================

    @Test
    fun `监听器收到状态跃迁通知`() {
        var receivedOld: EngineState? = null
        var receivedNew: EngineState? = null
        machine.addListener { old, new, _ ->
            receivedOld = old
            receivedNew = new
        }

        machine.transitionTo(EngineState.SCANNING, "测试")

        assertEquals(EngineState.IDLE, receivedOld)
        assertEquals(EngineState.SCANNING, receivedNew)
    }

    @Test
    fun `非法流转不触发监听器`() {
        var notified = false
        machine.addListener { _, _, _ -> notified = true }

        machine.transitionTo(EngineState.THINKING) // IDLE 直达 THINKING 非法

        assertFalse(notified)
    }
}
