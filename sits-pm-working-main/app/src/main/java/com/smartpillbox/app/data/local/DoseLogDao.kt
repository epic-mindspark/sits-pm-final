package com.smartpillbox.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface DoseLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: DoseLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<DoseLogEntity>)

    @Query("SELECT * FROM dose_logs WHERE medicineId = :medicineId ORDER BY scheduledTime DESC")
    suspend fun getLogsForMedicine(medicineId: Int): List<DoseLogEntity>

    @Query("SELECT * FROM dose_logs WHERE scheduledTime BETWEEN :startTime AND :endTime ORDER BY scheduledTime ASC")
    suspend fun getLogsForDateRange(startTime: Long, endTime: Long): List<DoseLogEntity>

    @Query("SELECT * FROM dose_logs WHERE status = 'pending' ORDER BY scheduledTime ASC")
    suspend fun getPendingDoses(): List<DoseLogEntity>

    @Query("SELECT * FROM dose_logs WHERE status = 'missed' ORDER BY scheduledTime DESC")
    suspend fun getMissedDoses(): List<DoseLogEntity>

    @Update
    suspend fun updateLog(log: DoseLogEntity)

    @Query("UPDATE dose_logs SET status = :status, actionTime = :actionTime WHERE id = :id")
    suspend fun updateDoseStatus(id: Int, status: String, actionTime: Long)

    @Query("SELECT COUNT(*) FROM dose_logs WHERE status = 'taken' AND scheduledTime BETWEEN :startTime AND :endTime")
    suspend fun getTakenCountForDateRange(startTime: Long, endTime: Long): Int

    @Query("SELECT COUNT(*) FROM dose_logs WHERE status = 'missed' AND scheduledTime BETWEEN :startTime AND :endTime")
    suspend fun getMissedCountForDateRange(startTime: Long, endTime: Long): Int
}