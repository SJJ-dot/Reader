package com.sjianjun.reader.module.reader

import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sjianjun.reader.R
import com.sjianjun.reader.bean.ReplacementRule
import com.sjianjun.reader.databinding.DialogReplacementRuleListBinding
import com.sjianjun.reader.databinding.ItemReplacementRuleBinding
import com.sjianjun.reader.event.EventBus
import com.sjianjun.reader.event.EventKey
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.repository.ReplacementRuleUseCase
import com.sjianjun.reader.utils.applyEdgeToEdgeDialogBar
import kotlinx.coroutines.launch
import sjj.novel.view.reader.page.PageStyle

class ReplacementRuleListDialogFragment : DialogFragment() {
    private var binding: DialogReplacementRuleListBinding? = null
    /** 新建规则时的默认作用域（书籍主域名等）；由调用方通过 [newInstance] 传入 */
    private val defaultScope: String? get() = arguments?.getString(ARG_DEFAULT_SCOPE)
    /**
     * 当前阅读上下文（书名、URL、章节标题拼接而成）。
     * 列表中 scope 不包含 / excludeScope 命中的规则将显示为禁用态。
     */
    private val activeScopeText: String? get() = arguments?.getString(ARG_ACTIVE_SCOPE_TEXT)
    private val adapter by lazy {
        RuleAdapter(
            onMoveUp = { rule -> moveRule(rule, true) },
            onMoveDown = { rule -> moveRule(rule, false) },
            onEdit = { rule -> openEditor(rule.id) },
            onDelete = { rule -> deleteRule(rule) },
            onToggleEnabled = { rule -> toggleRule(rule) },
            activeScopeText = activeScopeText,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, R.style.dialog_NoActionBar)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogReplacementRuleListBinding.inflate(inflater, container, false)
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
            root.setPadding(safeLeft, 0, safeRight, safeBottom)
            insets
        }
        binding?.rootOverlay?.setOnClickListener { dismissAllowingStateLoss() }
        binding?.panel?.setOnClickListener { }
        binding?.btnClose?.setOnClickListener { dismissAllowingStateLoss() }
        binding?.btnNewRule?.setOnClickListener { openEditor(null) }
        binding?.recyclerView?.layoutManager = LinearLayoutManager(requireContext())
        binding?.recyclerView?.adapter = adapter
        parentFragmentManager.setFragmentResultListener(ReplacementRuleEditDialogFragment.RESULT_KEY, viewLifecycleOwner) { _, _ ->
            loadRules()
            EventBus.post(EventKey.REPLACEMENT_RULES_CHANGED)
        }
        loadRules()
    }

    override fun onStart() {
        super.onStart()
        val window = dialog?.window ?: return
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        window.setGravity(Gravity.BOTTOM)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setBackgroundDrawable(ColorDrawable(ContextCompat.getColor(requireContext(), R.color.dn_background)))
        val pageStyle = PageStyle.getStyle(globalConfig.readerPageStyle.value)
        applyEdgeToEdgeDialogBar(!pageStyle.isDark)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            setCanceledOnTouchOutside(true)
        }
    }

    private fun loadRules() {
        viewLifecycleOwner.lifecycleScope.launch {
            val rules = ReplacementRuleUseCase.getAllRules()
            val sorted = sortRulesForDisplay(rules)
            adapter.submitList(sorted)
            binding?.tvEmpty?.visibility = if (rules.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    /**
     * 显示排序：
     * 1. 启用 且 作用域命中 → 顶部，按 order 升序
     * 2. 启用 但 作用域不命中 → 中部，按 order 升序
     * 3. 禁用 → 底部，按 order 升序
     */
    private fun sortRulesForDisplay(rules: List<ReplacementRule>): List<ReplacementRule> {
        val scope = activeScopeText
        return rules.sortedWith(
            compareBy<ReplacementRule> { if (it.isEnabled) 0 else 1 }
                .thenBy { if (scope == null) 0 else if (ReplacementRuleUseCase.matchesScope(it, scope)) 0 else 1 }
                .thenBy { it.order }
                .thenBy { it.id }
        )
    }

    private fun toggleRule(rule: ReplacementRule) {
        viewLifecycleOwner.lifecycleScope.launch {
            rule.isEnabled = !rule.isEnabled
            ReplacementRuleUseCase.saveRule(rule)
            loadRules()
            EventBus.post(EventKey.REPLACEMENT_RULES_CHANGED)
        }
    }

    private fun moveRule(rule: ReplacementRule, moveUp: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            val rules = ReplacementRuleUseCase.moveRule(rule.id, moveUp)
            adapter.submitList(rules)
            EventBus.post(EventKey.REPLACEMENT_RULES_CHANGED)
        }
    }

    private fun deleteRule(rule: ReplacementRule) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除规则")
            .setMessage("确定删除规则“${rule.name.ifBlank { rule.rule }}”吗？")
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    ReplacementRuleUseCase.deleteRule(rule)
                    loadRules()
                    EventBus.post(EventKey.REPLACEMENT_RULES_CHANGED)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openEditor(ruleId: String?) {
        // 新建规则时把默认作用域（如阅读模式当前页面的主域名）填入；编辑已有规则时不覆盖。
        val scope = if (ruleId.isNullOrBlank()) defaultScope else null
        ReplacementRuleEditDialogFragment.newInstance(ruleId, scope)
            .show(parentFragmentManager, ReplacementRuleEditDialogFragment.TAG)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private class RuleAdapter(
        private val onMoveUp: (ReplacementRule) -> Unit,
        private val onMoveDown: (ReplacementRule) -> Unit,
        private val onEdit: (ReplacementRule) -> Unit,
        private val onDelete: (ReplacementRule) -> Unit,
        private val onToggleEnabled: (ReplacementRule) -> Unit,
        private val activeScopeText: String?,
    ) : RecyclerView.Adapter<RuleAdapter.RuleViewHolder>() {
        private val data = mutableListOf<ReplacementRule>()

        fun submitList(rules: List<ReplacementRule>) {
            data.clear()
            data.addAll(rules)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RuleViewHolder {
            val binding = ItemReplacementRuleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return RuleViewHolder(binding)
        }

        override fun getItemCount(): Int = data.size

        override fun onBindViewHolder(holder: RuleViewHolder, position: Int) {
            holder.bind(data[position], position, data.lastIndex)
        }

        inner class RuleViewHolder(private val binding: ItemReplacementRuleBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(rule: ReplacementRule, position: Int, lastIndex: Int) {
                // 作用域命中检测：仅当调用方提供了 activeScopeText 时生效。
                val outOfScope = activeScopeText?.let { !ReplacementRuleUseCase.matchesScope(rule, it) } == true
                binding.tvRuleName.text = rule.name.ifBlank { rule.rule }
                binding.tvRuleState.text = buildString {
                    append(if (rule.isEnabled) "已启用" else "已禁用")
                    if (rule.isRegex) append(" · 正则匹配")
                    if (outOfScope) append(" · 作用域不匹配")
                }
                binding.tvRule.text = "规则：${rule.rule}"
                binding.tvReplace.text = "替换：${rule.replacement}"
                // 不在当前作用域内的规则视觉上置灰，但仍可编辑/删除/移动；点击切换启用照常生效，
                // 让用户能在排查时把它启用再修改 scope。
                binding.root.alpha = if (outOfScope) 0.5f else 1f
                binding.btnMoveUp.alpha = if (position == 0) 0.4f else 1f
                binding.btnMoveDown.alpha = if (position == lastIndex) 0.4f else 1f
                binding.btnMoveUp.setOnClickListener { if (position > 0) onMoveUp(rule) }
                binding.btnMoveDown.setOnClickListener { if (position < lastIndex) onMoveDown(rule) }
                binding.btnEdit.setOnClickListener { onEdit(rule) }
                binding.btnDelete.setOnClickListener { onDelete(rule) }
                // 点击 item 切换启用/禁用
                binding.root.setOnClickListener { onToggleEnabled(rule) }
            }
        }
    }

    companion object {
        const val TAG = "ReplacementRuleListDialogFragment"
        private const val ARG_DEFAULT_SCOPE = "defaultScope"
        private const val ARG_ACTIVE_SCOPE_TEXT = "activeScopeText"

        /**
         * @param defaultScope 新建规则时预填的作用域（通常是书籍主域名）
         * @param activeScopeText 当前阅读上下文（书名 + URL + 章节标题），用于在列表中把
         *   作用域不匹配的规则置灰显示。两个参数通常一并提供。
         */
        fun newInstance(defaultScope: String? = null, activeScopeText: String? = null): ReplacementRuleListDialogFragment {
            return ReplacementRuleListDialogFragment().apply {
                arguments = android.os.Bundle().apply {
                    if (!defaultScope.isNullOrBlank()) putString(ARG_DEFAULT_SCOPE, defaultScope)
                    if (!activeScopeText.isNullOrBlank()) putString(ARG_ACTIVE_SCOPE_TEXT, activeScopeText)
                }
            }
        }
    }
}

