package org.mangoswatch.data.repository

import java.net.HttpURLConnection
import java.net.URL

interface AssetFetcher {
    fun openConnection(url: URL): HttpURLConnection
}

object HttpAssetFetcher : AssetFetcher {
    override fun openConnection(url: URL): HttpURLConnection =
        url.openConnection() as HttpURLConnection
}
