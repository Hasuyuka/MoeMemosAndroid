package me.mudkip.moememos.ui.component

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImage
import coil3.decode.BitmapFactoryDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ImageRequest
import kotlinx.coroutines.launch
import me.mudkip.moememos.viewmodel.LocalMemos
import me.mudkip.moememos.viewmodel.LocalUserState
import timber.log.Timber
import java.io.File

@OptIn(ExperimentalCoilApi::class)
@Composable
fun MemoImage(
    url: String,
    modifier: Modifier = Modifier,
    resourceIdentifier: String? = null,
    onClick: (() -> Unit)? = null,
    /**
     * 是否播放动图。默认**不播放**：这里是缩略图（列表、网格、正文内），
     * 一屏几十张 GIF 同时解码播放是实打实的卡顿来源。
     * 想看动图点进查看器即可——那里用的是另一套加载器，仍然会动。
     */
    animate: Boolean = false,
) {
    var diskCacheFile: File? by remember { mutableStateOf(null) }
    val context = LocalContext.current
    val userStateViewModel = LocalUserState.current
    val memosViewModel = LocalMemos.current
    val scope = rememberCoroutineScope()
    val imageLoader = remember(context, userStateViewModel.okHttpClient) {
        ImageLoader.Builder(context)
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = { userStateViewModel.okHttpClient }
                    )
                )
            }
            .build()
    }
    val modelUri = remember(url) { url.toUri() }
    val modelFile = remember(url) {
        modelUri.takeIf { it.scheme == "file" }?.path?.let(::File)
    }
    // 不播放动图时，把解码器换成只会解出单帧的那个（BitmapFactoryDecoder 在所有 API 级别都可用，
    // 而 StaticImageDecoder 需要 API 29+，旧机器上会退回动图解码）。
    // 只影响解码：抓取仍走上面那个带自定义 OkHttp 的 loader。
    val model = remember(context, url, animate) {
        ImageRequest.Builder(context)
            .data(url)
            .apply {
                if (!animate) {
                    decoderFactory(BitmapFactoryDecoder.Factory())
                }
            }
            .build()
    }

    val imageModifier = modifier.clickable {
        if (onClick != null) {
            onClick()
            return@clickable
        }

        val fileToOpen = diskCacheFile ?: modelFile
        fileToOpen?.let {
            val fileUri: Uri = try {
                FileProvider.getUriForFile(context, context.packageName + ".fileprovider", it)
            } catch (e: Throwable) {
                Timber.d(e)
                null
            } ?: return@let

            val intent = Intent().apply {
                action = Intent.ACTION_VIEW
                setDataAndType(fileUri, "image/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                context.startActivity(intent)
            } catch (e: Throwable) {
                Timber.d(e)
            }
        }
    }

    AsyncImage(
        model = model,
        imageLoader = imageLoader,
        contentDescription = null,
        modifier = imageModifier,
        contentScale = ContentScale.Crop,
        onSuccess = { state ->
            val diskCache = imageLoader.diskCache
            val diskCacheKey = state.result.diskCacheKey

            if (diskCache != null && diskCacheKey != null) {
                val downloadedFile = diskCache.openSnapshot(diskCacheKey)?.data?.toFile()
                diskCacheFile = downloadedFile
                val shouldPersistDownloadedFile = resourceIdentifier != null &&
                    downloadedFile != null &&
                    modelUri.scheme != "file"
                if (shouldPersistDownloadedFile) {
                    scope.launch {
                        memosViewModel.cacheResourceFile(resourceIdentifier, Uri.fromFile(downloadedFile))
                    }
                }
            }
        },
        onError = {
            Timber.d("Failed to load memo image: %s", url)
        }
    )
}
