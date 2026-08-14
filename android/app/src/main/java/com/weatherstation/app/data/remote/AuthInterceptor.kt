package com.weatherstation.app.data.remote

import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val apiKeyProvider: () -> String
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val apiKey = apiKeyProvider()

        val requestBuilder = original.newBuilder()
            .header("User-Agent", "WeatherStation-Android/2.0")
            .header("Accept", "application/json")
            .header("Cache-Control", "no-cache, no-store, must-revalidate")
            .header("Pragma", "no-cache")

        if (apiKey.isNotBlank()) {
            requestBuilder.header("X-API-Key", apiKey)
        }

        return chain.proceed(requestBuilder.build())
    }
}
