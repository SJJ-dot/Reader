package com.sjianjun.reader.module.reader

import android.os.Bundle
import android.text.Layout
import android.text.StaticLayout
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.R
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.ReadingRecord
import com.sjianjun.reader.databinding.DialogBookIntroPickerBinding
import com.sjianjun.reader.databinding.ItemBookIntroSourceBinding
import com.sjianjun.reader.event.EventBus
import com.sjianjun.reader.event.EventKey
import com.sjianjun.reader.repository.DbFactory
import com.sjianjun.reader.utils.color
import com.sjianjun.reader.utils.format
import com.sjianjun.reader.view.click
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BookIntroPickerDialogFragment : DialogFragment() {
    private var binding: DialogBookIntroPickerBinding? = null
    private val introAdapter = BookIntroSourceAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_book_intro_picker, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding = DialogBookIntroPickerBinding.bind(view)
        binding?.rvIntroSources?.layoutManager = LinearLayoutManager(requireContext())
        binding?.rvIntroSources?.adapter = introAdapter

        binding?.btnClearIntro?.setOnClickListener {
            lifecycleScope.launch {
                saveBookIntroOverride(null)
                dismissAllowingStateLoss()
            }
        }

        binding?.btnSaveIntro?.click {
            val item = introAdapter.data.getOrNull(introAdapter.selectedPos)
            if (item == null) {
                dismissAllowingStateLoss()
                return@click
            }
            lifecycleScope.launch {
                saveBookIntroOverride(item.intro)
                dismissAllowingStateLoss()
            }
        }

        loadIntroSources()
    }

    override fun onStart() {
        super.onStart()
        val metrics = resources.displayMetrics
        val width = (metrics.widthPixels * 0.96f).toInt()
        dialog?.window?.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private fun loadIntroSources() {
        val title = requireArguments().getString(ARG_BOOK_TITLE).orEmpty()
        if (title.isBlank()) return
        lifecycleScope.launch {
            val sourceItems = withIo {
                val recordDao = DbFactory.db.readingRecordDao()
                val record = recordDao.getReadingRecordSync(title)
                val books = DbFactory.db.bookDao().getAllSourceBooksByTitle(title).first()
                val sourceDao = DbFactory.db.bookSourceDao()
                books.forEach {
                    it.bookSource = sourceDao.getBookSourceById(it.bookSourceId)
                    it.record = record
                }
                books
            }
            introAdapter.submitList(sourceItems)
        }
    }

    private suspend fun saveBookIntroOverride(intro: String?) = withIo {
        val title = requireArguments().getString(ARG_BOOK_TITLE).orEmpty()
        val bookId = requireArguments().getString(ARG_BOOK_ID).orEmpty()
        if (title.isBlank() || bookId.isBlank()) return@withIo
        val recordDao = DbFactory.db.readingRecordDao()
        val record = recordDao.getReadingRecordSync(title) ?: ReadingRecord(title, bookId)
        record.bookId = bookId
        record.bookIntro = intro
        recordDao.insertReadingRecord(record)
        EventBus.post(EventKey.BOOK_INTRO_CHANGED, bookId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private class BookIntroSourceAdapter : RecyclerView.Adapter<BookIntroSourceAdapter.IntroSourceViewHolder>() {
        var selectedPos = -1
        val data = mutableListOf<Book>()

        fun submitList(list: List<Book>) {
            data.clear()
            data.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IntroSourceViewHolder {
            val b = ItemBookIntroSourceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return IntroSourceViewHolder(b)
        }

        override fun onBindViewHolder(holder: IntroSourceViewHolder, position: Int) {
            val item = data[position]
            val b = holder.binding
            b.tvIntroSource.text = item.bookSource?.name ?: "未知书源"
            val introText = item.intro.format(false).toString().replace("\n", " ")
            val displayText = introText.ifBlank { "暂无简介" }
            b.tvIntroContent.tag = displayText
            b.tvIntroContent.text = displayText
            b.tvIntroContent.post {
                if (b.tvIntroContent.tag == displayText) {
                    b.tvIntroContent.text = buildMiddleEllipsizedText(b.tvIntroContent, displayText, 4)
                }
            }

            val selected = if (selectedPos == -1) {
                item.record?.bookIntro != null && item.intro == item.record?.bookIntro
            } else {
                position == selectedPos
            }

            if (selected) {
                b.root.setCardBackgroundColor(R.color.dn_color_primary.color())
                b.tvIntroSource.setTextColor(R.color.mdr_textWhite_text.color())
                b.tvIntroContent.setTextColor(R.color.mdr_textWhite_text.color())
            } else {
                b.root.setCardBackgroundColor(R.color.dn_background_card.color())
                b.tvIntroSource.setTextColor(R.color.dn_text_color_black.color())
                b.tvIntroContent.setTextColor(R.color.dn_text_color_black.color())
            }

            b.root.setOnClickListener {
                selectedPos = position
                notifyDataSetChanged()
            }
        }

        override fun getItemCount(): Int = data.size

        private fun buildMiddleEllipsizedText(textView: TextView, source: String, maxLines: Int): String {
            if (source.isBlank()) return source
            val width = textView.width - textView.paddingLeft - textView.paddingRight
            if (width <= 0) return source
            if (lineCount(textView, source, width) <= maxLines) return source

            var low = 2
            var high = source.length
            var best = middleEllipsize(source, 2)
            while (low <= high) {
                val keep = (low + high) / 2
                val candidate = middleEllipsize(source, keep)
                if (lineCount(textView, candidate, width) <= maxLines) {
                    best = candidate
                    low = keep + 1
                } else {
                    high = keep - 1
                }
            }
            return best
        }

        private fun middleEllipsize(text: String, keepChars: Int): String {
            if (text.length <= keepChars) return text
            val safeKeep = keepChars.coerceAtLeast(2)
            val startLen = safeKeep / 2
            val endLen = safeKeep - startLen
            return text.take(startLen) + "..." + text.takeLast(endLen)
        }

        private fun lineCount(textView: TextView, text: String, width: Int): Int {
            val layout = StaticLayout(
                text,
                textView.paint,
                width,
                Layout.Alignment.ALIGN_NORMAL,
                textView.lineSpacingMultiplier,
                textView.lineSpacingExtra,
                textView.includeFontPadding,
            )
            return layout.lineCount
        }

        class IntroSourceViewHolder(val binding: ItemBookIntroSourceBinding) : RecyclerView.ViewHolder(binding.root)
    }

    companion object {
        const val TAG = "BookIntroPickerDialogFragment"
        private const val ARG_BOOK_TITLE = "arg_book_title"
        private const val ARG_BOOK_ID = "arg_book_id"

        fun newInstance(bookTitle: String, bookId: String): BookIntroPickerDialogFragment {
            return BookIntroPickerDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_BOOK_TITLE, bookTitle)
                    putString(ARG_BOOK_ID, bookId)
                }
            }
        }
    }
}

