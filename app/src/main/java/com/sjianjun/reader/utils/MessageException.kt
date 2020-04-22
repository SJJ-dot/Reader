package com.sjianjun.reader.utils

import java.io.PrintStream
import java.io.PrintWriter

class MessageException(
    message: String?
) : Exception(message, null, false, false){
    override fun printStackTrace(s: PrintStream) {
        synchronized(s) {
            s.print(message)
        }
    }

    override fun printStackTrace(s: PrintWriter) {
        synchronized(s) {
            s.print(message)
        }
    }

}