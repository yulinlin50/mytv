package top.yogiczy.mytv.core.util.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest

object ApkInstaller {
    
    private const val TAG = "ApkInstaller"
    
    data class ApkInfo(
        val packageName: String,
        val versionName: String,
        val versionCode: Long,
        val permissions: List<String>,
        val signature: String,
        val label: String?
    )
    
    data class InstallResult(
        val success: Boolean,
        val error: String? = null,
        val apkInfo: ApkInfo? = null
    )

    fun getApkInfo(context: Context, filePath: String): ApkInfo? {
        if (!filePath.endsWith(".apk", ignoreCase = true)) {
            Log.w(TAG, "File is not an APK: $filePath")
            return null
        }
        
        val file = File(filePath)
        if (!file.exists()) {
            Log.w(TAG, "APK file does not exist: $filePath")
            return null
        }
        
        return try {
            val pm = context.packageManager
            val flags = PackageManager.GET_PERMISSIONS or 
                        PackageManager.GET_SIGNATURES or
                        PackageManager.GET_META_DATA
            
            val info = pm.getPackageArchiveInfo(filePath, flags) ?: return null
            
            val appInfo = info.applicationInfo ?: return null
            appInfo.sourceDir = filePath
            appInfo.publicSourceDir = filePath
            
            ApkInfo(
                packageName = info.packageName,
                versionName = info.versionName ?: "unknown",
                versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    info.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    info.versionCode.toLong()
                },
                permissions = info.requestedPermissions?.toList() ?: emptyList(),
                signature = calculateSignature(info.signatures),
                label = appInfo.loadLabel(pm).toString()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get APK info: ${e.message}", e)
            null
        }
    }
    
    fun verifySignature(context: Context, filePath: String, expectedSignature: String?): Boolean {
        if (expectedSignature == null) {
            Log.w(TAG, "No expected signature provided, skipping verification")
            return true
        }
        
        val apkInfo = getApkInfo(context, filePath) ?: return false
        val isValid = apkInfo.signature == expectedSignature
        
        if (!isValid) {
            Log.e(TAG, "Signature mismatch! Expected: $expectedSignature, Actual: ${apkInfo.signature}")
        }
        
        return isValid
    }
    
    fun installApk(
        context: Context, 
        filePath: String,
        expectedSignature: String? = null,
        onInstallRequested: ((ApkInfo) -> Unit)? = null
    ): InstallResult {
        if (!filePath.endsWith(".apk", ignoreCase = true)) {
            return InstallResult(false, "文件必须是 APK 格式")
        }
        
        val file = File(filePath)
        if (!file.exists()) {
            return InstallResult(false, "APK 文件不存在")
        }
        
        if (!isPathSafe(context, file)) {
            return InstallResult(false, "非法的文件路径")
        }
        
        val apkInfo = getApkInfo(context, filePath)
            ?: return InstallResult(false, "无法读取 APK 信息")
        
        if (expectedSignature != null && apkInfo.signature != expectedSignature) {
            Log.e(TAG, "Signature mismatch! Expected: $expectedSignature, Actual: ${apkInfo.signature}")
            return InstallResult(false, "APK 签名验证失败")
        }
        
        onInstallRequested?.invoke(apkInfo)
        
        return try {
            performInstall(context, file)
            InstallResult(true, apkInfo = apkInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Install failed: ${e.message}", e)
            InstallResult(false, "安装失败: ${e.message}")
        }
    }
    
    private fun isPathSafe(context: Context, file: File): Boolean {
        val canonicalPath = try {
            file.canonicalPath
        } catch (e: Exception) {
            return false
        }
        
        val allowedDirs = listOfNotNull(
            context.cacheDir,
            context.filesDir,
            context.externalCacheDir,
            context.getExternalFilesDir(null)
        )
        
        return allowedDirs.any { dir ->
            canonicalPath.startsWith(dir.canonicalPath)
        }
    }
    
    private fun calculateSignature(signatures: Array<Signature>?): String {
        if (signatures.isNullOrEmpty()) return ""
        
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            md.update(signatures[0].toByteArray())
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to calculate signature", e)
            ""
        }
    }
    
    private fun cleanOldInstallFiles(cacheDir: File) {
        try {
            cacheDir.listFiles()?.filter { 
                it.name.startsWith("install_") && it.name.endsWith(".apk") 
            }?.forEach { it.delete() }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clean old install files: ${e.message}")
        }
    }
    
    @SuppressLint("SetWorldReadable")
    private fun performInstall(context: Context, file: File) {
        val cacheDir = context.externalCacheDir ?: context.cacheDir
        cleanOldInstallFiles(cacheDir)
        val cachedApkFile = File(cacheDir, "install_${System.currentTimeMillis()}.apk")
        
        try {
            file.copyTo(cachedApkFile, overwrite = true)
            
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                cachedApkFile.setReadable(true, false)
            }
            
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.FileProvider",
                    cachedApkFile
                )
            } else {
                Uri.fromFile(cachedApkFile)
            }
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            context.startActivity(intent)
        } catch (e: Exception) {
            cachedApkFile.delete()
            throw e
        }
    }
}
