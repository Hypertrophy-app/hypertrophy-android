package app.hypertrophy.core.data.di

import app.hypertrophy.core.data.network.AuthSessionStore
import app.hypertrophy.core.data.network.AuthTokenPlugin
import app.hypertrophy.core.data.network.TokenProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideTokenProvider(): TokenProvider = TokenProvider { AuthSessionStore.token }

    @Provides
    @Singleton
    fun provideHttpClient(tokenProvider: TokenProvider): HttpClient = HttpClient(OkHttp) {
        expectSuccess = false
        install(AuthTokenPlugin) {
            this.tokenProvider = tokenProvider
        }
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                },
            )
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 10_000
        }
    }
}
