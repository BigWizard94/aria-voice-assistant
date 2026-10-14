package com.bigwizard.aria.data.remote

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * One shared OkHttp client for the whole app. Every OkHttpClient owns its own
 * connection pool and dispatcher threads, which is wasted memory on 1–2 GB
 * devices; derived clients ([OkHttpClient.newBuilder]) share those resources.
 */
object Http {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
