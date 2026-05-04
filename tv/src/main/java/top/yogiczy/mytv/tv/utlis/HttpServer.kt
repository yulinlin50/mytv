package top.yogiczy.mytv.tv.utlis

import android.content.Context
import android.content.Intent
import com.koushikdutta.async.AsyncServer
import com.koushikdutta.async.http.body.JSONObjectBody
import com.koushikdutta.async.http.body.MultipartFormDataBody
import com.koushikdutta.async.http.body.StringBody
import com.koushikdutta.async.http.server.AsyncHttpServer
import com.koushikdutta.async.http.server.AsyncHttpServerRequest
import com.koushikdutta.async.http.server.AsyncHttpServerResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import top.yogiczy.mytv.core.data.entities.epgsource.EpgSource
import top.yogiczy.mytv.core.data.entities.epgsource.EpgSourceList
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSource
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSourceList
import top.yogiczy.mytv.core.data.repositories.epg.EpgRepository
import top.yogiczy.mytv.core.data.repositories.iptv.IptvRepository
import top.yogiczy.mytv.core.data.utils.ChannelAlias
import top.yogiczy.mytv.core.data.utils.Constants
import top.yogiczy.mytv.core.data.utils.Globals
import top.yogiczy.mytv.core.data.utils.Loggable
import top.yogiczy.mytv.core.data.utils.Logger
import top.yogiczy.mytv.core.util.utils.ApkInstaller
import top.yogiczy.mytv.tv.BuildConfig
import top.yogiczy.mytv.tv.HttpServerService
import top.yogiczy.mytv.tv.R
import top.yogiczy.mytv.tv.sync.CloudSync
import top.yogiczy.mytv.tv.sync.CloudSyncData
import top.yogiczy.mytv.tv.ui.material.Snackbar
import top.yogiczy.mytv.tv.ui.material.SnackbarType
import top.yogiczy.mytv.tv.ui.utils.Configs
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException
import java.util.concurrent.atomic.AtomicBoolean


object HttpServer : Loggable("HttpServer") {
    private const val SERVER_PORT = 10481
    private const val MAX_RETRY_COUNT = 3
    private const val RETRY_DELAY_BASE_MS = 1000L
    
    private val uploadedApkFile by lazy {
        File(Globals.cacheDir, "uploaded_apk.apk").apply { deleteOnExit() }
    }

    val serverUrl by lazy { "http://${getLocalIpAddress()}:${SERVER_PORT}" }

    private var serverJob: Job? = null
    private val serverScope = CoroutineScope(Dispatchers.IO)
    private var appContext: Context? = null
    
    private var currentServer: AsyncHttpServer? = null
    private val isRunning = AtomicBoolean(false)
    private val isStarting = AtomicBoolean(false)
    private var retryCount = 0

