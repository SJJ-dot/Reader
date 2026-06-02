package com.sjianjun.reader.utils

/**
 * Trim whitespace from both ends of the string, including common invisible Unicode characters
 * such as BOM (U+FEFF) and zero-width spaces (U+200B, U+200C, U+200D).
 */
fun String.trimExt(): String {
    return this.trim { it <= ' ' || it == '\uFEFF' || it == '\u200B' || it == '\u200C' || it == '\u200D' || it == '\u2060' }
}

