package com.sjianjun.reader.module.bookcity

import com.sjianjun.reader.bean.BookmarkEntity
import com.sjianjun.reader.bean.BookmarkFolderEntity
import com.sjianjun.reader.repository.DbFactory
import com.sjianjun.reader.repository.dao.BrowserDataDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.UUID

/**
 * 浏览器收藏夹（Room 存储，见 [BrowserDataDao]）。
 *
 * 支持多级文件夹：
 * - 每条数据都有「真实 ID」([Bookmark.id] / [Folder.id])，显示名称可随意修改而不影响引用；
 * - 内置「书源」文件夹使用固定 ID [BOOK_SOURCE_FOLDER_ID]，默认名称"书源"、可改名、不可删除。
 *
 * 同时保留旧扁平接口（[list]/[add]/[update]/[remove]/[move]）以兼容现有 UI；
 * 新文件夹树 UI 请使用 [folders]/[bookmarksIn]/[childrenFolders]/[addFolder] 等接口。
 */
object BookmarkStore {

    /** 根目录（不是一个真实的 Folder 对象） */
    const val ROOT = "root"

    /** 内置书源文件夹的真实 ID（固定不变，仅显示名称可修改） */
    const val BOOK_SOURCE_FOLDER_ID = "folder_book_source"

    data class Bookmark(
        val id: String,
        val title: String,
        val url: String,
        val folderId: String,
        val time: Long,
    )

    data class Folder(
        val id: String,
        val name: String,
        val parentId: String,
        val builtin: Boolean,
        val time: Long,
    )

    private val dao: BrowserDataDao get() = DbFactory.db.browserDataDao()

    private fun <T> io(block: () -> T): T = runBlocking(Dispatchers.IO) { block() }

    /* ===================== 兼容旧 UI 的扁平接口 ===================== */

    fun list(): List<Bookmark> = io { dao.getAllBookmarks().map { it.toModel() } }

    fun add(title: String, url: String, folderId: String = ROOT) {
        if (url.isBlank()) return
        io {
            if (dao.countByUrl(url) > 0) return@io
            val fid = folderId.takeIf { it == ROOT || dao.getFolder(it) != null } ?: ROOT
            val maxOrder = dao.getAllBookmarks().maxOfOrNull { it.orderIndex } ?: -1
            dao.insertBookmark(
                BookmarkEntity(UUID.randomUUID().toString(), title.ifBlank { url }, url, fid, maxOrder + 1)
            )
        }
    }

    fun update(url: String, title: String, newUrl: String) {
        if (url.isBlank() || newUrl.isBlank()) return
        io {
            val b = dao.getAllBookmarks().firstOrNull { it.url == url } ?: return@io
            dao.updateBookmark(b.copy(title = title.ifBlank { newUrl }, url = newUrl))
        }
    }

    fun updateById(id: String, title: String, newUrl: String) {
        if (newUrl.isBlank()) return
        io {
            val b = dao.getBookmark(id) ?: return@io
            dao.updateBookmark(b.copy(title = title.ifBlank { newUrl }, url = newUrl))
        }
    }

    fun remove(url: String) {
        io { dao.getAllBookmarks().firstOrNull { it.url == url }?.let { dao.deleteBookmark(it.id) } }
    }

    fun removeById(id: String) {
        io { dao.deleteBookmark(id) }
    }

    /** 调整全部收藏的顺序（拖动排序时使用） */
    fun move(from: Int, to: Int) {
        if (from == to) return
        io {
            val all = dao.getAllBookmarks().toMutableList()
            if (from !in all.indices || to !in all.indices) return@io
            val item = all.removeAt(from)
            all.add(to, item)
            all.forEachIndexed { i, b -> if (b.orderIndex != i) dao.updateBookmark(b.copy(orderIndex = i)) }
        }
    }

    fun contains(url: String): Boolean = io { dao.countByUrl(url) > 0 }

    fun clear() = io {
        val folders = dao.getAllFolders()
        dao.deleteBookmarksByFolders(folders.map { it.id })
        dao.getAllBookmarks().filter { it.folderId == ROOT }.forEach { dao.deleteBookmark(it.id) }
        folders.filterNot { it.builtin }.forEach { dao.deleteFolders(listOf(it.id)) }
    }

    fun count(): Int = io { dao.countBookmarks() }

    /* ===================== 文件夹接口（供新 UI 使用） ===================== */

    fun folders(): List<Folder> = io { dao.getAllFolders().map { it.toModel() } }

    fun folder(id: String): Folder? = io { dao.getFolder(id)?.toModel() }

    fun bookmarksIn(folderId: String): List<Bookmark> = io { dao.getBookmarks(folderId).map { it.toModel() } }

    fun childrenFolders(folderId: String): List<Folder> =
        io { dao.getAllFolders().filter { it.parentId == folderId }.map { it.toModel() } }

    fun addFolder(name: String, parentId: String = ROOT): String = io {
        val id = UUID.randomUUID().toString()
        val pid = parentId.takeIf { it == ROOT || dao.getFolder(it) != null } ?: ROOT
        dao.insertFolder(BookmarkFolderEntity(id, name.trim(), pid))
        id
    }

    fun renameFolder(id: String, name: String) {
        if (name.isBlank()) return
        io { dao.renameFolder(id, name.trim()) }
    }

    /** 删除文件夹（含子孙文件夹与其中所有收藏）；内置文件夹不可删除。返回是否删除成功 */
    fun removeFolder(id: String): Boolean = io {
        if (id == ROOT || dao.getFolder(id) == null) false
        else {
            dao.removeFoldersCascade(listOf(id))
            true
        }
    }

    /** 把收藏移动到目标文件夹（插入到目标文件夹末尾） */
    fun moveBookmark(id: String, targetFolderId: String) = io {
        val b = dao.getBookmark(id) ?: return@io
        val target = if (targetFolderId == ROOT || dao.getFolder(targetFolderId) != null) targetFolderId else ROOT
        val maxOrder = dao.getBookmarks(target).maxOfOrNull { it.orderIndex } ?: -1
        dao.updateBookmark(b.copy(folderId = target, orderIndex = maxOrder + 1))
    }

    /** 按可见顺序批量设置某文件夹内收藏的排序（拖动排序时使用） */
    fun setOrder(ids: List<String>) {
        if (ids.isEmpty()) return
        io {
            ids.forEachIndexed { i, id ->
                dao.getBookmark(id)?.let { if (it.orderIndex != i) dao.updateBookmark(it.copy(orderIndex = i)) }
            }
        }
    }

    private fun BookmarkEntity.toModel() = Bookmark(id, title, url, folderId, time)
    private fun BookmarkFolderEntity.toModel() = Folder(id, name, parentId, builtin, time)
}
