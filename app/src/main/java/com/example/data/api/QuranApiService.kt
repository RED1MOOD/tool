package com.example.data.api

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

data class AyahApi(
    val number: Int,
    val text: String,
    val numberInSurah: Int,
    val juz: Int
)

data class EditionApi(
    val number: Int,
    val name: String,
    val englishName: String,
    val englishNameTranslation: String,
    val revelationType: String,
    val ayahs: List<AyahApi>
)

data class QuranResponse(
    val code: Int,
    val status: String,
    val data: List<EditionApi>
)

interface QuranApiService {
    @GET("surah/{surahId}/editions/quran-simple,en.asad")
    suspend fun getSurahDetails(@Path("surahId") surahId: Int): QuranResponse

    companion object {
        private const val BASE_URL = "https://api.alquran.cloud/v1/"

        fun create(): QuranApiService {
            val moshi = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()

            return retrofit.create(QuranApiService::class.java)
        }
    }
}
