package com.example.annotations

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FUNCTION)
annotation class LogInfo(
    val info: String = "",
)