package com.sjianjun.reader.repository.dao;

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sjianjun.reader.bean.ReadingRecord
import kotlinx.coroutines.flow.Flow

@Dao
public interface ReadingRecordDao {
    @Query("select * from ReadingRecord where bookTitle=:bookTitle")
    fun getReadingRecord(bookTitle: String): Flow<ReadingRecord?>

    @Query("select * from ReadingRecord where bookTitle=:bookTitle")
    fun getReadingRecordSync(bookTitle: String): ReadingRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertReadingRecord(record: ReadingRecord): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertReadingRecordList(record: List<ReadingRecord>): List<Long>

    @Query("select * from ReadingRecord  order by bookId")
    fun getAllReadingRecord(): Flow<List<ReadingRecord>>

    @Query("delete from ReadingRecord where bookTitle=:bookTitle")
    fun deleteReadingRecord(bookTitle: String): Int

    @Query("delete from ReadingRecord where bookId not in (select id from Book)")
    fun cleanReadingBook(): Int
}
