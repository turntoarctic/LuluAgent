package com.lulu.agent.ui.settings

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.lulu.agent.agent.evaluator.ScorePolicy
import com.lulu.agent.data.pref.AppSettings
import com.lulu.agent.data.pref.EncryptedDataStore
import com.lulu.agent.data.repository.ConfigRepository

/**
 * 筛选与评分策略配置页 (多维评分权重 + 0-Token 本地硬过滤规则)
 */
class FilterSettingsActivity : AppCompatActivity() {

    private lateinit var configRepository: ConfigRepository

    private lateinit var thresholdValueTv: TextView
    private lateinit var thresholdSeekBar: SeekBar
    private lateinit var prefDescTv: TextView
    private lateinit var minSalaryInput: EditText
    private lateinit var outsourcingSwitch: SwitchCompat
    private lateinit var excludeKeywordsInput: EditText

    private var selectedPreference: String = AppSettings.SCORE_PREF_BALANCED
    private val preferencePills = LinkedHashMap<String, TextView>()

    // 规范视觉色盘 (与 ApiKeyConfigActivity 保持一致)
    private val colorBg = Color.parseColor("#F8F9FB")
    private val colorCard = Color.WHITE
    private val colorCardStroke = Color.parseColor("#EAECEF")
    private val colorPrimary = Color.parseColor("#FA6542")         // 鹿鹿暖杏橙
    private val colorPrimaryDark = Color.parseColor("#DE4F2C")     // 高对比深橙
    private val colorPrimarySoft = Color.parseColor("#FFF4F0")     // 极淡微桃粉
    private val colorTextMain = Color.parseColor("#1F2329")        // 高阶石板黑
    private val colorTextSub = Color.parseColor("#646A73")         // 次级深灰
    private val colorTextTip = Color.parseColor("#8F959E")         // 浅灰说明
    private val colorInputBg = Color.parseColor("#F4F6F9")         // 输入框底色
    private val colorDivider = Color.parseColor("#F0F2F5")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        configRepository = ConfigRepository(
            EncryptedDataStore.getInstance(this),
            AppSettings.getInstance(this)
        )
        buildUi()
        loadCurrentSettings()
    }

    // ==================== UI 装配 ====================

    private fun buildUi() {
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colorBg)
        }

        rootLayout.addView(createTopBar())

        val rootScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp2px(16)
            setPadding(p, dp2px(6), p, dp2px(32))
        }

        container.addView(createScorePolicyCard())
        container.addView(createLocalFilterCard())

        // 底部主色保存按钮
        val saveBtn = TextView(this).apply {
            text = "保存策略"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            includeFontPadding = false
            background = GradientDrawable().apply {
                cornerRadius = dp2px(12).toFloat()
                setColor(colorPrimary)
            }
            val bp = dp2px(13)
            setPadding(bp, bp, bp, bp)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp2px(16) }
            setOnClickListener { saveSettings() }
        }
        container.addView(saveBtn)

        rootScroll.addView(container)
        rootLayout.addView(rootScroll)
        setContentView(rootLayout)
    }

    private fun createTopBar(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp2px(16), dp2px(12), dp2px(16), dp2px(12))
            setBackgroundColor(colorBg)

            val backBtn = TextView(this@FilterSettingsActivity).apply {
                text = "‹"
                textSize = 28f
                setTextColor(colorTextMain)
                gravity = Gravity.CENTER
                includeFontPadding = false
                layoutParams = LinearLayout.LayoutParams(dp2px(36), dp2px(36))
                setOnClickListener { finish() }
            }

            val title = TextView(this@FilterSettingsActivity).apply {
                text = "筛选与评分策略"
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(colorTextMain)
                includeFontPadding = false
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp2px(4) }
            }

            addView(backBtn)
            addView(title)
        }
    }

    /**
     * 卡片一：多维评分策略 (录取阈值 + 偏好权重)
     */
    private fun createScorePolicyCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createCardDrawable()
            val p = dp2px(16)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp2px(12) }
        }

        card.addView(createSectionTitle("🧮 评分策略", "DeepSeek 四维打分后按偏好加权合成综合分"))

        // 阈值行：标签 + 实时数值胶囊
        val thresholdRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp2px(14) }
        }
        thresholdRow.addView(TextView(this).apply {
            text = "综合评分录取阈值"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorTextMain)
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        thresholdValueTv = TextView(this).apply {
            text = "70 分"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorPrimaryDark)
            includeFontPadding = false
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp2px(100).toFloat()
                setColor(colorPrimarySoft)
            }
            setPadding(dp2px(12), dp2px(5), dp2px(12), dp2px(5))
        }
        thresholdRow.addView(thresholdValueTv)
        card.addView(thresholdRow)

        // 阈值滑动条 (40 ~ 90)
        thresholdSeekBar = SeekBar(this).apply {
            max = MAX_THRESHOLD - MIN_THRESHOLD
            progress = AppSettings.DEFAULT_MATCH_SCORE_THRESHOLD - MIN_THRESHOLD
            thumbOffset = dp2px(2)
            setPadding(0, dp2px(6), 0, dp2px(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    thresholdValueTv.text = "${progress + MIN_THRESHOLD} 分"
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        // 滑动条轨道着色
        thresholdSeekBar.progressDrawable?.setColorFilter(colorPrimary, android.graphics.PorterDuff.Mode.SRC_ATOP)
        thresholdSeekBar.thumb?.setColorFilter(colorPrimaryDark, android.graphics.PorterDuff.Mode.SRC_ATOP)
        card.addView(thresholdSeekBar)

        card.addView(TextView(this).apply {
            text = "四维加权综合分 ≥ 该阈值，鹿鹿才会发起打招呼沟通"
            textSize = 11f
            setTextColor(colorTextTip)
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        })

        card.addView(createDivider())

        card.addView(TextView(this).apply {
            text = "评分偏好"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorTextMain)
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        })

        // 偏好预设：2x2 胶囊矩阵
        val prefRow1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp2px(10) }
        }
        val prefRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp2px(8) }
        }
        prefRow1.addView(createPreferencePill(AppSettings.SCORE_PREF_BALANCED))
        prefRow1.addView(createPreferencePill(AppSettings.SCORE_PREF_TECH_FIRST, isEnd = true))
        prefRow2.addView(createPreferencePill(AppSettings.SCORE_PREF_SALARY_FIRST))
        prefRow2.addView(createPreferencePill(AppSettings.SCORE_PREF_STABILITY_FIRST, isEnd = true))
        card.addView(prefRow1)
        card.addView(prefRow2)

        prefDescTv = TextView(this).apply {
            textSize = 11f
            setTextColor(colorTextSub)
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp2px(10) }
        }
        card.addView(prefDescTv)

        return card
    }

    /**
     * 卡片二：本地 0-Token 硬过滤规则
     */
    private fun createLocalFilterCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createCardDrawable()
            val p = dp2px(16)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp2px(12) }
        }

        card.addView(createSectionTitle("🛡️ 本地硬过滤", "命中即拦截，0 Token 消耗，优先于大模型评估"))

        // 期望薪资下限
        card.addView(createFieldLabel("期望薪资下限 (K)", dp2px(14)))
        minSalaryInput = EditText(this).apply {
            hint = "填 0 或留空表示不限制"
            setHintTextColor(colorTextTip)
            textSize = 13f
            setTextColor(colorTextMain)
            inputType = InputType.TYPE_CLASS_NUMBER
            background = createInputDrawable()
            val ep = dp2px(12)
            setPadding(ep, ep, ep, ep)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp2px(8) }
        }
        card.addView(minSalaryInput)

        // 外包过滤开关
        val switchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp2px(16) }
        }
        switchRow.addView(TextView(this).apply {
            text = "过滤外包/劳务派遣岗位"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorTextMain)
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        outsourcingSwitch = SwitchCompat(this).apply {
            trackDrawable?.setColorFilter(colorPrimary, android.graphics.PorterDuff.Mode.SRC_ATOP)
        }
        switchRow.addView(outsourcingSwitch)
        card.addView(switchRow)

        card.addView(createDivider())

        // 自定义排除关键词
        card.addView(createFieldLabel("自定义排除关键词", dp2px(0)))
        excludeKeywordsInput = EditText(this).apply {
            hint = "如：996， 销售驻点， 借调（逗号/顿号分隔）"
            setHintTextColor(colorTextTip)
            textSize = 13f
            setTextColor(colorTextMain)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
            gravity = Gravity.TOP
            background = createInputDrawable()
            val ep = dp2px(12)
            setPadding(ep, ep, ep, ep)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp2px(8) }
        }
        card.addView(excludeKeywordsInput)

        card.addView(TextView(this).apply {
            text = "岗位/公司/JD 命中任意关键词将被直接跳过，不消耗 Token"
            textSize = 11f
            setTextColor(colorTextTip)
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp2px(6) }
        })

        return card
    }

    // ==================== 交互与持久化 ====================

    private fun createPreferencePill(label: String, isEnd: Boolean = false): TextView {
        return TextView(this).apply {
            text = label
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(dp2px(4), dp2px(9), dp2px(4), dp2px(9))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (!isEnd) marginEnd = dp2px(8)
            }
            setOnClickListener {
                selectedPreference = label
                refreshPreferencePills()
            }
            preferencePills[label] = this
        }
    }

    private fun refreshPreferencePills() {
        for ((label, pill) in preferencePills) {
            if (label == selectedPreference) {
                pill.background = GradientDrawable().apply {
                    cornerRadius = dp2px(100).toFloat()
                    setColor(colorPrimary)
                }
                pill.setTextColor(Color.WHITE)
            } else {
                pill.background = GradientDrawable().apply {
                    cornerRadius = dp2px(100).toFloat()
                    setColor(Color.WHITE)
                    setStroke(dp2px(1), colorCardStroke)
                }
                pill.setTextColor(colorTextSub)
            }
        }
        prefDescTv.text = "当前权重：${ScorePolicy.describeWeights(selectedPreference)}"
    }

    private fun loadCurrentSettings() {
        val threshold = configRepository.getMatchScoreThreshold()
        thresholdSeekBar.progress = threshold - MIN_THRESHOLD
        thresholdValueTv.text = "$threshold 分"

        selectedPreference = configRepository.getScorePreference()
        refreshPreferencePills()

        val minSalaryK = configRepository.getMinSalaryFilterK()
        minSalaryInput.setText(if (minSalaryK > 0) minSalaryK.toString() else "")
        outsourcingSwitch.isChecked = configRepository.isFilterOutsourcing()
        excludeKeywordsInput.setText(configRepository.getExcludeKeywords())
    }

    private fun saveSettings() {
        val threshold = (thresholdSeekBar.progress + MIN_THRESHOLD).coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)
        configRepository.setMatchScoreThreshold(threshold)
        configRepository.setScorePreference(selectedPreference)

        val salaryK = minSalaryInput.text.toString().trim().toIntOrNull() ?: 0
        configRepository.setMinSalaryFilterK(salaryK.coerceIn(0, 999))
        configRepository.setFilterOutsourcing(outsourcingSwitch.isChecked)
        configRepository.setExcludeKeywords(excludeKeywordsInput.text.toString())

        Toast.makeText(this, "策略已保存，下一轮评估即刻生效 🦌", Toast.LENGTH_SHORT).show()
        finish()
    }

    // ==================== 视觉零件 ====================

    private fun createSectionTitle(title: String, subtitle: String): View {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        wrapper.addView(TextView(this).apply {
            text = title
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorTextMain)
            includeFontPadding = false
        })
        wrapper.addView(TextView(this).apply {
            text = subtitle
            textSize = 11f
            setTextColor(colorTextTip)
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp2px(4) }
        })
        return wrapper
    }

    private fun createFieldLabel(text: String, topMarginDp: Int): View {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorTextMain)
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp2px(topMarginDp) }
        }
    }

    private fun createDivider(): View {
        return View(this).apply {
            setBackgroundColor(colorDivider)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp2px(1)
            ).apply {
                topMargin = dp2px(16)
                bottomMargin = dp2px(14)
            }
        }
    }

    private fun createCardDrawable(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp2px(14).toFloat()
            setColor(colorCard)
            setStroke(dp2px(1), colorCardStroke)
        }
    }

    private fun createInputDrawable(): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = dp2px(10).toFloat()
            setColor(colorInputBg)
            setStroke(dp2px(1), Color.parseColor("#E5E8EC"))
        }
    }

    private fun dp2px(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density + 0.5f).toInt()
    }

    companion object {
        const val MIN_THRESHOLD = 40
        const val MAX_THRESHOLD = 90
    }
}
