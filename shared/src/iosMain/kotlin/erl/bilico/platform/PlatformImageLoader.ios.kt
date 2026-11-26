package erl.bilico.platform

import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.network.ktor3.KtorNetworkFetcherFactory
import erl.bilico.data.network.BiliClient

@OptIn(ExperimentalCoilApi::class)
actual fun ImageLoader.Builder.configurePlatformImageLoader(client: BiliClient): ImageLoader.Builder =
    components {
        add(KtorNetworkFetcherFactory(client.http))
    }
