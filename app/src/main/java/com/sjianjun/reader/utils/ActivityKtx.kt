package com.sjianjun.reader.utils

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment

inline fun <reified T> Fragment.startActivity(k: String, v: Any?) {
    context?.startActivity<T>(k to v)
}

inline fun <reified T> Fragment.startActivity(vararg params: Pair<String, Any?>) {
    context?.startActivity<T>(*params)
}

inline fun <reified T> Context.startActivity(vararg params: Pair<String, Any?>) {
    val intent = Intent(this, T::class.java)
    params.forEach {
        intent.putExtra(it.first, it.second.toString())
    }
    startActivity(intent)
}


val Context?.act: AppCompatActivity?
    get() {
        if (this == null) {
            return null
        }
        if (this is AppCompatActivity) {
            return this
        }
        return (this as? ContextWrapper)?.baseContext?.act
    }

val View.act: AppCompatActivity?
    get() = context?.act