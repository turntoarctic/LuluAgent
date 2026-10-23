package com.jobmate.app.ui.settings

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * 求职搭子 - 我的设置 (独立页面，替代原首页弹窗)
 */
class SettingsActivity : AppCompatActivity() {

    // 与主控制台一致的温润色盘
    private val colorBg = Color.parseColor("#F8F9FB")
    private val colorCard = Color.WHITE
    private val colorCardStroke = Color.parseColor("#F0F2F5")
    private val colorPrimary = Color.parseColor("#274DEA")
    private val colorTextMain = Color.parseColor("#1F2329")
    private val colorTextSub = Color.parseColor("#8F959E")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(buildContentView())
    }

    private fun buildContentView(): View {
        val root = RelativeLayout(this).apply {
            setBackgroundColor(colorBg)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // 顶部标题栏
        val topBar = createTopBar().apply { id = View.generateViewId() }
        val topBarParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        ).apply { addRule(RelativeLayout.ALIGN_PARENT_TOP) }
        root.addView(topBar, topBarParams)

        // 设置卡片区
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp2px(18).toFloat()
                setColor(colorCard)
                setStroke(dp2px(1), colorCardStroke)
            }
            val p = dp2px(16)
            setPadding(p, dp2px(6), p, dp2px(6))
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp2px(10)
                addRule(RelativeLayout.BELOW, topBar.id)
                marginStart = dp2px(18)
                marginEnd = dp2px(18)
            }
        }

        card.addView(
            createSettingItem(
                icon = "🔑",
                title = "DeepSeek API Key",
                desc = "配置智能研判引擎的密钥凭证",
                onClick = { startActivity(Intent(this, ApiKeyConfigActivity::class.java)) }
            )
        )
        card.addView(createDivider())
        card.addView(
            createSettingItem(
                icon = "📝",
                title = "我的简历与背景",
                desc = "AI 会结合你的经历生成更懂你的破冰语",
                onClick = { startActivity(Intent(this, ResumeEditorActivity::class.java)) }
            )
        )
        card.addView(createDivider())
        card.addView(
            createSettingItem(
                icon = "🎚️",
                title = "筛选与评分策略",
                desc = "薪资下限、排除关键词与录取阈值随心调",
                onClick = { startActivity(Intent(this, FilterSettingsActivity::class.java)) }
            )
        )
        card.addView(createDivider())
        card.addView(
            createSettingItem(
                icon = "🔋",
                title = "忽略电池优化",
                desc = "防止后台任务被系统断流中断",
                onClick = { requestIgnoreBatteryOptimization() }
            )
        )

        root.addView(card)
        return root
    }

    private fun createTopBar(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp2px(16), dp2px(42), dp2px(20), dp2px(12))

            val backBtn = TextView(this@SettingsActivity).apply {
                text = "‹"
                textSize = 28f
                setTextColor(colorTextMain)
                gravity = Gravity.CENTER
                includeFontPadding = false
                layoutParams = LinearLayout.LayoutParams(dp2px(36), dp2px(36))
                setOnClickListener { finish() }
            }

            val title = TextView(this@SettingsActivity).apply {
                text = "我的设置 ⚙️"
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

    private fun createSettingItem(
        icon: String,
        title: String,
        desc: String,
        onClick: () -> Unit
    ): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp2px(4), dp2px(14), dp2px(4), dp2px(14))
            setOnClickListener { onClick() }
        }

        val iconTv = TextView(this).apply {
            text = icon
            textSize = 18f
            gravity = Gravity.CENTER
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(dp2px(30), dp2px(30))
        }

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp2px(12)
                marginEnd = dp2px(8)
            }
        }

        val titleTv = TextView(this).apply {
            text = title
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorTextMain)
            includeFontPadding = false
        }

        val descTv = TextView(this).apply {
            text = desc
            textSize = 11f
            setTextColor(colorTextSub)
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp2px(3) }
        }

        textCol.addView(titleTv)
        textCol.addView(descTv)

        val chevron = TextView(this).apply {
            text = "›"
            textSize = 22f
            setTextColor(colorTextSub)
            gravity = Gravity.CENTER
            includeFontPadding = false
        }

        row.addView(iconTv)
        row.addView(textCol)
        row.addView(chevron)
        return row
    }

    private fun createDivider(): View {
        return View(this).apply {
            setBackgroundColor(colorCardStroke)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp2px(1)
            )
        }
    }

    private fun requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                )
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    private fun dp2px(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density + 0.5f).toInt()
    }
}