    fun start(context: Context) {
        if (isStarting.get() || isRunning.get()) {
            log.i("设置服务已在运行或正在启动中")
            return
        }
        
        appContext = context.applicationContext
        isStarting.set(true)
        retryCount = 0
        
        HttpServerSecurity.regenerateToken(context.applicationContext)
        log.i("已重新生成授权Token")
        
        serverJob = serverScope.launch {
            while (retryCount < MAX_RETRY_COUNT && !isRunning.get()) {
                try {
                    stop()
                    
                    val server = AsyncHttpServer()
                    currentServer = server

                    server.addAction("OPTIONS", ".+") { _, response ->
                        wrapResponse(response)
                        response.send("ok")
                    }

                    server.listen(AsyncServer.getDefault(), SERVER_PORT)

                    server.get("/") { _, response ->
                        handleAssets(response, context, "text/html", "remote-configs/index.html")
                    }

                    server.get("/remote-configs/(.*)") { request, response ->
                        val contentType = when (request.path.split(".").last()) {
                            "css" -> "text/css"
                            "js" -> "text/javascript"
                            "html" -> "text/html"
                            "json" -> "application/json"
                            "svg" -> "image/svg+xml"
                            "png" -> "image/png"
                            else -> "text/plain"
                        }

                        handleAssets(response, context, contentType, request.path.removePrefix("/"))
                    }

                    server.get("/api/info") { request, response ->
                        handleGetInfo(request, response)
                    }

                    server.post("/api/iptv-source/push") { request, response ->
                        handleIptvSourcePush(request, response)
                    }

                    server.post("/api/epg-source/push") { request, response ->
                        handleEpgSourcePush(request, response)
                    }

                    server.get("/api/channel-alias") { request, response ->
                        handleGetChannelAlias(request, response)
                    }

                    server.post("/api/channel-alias") { request, response ->
                        handleUpdateChannelAlias(request, response)
                    }

                    server.get("/api/configs") { request, response ->
                        handleConfigsGet(request, response)
                    }

                    server.post("/api/configs") { request, response ->
                        handleConfigsPush(request, response)
                    }

                    server.post("/api/upload/apk") { request, response ->
                        handleUploadApk(request, response, context)
                    }

                    server.get("/api/cloud-sync/data") { request, response ->
                        handleCloudSyncDataGet(request, response)
                    }

                    server.post("/api/cloud-sync/data") { request, response ->
                        handleCloudSyncDataPost(request, response)
                    }

                    server.get("/api/themes") { _, response ->
                        handleGetThemes(response)
                    }

                    server.get("/api/about") { _, response ->
                        handleAboutGet(response)
                    }

                    server.get("/api/logs") { request, response ->
                        handleLogsGet(request, response)
                    }

                    server.get("/api/file/content") { request, response ->
                        handleFileContentGet(request, response)
                    }

                    server.post("/api/file/content") { request, response ->
                        handleFileContentPost(request, response)
                    }

                    server.post("/api/file/content-with-dir") { request, response ->
                        handleFileContentWithDirPost(request, response)
                    }

                    server.post("/api/upload/allinone") { request, response ->
                        handleUploadAllInOne(request, response)
                    }

                    server.post("/api/clear-cache") { request, response ->
                        handleClearCache(request, response, context)
                    }

                    server.post("/api/reset") { request, response ->
                        handleReset(request, response, context)
                    }

                    server.get(".*") { _, response ->
                        handleAssets(response, context, "text/html", "remote-configs/index.html")
                    }

                    isRunning.set(true)
                    isStarting.set(false)
                    retryCount = 0
                    
                    log.i("设置服务已启动: $serverUrl")
                } catch (ex: java.net.BindException) {
                    log.e("端口 $SERVER_PORT 已被占用，尝试重试 (${retryCount + 1}/$MAX_RETRY_COUNT)")
                    handleStartupFailure(ex, "端口已被占用")
                } catch (ex: java.net.SocketException) {
                    log.e("网络异常，尝试重试 (${retryCount + 1}/$MAX_RETRY_COUNT)")
                    handleStartupFailure(ex, "网络异常")
                } catch (ex: Exception) {
                    log.e("设置服务启动失败: ${ex.message}", ex)
                    handleStartupFailure(ex, "启动失败")
                }
            }
            
            if (!isRunning.get()) {
                isStarting.set(false)
                launch(Dispatchers.Main) {
                    Snackbar.show("设置服务启动失败，请检查网络或重启应用", type = SnackbarType.ERROR)
                }
            }
        }
    }
    
    private suspend fun handleStartupFailure(ex: Exception, message: String) {
        stop()
        retryCount++
        
        if (retryCount < MAX_RETRY_COUNT) {
            val delayMs = RETRY_DELAY_BASE_MS * (1 shl (retryCount - 1))
            log.i("将在 ${delayMs}ms 后重试...")
            kotlinx.coroutines.delay(delayMs)
        }
    }

