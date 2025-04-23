package com.cedo.kmmbuttonlikepostmanapp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform