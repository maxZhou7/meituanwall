package com.meituan.android.walle

class SignatureNotFoundException : Exception {
    constructor(message: String) : super(message)
    
    constructor(message: String, cause: Throwable?) : super(message, cause)
}
