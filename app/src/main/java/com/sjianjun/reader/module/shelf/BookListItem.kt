package com.sjianjun.reader.module.shelf

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.sjianjun.reader.R
import com.sjianjun.reader.utils.MyColors
import com.sjianjun.reader.utils.isNight
import com.sjianjun.reader.view.BadgeView
import com.sjianjun.reader.view.RotateLoading
import sjj.alog.Log
import splitties.dimensions.dp
import splitties.resources.color
import splitties.views.assignAndGetGeneratedId
import splitties.views.dsl.constraintlayout.*
import splitties.views.dsl.core.*
import splitties.views.dsl.isInPreview
import splitties.views.imageResource
import splitties.views.lines

class BookListItem @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : MaterialCardView(context, attrs, defStyleAttr) {
    val bookCover = imageView {
        assignAndGetGeneratedId()
        minimumWidth = dp(65)
        if (isInPreview) {
            setBackgroundColor(Color.YELLOW)
        } else {
            if (isNight) {
                foreground = ColorDrawable(MyColors.NIGHT_FOREGROUND)
            }
        }
    }

    val startingStation = textView {
        assignAndGetGeneratedId()
        typeface = Typeface.MONOSPACE
        setBackgroundResource(R.drawable.shape_sou_fa)
        gravity = Gravity.CENTER
        text = "起"
        includeFontPadding = false
        setTextColor(color(R.color.mdr_white))
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10f)
    }

    val bookName = textView(theme = R.style.text_body1) {
        assignAndGetGeneratedId()
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.CENTER_VERTICAL
        lines = 1
        textSize = 20f
        if (isInPreview) {
            text = "书名"
        }
    }
    val author = textView(theme = R.style.text_body2) {
        assignAndGetGeneratedId()
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.CENTER_VERTICAL
        lines = 1
        if (isInPreview) {
            text = "作者"
        }
    }
    val lastChapter = textView(theme = R.style.text_body2) {
        assignAndGetGeneratedId()
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.CENTER_VERTICAL
        lines = 1
        if (isInPreview) {
            text = "最新章节"
        }
    }
    val haveRead = textView(theme = R.style.text_body2) {
        assignAndGetGeneratedId()
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.CENTER_VERTICAL
        lines = 1
        if (isInPreview) {
            text = "已读章节"
        }
    }
    val origin = textView(theme = R.style.text_body2) {
        assignAndGetGeneratedId()
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.CENTER_VERTICAL
        lines = 1
        if (isInPreview) {
            text = "来源网站"
        }
    }
    val bvUnread = BadgeView(context).apply {
        assignAndGetGeneratedId()
        includeFontPadding = false
        if (isInPreview) {
            text = "99"
        }
    }
    val loading = RotateLoading(context).apply {
        assignAndGetGeneratedId()
    }
    val syncError = imageView {
        assignAndGetGeneratedId()
        imageResource = R.mipmap.ic_sync_error
        ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(color(R.color.mdr_red_100)))
    }
    val constraintLayout = constraintLayout {
        assignAndGetGeneratedId()
        add(bookCover, lParams(matchConstraints, matchConstraints) {
            margin = dp(5)
            dimensionRatio = "65:90"
            bottomToBottom = parentId
            topToTop = parentId
            startToStart = parentId
        })
        add(startingStation, lParams(height = wrapContent) {
            margin = dp(3)
            dimensionRatio = "1:1"
            endToEndOf(bookCover)
            topToTopOf(bookCover)
        })
        add(bookName, lParams(height = wrapContent) {
            marginStart = dp(5)
            marginEnd = dp(5)
            bottomToTopOf(author)
            endToEnd = parentId
            startToEndOf(bookCover)
            topToTop = parentId
        })
        add(author, lParams(height = wrapContent) {
            bottomToTopOf(lastChapter)
            endToEndOf(bookName)
            startToStartOf(bookName)
            topToBottomOf(bookName)
        })
        add(lastChapter, lParams(height = wrapContent) {
            bottomToTopOf(haveRead)
            endToEndOf(bookName)
            startToStartOf(bookName)
            topToBottomOf(author)
        })
        add(haveRead, lParams(height = wrapContent) {
            bottomToTopOf(origin)
            endToEndOf(bookName)
            startToStartOf(bookName)
            topToBottomOf(lastChapter)
        })
        add(origin, lParams(height = wrapContent) {
            bottomMargin = dp(5)
            bottomToBottom = parentId
            endToEndOf(bookName)
            startToStartOf(bookName)
            topToBottomOf(haveRead)
        })
        add(bvUnread, lParams(wrapContent, wrapContent) {
            topMargin = dp(5)
            endMargin = dp(16)
            endToEnd = parentId
            topToTop = parentId
        })
        add(loading, lParams(dp(26), dp(26)) {
            topMargin = dp(5)
            endMargin = dp(16)
            rightToRight = parentId
            topToTop = parentId
        })
        add(syncError, lParams(wrapContent, wrapContent) {
            endMargin = dp(16)
            bottomMargin = dp(8)
            bottomToBottom = parentId
            endToEnd = parentId
        })
    }

    init {
        layoutParams = RecyclerView.LayoutParams(-1, dp(98)).apply {
            margin = dp(5)
        }
        if (isNight) {
            setCardBackgroundColor(MyColors.NIGHT_BACKGROUND_1)
        }
        add(constraintLayout, lParams(matchParent, dp(98)))
    }
}