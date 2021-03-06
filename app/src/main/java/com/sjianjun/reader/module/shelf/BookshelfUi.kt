package com.sjianjun.reader.module.shelf

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.View.GONE
import android.view.ViewGroup
import androidx.core.widget.ContentLoadingProgressBar
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.sjianjun.reader.R
import splitties.dimensions.dp
import splitties.views.assignAndGetGeneratedId
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.parentId
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.wrapCtxIfNeeded
import splitties.views.dsl.idepreview.UiPreView
import splitties.views.dsl.recyclerview.recyclerView
import splitties.views.recyclerview.verticalLayoutManager

class BookshelfUi(override val ctx: Context) : Ui {
    val recyclerViw = recyclerView {
        assignAndGetGeneratedId()
        layoutManager = verticalLayoutManager()
    }
    val rootRefresh = SwipeRefreshLayout(ctx).apply {
        assignAndGetGeneratedId()
        add(recyclerViw, ViewGroup.LayoutParams(-1, -1))
    }
    val loading =
        ContentLoadingProgressBar(ctx.wrapCtxIfNeeded(R.style.ProgressBar_Horizontal)).apply {
            assignAndGetGeneratedId()
            visibility = GONE
            if (isInEditMode) {
                visibility = View.VISIBLE
                progress = 50
                secondaryProgress = 25
            }
        }
    override val root: View = constraintLayout {
        assignAndGetGeneratedId()
        add(rootRefresh, lParams(-1) {
            bottomToBottom = parentId
            topToTop = parentId
        })
        add(loading, lParams(-1, ctx.dp(8)) {
            topToTop = parentId
        })
    }
}

//region IDE BookshelfUiPre
class BookshelfUiPre(
    context: Context,
    attrs: AttributeSet?,
    defStyleAttr: Int
) : UiPreView(context, attrs, defStyleAttr, {
    BookshelfUi(it)
})
//endregion