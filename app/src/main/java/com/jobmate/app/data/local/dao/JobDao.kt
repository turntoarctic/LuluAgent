package com.jobmate.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jobmate.app.data.local.entity.JobEntity

@Dao
interface JobDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrIgnore(job: JobEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(job: JobEntity): Long

    @Update
    suspend fun update(job: JobEntity)

    @Query("SELECT * FROM jobs WHERE job_id = :jobId LIMIT 1")
    suspend fun findByJobId(jobId: String): JobEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM jobs WHERE job_id = :jobId)")
    suspend fun isJobExists(jobId: String): Boolean

    // 【修改】：已投递 (DELIVERED) 的岗位同样视为已沟通过，防止次日重复扫描发起
    @Query("SELECT EXISTS(SELECT 1 FROM jobs WHERE job_id = :jobId AND status IN ('COMMUNICATED', 'DELIVERED'))")
    suspend fun isJobCommunicated(jobId: String): Boolean

    // 【新增】：该岗位是否曾被评估放弃 (REJECTED / FILTERED_OUT)——跨会话持久化去重，
    // 避免进程重启后对同一岗位重复抓取详情并重复消耗 DeepSeek Token
    @Query("SELECT EXISTS(SELECT 1 FROM jobs WHERE job_id = :jobId AND status IN ('REJECTED', 'FILTERED_OUT'))")
    suspend fun isJobDiscardedBefore(jobId: String): Boolean

    // 【新增】：拉取近期已终结岗位 (已沟通/已投递/已放弃)，用于启动时预载前置去重键
    @Query(
        """
        SELECT * FROM jobs
        WHERE status IN ('COMMUNICATED', 'DELIVERED', 'REJECTED', 'FILTERED_OUT')
          AND updated_at >= :since
        ORDER BY updated_at DESC
        LIMIT 500
        """
    )
    suspend fun getRecentlySettledJobs(since: Long): List<JobEntity>

    // 【修改】：沟通总量统计兼顾 COMMUNICATED 与 DELIVERED，确保每日打招呼上限配额准确扣减
    @Query("SELECT COUNT(1) FROM jobs WHERE status IN ('COMMUNICATED', 'DELIVERED') AND updated_at >= :startTime AND updated_at <= :endTime")
    suspend fun getCommunicatedCountBetween(startTime: Long, endTime: Long): Int

    // 维持原样：专门统计投递成功的数量
    @Query("SELECT COUNT(1) FROM jobs WHERE status IN ('DELIVERED', 'RESUME_SENT') AND updated_at >= :startTime AND updated_at <= :endTime")
    suspend fun getDeliveredCountBetween(startTime: Long, endTime: Long): Int

    // 【新增】：今日 AI 深度评估量 (含评估后放弃的，四维打分完成即计入)
    @Query(
        """
        SELECT COUNT(1) FROM jobs
        WHERE match_score >= 0
          AND status IN ('EVALUATED', 'REJECTED', 'COMMUNICATED', 'DELIVERED')
          AND updated_at >= :startTime AND updated_at <= :endTime
        """
    )
    suspend fun getEvaluatedCountBetween(startTime: Long, endTime: Long): Int

    // 【新增】：今日本地规则拦截量 (0-Token 过滤)
    @Query("SELECT COUNT(1) FROM jobs WHERE status = 'FILTERED_OUT' AND updated_at >= :startTime AND updated_at <= :endTime")
    suspend fun getFilteredOutCountBetween(startTime: Long, endTime: Long): Int

    @Query("UPDATE jobs SET status = :status, updated_at = :updatedAt WHERE job_id = :jobId")
    suspend fun updateStatus(jobId: String, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("""
        UPDATE jobs
        SET match_score = :score,
            tech_score = :techScore,
            experience_score = :experienceScore,
            salary_score = :salaryScore,
            stability_score = :stabilityScore,
            eval_reason = :reason,
            suggested_greeting = :greeting,
            status = :status,
            updated_at = :updatedAt
        WHERE job_id = :jobId
    """)
    suspend fun updateEvaluationResult(
        jobId: String,
        score: Int,
        techScore: Int,
        experienceScore: Int,
        salaryScore: Int,
        stabilityScore: Int,
        reason: String,
        greeting: String,
        status: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("SELECT * FROM jobs ORDER BY updated_at DESC LIMIT :limit OFFSET :offset")
    suspend fun getRecentJobs(limit: Int, offset: Int = 0): List<JobEntity>
}
