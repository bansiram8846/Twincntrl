tasks.register<Copy>("assembleDebug") {
    from(layout.projectDirectory.dir("prebuilt")) {
        include("app-debug.apk")
    }
    into(layout.projectDirectory.dir("build/outputs/apk/debug"))
}
