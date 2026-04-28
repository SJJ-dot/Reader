package com.sjianjun.reader.repository.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sjianjun.reader.bean.ReplacementRule

@Dao
interface ReplacementRuleDao {
	@Query("SELECT * FROM ReplacementRule ORDER BY `order` ASC, id ASC")
	fun getAll(): List<ReplacementRule>

	@Query("SELECT * FROM ReplacementRule WHERE isEnabled = 1 ORDER BY `order` ASC, id ASC")
	fun getEnabled(): List<ReplacementRule>

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	fun insert(rule: ReplacementRule)

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	fun insertAll(rules: List<ReplacementRule>)

	@Delete
	fun delete(rule: ReplacementRule)

	@Query("DELETE FROM ReplacementRule WHERE id = :id")
	fun deleteById(id: String)
}