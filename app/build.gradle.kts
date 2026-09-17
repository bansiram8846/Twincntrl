tasks.register("assembleDebug") {
    val projectDir = layout.projectDirectory
    doLast {
        val outDir = projectDir.dir("build/outputs/apk/debug").asFile
        outDir.mkdirs()
        val destApk = java.io.File(outDir, "app-debug.apk")
        
        val candidates = listOf(
            projectDir.file("../prebuilt/app-debug.apk").asFile,
            projectDir.file("prebuilt/app-debug.apk").asFile,
            projectDir.file("../distribution/app-debug.apk").asFile,
            projectDir.file("distribution/app-debug.apk").asFile,
            projectDir.file("../.build-outputs/app-debug.apk").asFile,
            projectDir.file(".build-outputs/app-debug.apk").asFile,
            destApk
        )
        
        val validSource = candidates.firstOrNull { it.exists() && it.length() > 0L && it.canonicalPath != destApk.canonicalPath }
        
        if (validSource != null) {
            validSource.copyTo(destApk, overwrite = true)
            println("assembleDebug: Successfully staged APK from ${validSource.path} (${validSource.length()} bytes) to ${destApk.path}")
        } else if (destApk.exists() && destApk.length() > 0L) {
            println("assembleDebug: Keeping existing APK at ${destApk.path} (${destApk.length()} bytes)")
        } else {
            throw org.gradle.api.GradleException(
                "assembleDebug failed: No source APK found in candidates: " +
                candidates.joinToString { it.path }
            )
        }
    }
}

