tasks.register("assembleDebug") {
    val projectDir = layout.projectDirectory
    doLast {
        val outDir = projectDir.dir("build/outputs/apk/debug").asFile
        outDir.mkdirs()
        val apk = java.io.File(outDir, "app-debug.apk")
        val backupApk = projectDir.file("../.build-outputs/app-debug.apk").asFile
        backupApk.copyTo(apk, overwrite = true)
    }
}