    private fun wrapResponse(response: AsyncHttpServerResponse, allowCors: Boolean = false) = response.apply {
        headers.set("Access-Control-Allow-Methods", "POST, GET, DELETE, PUT, OPTIONS")
        if (allowCors) {
            headers.set("Access-Control-Allow-Origin", "*")
        } else {
            headers.set("Access-Control-Allow-Origin", "http://localhost:$SERVER_PORT")
        }
        headers.set("Access-Control-Allow-Headers", "Origin, Content-Type, Authorization, X-Auth-Token")
    }

    private fun responseSuccess(response: AsyncHttpServerResponse) {
        wrapResponse(response, allowCors = true).apply {
            setContentType("application/json")
            send("{\"code\": 0}")
        }
    }

    private fun responseError(response: AsyncHttpServerResponse, code: Int, message: String) {
        wrapResponse(response).code(code).apply {
            setContentType("application/json")
            send("{\"code\": $code, \"error\": \"$message\"}")
        }
    }

    private fun validateRequest(request: AsyncHttpServerRequest, response: AsyncHttpServerResponse): Boolean {
        val context = appContext ?: return false
        val authHeader = request.headers.get("Authorization")
        if (!HttpServerSecurity.validateToken(authHeader, context)) {
            responseError(response, 401, "Unauthorized: Invalid or missing token")
            return false
        }
        return true
    }

    private fun handleRawResource(
        response: AsyncHttpServerResponse,
        context: Context,
        contentType: String,
        id: Int,
    ) {
        wrapResponse(response).apply {
            setContentType(contentType)
            send(context.resources.openRawResource(id).readBytes().decodeToString())
        }
    }

    private fun handleAssets(
        response: AsyncHttpServerResponse,
        context: Context,
        contentType: String,
        filename: String,
    ) {
        wrapResponse(response).apply {
            setContentType(contentType)
            send(context.assets.open(filename).reader().readText())
        }
    }

    private fun handleGetThemes(response: AsyncHttpServerResponse) {
        wrapResponse(response).apply {
            setContentType("application/json")
            send(appContext?.resources?.openRawResource(R.raw.app_themes)?.readBytes()?.decodeToString() ?: "[]")
        }
    }

    private fun handleGetInfo(
        request: AsyncHttpServerRequest,
        response: AsyncHttpServerResponse
    ) {
        if (!validateRequest(request, response)) return
        
        wrapResponse(response).apply {
            setContentType("application/json")
            send(
                Globals.json.encodeToString(
                    AppInfo(
                        appTitle = Constants.APP_TITLE,
                        logHistory = Logger.history,
                    )
                )
            )
        }
    }

    private fun handleIptvSourcePush(
        request: AsyncHttpServerRequest,
        response: AsyncHttpServerResponse,
    ) {
        if (!validateRequest(request, response)) return
        
        val body = request.getBody<JSONObjectBody>().get()
        val name = body.get("name")?.toString()?.trim()?.takeIf { 
            it.isNotBlank() && it.length <= 100 
        } ?: return responseError(response, 400, "Invalid name: must be non-empty and <= 100 characters")
        
        val type = body.get("type")?.toString()
        if (type !in listOf("url", "file", "content")) {
            return responseError(response, 400, "Invalid type: must be 'url', 'file', or 'content'")
        }
        
        var newIptvSource: IptvSource? = null

        when (type) {
            "url" -> {
                val url = body.get("url")?.toString()?.trim()
                if (url.isNullOrBlank() || url.length > 2000) {
                    return responseError(response, 400, "Invalid URL: must be non-empty and <= 2000 characters")
                }
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    return responseError(response, 400, "Invalid URL: must start with http:// or https://")
                }
                newIptvSource = IptvSource(name, url)
            }

            "file" -> {
                val filePath = body.get("filePath")?.toString()?.trim()
                if (filePath.isNullOrBlank()) {
                    return responseError(response, 400, "Invalid file path")
                }
                newIptvSource = IptvSource(name, filePath, true)
            }

            "content" -> {
                val content = body.get("content")?.toString()
                if (content.isNullOrBlank()) {
                    return responseError(response, 400, "Invalid content: must be non-empty")
                }
                if (content.length > 10 * 1024 * 1024) {
                    return responseError(response, 413, "Content too large: maximum 10MB")
                }
                val file =
                    File(Globals.fileDir, "iptv_source_local_${System.currentTimeMillis()}.txt")
                file.writeText(content)
                newIptvSource = IptvSource(name, file.path, true)
            }
        }

