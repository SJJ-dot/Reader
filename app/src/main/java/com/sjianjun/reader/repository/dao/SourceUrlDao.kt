package com.sjianjun.reader.repository.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sjianjun.reader.bean.SourceUrl

@Dao
interface SourceUrlDao {

    @Query("select * from SourceUrl order by sortOrder asc")
    fun getAll(): List<SourceUrl>

    @Query("select * from SourceUrl where autoUpdate = 1 order by sortOrder asc")
    fun getAutoUpdate(): List<SourceUrl>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(list: List<SourceUrl>)

    @Query("delete from SourceUrl")
    fun deleteAll()
}
