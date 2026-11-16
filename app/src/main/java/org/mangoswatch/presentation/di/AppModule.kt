package org.mangoswatch.presentation.di

import android.content.Context.CONNECTIVITY_SERVICE
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single
import org.mangoswatch.data.datasource.UserPreferences
import org.mangoswatch.data.repository.EndpointTaxonomyRepositoryImpl
import org.mangoswatch.data.repository.UserPreferencesRepositoryImpl
import org.mangoswatch.data.repository.StreamsRepositoryImpl
import org.mangoswatch.data.resolver.AppInfoResolver
import org.mangoswatch.data.vpn.VpnState
import org.mangoswatch.domain.repository.EndpointTaxonomyRepository
import org.mangoswatch.domain.repository.UserPreferencesRepository
import org.mangoswatch.domain.repository.StreamsRepository
import org.mangoswatch.presentation.viewmodel.MainScreenViewModel

val appModule = module {
    single<PackageManager> { androidContext().packageManager }
    single<ConnectivityManager> { androidContext().getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager }
    single<StreamsRepositoryImpl>() bind StreamsRepository::class
    single<EndpointTaxonomyRepositoryImpl>() bind EndpointTaxonomyRepository::class
    single<UserPreferences> { UserPreferences(androidContext()) }
    single<UserPreferencesRepositoryImpl>() bind UserPreferencesRepository::class
    viewModel<MainScreenViewModel> { MainScreenViewModel(get(), get(), get(), get(), get()) }
    single<VpnState>{ VpnState() }
    single<AppInfoResolver> { AppInfoResolver(get()) }
}
