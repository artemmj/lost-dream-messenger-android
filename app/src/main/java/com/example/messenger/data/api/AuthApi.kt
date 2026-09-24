package com.example.messenger.data.api

import com.example.messenger.data.dto.LoginRequest
import com.example.messenger.data.dto.RegisterRequest
import com.example.messenger.data.dto.RegisterResponse
import com.example.messenger.data.dto.TokenPair
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("auth/login/")
    suspend fun login(@Body body: LoginRequest): TokenPair

    @POST("auth/register/")
    suspend fun register(@Body body: RegisterRequest): RegisterResponse
}
