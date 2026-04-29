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
import com.sjianjun.reader.repository.ReplacementRuleUseCase
import kotlinx.coroutines.launch

class ReplacementRuleListDialogFragment : DialogFragment() {
    private var binding: DialogReplacementRuleListBinding? = null
    private val adapter = RuleAdapter(
        onMoveUp = { rule -> moveRule(rule, true) },
        onMoveDown = { rule -> moveRule(rule, false) },
        onEdit = { rule -> openEditor(rule.id) },
        onDelete = { rule -> deleteRule(rule) },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogReplacementRuleListBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
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
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            setCanceledOnTouchOutside(true)
        }
    }

    private fun loadRules() {
        viewLifecycleOwner.lifecycleScope.launch {
            val rules = ReplacementRuleUseCase.getAllRules()
            adapter.submitList(rules)
            binding?.tvEmpty?.visibility = if (rules.isEmpty()) View.VISIBLE else View.GONE
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
        ReplacementRuleEditDialogFragment.newInstance(ruleId)
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
                binding.tvRuleName.text = rule.name.ifBlank { rule.rule }
                binding.tvRuleState.text = buildString {
                    append(if (rule.isEnabled) "已启用" else "已禁用")
                    if (rule.isRegex) append(" · 正则匹配")
                }
                binding.tvRule.text = "规则：${rule.rule}"
                binding.tvReplace.text = "替换：${rule.replacement}"
                binding.btnMoveUp.alpha = if (position == 0) 0.4f else 1f
                binding.btnMoveDown.alpha = if (position == lastIndex) 0.4f else 1f
                binding.btnMoveUp.setOnClickListener { if (position > 0) onMoveUp(rule) }
                binding.btnMoveDown.setOnClickListener { if (position < lastIndex) onMoveDown(rule) }
                binding.btnEdit.setOnClickListener { onEdit(rule) }
                binding.btnDelete.setOnClickListener { onDelete(rule) }
            }
        }
    }

    companion object {
        const val TAG = "ReplacementRuleListDialogFragment"
    }
}

