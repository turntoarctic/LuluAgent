package com.jobmate.app.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.widget.ViewFlipper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jobmate.app.JobmateApp
import com.jobmate.app.accessibility.BossAccessibilityService
import com.jobmate.app.data.local.AppDatabase
import com.jobmate.app.data.local.entity.JobEntity
import com.jobmate.app.data.pref.AppSettings
import com.jobmate.app.dispatcher.contract.DispatcherBroadcasts
import com.jobmate.app.dispatcher.fsm.EngineState
import com.jobmate.app.floating.FloatingHUDService
import com.jobmate.app.ui.settings.ApiKeyConfigActivity
import com.jobmate.app.ui.settings.FilterSettingsActivity
import com.jobmate.app.ui.settings.ResumeEditorActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 求职搭子 - 主控宿主 (单 Activity 三 Tab：首页 / 投递手账 / 我的设置)
 */
class MainActivity : AppCompatActivity() {

    private val bossPackage ="com.hpbr.bosszhipin"

    // Tab 索引
    private val TAB_HOME = 0
    private val TAB_RECORDS = 1
    private val TAB_SETTINGS = 2
    private var currentTab = TAB_HOME

    // 视觉规范色盘：湛蓝主色 + 淡雅灰阶
    private val colorBg = Color.parseColor("#F8F9FB")
    private val colorCard = Color.WHITE
    private val colorCardStroke = Color.parseColor("#F0F2F5")
    private val colorPrimary = Color.parseColor("#274DEA")
    private val colorPrimarySoft = Color.parseColor("#E9EDFC")
    private val colorTextMain = Color.parseColor("#1F2329")
    private val colorTextSub = Color.parseColor("#8F959E")
    private val colorTextBody = Color.parseColor("#4E5969")
    private val colorSuccess = Color.parseColor("#2BA471")
    private val colorSuccessSoft = Color.parseColor("#EBF6F1")

    // 宿主级视图
    private lateinit var viewFlipper: ViewFlipper
    private lateinit var titleTv: TextView
    private lateinit var subtitleTv: TextView
    private val tabViews = mutableListOf<LinearLayout>()

    // 首页视图引用
    private lateinit var accessibilityBtn: TextView
    private lateinit var overlayBtn: TextView
    private lateinit var statusTextTv: TextView
    private lateinit var percentageTv: TextView
    private lateinit var mainActionButton: TextView
    private lateinit var communicatedCountTv: TextView
    private lateinit var deliveredCountTv: TextView
    private lateinit var evaluatedCountTv: TextView
    private lateinit var filteredCountTv: TextView

