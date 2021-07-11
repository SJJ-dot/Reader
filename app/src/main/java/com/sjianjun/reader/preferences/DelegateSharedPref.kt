package com.sjianjun.reader.preferences

import android.content.SharedPreferences

open class DelegateSharedPref(private val pref: SharedPreferences) :
    SharedPreferences by pref
