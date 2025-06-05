// File: di/NetworkModule.kt
package com.voipex.android.di

import com.voipex.android.BuildConfig
import com.voipex.android.data.api.AuthApi
import com.voipex.android.data.api.CallApi
import com.voipex.android.data.api.UserApi
import com.voipex.android.data.local.PreferencesManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    @Provides
    @Singleton
    fun provideOkHttpClient(preferencesManager: PreferencesManager): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val token = preferencesManager.getAuthToken()
                val request = if (token != null) {
                    chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $token")
                        .build()
                } else {
                    chain.request()
                }
                chain.proceed(request)
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi {
        return retrofit.create(AuthApi::class.java)
    }
    
    @Provides
    @Singleton
    fun provideUserApi(retrofit: Retrofit): UserApi {
        return retrofit.create(UserApi::class.java)
    }
    
    @Provides
    @Singleton
    fun provideCallApi(retrofit: Retrofit): CallApi {
        return retrofit.create(CallApi::class.java)
    }
}

// File: data/api/AuthApi.kt
package com.voipex.android.data.api

import com.voipex.android.data.models.AuthResponse
import com.voipex.android.data.models.LoginRequest
import com.voipex.android.data.models.RegisterRequest
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse
    
    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse
    
    @POST("auth/logout")
    suspend fun logout()
}

// File: data/api/UserApi.kt
package com.voipex.android.data.api

import com.voipex.android.data.models.User
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Query
import retrofit2.http.Body

interface UserApi {
    @GET("users/profile")
    suspend fun getProfile(): User
    
    @PUT("users/profile")
    suspend fun updateProfile(@Body user: User): User
    
    @GET("users/search")
    suspend fun searchUsers(@Query("query") query: String): List<User>
    
    @GET("users/contacts")
    suspend fun getContacts(): List<User>
}

// File: data/api/CallApi.kt
package com.voipex.android.data.api

import com.voipex.android.data.models.Call
import com.voipex.android.data.models.InitiateCallRequest
import com.voipex.android.data.models.InitiateCallResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface CallApi {
    @POST("calls/initiate")
    suspend fun initiateCall(@Body request: InitiateCallRequest): InitiateCallResponse
    
    @POST("calls/end")
    suspend fun endCall(@Body callId: Map<String, String>)
    
    @GET("calls/history")
    suspend fun getCallHistory(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): List<Call>
}