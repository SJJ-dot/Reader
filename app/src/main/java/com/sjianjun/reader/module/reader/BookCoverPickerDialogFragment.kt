package com.sjianjun.reader.module.reader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.R
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.ReadingRecord
import com.sjianjun.reader.databinding.DialogBookCoverPickerBinding
import com.sjianjun.reader.databinding.ItemBookCoverSourceBinding
import com.sjianjun.reader.event.EventBus
import com.sjianjun.reader.event.EventKey
import com.sjianjun.reader.repository.BookUseCase
import com.sjianjun.reader.repository.DbFactory
import com.sjianjun.reader.utils.color
import com.sjianjun.reader.utils.glide
import com.sjianjun.reader.view.click
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BookCoverPickerDialogFragment : DialogFragment() {
    private var binding: DialogBookCoverPickerBinding? = null
    private val coverAdapter = BookCoverSourceAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_book_cover_picker, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding = DialogBookCoverPickerBinding.bind(view)
        binding?.rvCoverSources?.layoutManager = GridLayoutManager(requireContext(), 3)
        binding?.rvCoverSources?.adapter = coverAdapter
        binding?.btnClearCover?.setOnClickListener {
            lifecycleScope.launch {
                saveBookCoverOverride(null)
                dismissAllowingStateLoss()
            }
        }
        binding?.btnSave?.click {
            val item = coverAdapter.data.getOrNull(coverAdapter.selectedPos)
            if (item == null) {
                dismissAllowingStateLoss()
                return@click
            }
            val cover = item.cover?.takeIf { it.isNotBlank() }
            if (cover == null) {
                Toast.makeText(requireContext(), "该书源暂无封面", Toast.LENGTH_SHORT).show()
                return@click
            }
            lifecycleScope.launch {
                saveBookCoverOverride(cover)
                dismissAllowingStateLoss()
            }
        }
        binding?.swipeRefresh?.setOnRefreshListener {
            lifecycleScope.launch {
                withIo {
                    val list = coverAdapter.data
                    list.map {
                        async { BookUseCase.reloadBookFromNet(it) }
                    }.awaitAll()
                }
                binding?.swipeRefresh?.isRefreshing = false
                loadCoverSources()
            }

        }

        loadCoverSources()
    }

    private fun loadCoverSources() {
        val title = requireArguments().getString(ARG_BOOK_TITLE).orEmpty()
        if (title.isBlank()) {
            return
        }
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
            coverAdapter.submitList(sourceItems)
        }
    }

    private suspend fun saveBookCoverOverride(cover: String?) = withIo {
        val title = requireArguments().getString(ARG_BOOK_TITLE).orEmpty()
        val bookId = requireArguments().getString(ARG_BOOK_ID).orEmpty()
        if (title.isBlank() || bookId.isBlank()) {
            return@withIo
        }
        val recordDao = DbFactory.db.readingRecordDao()
        val record = recordDao.getReadingRecordSync(title) ?: ReadingRecord(title, bookId)
        record.bookId = bookId
        record.bookCover = cover
        recordDao.insertReadingRecord(record)
        EventBus.post(EventKey.BOOK_COVER_CHANGED, bookId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private class BookCoverSourceAdapter : RecyclerView.Adapter<BookCoverSourceAdapter.CoverSourceViewHolder>() {
        var selectedPos = -1
        val data = mutableListOf<Book>()

        fun submitList(list: List<Book>) {
            data.clear()
            data.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CoverSourceViewHolder {
            val binding = ItemBookCoverSourceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return CoverSourceViewHolder(binding)
        }

        override fun onBindViewHolder(holder: CoverSourceViewHolder, position: Int) {
            val item = data[position]
            val binding = holder.binding
            binding.ivCover.glide(item.cover)
            binding.tvSource.text = item.bookSource?.name ?: "未知"
            val selected = if (selectedPos == -1) {
                item.cover == item.record?.bookCover && item.record?.bookCover?.isNotBlank() == true
            } else {
                position == selectedPos
            }
            if (selected) {
                binding.root.setBackgroundResource(R.color.dn_color_primary)
                binding.tvSource.setTextColor(R.color.mdr_textWhite_text.color())
            } else {
                binding.root.setBackgroundResource(R.color.dn_background_card)
                binding.tvSource.setTextColor(R.color.dn_text_color_black.color())
            }

            binding.root.setOnClickListener {
                selectedPos = position
                notifyDataSetChanged()
            }
        }

        override fun getItemCount(): Int = data.size

        class CoverSourceViewHolder(
            val binding: ItemBookCoverSourceBinding,
        ) : RecyclerView.ViewHolder(binding.root) {
            fun bind(item: Book, selected: Boolean, onClick: (Book) -> Unit) {

            }
        }
    }

    companion object {
        const val TAG = "BookCoverPickerDialogFragment"
        private const val ARG_BOOK_TITLE = "arg_book_title"
        private const val ARG_BOOK_ID = "arg_book_id"

        fun newInstance(bookTitle: String, bookId: String): BookCoverPickerDialogFragment {
            return BookCoverPickerDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_BOOK_TITLE, bookTitle)
                    putString(ARG_BOOK_ID, bookId)
                }
            }
        }
    }
}

