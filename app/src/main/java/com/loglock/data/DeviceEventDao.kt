package com.loglock.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface DeviceEventDao {

    @Insert
    suspend fun insert(event: DeviceEvent): Long

    @Query("SELECT * FROM device_events WHERE timestamp > :since ORDER BY timestamp DESC")
    fun getEventsSince(since: Long): LiveData<List<DeviceEvent>>

    @Query("DELETE FROM device_events WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}
