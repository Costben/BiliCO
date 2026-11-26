package erl.bilico.platform

import coil3.ImageLoader
import erl.bilico.data.network.BiliClient

expect fun ImageLoader.Builder.configurePlatformImageLoader(client: BiliClient): ImageLoader.Builder
