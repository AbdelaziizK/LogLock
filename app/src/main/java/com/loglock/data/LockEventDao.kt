package com.loglock.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface LockEventDao {

    @Query("SELECT * FROM lock_events WHERE lockTime > :since ORDER BY lockTime DESC")
    fun getRecentEvents(since: Long): LiveData<List<LockEvent>>

    @Insert
    suspend fun insert(event: LockEvent): Long

    @Update
    suspend fun update(event: LockEvent)

    @Query("DELETE FROM lock_events WHERE lockTime < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("SELECT * FROM lock_events WHERE id = :id")
    suspend fun getById(id: Long): LockEvent?
}
