package com.sjianjun.reader.module.setting

import android.content.Context
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sjianjun.reader.databinding.DialogCreateFolderBinding
import com.sjianjun.reader.databinding.DialogDirectoryPickerBinding
import com.sjianjun.reader.databinding.ItemDirectoryPickerEntryBinding
import com.sjianjun.reader.utils.toast
import java.io.File
import java.util.Locale

class DirectoryPickerDialog(
    private val context: Context,
    startDirectory: File,
    private val onDirectorySelected: (File) -> Unit,
    private val onCancelled: (() -> Unit)? = null,
) {
    private val rootDirectory: File = Environment.getExternalStorageDirectory().absoluteFile
    private var currentDirectory: File = startDirectory.absoluteFile

    fun show() {
        if (!currentDirectory.exists()) {
            currentDirectory.mkdirs()
        }
        showDirectory(currentDirectory.absoluteFile)
    }

    private fun showDirectory(directory: File) {
        currentDirectory = directory.absoluteFile
        val entries = buildEntries(directory)
        val binding = DialogDirectoryPickerBinding.inflate(LayoutInflater.from(context))
        binding.pathText.text = directory.absolutePath
        binding.directoryList.adapter = object : ArrayAdapter<DirectoryEntry>(
            context,
            0,
            entries
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val itemBinding = if (convertView == null) {
                    ItemDirectoryPickerEntryBinding.inflate(LayoutInflater.from(context), parent, false)
                } else {
                    ItemDirectoryPickerEntryBinding.bind(convertView)
                }
                val entry = getItem(position) ?: return itemBinding.root
                itemBinding.titleText.text = entry.label
                itemBinding.subtitleText.text = entry.directory.absolutePath
                itemBinding.iconText.text = if (entry.label.startsWith("返回上一级")) "⬆️" else "📁"
                return itemBinding.root
            }
        }
        binding.directoryList.setOnItemClickListener { _, _, position, _ ->
            val entry = entries.getOrNull(position) ?: return@setOnItemClickListener
            showDirectory(entry.directory)
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            .create()
        dialog.setOnCancelListener {
            onCancelled?.invoke()
        }
        binding.cancelButton.setOnClickListener {
            dialog.cancel()
        }
        binding.rootButton.setOnClickListener {
            dialog.dismiss()
            showDirectory(rootDirectory)
        }
        binding.newFolderButton.setOnClickListener {
            dialog.dismiss()
            showCreateFolderDialog(directory)
        }
        binding.selectButton.setOnClickListener {
            dialog.dismiss()
            onDirectorySelected(directory)
        }
        dialog.show()
    }

    private fun showCreateFolderDialog(parentDirectory: File) {
        val binding = DialogCreateFolderBinding.inflate(LayoutInflater.from(context))
        binding.pathText.text = parentDirectory.absolutePath
        val dialog: AlertDialog = MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            .create()
        binding.cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        binding.confirmButton.setOnClickListener {
            val folderName = binding.editView.text?.toString().orEmpty().trim()
            if (folderName.isBlank()) {
                toast("请输入文件夹名称")
                return@setOnClickListener
            }
            val targetDirectory = File(parentDirectory, folderName)
            if (targetDirectory.exists()) {
                toast("文件夹已存在")
                dialog.dismiss()
                showDirectory(targetDirectory)
                return@setOnClickListener
            }
            if (!targetDirectory.mkdirs()) {
                toast("创建文件夹失败")
                return@setOnClickListener
            }
            dialog.dismiss()
            showDirectory(targetDirectory)
        }
        dialog.show()
    }

    private fun buildEntries(directory: File): List<DirectoryEntry> {
        val entries = mutableListOf<DirectoryEntry>()
        directory.parentFile
            ?.takeIf { it.exists() }
            ?.let { parent ->
                if (!rootDirectory.absolutePath.contains(parent.absolutePath)) {
                    entries += DirectoryEntry("返回上一级..", parent.absoluteFile)
                }
            }

        val children = directory.listFiles()
            ?.asSequence()
            ?.filter { it.isDirectory }
            ?.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.lowercase(Locale.getDefault()) })
            ?.map { child -> DirectoryEntry(child.name, child.absoluteFile) }
            ?.toList()
            .orEmpty()

        entries += children
        return entries
    }

    private data class DirectoryEntry(
        val label: String,
        val directory: File,
    )
}