    // 投递手账视图引用
    private lateinit var recordsRecyclerView: RecyclerView
    private lateinit var recordsEmptyTv: TextView
    private val records = mutableListOf<JobEntity>()
    private val recordsAdapter by lazy { HistoryAdapter(records) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContentView())
        registerStatusReceiver()
    }

    override fun onResume() {
        super.onResume()
        refreshHomeTab()
        if (currentTab == TAB_RECORDS) loadRecords()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(statusReceiver)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // 非首页 Tab 按返回时回到首页，再次返回才退出应用
        if (currentTab != TAB_HOME) {
            selectTab(TAB_HOME)
        } else {
            super.onBackPressed()
        }
    }

    // ==================== 宿主骨架：顶栏 + 三页 Flipper + 底部导航 ====================

    private fun buildContentView(): View {
        val root = RelativeLayout(this).apply {
            setBackgroundColor(colorBg)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // 1. 顶部标题栏（标题随 Tab 切换）
        val topBar = createTopBar().apply { id = View.generateViewId() }
        root.addView(topBar, RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        ).apply { addRule(RelativeLayout.ALIGN_PARENT_TOP) })

        // 2. 底部导航
        val bottomNav = createBottomNav().apply { id = View.generateViewId() }
        root.addView(bottomNav, RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            dp2px(60)
        ).apply { addRule(RelativeLayout.ALIGN_PARENT_BOTTOM) })

        // 3. 中间内容翻转容器（三 Tab 页面常驻，切换零重建）
        viewFlipper = ViewFlipper(this).apply { id = View.generateViewId() }
        viewFlipper.addView(buildHomePage())
        viewFlipper.addView(buildRecordsPage())
        viewFlipper.addView(buildSettingsPage())
        root.addView(viewFlipper, RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        ).apply {
            addRule(RelativeLayout.BELOW, topBar.id)
            addRule(RelativeLayout.ABOVE, bottomNav.id)
        })

        return root
    }

    private fun createTopBar(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp2px(20), dp2px(18), dp2px(20), dp2px(12))

            titleTv = TextView(this@MainActivity).apply {
                text ="求职搭子"
                textSize = 21f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(colorTextMain)
                includeFontPadding = false
            }

            subtitleTv = TextView(this@MainActivity).apply {
                text ="深呼吸，今天也会遇到懂你的好伯乐"
                textSize = 12f
                setTextColor(colorTextSub)
                includeFontPadding = false
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp2px(4) }
            }

            addView(titleTv)
            addView(subtitleTv)
        }
    }

    private fun createBottomNav(): View {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke(dp2px(1), colorCardStroke)
            }
        }

        val tabs = listOf(
            "首页" to TAB_HOME,
            "投递手账" to TAB_RECORDS,
            "我的设置" to TAB_SETTINGS
        )
        tabs.forEach { (label, index) ->
            val tabView = createNavItem(label, index)
            tabViews.add(tabView)
            nav.addView(tabView)
        }
        return nav
    }

    private fun createNavItem(label: String, tabIndex: Int): LinearLayout {
        val tab = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            setOnClickListener { selectTab(tabIndex) }

            val dot = View(this@MainActivity).apply {
                id = View.generateViewId()
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(colorPrimary)
                }
                layoutParams = LinearLayout.LayoutParams(dp2px(4), dp2px(4))
            }

            val labelTv = TextView(this@MainActivity).apply {
                text = label
                textSize = 13f
                gravity = Gravity.CENTER
                includeFontPadding = false
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp2px(3) }
            }

            addView(dot)
            addView(labelTv)
        }
        return tab
    }

    /** Tab 切换：更新选中态、标题与内容页 */
    private fun selectTab(tabIndex: Int) {
        if (currentTab == tabIndex) return
        currentTab = tabIndex
        viewFlipper.displayedChild = tabIndex

        when (tabIndex) {
            TAB_HOME -> {
                titleTv.text ="求职搭子"
                subtitleTv.text ="深呼吸，今天也会遇到懂你的好伯乐"
                refreshHomeTab()
            }
            TAB_RECORDS -> {
                titleTv.text ="投递手账"
                subtitleTv.text ="记录替你敲开的每一扇门与每一次权衡"
                loadRecords()
            }
            TAB_SETTINGS -> {
                titleTv.text ="我的设置"
                subtitleTv.text ="密钥、简历与策略，都在这里"
            }
        }
        tabViews.forEachIndexed { index, tab ->
            val selected = index == tabIndex
            val labelTv = tab.getChildAt(1) as TextView
            labelTv.setTextColor(if (selected) colorPrimary else colorTextSub)
            labelTv.typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            tab.getChildAt(0).visibility = if (selected) View.VISIBLE else View.INVISIBLE
        }
    }

    // ==================== Tab 1：首页 ====================

    private fun buildHomePage(): View {
        val scrollView = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp2px(18)
            setPadding(pad, dp2px(6), pad, dp2px(24))
        }

        contentLayout.addView(buildPreparednessCard())
        contentLayout.addView(buildRunningStatusCard())
        contentLayout.addView(buildDataDashboardCard())

        scrollView.addView(contentLayout)
        return scrollView
    }

    private fun buildPreparednessCard(): View {
        val card = createCardContainer()
            .setPadding(dp2px(16), dp2px(16), dp2px(16), dp2px(16)) as LinearLayout

        card.addView(createSectionHeader("出发准备清单"))

        val (row1, btn1) = createPermRow(
            title ="敲门开路",
            desc ="用于在 Boss 推荐流替你探寻新岗位",
            onAction = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        )
        accessibilityBtn = btn1

        val (row2, btn2) = createPermRow(
            title ="悄悄支招",
            desc ="运行时在屏幕旁提供悬浮控制台",
            onAction = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                    )
                }
            }
        )
        overlayBtn = btn2

        card.addView(row1)
        card.addView(createCardDivider())
        card.addView(row2)

        return card
    }

    private fun createPermRow(
        title: String,
        desc: String,
        onAction: () -> Unit
    ): Pair<View, TextView> {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp2px(10) }
        }

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp2px(8)
            }
        }

        textCol.addView(TextView(this).apply {
            text = title
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorTextMain)
            includeFontPadding = false
        })
        textCol.addView(TextView(this).apply {
            text = desc
            textSize = 11f
            setTextColor(colorTextSub)
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp2px(2) }
        })

        val actionBtn = TextView(this).apply {
            text ="去开启"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorPrimary)
            gravity = Gravity.CENTER
            includeFontPadding = false
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp2px(20).toFloat()
                setColor(colorPrimarySoft)
            }
            setPadding(dp2px(14), dp2px(6), dp2px(14), dp2px(6))
            setOnClickListener { onAction() }
        }

        row.addView(textCol)
        row.addView(actionBtn)
        return Pair(row, actionBtn)
    }

    private fun buildRunningStatusCard(): View {
        val card = createCardContainer()
            .setPadding(dp2px(20), dp2px(20), dp2px(20), dp2px(20)) as LinearLayout
        (card.layoutParams as LinearLayout.LayoutParams).topMargin = dp2px(14)

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val statusPill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp2px(100).toFloat()
                setColor(colorPrimarySoft)
            }
            setPadding(dp2px(10), dp2px(4), dp2px(12), dp2px(4))
        }

        statusPill.addView(View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(colorPrimary)
            }
            layoutParams = LinearLayout.LayoutParams(dp2px(6), dp2px(6)).apply {
                marginEnd = dp2px(6)
            }
        })

        statusTextTv = TextView(this).apply {
            text ="待命中 (就绪)"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorPrimary)
            includeFontPadding = false
        }
        statusPill.addView(statusTextTv)

        val progressContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                weight = 1f
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
            }
        }
        progressContainer.addView(TextView(this).apply {
            text ="今日配额 "
            textSize = 12f
            setTextColor(colorTextSub)
            includeFontPadding = false
        })
        percentageTv = TextView(this).apply {
            text ="0%"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorTextMain)
            includeFontPadding = false
        }
        progressContainer.addView(percentageTv)

        topRow.addView(statusPill)
        topRow.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 0, 1f) })
        topRow.addView(progressContainer)
        card.addView(topRow)

        mainActionButton = TextView(this).apply {
            text ="出发，帮我盯岗位"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp2px(14).toFloat()
                setColor(colorPrimary)
            }
            val bp = dp2px(15)
            setPadding(bp, bp, bp, bp)
            setOnClickListener { handleMainActionClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp2px(18) }
        }
        card.addView(mainActionButton)

        return card
    }

    private fun buildDataDashboardCard(): View {
        val card = createCardContainer()
            .setPadding(dp2px(16), dp2px(18), dp2px(16), dp2px(18)) as LinearLayout
        (card.layoutParams as LinearLayout.LayoutParams).topMargin = dp2px(14)

        card.addView(TextView(this).apply {
            text ="求职战报小结"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorTextSub)
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp2px(14) }
        })

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val (c1, tv1) = createDataColumn("今日沟通")
        val (c2, tv2) = createDataColumn("今日投递")
        val (c3, tv3) = createDataColumn("AI 研判")
        val (c4, tv4) = createDataColumn("本地拦截")
        communicatedCountTv = tv1
        deliveredCountTv = tv2
        evaluatedCountTv = tv3
        filteredCountTv = tv4

        row.addView(c1)
        row.addView(c2)
        row.addView(c3)
        row.addView(c4)
        card.addView(row)

        return card
    }

    private fun createDataColumn(label: String): Pair<View, TextView> {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val countTv = TextView(this).apply {
            text ="0"
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorTextMain)
            gravity = Gravity.CENTER
            includeFontPadding = false
        }

        col.addView(countTv)
        col.addView(TextView(this).apply {
            text = label
            textSize = 11f
            setTextColor(colorTextSub)
            gravity = Gravity.CENTER
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp2px(4) }
        })

        return Pair(col, countTv)
    }

    // ==================== Tab 2：投递手账 ====================

    private fun buildRecordsPage(): View {
        val container = FrameLayout(this).apply {
            setBackgroundColor(colorBg)
            val p = dp2px(4)
            setPadding(dp2px(16), p, dp2px(16), dp2px(12))
        }

        recordsRecyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = recordsAdapter
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        recordsEmptyTv = TextView(this).apply {
            text ="还没有投递记录\n启动求职搭子后，每一次沟通与评估都会记录在这里"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(colorTextSub)
            setLineSpacing(dp2px(4).toFloat(), 1.0f)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }

        container.addView(recordsRecyclerView)
        container.addView(recordsEmptyTv)
        return container
    }

    private fun loadRecords() {
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) {
                AppDatabase.getInstance(this@MainActivity).jobDao().getRecentJobs(limit = 100)
            }
            records.clear()
            records.addAll(list)
            recordsAdapter.notifyDataSetChanged()
            recordsEmptyTv.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    // ==================== Tab 3：我的设置 ====================

    private fun buildSettingsPage(): View {
        val scrollView = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp2px(18), dp2px(6), dp2px(18), dp2px(24))
        }

        val card = createCardContainer()
            .setPadding(dp2px(16), dp2px(6), dp2px(16), dp2px(6)) as LinearLayout

        card.addView(createSettingRow(
            title ="DeepSeek API Key",
            desc ="配置智能研判引擎的密钥凭证",
            onClick = { startActivity(Intent(this, ApiKeyConfigActivity::class.java)) }
        ))
        card.addView(createCardDivider())
        card.addView(createSettingRow(
            title ="我的简历与背景",
            desc ="AI 会结合你的经历生成更懂你的破冰语",
            onClick = { startActivity(Intent(this, ResumeEditorActivity::class.java)) }
        ))
        card.addView(createCardDivider())
        card.addView(createSettingRow(
            title ="筛选与评分策略",
            desc ="薪资下限、排除关键词与录取阈值随心调",
            onClick = { startActivity(Intent(this, FilterSettingsActivity::class.java)) }
        ))
        card.addView(createCardDivider())
        card.addView(createSettingRow(
            title ="忽略电池优化",
            desc ="防止后台任务被系统断流中断",
            onClick = { requestIgnoreBatteryOptimization() }
        ))

        page.addView(card)
        scrollView.addView(page)
        return scrollView
    }

    private fun createSettingRow(
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

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp2px(8)
            }
        }

        textCol.addView(TextView(this).apply {
            text = title
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorTextMain)
            includeFontPadding = false
        })
        textCol.addView(TextView(this).apply {
            text = desc
            textSize = 11f
            setTextColor(colorTextSub)
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp2px(3) }
        })

        row.addView(textCol)
        row.addView(TextView(this).apply {
            text ="›"
            textSize = 22f
            setTextColor(colorTextSub)
            gravity = Gravity.CENTER
            includeFontPadding = false
        })
        return row
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

    // ==================== 公共构建器 ====================

    private fun createCardContainer(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp2px(18).toFloat()
                setColor(colorCard)
                setStroke(dp2px(1), colorCardStroke)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp2px(10) }
        }
    }

    private fun createCardDivider(): View {
        return View(this).apply {
            setBackgroundColor(colorCardStroke)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp2px(1)
            )
        }
    }

    private fun createSectionHeader(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorTextSub)
            includeFontPadding = false
        }
    }

    private fun dp2px(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density + 0.5f).toInt()
    }

    // ==================== 首页业务：状态驱动 ====================

    private fun refreshHomeTab() {
        val isAccessibilityOk = BossAccessibilityService.isConnected()
        updateButtonStatus(accessibilityBtn, isAccessibilityOk)

        val isOverlayOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
        updateButtonStatus(overlayBtn, isOverlayOk)

        val app = application as JobmateApp
        updateEngineStateUi(app.stateMachine.getCurrentState())
        loadDashboardData(app)
    }

    private fun updateButtonStatus(btn: TextView, isGranted: Boolean) {
        if (isGranted) {
            btn.text ="已就绪"
            btn.setTextColor(colorSuccess)
            btn.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp2px(20).toFloat()
                setColor(colorSuccessSoft)
            }
            btn.isEnabled = false
        } else {
            btn.text ="去开启"
            btn.setTextColor(colorPrimary)
            btn.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp2px(20).toFloat()
                setColor(colorPrimarySoft)
            }
            btn.isEnabled = true
        }
    }

    private fun updateEngineStateUi(state: EngineState) {
        statusTextTv.text = if (state == EngineState.IDLE) "待命中 (随时出发)" else state.title

        try {
            statusTextTv.setTextColor(Color.parseColor(state.indicatorColorHex))
        } catch (e: Exception) {
            statusTextTv.setTextColor(colorPrimary)
        }

        when {
            state == EngineState.EMERGENCY_STOP -> {
                mainActionButton.text ="已确认账号正常，继续出发"
                mainActionButton.background = GradientDrawable().apply {
                    cornerRadius = dp2px(14).toFloat()
                    setColor(Color.parseColor(state.indicatorColorHex))
                }
            }
            state.isOperating() -> {
                mainActionButton.text ="休息一下 (暂停中)"
                mainActionButton.background = GradientDrawable().apply {
                    cornerRadius = dp2px(14).toFloat()
                    setColor(Color.parseColor("#64748B"))
                }
            }
            else -> {
                mainActionButton.text ="出发，帮我盯岗位"
                mainActionButton.background = GradientDrawable().apply {
                    cornerRadius = dp2px(14).toFloat()
                    setColor(colorPrimary)
                }
            }
        }
    }

    private fun loadDashboardData(app: JobmateApp) {
        lifecycleScope.launch {
            val (todayChat, todayApplied, todayEvaluated, todayFiltered) = withContext(Dispatchers.IO) {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val start = cal.timeInMillis
                val end = System.currentTimeMillis()

                val dao = app.database.jobDao()
                DashboardStats(
                    communicated = dao.getCommunicatedCountBetween(start, end),
                    delivered = dao.getDeliveredCountBetween(start, end),
                    evaluated = dao.getEvaluatedCountBetween(start, end),
                    filtered = dao.getFilteredOutCountBetween(start, end)
                )
            }

            communicatedCountTv.text = todayChat.toString()
            deliveredCountTv.text = todayApplied.toString()
            evaluatedCountTv.text = todayEvaluated.toString()
            filteredCountTv.text = todayFiltered.toString()

            val maxCount = app.configRepository.getMaxDailyGreetings()
            val percent = if (maxCount > 0) minOf(100, (todayChat * 100) / maxCount) else 0
            percentageTv.text ="$percent%"
        }
    }

    /** 看板四项统计的轻量承载 */
    private data class DashboardStats(
        val communicated: Int,
        val delivered: Int,
        val evaluated: Int,
        val filtered: Int
    )

    private fun handleMainActionClick() {
        val app = application as JobmateApp
        val currentState = app.stateMachine.getCurrentState()

        if (currentState == EngineState.EMERGENCY_STOP) {
            showEmergencyRecoverDialog()
            return
        }

        if (currentState.isOperating()) {
            app.taskDispatcher.pause()
            updateEngineStateUi(EngineState.PAUSED)
            return
        }

        if (currentState == EngineState.PAUSED) {
            app.taskDispatcher.resume()
            updateEngineStateUi(EngineState.SCANNING)
            return
        }

        if (!BossAccessibilityService.isConnected()) {
            Toast.makeText(this,"请先开启【敲门开路】权限", Toast.LENGTH_SHORT).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this,"请先开启【悄悄支招】权限", Toast.LENGTH_SHORT).show()
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            return
        }

        if (!app.configRepository.hasValidApiKey()) {
            Toast.makeText(this,"请先在【我的设置】中配置 DeepSeek Key", Toast.LENGTH_LONG).show()
            selectTab(TAB_SETTINGS)
            return
        }

        val bossIntent = packageManager.getLaunchIntentForPackage(bossPackage)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        if (bossIntent != null) {
            startActivity(bossIntent)
        } else {
            Toast.makeText(this,"未检测到 Boss 直聘安装", Toast.LENGTH_SHORT).show()
        }

        FloatingHUDService.startService(this)
        app.taskDispatcher.start(warmUpDelayMs = 4000L)
        updateEngineStateUi(EngineState.SCANNING)

        Toast.makeText(this,"求职搭子已出发，正在跳转 Boss 直聘", Toast.LENGTH_SHORT).show()
    }

    private fun showEmergencyRecoverDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("风控熔断确认")
            .setMessage(
                "求职搭子检测到风控信号后已自动刹车。\n\n" +
                    "建议先打开 Boss 直聘手动确认账号状态正常（无验证码、无功能限制），" +
                    "再解除熔断恢复自动化，给账号留一段冷却时间更稳妥。"
            )
            .setPositiveButton("确认恢复") { dialog, _ ->
                dialog.dismiss()
                (application as JobmateApp).taskDispatcher.recoverFromEmergencyStop()
                updateEngineStateUi(EngineState.IDLE)
            }
            .setNegativeButton("暂不恢复", null)
            .show()
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == DispatcherBroadcasts.ACTION_ENGINE_STATE_CHANGED) {
                val newState = intent.getStringExtra(DispatcherBroadcasts.EXTRA_NEW_STATE) ?: return
                try {
                    val state = EngineState.valueOf(newState)
                    updateEngineStateUi(state)
                    loadDashboardData(application as JobmateApp)
                } catch (e: Exception) {
                    // 容错
                }
            }
        }
    }

    private fun registerStatusReceiver() {
        val filter = IntentFilter(DispatcherBroadcasts.ACTION_ENGINE_STATE_CHANGED)
        ContextCompat.registerReceiver(
            this,
            statusReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    // ==================== 投递手账列表与详情 ====================

    inner class HistoryAdapter(private val dataList: List<JobEntity>) :
        RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val titleTv: TextView = view.findViewWithTag("title")
            val salaryTv: TextView = view.findViewWithTag("salary")
            val companyTv: TextView = view.findViewWithTag("company")
            val statusChip: TextView = view.findViewWithTag("status")
            val reasonTv: TextView = view.findViewWithTag("reason")
            val dimsTv: TextView = view.findViewWithTag("dims")
            val scoreTv: TextView = view.findViewWithTag("score")
            val timeTv: TextView = view.findViewWithTag("time")
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val card = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    cornerRadius = dp2px(16).toFloat()
                    setColor(colorCard)
                    setStroke(dp2px(1), colorCardStroke)
                }
                val p = dp2px(16)
                setPadding(p, p, p, p)
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                ).apply {
                    val m = dp2px(10)
                    setMargins(0, dp2px(5), 0, m)
                }
            }

            val row1 = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row1.addView(TextView(parent.context).apply {
                tag ="title"
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(colorTextMain)
                includeFontPadding = false
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            row1.addView(TextView(parent.context).apply {
                tag ="salary"
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(colorPrimary)
                includeFontPadding = false
            })
            card.addView(row1)

            val row2 = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp2px(6) }
            }
            row2.addView(TextView(parent.context).apply {
                tag ="company"
                textSize = 12f
                setTextColor(colorTextSub)
                includeFontPadding = false
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp2px(8)
                }
            })
            row2.addView(TextView(parent.context).apply {
                tag ="status"
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                setPadding(dp2px(8), dp2px(3), dp2px(8), dp2px(3))
            })
            card.addView(row2)

            val bubble = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    cornerRadius = dp2px(10).toFloat()
                    setColor(Color.parseColor("#F4F6F9"))
                }
                val bp = dp2px(10)
                setPadding(bp, bp, bp, bp)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp2px(10) }
            }

            bubble.addView(TextView(parent.context).apply {
                tag ="reason"
                textSize = 12f
                setTextColor(colorTextBody)
                setLineSpacing(dp2px(2).toFloat(), 1.0f)
                maxLines = 4
                includeFontPadding = false
            })

            bubble.addView(TextView(parent.context).apply {
                tag ="dims"
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#2E7BE6"))
                includeFontPadding = false
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp2px(6) }
            })
            card.addView(bubble)

            val row4 = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp2px(10) }
            }
            row4.addView(TextView(parent.context).apply {
                tag ="time"
                textSize = 11f
                setTextColor(colorTextSub)
                includeFontPadding = false
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            row4.addView(TextView(parent.context).apply {
                tag ="score"
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                setPadding(dp2px(6), dp2px(2), dp2px(6), dp2px(2))
            })
            card.addView(row4)

            return ViewHolder(card)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = dataList[position]
            holder.titleTv.text = item.title
            holder.salaryTv.text = item.salaryText

            val compName = item.companyName.trim()
            val cityName = item.city.trim().ifBlank { "全国" }
            holder.companyTv.text = if (compName.isNotEmpty()) "$compName · $cityName" else cityName

            when (item.status) {
                JobEntity.STATUS_DELIVERED -> {
                    holder.statusChip.text ="已投递"
                    holder.statusChip.setTextColor(colorSuccess)
                    holder.statusChip.background = pillDrawable(colorSuccessSoft)
                }
                JobEntity.STATUS_COMMUNICATED -> {
                    holder.statusChip.text ="已打招呼"
                    holder.statusChip.setTextColor(colorPrimary)
                    holder.statusChip.background = pillDrawable(colorPrimarySoft)
                }
                JobEntity.STATUS_EVALUATED -> {
                    holder.statusChip.text ="契合度高"
                    holder.statusChip.setTextColor(Color.parseColor("#2E7BE6"))
                    holder.statusChip.background = pillDrawable("#EDF4FE")
                }
                JobEntity.STATUS_REJECTED -> {
                    holder.statusChip.text ="已跳过"
                    holder.statusChip.setTextColor(Color.parseColor("#6B7280"))
                    holder.statusChip.background = pillDrawable("#EEF1F5")
                }
                JobEntity.STATUS_FILTERED_OUT -> {
                    holder.statusChip.text ="规则过滤"
                    holder.statusChip.setTextColor(Color.parseColor("#6B7280"))
                    holder.statusChip.background = pillDrawable("#EEF1F5")
                }
                else -> {
                    holder.statusChip.text = item.status
                    holder.statusChip.setTextColor(colorTextSub)
                    holder.statusChip.background = pillDrawable(colorCardStroke)
                }
            }

            val cleanReason = item.evalReason.ifBlank { "正在分析岗位的契合度与潜在风险..." }
            holder.reasonTv.text ="搭子评语：$cleanReason"

            val threshold = AppSettings.getInstance(holder.itemView.context).getMatchScoreThreshold()
            if (item.matchScore >= 0) {
                holder.scoreTv.text ="契合度: ${item.matchScore}分"
                if (item.matchScore >= threshold) {
                    holder.scoreTv.setTextColor(colorSuccess)
                    holder.scoreTv.background = pillDrawable(colorSuccessSoft)
                } else {
                    holder.scoreTv.setTextColor(colorTextSub)
                    holder.scoreTv.background = pillDrawable(colorCardStroke)
                }
            } else {
                holder.scoreTv.text ="契合度: --"
                holder.scoreTv.setTextColor(colorTextSub)
                holder.scoreTv.background = null
            }

            if (item.techScore >= 0 && item.experienceScore >= 0 &&
                item.salaryScore >= 0 && item.stabilityScore >= 0
            ) {
                holder.dimsTv.visibility = View.VISIBLE
                holder.dimsTv.text =
                    "技${item.techScore} · 验${item.experienceScore} · 薪${item.salaryScore} · 稳${item.stabilityScore}"
            } else {
                holder.dimsTv.visibility = View.GONE
            }

            val sdf = SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault())
            holder.timeTv.text = sdf.format(Date(item.updatedAt))

            holder.itemView.setOnClickListener { showJobDetailDialog(item) }
        }

        override fun getItemCount(): Int = dataList.size

        private fun pillDrawable(bgHex: String): GradientDrawable {
            return GradientDrawable().apply {
                cornerRadius = dp2px(100).toFloat()
                setColor(Color.parseColor(bgHex))
            }
        }
    }

    /** 点击记录卡片时展示完整详情：评分四维、评语全文、JD 原文与 HR 活跃状态 */
    private fun showJobDetailDialog(item: JobEntity) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp2px(20)
            setPadding(p, dp2px(12), p, dp2px(8))
        }

        fun addLabel(text: String): TextView = TextView(this).apply {
            this.text = text
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorTextSub)
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp2px(14) }
        }

        fun addBody(text: String, isPrimary: Boolean = false): TextView = TextView(this).apply {
            this.text = text
            textSize = if (isPrimary) 14f else 13f
            if (isPrimary) typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (isPrimary) colorPrimary else colorTextBody)
            setLineSpacing(dp2px(3).toFloat(), 1.0f)
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp2px(4) }
        }

        content.addView(TextView(this).apply {
            text = item.title
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorTextMain)
            includeFontPadding = false
        })

        content.addView(TextView(this).apply {
            text = item.salaryText
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorPrimary)
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp2px(4) }
        })

        val compName = item.companyName.trim()
        val cityName = item.city.trim().ifBlank { "全国" }
        content.addView(addBody("$compName · $cityName"))

        val sdf = SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault())
        content.addView(addBody("更新于 ${sdf.format(Date(item.updatedAt))} · ${describeStatus(item.status)}"))

        if (item.matchScore >= 0) {
            content.addView(addLabel("契合度评分"))
            val threshold = AppSettings.getInstance(this).getMatchScoreThreshold()
            val passText = if (item.matchScore >= threshold) "达标" else "未达标"
            content.addView(addBody("综合 ${item.matchScore} 分（录取阈值 $threshold 分，$passText）", isPrimary = true))

            if (item.techScore >= 0 && item.experienceScore >= 0 &&
                item.salaryScore >= 0 && item.stabilityScore >= 0
            ) {
                content.addView(addBody(
                    "技术栈 ${item.techScore} · 经验年限 ${item.experienceScore} · 薪资 ${item.salaryScore} · 稳定性 ${item.stabilityScore}"
                ))
            }
        }

        content.addView(addLabel("搭子评语"))
        content.addView(addBody(item.evalReason.ifBlank { "暂无评语" }))

        if (item.hrName.isNotBlank() || item.hrActiveStatus.isNotBlank()) {
            content.addView(addLabel("HR 信息"))
            val hrLine = buildString {
                if (item.hrName.isNotBlank()) append(item.hrName)
                if (item.hrTitle.isNotBlank()) append(" · ${item.hrTitle}")
                if (item.hrActiveStatus.isNotBlank()) append("（${item.hrActiveStatus}）")
            }
            content.addView(addBody(hrLine))
        }

        content.addView(addLabel("岗位 JD 全文"))
        content.addView(addBody(item.jobDescription.ifBlank { "未抓取到详情内容" }))

        val scrollView = ScrollView(this).apply {
            addView(content)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("岗位详情")
            .setView(scrollView)
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun describeStatus(status: String): String = when (status) {
        JobEntity.STATUS_DELIVERED -> "已投递"
        JobEntity.STATUS_COMMUNICATED -> "已打招呼"
        JobEntity.STATUS_EVALUATED -> "契合度高"
        JobEntity.STATUS_REJECTED -> "已跳过"
        JobEntity.STATUS_FILTERED_OUT -> "规则过滤"
        else -> status
    }
}
