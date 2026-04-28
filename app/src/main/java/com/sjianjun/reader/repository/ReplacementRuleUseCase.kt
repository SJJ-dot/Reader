package com.sjianjun.reader.repository

import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.bean.ReplacementRule
import sjj.alog.Log

object ReplacementRuleUseCase {
    private val ruleDao get() = DbFactory.db.replacementRule()

    suspend fun getAllRules(): List<ReplacementRule> = withIo { normalizeOrders(ruleDao.getAll(), persist = false) }

    suspend fun getEnabledRules(): List<ReplacementRule> = withIo { normalizeOrders(ruleDao.getEnabled(), persist = false) }

    suspend fun saveRule(rule: ReplacementRule): ReplacementRule = withIo {
        val allRules = ruleDao.getAll().toMutableList()
        val existingIndex = allRules.indexOfFirst { it.id == rule.id }
        if (existingIndex >= 0) {
            rule.order = allRules[existingIndex].order
            allRules[existingIndex] = rule
        } else {
            rule.order = allRules.size
            allRules.add(rule)
        }
        val normalized = normalizeOrders(allRules, persist = true)
        return@withIo normalized.first { it.id == rule.id }
    }

    suspend fun deleteRule(rule: ReplacementRule) = withIo {
        val rules = ruleDao.getAll().filterNot { it.id == rule.id }
        ruleDao.deleteById(rule.id)
        normalizeOrders(rules, persist = true)
    }

    suspend fun moveRule(ruleId: String, moveUp: Boolean): List<ReplacementRule> = withIo {
        val rules = ruleDao.getAll().toMutableList()
        val index = rules.indexOfFirst { it.id == ruleId }
        if (index == -1) {
            return@withIo rules
        }
        val targetIndex = if (moveUp) index - 1 else index + 1
        if (targetIndex !in rules.indices) {
            return@withIo rules
        }
        val current = rules.removeAt(index)
        rules.add(targetIndex, current)
        rules.forEachIndexed { order, rule -> rule.order = order }
        ruleDao.insertAll(rules)
        return@withIo rules
    }

    fun applyRules(
        text: String?,
        rules: List<ReplacementRule>,
        book: Book?,
        chapter: Chapter?,
        applyToTitle: Boolean
    ): String {
        var result = text ?: return ""
        rules.sortedBy { it.order }.forEach { rule ->
            result = applyRule(result, rule, book, chapter, applyToTitle)
        }
        return result
    }

    private fun applyRule(
        text: String,
        rule: ReplacementRule,
        book: Book?,
        chapter: Chapter?,
        applyToTitle: Boolean
    ): String {
        if (!rule.isEnabled) {
            return text
        }
        if (applyToTitle && !rule.applyToTitle) {
            return text
        }
        if (!applyToTitle && !rule.applyToContent) {
            return text
        }
        if (!matchesScope(rule, book, chapter)) {
            return text
        }
        return try {
            if (rule.isRegex) {
                Regex(rule.rule).replace(text, rule.replacement)
            } else {
                text.replace(rule.rule, rule.replacement)
            }
        } catch (e: Throwable) {
            Log.e("应用净化规则失败：${rule}", e)
            text
        }
    }

    private fun matchesScope(rule: ReplacementRule, book: Book?, chapter: Chapter?): Boolean {
        val title = book?.title.orEmpty()
        val url = book?.url.orEmpty()
        val chapterTitle = chapter?.title.orEmpty()
        val searchText = listOf(title, url, chapterTitle).joinToString("\n")

        val scope = rule.scope?.trim().orEmpty()
        if (scope.isNotEmpty() && !searchText.contains(scope, ignoreCase = true)) {
            return false
        }

        val excludeScope = rule.excludeScope?.trim().orEmpty()
        if (excludeScope.isNotEmpty() && searchText.contains(excludeScope, ignoreCase = true)) {
            return false
        }
        return true
    }

    private suspend fun normalizeOrders(rules: List<ReplacementRule>, persist: Boolean): List<ReplacementRule> = withIo {
        val normalized = rules.sortedWith(compareBy<ReplacementRule> { it.order }.thenBy { it.id }).mapIndexed { index, rule ->
            rule.apply { order = index }
        }
        if (persist) {
            ruleDao.insertAll(normalized)
        }
        return@withIo normalized
    }
}

