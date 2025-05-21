package com.example.kspgenerationtryout

import com.example.annotations.LogInfo


interface MyApi {
    @LogInfo(info = "This is a log message")
    fun test(name: String, msg: String): String

    @LogInfo(info = "This is a log message")
    fun test2(name: String, msg: String): String
}