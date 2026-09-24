package com.example.messenger.data.api

import com.example.messenger.data.dto.Me
import com.example.messenger.data.dto.Page
import com.example.messenger.data.dto.ProfileUpdate
import com.example.messenger.data.dto.User
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Query

interface UserApi {
    @GET("users/me/")
    suspend fun me(): Me

    /** Непереданные поля остаются как есть; пустой email означает «очистить» */
    @PATCH("users/me/")
    suspend fun updateMe(@Body body: ProfileUpdate): Me

    @GET("users/search/")
    suspend fun search(@Query("q") query: String? = null): Page<User>
}
