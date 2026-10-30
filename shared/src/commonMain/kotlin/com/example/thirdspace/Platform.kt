package com.example.thirdspace

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform