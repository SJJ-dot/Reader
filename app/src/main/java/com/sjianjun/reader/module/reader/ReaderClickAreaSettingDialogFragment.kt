package com.sjianjun.reader.module.reader

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sjianjun.reader.databinding.DialogReaderClickAreaSettingBinding
import com.sjianjun.reader.preferences.globalConfig

class ReaderClickAreaSettingDialogFragment : DialogFragment() {
    private var binding: DialogReaderClickAreaSettingBinding? = null
    private val cellViews by lazy {
        listOf(
            binding!!.clickAreaCell0,
            binding!!.clickAreaCell1,
            binding!!.clickAreaCell2,
            binding!!.clickAreaCell3,
            binding!!.clickAreaCell4,
            binding!!.clickAreaCell5,
            binding!!.clickAreaCell6,
            binding!!.clickAreaCell7,
            binding!!.clickAreaCell8,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogReaderClickAreaSettingBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val root = binding?.root!!
        ViewCompat.setOnApplyWindowInsetsListener(view) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val cutout = insets.displayCutout
            val safeLeft = maxOf(systemBars.left, cutout?.safeInsetLeft ?: 0)
            val safeTop = maxOf(systemBars.top, cutout?.safeInsetTop ?: 0)
            val safeRight = maxOf(systemBars.right, cutout?.safeInsetRight ?: 0)
            val safeBottom = maxOf(systemBars.bottom, cutout?.safeInsetBottom ?: 0)
            root.setPadding(safeLeft, safeTop, safeRight, safeBottom)
            insets
        }
        binding?.btnClose?.setOnClickListener { dismissAllowingStateLoss() }
        binding?.rootOverlay?.setOnClickListener { dismissAllowingStateLoss() }
        binding?.panel?.setOnClickListener { }
        cellViews.forEachIndexed { index, textView ->
            textView.setOnClickListener { showActionChooser(index) }
        }
        globalConfig.readerClickAreaActions.observe(viewLifecycleOwner){
            renderCells()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog?.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            setCanceledOnTouchOutside(true)
        }
    }

    private fun renderCells() {
        val actions = ReaderClickAreaAction.normalize(globalConfig.readerClickAreaActions.value)
        cellViews.forEachIndexed { index, textView ->
            textView.text = buildCellText(actions[index])
        }
    }

    private fun buildCellText(action: Int): String {
        return ReaderClickAreaAction.label(action)
    }

    private fun showActionChooser(index: Int) {
        val actions = ReaderClickAreaAction.normalize(globalConfig.readerClickAreaActions.value).toMutableList()
        val currentAction = actions[index]
        val options = ReaderClickAreaAction.dialogOptions()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("设置${ReaderClickAreaAction.cellLabel(index)}点击功能")
            .setSingleChoiceItems(options, ReaderClickAreaAction.optionIndexForAction(currentAction)) { dialog, which ->
                actions[index] = ReaderClickAreaAction.actionForOption(which)
                globalConfig.readerClickAreaActions.postValue(actions)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    companion object {
        const val TAG = "ReaderClickAreaSettingDialogFragment"
    }
}

