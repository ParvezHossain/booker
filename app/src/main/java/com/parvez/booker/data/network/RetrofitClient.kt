package com.parvez.booker.data.network

import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Singleton managing OkHttpClient and Retrofit initialization with dynamic HTTP Basic Auth credentials.
 */
object RetrofitClient {

    private const val BASE_URL = "http://192.168.0.122:8080/api/"

    /**
     * Active basic auth username.
     */
    var username: String = ""

    /**
     * Active basic auth password.
     */
    var password: String = ""

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    /**
     * Interceptor injecting dynamic Basic Authentication header if credentials exist.
     */
    private val authInterceptor = Interceptor { chain ->
        val requestBuilder = chain.request().newBuilder()

        if (username.isNotEmpty() || password.isNotEmpty()) {
            val credential = Credentials.basic(username, password)
            requestBuilder.header("Authorization", credential)
        }

        chain.proceed(requestBuilder.build())
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .build()

    val bookApi: BookApi = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(BookApi::class.java)
}