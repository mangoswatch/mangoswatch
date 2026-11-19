package org.mangoswatch.presentation.di

import android.content.Context.CONNECTIVITY_SERVICE
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module
import org.mangoswatch.data.datasource.UserSettingsDataSource
import org.mangoswatch.data.repository.EndpointTaxonomyRepositoryImpl
import org.mangoswatch.data.repository.UserSettingsRepositoryImpl
import org.mangoswatch.data.repository.StreamsRepositoryImpl
import org.mangoswatch.data.repository.AssetRepositoryImpl
import org.mangoswatch.data.resolver.AppInfoResolver
import org.mangoswatch.data.analyzer.TrafficAnalyzer
import org.mangoswatch.data.repository.AssetSync
import org.mangoswatch.data.vpn.VpnState
import org.mangoswatch.domain.repository.EndpointTaxonomyRepository
import org.mangoswatch.domain.repository.UserSettingsRepository
import org.mangoswatch.domain.repository.StreamsRepository
import org.mangoswatch.domain.repository.AssetRepository
import org.mangoswatch.domain.parser.ip.IpRangesCache
import org.mangoswatch.presentation.viewmodel.MainScreenViewModel

val appModule = module {
    single<PackageManager> { androidContext().packageManager }
    single<ConnectivityManager> { androidContext().getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager }
    single<StreamsRepositoryImpl> { StreamsRepositoryImpl() } bind StreamsRepository::class
    single<AssetSync> { AssetSync() }
    single<AssetRepositoryImpl> { AssetRepositoryImpl(androidContext(), get()) } bind AssetRepository::class
    single<IpRangesCache> { IpRangesCache(get()) }
    single<EndpointTaxonomyRepositoryImpl> { EndpointTaxonomyRepositoryImpl(get()) } bind EndpointTaxonomyRepository::class
    single<UserSettingsDataSource> { UserSettingsDataSource(androidContext()) }
    single<UserSettingsRepositoryImpl> { UserSettingsRepositoryImpl(get()) } bind UserSettingsRepository::class
    viewModel<MainScreenViewModel> { MainScreenViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    single<VpnState>{ VpnState() }
    single<AppInfoResolver> { AppInfoResolver(get()) }
    single<TrafficAnalyzer> { TrafficAnalyzer() }
}
