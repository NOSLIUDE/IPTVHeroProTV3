package com.iptvhero.pro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iptvhero.pro.data.local.AppDatabase
import com.iptvhero.pro.data.repository.IptvRepository
import com.iptvhero.pro.ui.navigation.AppNavigation
import com.iptvhero.pro.ui.viewmodel.MainViewModel
import com.iptvhero.pro.ui.viewmodel.MainViewModelFactory
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = AppDatabase.getDatabase(applicationContext)
        val httpClient = OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) IPTVHeroProTV/6.7")
                    .header("Accept", "*/*")
                    .build()
                chain.proceed(req)
            }
            .build()

        val repository = IptvRepository(
            profileDao = database.profileDao(),
            channelDao = database.channelDao(),
            httpClient = httpClient
        )
        val factory = MainViewModelFactory(repository, applicationContext)

        setContent {
            val viewModel: MainViewModel = viewModel(factory = factory)
            AppNavigation(
                viewModel = viewModel,
                onExitApp = { finish() }
            )
        }
    }
}
