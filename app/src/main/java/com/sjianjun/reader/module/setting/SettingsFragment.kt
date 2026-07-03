package com.sjianjun.reader.module.setting

import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.os.Process
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import com.sjianjun.reader.BuildConfig
import com.sjianjun.reader.R
import com.sjianjun.reader.databinding.FragmentSettingsBinding
import com.sjianjun.reader.module.bookcity.BrowserReadModeActivity
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.repository.DbFactory
import com.sjianjun.reader.utils.HttpServiceHelper
import com.sjianjun.reader.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import sjj.alog.Log
import java.io.File
import kotlin.coroutines.resume

class SettingsFragment : Fragment() {
    private val importDatabaseFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val context = context ?: return@registerForActivityResult
        uri ?: return@registerForActivityResult
        MaterialAlertDialogBuilder(context)
            .setTitle("导入数据库")
            .setMessage("将从所选数据库文件导入。\n当前数据库会被覆盖，是否继续？")
            .setNegativeButton("取消", null)
            .setPositiveButton("覆盖导入") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            DbFactory.importDatabaseFromUri(uri)
                        }
                    }.onSuccess {
                        toast("数据库导入成功，应用即将重启")
                        restartApp()
                    }.onFailure {
                        Log.e("数据库导入失败：${it.message ?: "未知错误"}", it)
                        toast("数据库导入失败：${it.message ?: "未知错误"}")
                    }
                }
            }
            .show()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        FragmentSettingsBinding.bind(view).apply {
            databaseLocationValue.text = globalConfig.databaseStorageDir ?: "内部存储"

            HttpServiceHelper.isRunning.observe(viewLifecycleOwner) {
                debugServiceSwitch.isChecked = it
            }
            debugServiceSwitch.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    if (HttpServiceHelper.isRunning.value != true) {
                        HttpServiceHelper.startHttpServer()
                    }
                } else {
                    HttpServiceHelper.stopHttpServer()
                }
            }

            databaseChangeLocation.setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    handleDatabaseLocationChange()
                }
            }
            databaseExport.setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    shareDatabaseBackup()
                }
            }
            databaseImport.setOnClickListener {
                importDatabaseFileLauncher.launch(arrayOf("application/octet-stream", "application/x-sqlite3", "application/vnd.sqlite3", "*/*"))
            }

            globalConfig.autoCheckUpdate.observe(viewLifecycleOwner) {
                autoCheckUpdateSwitch.isChecked = it
            }
            autoCheckUpdateSwitch.setOnCheckedChangeListener { _, isChecked ->
                globalConfig.autoCheckUpdate.postValue(isChecked)
            }

            globalConfig.autoBrowserReadMode.observe(viewLifecycleOwner) {
                autoBrowserReadModeSwitch.isChecked = it
            }
            autoBrowserReadModeSwitch.setOnCheckedChangeListener { _, isChecked ->
                globalConfig.autoBrowserReadMode.postValue(isChecked)
            }

            // 浏览器阅读模式缓存
            browserReadCacheSizeInput.setText(globalConfig.browserReadModeCacheSizeMB.toString())
            browserReadCacheSizeInput.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) commitBrowserReadCacheSize()
            }
            browserReadCacheClear.setOnClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("清除阅读模式缓存")
                    .setMessage("将清空已离线的阅读模式提取内容，下次打开需要重新加载。是否继续？")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("清除") { _, _ ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            runCatching {
                                BrowserReadModeActivity.clearDiskCache()
                            }.onSuccess {
                                refreshBrowserReadCacheSize()
                                toast("已清除")
                            }.onFailure {
                                Log.e("清除阅读模式缓存失败", it)
                                toast("清除失败：${it.message ?: "未知错误"}")
                            }
                        }
                    }
                    .show()
            }
            refreshBrowserReadCacheSize()

            appCacheClear.setOnClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("清除应用缓存")
                    .setMessage("将清除图片缓存、网页缓存/本地存储、阅读3.0规则缓存、下载缓存和临时导出/分享文件。\n不会删除书籍、章节内容、书源、设置或数据库，也不会主动清除 Cookie。\n网页和图片下次打开需要重新加载，部分站点本地状态可能重置。是否继续？")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("清除") { _, _ ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            appCacheClear.isEnabled = false
                            appCacheTotalSize.text = "当前占用：清理中..."
                            runCatching {
                                SettingsCacheHelper.clearCaches(requireContext())
                            }.onSuccess {
                                refreshAppCacheStats()
                                toast("已清除应用缓存")
                            }.onFailure {
                                Log.e("清除应用缓存失败", it)
                                toast("清除失败：${it.message ?: "未知错误"}")
                                refreshAppCacheStats()
                            }
                            appCacheClear.isEnabled = true
                        }
                    }
                    .show()
            }
            refreshAppCacheStats()

            // CPU/内存信息显示开关
            globalConfig.showCpuMemInfo.observe(viewLifecycleOwner) {
                cpuMemSwitch.isChecked = it
            }
            cpuMemSwitch.setOnCheckedChangeListener { _, isChecked ->
                globalConfig.showCpuMemInfo.postValue(isChecked)
            }
        }
    }

    private fun FragmentSettingsBinding.refreshBrowserReadCacheSize() {
        browserReadCacheCurrentSize.text = "默认 50MB，当前占用：计算中..."
        viewLifecycleOwner.lifecycleScope.launch {
            val size = withContext(Dispatchers.IO) {
                BrowserReadModeActivity.diskCacheSize()
            }
            browserReadCacheCurrentSize.text = "默认 50MB，当前占用：${formatBytes(size)}"
        }
    }

    private fun FragmentSettingsBinding.refreshAppCacheStats() {
        appCacheTotalSize.text = "当前占用：计算中..."
        appCacheImageSize.text = "图片缓存：计算中..."
        appCacheWebviewSize.text = "网页缓存：计算中..."
        appCacheJsSize.text = "阅读3.0规则缓存：计算中..."
        appCacheTempSize.text = "临时文件：计算中..."
        viewLifecycleOwner.lifecycleScope.launch {
            val stats = SettingsCacheHelper.calculateStats(requireContext().applicationContext)
            appCacheTotalSize.text = "当前占用：${formatBytes(stats.totalBytes)}"
            appCacheImageSize.text = "图片缓存：${formatBytes(stats.imageBytes)}"
            appCacheWebviewSize.text = "网页缓存：${formatBytes(stats.webViewBytes)}"
            appCacheJsSize.text = "阅读3.0规则缓存：${formatBytes(stats.jsBytes)}"
            appCacheTempSize.text = "临时文件：${formatBytes(stats.tempBytes)}"
        }
    }

    private fun formatBytes(bytes: Long): String {
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        return when {
            bytes >= gb -> String.format(java.util.Locale.getDefault(), "%.2f GB", bytes / gb)
            bytes >= mb -> String.format(java.util.Locale.getDefault(), "%.2f MB", bytes / mb)
            bytes >= kb -> String.format(java.util.Locale.getDefault(), "%.2f KB", bytes / kb)
            else -> "${bytes} B"
        }
    }

    private fun FragmentSettingsBinding.commitBrowserReadCacheSize() {
        val raw = browserReadCacheSizeInput.text?.toString()?.trim().orEmpty()
        val value = raw.toIntOrNull()
        val current = globalConfig.browserReadModeCacheSizeMB
        if (value == null || value < 1) {
            toast("请输入有效的缓存大小（MB）")
            browserReadCacheSizeInput.setText(current.toString())
            return
        }
        val clamped = value.coerceIn(1, 10240)
        if (clamped != value) {
            browserReadCacheSizeInput.setText(clamped.toString())
        }
        if (clamped != current) {
            globalConfig.browserReadModeCacheSizeMB = clamped
            BrowserReadModeActivity.resetDiskCache()
            toast("已保存")
        }
    }

    private suspend fun handleDatabaseLocationChange() {
        if (useInternalStorage() ?: return) {
            switchDatabaseStorage(DbFactory.getInternalDatabaseDir())
        } else {
            if (!awaitExternalStoragePermission()) {
                toast("请先授予文件管理权限")
                return
            }
            val directory = awaitDirectorySelection() ?: return
            switchDatabaseStorage(directory)
        }
    }

    private suspend fun useInternalStorage(): Boolean? = suspendCancellableCoroutine { continuation ->
        val context = context ?: run {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        MaterialAlertDialogBuilder(context)
            .setTitle("选择数据库存储位置")
            .setItems(arrayOf("内部存储", "外部存储（卸载保留数据）")) { dialog, which ->
                dialog.dismiss()
                if (!continuation.isActive) return@setItems
                continuation.resume(which == 0)
            }
            .setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss()
                if (continuation.isActive) continuation.resume(null)
            }
            .setOnCancelListener {
                if (continuation.isActive) continuation.resume(null)
            }
            .show()
    }

    private suspend fun awaitDirectorySelection(): File? = suspendCancellableCoroutine<File?> { continuation ->
        val context = context ?: run {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        val configuredPath = globalConfig.databaseStorageDir.orEmpty().trim()
        val dir = if (configuredPath.isBlank()) {
            File(Environment.getExternalStorageDirectory(), if (BuildConfig.DEBUG) "reader_debug" else "reader")
        } else {
            File(configuredPath)
        }
        val picker = DirectoryPickerDialog(
            context,
            dir,
            { directory ->
                if (continuation.isActive) continuation.resume(directory)
            },
            {
                if (continuation.isActive) continuation.resume(null)
            }
        )
        picker.show()
    }

    private suspend fun useExistingDatabase(): Boolean? = suspendCancellableCoroutine { continuation ->
        val context = context ?: run {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        MaterialAlertDialogBuilder(context)
            .setTitle("目录中已存在数据库")
            .setMessage("直接切换数据库，或者用当前数据库覆盖它。")
            .setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss()
                if (continuation.isActive) continuation.resume(null)
            }
            .setNeutralButton("切换") { dialog, _ ->
                dialog.dismiss()
                if (continuation.isActive) continuation.resume(true)
            }
            .setPositiveButton("覆盖") { dialog, _ ->
                dialog.dismiss()
                if (continuation.isActive) continuation.resume(false)
            }
            .setOnCancelListener {
                if (continuation.isActive) continuation.resume(null)
            }
            .show()
    }

    private suspend fun awaitConfirm(title: String, message: String, positiveText: String): Boolean =
        suspendCancellableCoroutine { continuation ->
            val context = context ?: run {
                continuation.resume(false)
                return@suspendCancellableCoroutine
            }
            MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("取消") { dialog, _ ->
                    dialog.dismiss()
                    if (continuation.isActive) continuation.resume(false)
                }
                .setPositiveButton(positiveText) { dialog, _ ->
                    dialog.dismiss()
                    if (continuation.isActive) continuation.resume(true)
                }
                .setOnCancelListener {
                    if (continuation.isActive) continuation.resume(false)
                }
                .show()
        }

    private suspend fun shareDatabaseBackup() {
        val context = context ?: return
        if (!awaitConfirm(
                title = "分享数据库备份",
                message = "将生成数据库备份文件，并通过系统分享发送。是否继续？",
                positiveText = "分享"
            )
        ) {
            return
        }
        runCatching {
            withContext(Dispatchers.IO) {
                DbFactory.exportDatabaseToShareFile()
            }
        }.onSuccess { shareFile ->
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                shareFile,
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "小说阅读器数据库备份")
                putExtra(Intent.EXTRA_TITLE, shareFile.name)
                clipData = ClipData.newRawUri(shareFile.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooserIntent = Intent.createChooser(shareIntent, "分享数据库备份").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(chooserIntent)
        }.onFailure {
            Log.e("数据库分享失败：${it.message ?: "未知错误"}", it)
            toast("数据库分享失败：${it.message ?: "未知错误"}")
        }
    }

    private suspend fun awaitExternalStoragePermission(): Boolean {
        val context = context ?: return false
        if (PermissionLists.getManageExternalStoragePermission().isGrantedPermission(context)) {
            return true
        }
        val shouldRequestPermission = awaitConfirm(
            title = "需要文件管理权限",
            message = "切换数据库位置到外部存储需要授予文件管理权限，是否去授权？",
            positiveText = "去授权"
        )
        if (!shouldRequestPermission) {
            return false
        }
        return suspendCancellableCoroutine { continuation ->
            XXPermissions.with(this)
                .permission(PermissionLists.getManageExternalStoragePermission())
                .request { grantedList, _ ->
                    continuation.resume(grantedList.isNotEmpty())
                }
        }
    }

    private suspend fun switchDatabaseStorage(targetDir: File) {
        val old = DbFactory.getDatabaseFile()
        val new = File(targetDir, DbFactory.DB_NAME)
        if (old.absolutePath == new.absolutePath) {
            toast("数据库位置未改变")
            return
        }
        val useExistingDatabase = if (File(targetDir, DbFactory.DB_NAME).exists()) {
            useExistingDatabase() ?: return
        } else {
            false
        }
        if (!awaitConfirm(title = "切换数据库位置", message = "完成后应用会自动重启。", positiveText = "确定")) {
            return
        }
        runCatching {
            withContext(Dispatchers.IO) {
                DbFactory.switchDatabaseStorageToDirectory(targetDir, useExistingDatabase)
            }
        }.onSuccess {
            toast("数据库位置已切换，应用即将重启")
            restartApp()
        }.onFailure {
            Log.e("切换失败：${it.message ?: "未知错误"}", it)
            toast("切换失败：${it.message ?: "未知错误"}")
        }
    }


    private fun restartApp() {
        val context = context ?: return
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        } ?: return
        startActivity(launchIntent)
        activity?.finishAffinity()
        Process.killProcess(Process.myPid())
    }

}