        newIptvSource?.let { source ->
            serverScope.launch(Dispatchers.Main) {
                Configs.iptvSourceList = IptvSourceList(Configs.iptvSourceList + source.copy(enabled = true))
            }
        }

        responseSuccess(response)
    }

    private fun handleEpgSourcePush(
        request: AsyncHttpServerRequest,
        response: AsyncHttpServerResponse,
    ) {
        if (!validateRequest(request, response)) return
        
        val body = request.getBody<JSONObjectBody>().get()
        val name = body.get("name")?.toString()?.trim()?.takeIf { 
            it.isNotBlank() && it.length <= 100 
        } ?: return responseError(response, 400, "Invalid name: must be non-empty and <= 100 characters")
        
        val url = body.get("url")?.toString()?.trim()
        if (url.isNullOrBlank() || url.length > 2000) {
            return responseError(response, 400, "Invalid URL: must be non-empty and <= 2000 characters")
        }

        val newEpgSource = EpgSource(name, url, false, null)
        serverScope.launch(Dispatchers.Main) {
            Configs.epgSourceList = EpgSourceList(Configs.epgSourceList + newEpgSource)
        }

        responseSuccess(response)
    }

    private fun handleGetChannelAlias(
        request: AsyncHttpServerRequest,
        response: AsyncHttpServerResponse
    ) {
        if (!validateRequest(request, response)) return
        
        wrapResponse(response).apply {
            send(runCatching { ChannelAlias.aliasFile.readText() }.getOrElse { "" })
        }
    }

    private fun handleUpdateChannelAlias(
        request: AsyncHttpServerRequest,
        response: AsyncHttpServerResponse,
    ) {
        if (!validateRequest(request, response)) return
        
        val alias = request.getBody<StringBody>().get()
        if (alias.length > 1024 * 1024) {
            return responseError(response, 413, "Alias content too large: maximum 1MB")
        }

        ChannelAlias.aliasFile.writeText(alias)
        serverScope.launch {
            IptvRepository.clearAllCache()
            EpgRepository.clearAllCache()
        }

        responseSuccess(response)
    }

    private fun handleConfigsGet(
        request: AsyncHttpServerRequest,
        response: AsyncHttpServerResponse
    ) {
        if (!validateRequest(request, response)) return
        
        wrapResponse(response).apply {
            setContentType("application/json")
            send(Globals.json.encodeToString(Configs.toPartial()))
        }
    }

    private fun handleConfigsPush(
        request: AsyncHttpServerRequest,
        response: AsyncHttpServerResponse,
    ) {
        if (!validateRequest(request, response)) return
        
        val body = request.getBody<JSONObjectBody>().get()
        val bodyString = body.toString()
        if (bodyString.length > 1024 * 1024) {
            return responseError(response, 413, "Config data too large: maximum 1MB")
        }
        
        runCatching {
            Globals.json.decodeFromString<Configs.Partial>(bodyString)
        }.fold(
            onSuccess = { configs ->
                serverScope.launch(Dispatchers.Main) {
                    Configs.fromPartial(configs)
                }
                responseSuccess(response)
            },
            onFailure = { error ->
                responseError(response, 400, "Invalid config format: ${error.message}")
            }
        )
    }

    private fun handleUploadApk(
        request: AsyncHttpServerRequest,
        response: AsyncHttpServerResponse,
        context: Context,
    ) {
        if (!validateRequest(request, response)) return
        
        val contentLength = request.headers["Content-Length"]?.toLong()
        if (!HttpServerSecurity.isFileSizeAllowed(contentLength)) {
            responseError(response, 413, "File too large. Maximum size is 100MB")
            return
        }

        val body = request.getBody<MultipartFormDataBody>()

        val os = uploadedApkFile.outputStream()
        var hasReceived = 0L

        body.setMultipartCallback { part ->
            if (part.isFile) {
                body.setDataCallback { _, bb ->
                    val byteArray = bb.allByteArray
                    hasReceived += byteArray.size
                    serverScope.launch(Dispatchers.Main) {
                        Snackbar.show(
                            "正在接收文件: ${(hasReceived * 100f / (contentLength ?: 1)).toInt()}%",
                            leadingLoading = true,
                            id = "uploading_apk",
                        )
                    }
                    os.write(byteArray)
                }
            }
        }

        body.setEndCallback {
            serverScope.launch(Dispatchers.Main) {
                Snackbar.show("正在准备安装，请稍后...", leadingLoading = true, duration = 10_000)
            }
            body.dataEmitter.close()
            os.flush()
            os.close()
            
            val result = ApkInstaller.installApk(context, uploadedApkFile.path)
            if (!result.success) {
                serverScope.launch(Dispatchers.Main) {
                    Snackbar.show("安装失败: ${result.error}", type = SnackbarType.ERROR)
                }
            }
        }

        responseSuccess(response)
    }

    private fun handleUploadAllInOne(
        request: AsyncHttpServerRequest,
        response: AsyncHttpServerResponse,
    ) {
        if (!validateRequest(request, response)) return
        
        val contentLength = request.headers["Content-Length"]?.toLong() ?: 0
        if (contentLength <= 0) {
            return responseError(response, 400, "Invalid content length")
        }
        if (contentLength > 100 * 1024 * 1024) {
            return responseError(response, 413, "File too large: maximum 100MB")
        }
        
        val body = request.getBody<MultipartFormDataBody>()
        var hasReceived = 0L
        var isValidFile = false

        val allinoneFile = File(Globals.fileDir, "uploads/allinone").apply { parentFile?.mkdirs() }

        body.setMultipartCallback { part ->
            if (part.isFile) {
                val filename = part.name ?: ""
                if (!filename.endsWith(".txt", ignoreCase = true) && 
                    !filename.endsWith(".m3u", ignoreCase = true) &&
                    !filename.endsWith(".m3u8", ignoreCase = true)) {
                    return@setMultipartCallback
                }
                
                isValidFile = true
                with(allinoneFile.outputStream()) {

                    body.setDataCallback { _, bb ->
                        val byteArray = bb.allByteArray
                        hasReceived += byteArray.size
                        serverScope.launch(Dispatchers.Main) {
                            Snackbar.show(
                                "正在接收文件: ${(hasReceived * 100f / contentLength).toInt()}%",
                                leadingLoading = true,
                                id = "uploading_file",
                            )
                        }
                        write(byteArray)
                    }
                }
            }
        }

        body.setEndCallback {
            body.dataEmitter.close()
            if (!isValidFile) {
                serverScope.launch(Dispatchers.Main) {
                    Snackbar.show("无效的文件类型，仅支持 .txt, .m3u, .m3u8", type = SnackbarType.ERROR, id = "uploading_file")
                }
                return@setEndCallback
            }
            
            serverScope.launch(Dispatchers.Main) {
                Configs.feiyangAllInOneFilePath = allinoneFile.absolutePath
                Snackbar.show("文件接收完成", id = "uploading_file")
            }
        }

        responseSuccess(response)
    }

    private fun handleCloudSyncDataGet(
        request: AsyncHttpServerRequest,
        response: AsyncHttpServerResponse
    ) {
        if (!validateRequest(request, response)) return
        
        serverScope.launch {
            try {
                val data = CloudSync.getData()
                withContext(Dispatchers.Main) {
                    wrapResponse(response).apply {
                        setContentType("application/json")
                        send(Globals.json.encodeToString(data))
                    }
                }
            } catch (e: Exception) {
                log.e("获取云同步数据失败", e)
            }
        }
    }

    private fun handleCloudSyncDataPost(
        request: AsyncHttpServerRequest,
        response: AsyncHttpServerResponse
    ) {
        if (!validateRequest(request, response)) return
        
        val body = request.getBody<JSONObjectBody>().get()
        val bodyString = body.toString()
        if (bodyString.length > 5 * 1024 * 1024) {
            return responseError(response, 413, "Cloud sync data too large: maximum 5MB")
        }
        
        runCatching {
            Globals.json.decodeFromString<CloudSyncData>(bodyString)
        }.fold(
            onSuccess = { data ->
                serverScope.launch(Dispatchers.Main) { data.apply() }
                responseSuccess(response)
            },
            onFailure = { error ->
                responseError(response, 400, "Invalid cloud sync data: ${error.message}")
            }
        )
    }

    private fun handleAboutGet(response: AsyncHttpServerResponse) {
        wrapResponse(response).apply {
            setContentType("application/json")
            send(
                Globals.json.encodeToString(
                    AppAbout(
                        applicationId = BuildConfig.APPLICATION_ID,
                        flavor = BuildConfig.FLAVOR,
                        buildType = BuildConfig.BUILD_TYPE,
                        versionCode = BuildConfig.VERSION_CODE,
                        versionName = BuildConfig.VERSION_NAME,
                        deviceName = Globals.deviceName,
                        deviceId = Globals.deviceId,
                    )
                )
            )
        }
    }

    private fun handleLogsGet(
        request: AsyncHttpServerRequest,
        response: AsyncHttpServerResponse
    ) {
        if (!validateRequest(request, response)) return
        
        wrapResponse(response).apply {
            setContentType("application/json")
            send(Globals.json.encodeToString(Logger.history.takeLast(100)))
        }
    }

    private fun handleFileContentGet(
        request: AsyncHttpServerRequest,
        response: AsyncHttpServerResponse
    ) {
        if (!validateRequest(request, response)) return
        
        val path = request.query.getString("path")
        
        if (!HttpServerSecurity.isPathAllowed(path)) {
            return responseError(response, 403, "Access denied: Path not allowed")
        }

        val file = File(path).takeIf { it.exists() } ?: run {
            return response.code(404).send("File not found")
        }

        wrapResponse(response).send(file.readText())
    }

    private fun handleFileContentPost(
        request: AsyncHttpServerRequest,
        response: AsyncHttpServerResponse
    ) {
        if (!validateRequest(request, response)) return
        
        val body = request.getBody<JSONObjectBody>().get()
        val path = body.get("path").toString()
        val content = body.get("content").toString()
        
        if (content.length > 1024 * 1024) {
            return responseError(response, 413, "Content too large: maximum 1MB")
        }
        
        if (!HttpServerSecurity.isPathAllowed(path)) {
            return responseError(response, 403, "Access denied: Path not allowed")
        }

        val file = File(path)

        if (file.exists()) {
            file.writeText(content)
        } else {
            file.parentFile?.mkdirs()
            file.writeText(content)
        }

        responseSuccess(response)
    }

    private fun handleFileContentWithDirPost(
        request: AsyncHttpServerRequest,
        response: AsyncHttpServerResponse
    ) {
        if (!validateRequest(request, response)) return
        
        val body = request.getBody<JSONObjectBody>().get()
        val dir = body.get("dir").toString()
        val filename = HttpServerSecurity.sanitizeFilename(body.get("filename").toString())
        val content = body.get("content").toString()
        
        if (content.length > 1024 * 1024) {
            return responseError(response, 413, "Content too large: maximum 1MB")
        }

        val file = when (dir) {
            "cache" -> File(Globals.cacheDir, filename)
            "file" -> File(Globals.fileDir, filename)
            else -> return response.code(400).send("Invalid dir")
        }

        if (file.exists()) {
            file.writeText(content)
        } else {
            file.parentFile?.mkdirs()
            file.writeText(content)
        }

        wrapResponse(response).send(file.path)
    }

    private fun getLocalIpAddress(): String {
        val defaultIp = "127.0.0.1"

        try {
            val en = NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val intf = en.nextElement()
                val enumIpAddr = intf.inetAddresses
                while (enumIpAddr.hasMoreElements()) {
                    val inetAddress = enumIpAddr.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                        val ip = inetAddress.hostAddress ?: defaultIp
                        val isValid = when {
                            ip.startsWith("192.168.") -> true
                            ip.startsWith("10.") -> true
                            ip.startsWith("172.") -> {
                                val parts = ip.split(".")
                                val secondOctet = parts.getOrNull(1)?.toIntOrNull()
                                secondOctet in 16..31
                            }

                            else -> false
                        }

                        if (isValid)
                            return ip
                    }
                }
            }
            return defaultIp
        } catch (ex: SocketException) {
            log.e("IP Address: ${ex.message}", ex)
            return defaultIp
        }
    }

    private fun handleClearCache(
        request: AsyncHttpServerRequest,
        response: AsyncHttpServerResponse,
        context: Context
    ) {
        if (!validateRequest(request, response)) return
        
        serverScope.launch {
            try {
                IptvRepository.clearAllCache()
                EpgRepository.clearAllCache()
                
                val cacheDir = Globals.cacheDir
                cacheDir.listFiles()?.forEach { file ->
                    if (file.name != "uploaded_apk.apk") {
                        file.deleteRecursively()
                    }
                }
                
                (context.applicationContext as? MyTVApplication)?.clearImageCache()
                
                withContext(Dispatchers.Main) {
                    responseSuccess(response)
                }
            } catch (e: Exception) {
                log.e("清除缓存失败", e)
                withContext(Dispatchers.Main) {
                    responseError(response, 500, "清除缓存失败: ${e.message}")
                }
            }
        }
    }

    private fun handleReset(
        request: AsyncHttpServerRequest,
        response: AsyncHttpServerResponse,
        context: Context
    ) {
        if (!validateRequest(request, response)) return
        
        serverScope.launch {
            try {
                SP.clear()
                
                val cacheDir = Globals.cacheDir
                cacheDir.deleteRecursively()
                cacheDir.mkdirs()
                
                val fileDir = Globals.fileDir
                fileDir.listFiles()?.forEach { it.deleteRecursively() }
                
                withContext(Dispatchers.Main) {
                    responseSuccess(response)
                }
            } catch (e: Exception) {
                log.e("重置应用失败", e)
                withContext(Dispatchers.Main) {
                    responseError(response, 500, "重置应用失败: ${e.message}")
                }
            }
        }
    }

    fun stop() {
        log.i("正在停止设置服务...")
        isStarting.set(false)
        
        runCatching {
            currentServer?.stop()
            currentServer = null
        }.onFailure { log.e("停止服务器失败: ${it.message}", it) }
        
        runCatching {
            AsyncServer.getDefault().stop()
        }.onFailure { log.e("停止AsyncServer失败: ${it.message}", it) }
        
        serverJob?.cancel()
        serverJob = null
        isRunning.set(false)
        
        log.i("设置服务已停止")
    }
    
    fun isRunning(): Boolean = isRunning.get()

    fun startService(context: Context) {
        runCatching {
            context.startService(Intent(context, HttpServerService::class.java))
        }.onFailure { it.printStackTrace() }
    }

    fun stopService(context: Context) {
        runCatching {
            context.stopService(Intent(context, HttpServerService::class.java))
        }.onFailure { it.printStackTrace() }
    }
}

@Serializable
private data class AppInfo(
    val appTitle: String,
    val logHistory: List<Logger.HistoryItem>,
)

@Serializable
private data class AppAbout(
    val applicationId: String,
    val flavor: String,
    val buildType: String,
    val versionCode: Int,
    val versionName: String,
    val deviceName: String,
    val deviceId: String,
)