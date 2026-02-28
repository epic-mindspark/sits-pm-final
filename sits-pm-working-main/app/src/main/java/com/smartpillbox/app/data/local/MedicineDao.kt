package com.smartpillbox.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicineDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedicine(medicine: MedicineEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(medicines: List<MedicineEntity>)

    @Query("SELECT * FROM medicines WHERE isActive = 1 ORDER BY name ASC")
    fun getActiveMedicines(): Flow<List<MedicineEntity>>

    @Query("SELECT COUNT(*) FROM medicines WHERE isActive = 1")
    suspend fun getActiveMedicineCount(): Int

    @Query("SELECT * FROM medicines ORDER BY name ASC")
    fun getAllMedicines(): Flow<List<MedicineEntity>>

    @Query("SELECT * FROM medicines WHERE id = :id")
    suspend fun getMedicineById(id: Int): MedicineEntity?

    @Query("SELECT * FROM medicines WHERE firebaseKey = :fbKey LIMIT 1")
    suspend fun getMedicineByFirebaseKey(fbKey: String): MedicineEntity?

    @Update
    suspend fun updateMedicine(medicine: MedicineEntity)

    @Delete
    suspend fun deleteMedicine(medicine: MedicineEntity)

    @Query("DELETE FROM medicines WHERE id = :id")
    suspend fun deleteMedicineById(id: Int)
}