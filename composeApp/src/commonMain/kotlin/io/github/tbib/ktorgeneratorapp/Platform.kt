package io.github.tbib.ktorgeneratorapp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform