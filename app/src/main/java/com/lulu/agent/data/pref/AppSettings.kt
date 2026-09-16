package com.lulu.agent.data.pref

import android.content.Context
import android.content.SharedPreferences

class AppSettings(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    /**
     * 获取速度倍率因子 (移植并适配 BaseComplexTask 逻辑)
     * 快速 -> 延时变短 (x0.75)
     * 正常 -> 延时不变 (x1.0)
     * 慢速 -> 延时变长 (x1.5)
     */
    fun getSpeedFactor(): Float {
        return when (getSlideRate()) {
            "快速", "较快" -> 0.75f
            "慢速", "较慢", "极慢" -> 1.5f
            "正常" -> 1.0f
            else -> 1.0f
        }
    }

    fun getAdjustedDelay(originDelay: Long): Long {
        return (originDelay * getSpeedFactor()).toLong()
    }

    fun getSlideRate(): String = prefs.getString(KEY_SLIDE_RATE, "正常") ?: "正常"
    fun setSlideRate(rate: String) = prefs.edit().putString(KEY_SLIDE_RATE, rate).apply()

    fun getMaxDailyGreetings(): Int = prefs.getInt(KEY_MAX_DAILY_GREETINGS, 45)
    fun setMaxDailyGreetings(count: Int) = prefs.edit().putInt(KEY_MAX_DAILY_GREETINGS, count).apply()

    fun getMinSalaryFilterK(): Int = prefs.getInt(KEY_MIN_SALARY_K, 0)
    fun setMinSalaryFilterK(salaryK: Int) = prefs.edit().putInt(KEY_MIN_SALARY_K, salaryK).apply()

    fun isFilterOutsourcing(): Boolean = prefs.getBoolean(KEY_FILTER_OUTSOURCING, true)
    fun setFilterOutsourcing(filter: Boolean) = prefs.edit().putBoolean(KEY_FILTER_OUTSOURCING, filter).apply()

    /**
     * 综合评分录取阈值 (40~90)：最终得分达到该值才会发起打招呼
     */
    fun getMatchScoreThreshold(): Int =
        prefs.getInt(KEY_MATCH_SCORE_THRESHOLD, DEFAULT_MATCH_SCORE_THRESHOLD).coerceIn(40, 90)

    fun setMatchScoreThreshold(threshold: Int) {
        prefs.edit().putInt(KEY_MATCH_SCORE_THRESHOLD, threshold.coerceIn(40, 90)).apply()
    }

    /**
     * 评分偏好模式：决定四维分数加权合成的权重配比
     */
    fun getScorePreference(): String = prefs.getString(KEY_SCORE_PREFERENCE, SCORE_PREF_BALANCED) ?: SCORE_PREF_BALANCED
    fun setScorePreference(preference: String) = prefs.edit().putString(KEY_SCORE_PREFERENCE, preference).apply()

    /**
     * 用户自定义排除关键词原文 (逗号/顿号/分号分隔)
     */
    fun getExcludeKeywords(): String = prefs.getString(KEY_EXCLUDE_KEYWORDS, "") ?: ""
    fun setExcludeKeywords(keywords: String) = prefs.edit().putString(KEY_EXCLUDE_KEYWORDS, keywords.trim()).apply()

    fun isPaused(): Boolean = prefs.getBoolean(KEY_IS_PAUSED, false)
    fun setPaused(paused: Boolean) = prefs.edit().putBoolean(KEY_IS_PAUSED, paused).apply()

    companion object {
        private const val PREFS_NAME = "boss_agent_settings"
        private const val KEY_SLIDE_RATE = "slideRate"
        private const val KEY_MAX_DAILY_GREETINGS = "max_daily_greetings"
        private const val KEY_MIN_SALARY_K = "min_salary_k"
        private const val KEY_FILTER_OUTSOURCING = "filter_outsourcing"
        private const val KEY_IS_PAUSED = "isPause"
        private const val KEY_MATCH_SCORE_THRESHOLD = "match_score_threshold"
        private const val KEY_SCORE_PREFERENCE = "score_preference"
        private const val KEY_EXCLUDE_KEYWORDS = "exclude_keywords"

        const val DEFAULT_MATCH_SCORE_THRESHOLD = 70

        // 评分偏好模式常量
        const val SCORE_PREF_BALANCED = "均衡"
        const val SCORE_PREF_TECH_FIRST = "技术优先"
        const val SCORE_PREF_SALARY_FIRST = "薪资优先"
        const val SCORE_PREF_STABILITY_FIRST = "稳字当先"

        @Volatile
        private var instance: AppSettings? = null

        fun getInstance(context: Context): AppSettings {
            return instance ?: synchronized(this) {
                instance ?: AppSettings(context).also { instance = it }
            }
        }
    }
}
