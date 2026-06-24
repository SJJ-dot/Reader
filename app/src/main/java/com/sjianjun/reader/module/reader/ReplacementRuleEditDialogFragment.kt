package com.sjianjun.reader.module.reader

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sjianjun.reader.R
import com.sjianjun.reader.bean.ReplacementRule
import com.sjianjun.reader.databinding.DialogReplacementRuleEditBinding
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.repository.ReplacementRuleUseCase
import com.sjianjun.reader.utils.applyEdgeToEdgeDialogBar
import com.sjianjun.reader.utils.toast
import kotlinx.coroutines.launch
import sjj.novel.view.reader.page.PageStyle

class ReplacementRuleEditDialogFragment : DialogFragment() {
    private var binding: DialogReplacementRuleEditBinding? = null
    private var editingRule: ReplacementRule = ReplacementRule()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, R.style.dialog_NoActionBar)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogReplacementRuleEditBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val root = binding?.root!!
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val cutout = insets.displayCutout
            val safeLeft = maxOf(systemBars.left, cutout?.safeInsetLeft ?: 0)
            val safeRight = maxOf(systemBars.right, cutout?.safeInsetRight ?: 0)
            val safeBottom = maxOf(systemBars.bottom, cutout?.safeInsetBottom ?: 0)
            val offset = (imeInsets.bottom - systemBars.bottom).coerceAtLeast(0)
            root.setPadding(safeLeft, 0, safeRight, safeBottom + offset)
            insets
        }
        binding?.rootOverlay?.setOnClickListener { hideKeyboard() }
        // 点击 ScrollView 或 panel 的空白处隐藏输入法（panel clickable 会拦截 ScrollView 的事件，需要两边都绑定）
        val hideKeyboard = View.OnClickListener { hideKeyboard() }
        binding?.editScrollView?.setOnClickListener(hideKeyboard)
        binding?.panel?.setOnClickListener(hideKeyboard)
        binding?.btnSave?.setOnClickListener { saveRule() }
        binding?.tvHelp?.setOnClickListener { showRegexHelpDialog() }
        loadRule()
    }

    private fun hideKeyboard() {
        val focus = dialog?.currentFocus ?: binding?.root ?: return
        val imm = focus.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(focus.windowToken, 0)
        focus.clearFocus()
    }

    override fun onStart() {
        super.onStart()
        val window = dialog?.window ?: return
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        window.setGravity(Gravity.BOTTOM)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        window.setBackgroundDrawable(ColorDrawable(ContextCompat.getColor(requireContext(), R.color.dn_background)))
        val pageStyle = PageStyle.getStyle(globalConfig.readerPageStyle.value)
        applyEdgeToEdgeDialogBar(!pageStyle.isDark)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            setCanceledOnTouchOutside(true)
        }
    }

    private fun loadRule() {
        val ruleId = arguments?.getString(ARG_RULE_ID)
        viewLifecycleOwner.lifecycleScope.launch {
            editingRule = if (ruleId.isNullOrBlank()) {
                ReplacementRule().apply {
                    arguments?.getString(ARG_DEFAULT_SCOPE)?.takeIf { it.isNotBlank() }?.let { scope = it }
                }
            } else {
                ReplacementRuleUseCase.getAllRules().firstOrNull { it.id == ruleId } ?: ReplacementRule()
            }
            renderRule(editingRule)
        }
    }

    private fun renderRule(rule: ReplacementRule) {
        val binding = binding ?: return
        binding.tvTitle.text = if (arguments?.getString(ARG_RULE_ID).isNullOrBlank()) "新建规则" else "编辑规则"
        binding.etName.setText(rule.name)
        binding.etRule.setText(rule.rule)
        binding.etReplacement.setText(rule.replacement)
        binding.etScope.setText(rule.scope.orEmpty())
        binding.etExcludeScope.setText(rule.excludeScope.orEmpty())
        binding.cbEnabled.isChecked = rule.isEnabled
        binding.cbRegex.isChecked = rule.isRegex
        binding.cbApplyTitle.isChecked = rule.applyToTitle
        binding.cbApplyContent.isChecked = rule.applyToContent
    }

    private fun collectRuleFromInput(): ReplacementRule {
        val binding = binding ?: return editingRule
        return editingRule.apply {
            name = binding.etName.text?.toString().orEmpty().trim()
            rule = binding.etRule.text?.toString().orEmpty()
            replacement = binding.etReplacement.text?.toString().orEmpty()
            scope = binding.etScope.text?.toString()?.trim().takeUnless { it.isNullOrEmpty() }
            excludeScope = binding.etExcludeScope.text?.toString()?.trim().takeUnless { it.isNullOrEmpty() }
            isEnabled = binding.cbEnabled.isChecked
            isRegex = binding.cbRegex.isChecked
            applyToTitle = binding.cbApplyTitle.isChecked
            applyToContent = binding.cbApplyContent.isChecked
        }
    }

    private fun saveRule() {
        val rule = collectRuleFromInput()
        if (rule.rule.isBlank()) {
            toast("请填写匹配规则")
            return
        }
        if (rule.isRegex) {
            try {
                Regex(rule.rule)
            } catch (e: Throwable) {
                toast("正则表达式无效：${e.message}")
                return
            }
        }
        if (!rule.applyToTitle && !rule.applyToContent) {
            toast("请至少勾选标题或内容其中一个作用范围")
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            editingRule = ReplacementRuleUseCase.saveRule(rule)
            parentFragmentManager.setFragmentResult(RESULT_KEY, bundleOf())
            toast("规则已保存")
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showRegexHelpDialog() {
        val examples = arrayOf(
            "模板1：替换“关键字1”到“关键字2”之间的文字(含换行)",
            "模板2：替换“关键字1”...“关键字2”...“关键字3”之间的文字(含换行)",
            "模板3：替换章节开始到“关键字”第一次出现之间的文字(含换行)",
            "模板4：替换最后一个“关键字”到文本结束之间的文字(含换行)"
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("常用正则模板（点击填充）")
            .setItems(examples) { _, which ->
                val binding = binding ?: return@setItems
                when (which) {
                    0 -> {
                        binding.etRule.setText("(?s)关键字1.*?关键字2")
                        binding.etReplacement.setText("测试1")
                        binding.cbRegex.isChecked = true
                    }
                    1 -> {
                        binding.etRule.setText("(?s)关键字1.*?关键字2.*?关键字3")
                        binding.etReplacement.setText("测试2")
                        binding.cbRegex.isChecked = true
                    }
                    2 -> {
                        binding.etRule.setText("(?s)\\A.*?关键字")
                        binding.etReplacement.setText("")
                        binding.cbRegex.isChecked = true
                    }
                    3 -> {
                        binding.etRule.setText("(?s)关键字(?!.*关键字).*")
                        binding.etReplacement.setText("")
                        binding.cbRegex.isChecked = true
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    companion object {
        private const val ARG_RULE_ID = "ruleId"
        private const val ARG_DEFAULT_SCOPE = "defaultScope"
        const val TAG = "ReplacementRuleEditDialogFragment"
        const val RESULT_KEY = "replacement_rule_saved"

        fun newInstance(ruleId: String?, defaultScope: String? = null): ReplacementRuleEditDialogFragment {
            return ReplacementRuleEditDialogFragment().apply {
                arguments = bundleOf(
                    ARG_RULE_ID to ruleId,
                    ARG_DEFAULT_SCOPE to defaultScope,
                )
            }
        }
    }
}
