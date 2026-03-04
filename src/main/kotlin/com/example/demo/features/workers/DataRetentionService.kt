package com.example.demo.features.workers

import com.example.demo.features.roster.service.RosterWeekPolicy
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import kotlin.math.max

@Service
class DataRetentionService(
    private val jdbcTemplate: JdbcTemplate,
    private val rosterWeekPolicy: RosterWeekPolicy,
    @Value("\${app.retention.meal-days:365}")
    private val mealDays: Long,
    @Value("\${app.retention.weekly-days:365}")
    private val weeklyDays: Long,
    @Value("\${app.retention.notifications-days:90}")
    private val notificationsDays: Long,
    @Value("\${app.retention.dispatch-log-days:60}")
    private val dispatchLogDays: Long,
    @Value("\${app.retention.password-reset-days:90}")
    private val passwordResetDays: Long,
    @Value("\${app.retention.suspicious-resolved-days:365}")
    private val suspiciousResolvedDays: Long,
    @Value("\${app.retention.inactive-push-token-days:120}")
    private val inactivePushTokenDays: Long,
    @Value("\${app.retention.batch-size:1000}")
    private val batchSize: Int,
) {
    @Transactional
    fun cleanup(): Map<String, Int> {
        val now = rosterWeekPolicy.now()
        val today = rosterWeekPolicy.today()
        val result = linkedMapOf<String, Int>()
        val safeBatchSize = max(100, batchSize)

        result["meal_transaction"] = deleteInBatches(
            """
            WITH to_delete AS (
                SELECT ctid
                FROM public.meal_transaction
                WHERE meal_timestamp < ?
                ORDER BY meal_timestamp
                LIMIT ?
            )
            DELETE FROM public.meal_transaction target
            USING to_delete
            WHERE target.ctid = to_delete.ctid
            """.trimIndent(),
            arrayOf(Timestamp.valueOf(now.minusDays(mealDays)), safeBatchSize)
        )
        result["meal_permission"] = deleteInBatches(
            """
            WITH to_delete AS (
                SELECT ctid
                FROM public.meal_permission
                WHERE date < ?
                ORDER BY date
                LIMIT ?
            )
            DELETE FROM public.meal_permission target
            USING to_delete
            WHERE target.ctid = to_delete.ctid
            """.trimIndent(),
            arrayOf(java.sql.Date.valueOf(today.minusDays(mealDays)), safeBatchSize)
        )

        result["weekly_report_snapshot"] = deleteInBatches(
            """
            WITH to_delete AS (
                SELECT ctid
                FROM public.weekly_report_snapshot
                WHERE report_date < ?
                ORDER BY report_date
                LIMIT ?
            )
            DELETE FROM public.weekly_report_snapshot target
            USING to_delete
            WHERE target.ctid = to_delete.ctid
            """.trimIndent(),
            arrayOf(java.sql.Date.valueOf(today.minusDays(weeklyDays)), safeBatchSize)
        )
        result["curator_week_audit"] = deleteInBatches(
            """
            WITH to_delete AS (
                SELECT ctid
                FROM public.curator_week_audit
                WHERE week_start < ?
                ORDER BY week_start
                LIMIT ?
            )
            DELETE FROM public.curator_week_audit target
            USING to_delete
            WHERE target.ctid = to_delete.ctid
            """.trimIndent(),
            arrayOf(java.sql.Date.valueOf(today.minusDays(weeklyDays)), safeBatchSize)
        )
        result["curator_week_submission"] = deleteInBatches(
            """
            WITH to_delete AS (
                SELECT ctid
                FROM public.curator_week_submission
                WHERE week_start < ?
                ORDER BY week_start
                LIMIT ?
            )
            DELETE FROM public.curator_week_submission target
            USING to_delete
            WHERE target.ctid = to_delete.ctid
            """.trimIndent(),
            arrayOf(java.sql.Date.valueOf(today.minusDays(weeklyDays)), safeBatchSize)
        )
        result["chef_week_confirmation"] = deleteInBatches(
            """
            WITH to_delete AS (
                SELECT ctid
                FROM public.chef_week_confirmation
                WHERE week_start < ?
                ORDER BY week_start
                LIMIT ?
            )
            DELETE FROM public.chef_week_confirmation target
            USING to_delete
            WHERE target.ctid = to_delete.ctid
            """.trimIndent(),
            arrayOf(java.sql.Date.valueOf(today.minusDays(weeklyDays)), safeBatchSize)
        )

        result["notifications"] = deleteInBatches(
            """
            WITH to_delete AS (
                SELECT ctid
                FROM public.notifications
                WHERE created_at < ?
                ORDER BY created_at
                LIMIT ?
            )
            DELETE FROM public.notifications target
            USING to_delete
            WHERE target.ctid = to_delete.ctid
            """.trimIndent(),
            arrayOf(Timestamp.valueOf(now.minusDays(notificationsDays)), safeBatchSize)
        )
        result["notification_dispatch_log"] = deleteInBatches(
            """
            WITH to_delete AS (
                SELECT ctid
                FROM public.notification_dispatch_log
                WHERE sent_at < ?
                ORDER BY sent_at
                LIMIT ?
            )
            DELETE FROM public.notification_dispatch_log target
            USING to_delete
            WHERE target.ctid = to_delete.ctid
            """.trimIndent(),
            arrayOf(Timestamp.valueOf(now.minusDays(dispatchLogDays)), safeBatchSize)
        )
        result["password_reset_log"] = deleteInBatches(
            """
            WITH to_delete AS (
                SELECT ctid
                FROM public.password_reset_log
                WHERE "timestamp" < ?
                ORDER BY "timestamp"
                LIMIT ?
            )
            DELETE FROM public.password_reset_log target
            USING to_delete
            WHERE target.ctid = to_delete.ctid
            """.trimIndent(),
            arrayOf(Timestamp.valueOf(now.minusDays(passwordResetDays)), safeBatchSize)
        )
        result["suspicious_transaction"] = deleteInBatches(
            """
            WITH to_delete AS (
                SELECT ctid
                FROM public.suspicious_transaction
                WHERE resolved = true
                  AND COALESCE(resolved_at, attempt_timestamp) < ?
                ORDER BY COALESCE(resolved_at, attempt_timestamp)
                LIMIT ?
            )
            DELETE FROM public.suspicious_transaction target
            USING to_delete
            WHERE target.ctid = to_delete.ctid
            """.trimIndent(),
            arrayOf(Timestamp.valueOf(now.minusDays(suspiciousResolvedDays)), safeBatchSize)
        )
        result["push_device_token"] = deleteInBatches(
            """
            WITH to_delete AS (
                SELECT ctid
                FROM public.push_device_token
                WHERE active = false
                  AND updated_at < ?
                ORDER BY updated_at
                LIMIT ?
            )
            DELETE FROM public.push_device_token target
            USING to_delete
            WHERE target.ctid = to_delete.ctid
            """.trimIndent(),
            arrayOf(Timestamp.valueOf(now.minusDays(inactivePushTokenDays)), safeBatchSize)
        )

        return result
    }

    private fun deleteInBatches(sql: String, args: Array<Any>): Int {
        var total = 0
        while (true) {
            val affected = jdbcTemplate.update(sql, *args)
            if (affected <= 0) return total
            total += affected
            if (affected < max(100, batchSize)) return total
        }
    }
}
