package com.jyotirmoyb.loan.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

interface RemoteConfigApi {
    @GET("remote-config/{file}")
    suspend fun fetchConfig(
        @Path("file") file: String,
        @Header("If-None-Match") ifNoneMatch: String? = null
    ): Response<ResponseBody>
}
