package com.sjianjun.reader.preferences

import android.content.SharedPreferences

open class DelegateSharedPref(protected val pref: SharedPreferences) :
    SharedPreferences by pref
