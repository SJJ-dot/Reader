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
import com.sjianjun.reader.R
import com.sjianjun.reader.databinding.FragmentSettingsBinding
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.repository.DbFactory
import com.sjianjun.reader.utils.HttpServiceHelper
import com.sjianjun.reader.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sjj.alog.Log
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

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
            File(Environment.getExternalStorageDirectory(), "reader")
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