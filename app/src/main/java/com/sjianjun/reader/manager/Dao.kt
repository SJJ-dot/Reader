package com.sjianjun.reader.manager

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Query
import com.sjianjun.reader.bean.JavaScript

@Dao
class Dao {
    @Query("SELECT * FROM JavaScript WHERE source = :source")
    fun getJavaScriptBySource(source: String): LiveData<JavaScript> {
        TODO()
    }
    @Query("SELECT * FROM JavaScript")
    fun getAllJavaScript() :LiveData<List<JavaScript>>  {
        TODO()
    }
}