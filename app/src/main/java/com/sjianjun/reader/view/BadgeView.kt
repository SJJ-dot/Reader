package com.sjianjun.reader.view

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import com.sjianjun.reader.R
import com.sjianjun.reader.utils.color
import com.sjianjun.reader.utils.dp2Px


class BadgeView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : MaterialCardView(context, attrs, defStyleAttr) {
    private var tvBadgeCount: TextView? = null

    var badgeCount = 0
        set(value) {
            field = value
            tvBadgeCount?.text = value.toString()
        }

    init {
        inflate(context, R.layout.view_badge, this)
        tvBadgeCount = findViewById(R.id.tv_badge)
        radius = 5.dp2Px(context).toFloat()
        badgeCount = 0
        setHighlight(true)
    }

    fun setHighlight(highlight: Boolean) {
        setCardBackgroundColor((if (highlight) R.color.dn_badge_color else R.color.mdr_grey_700).color(context))
    }
}
