package app.glance.launcher.system

/**
 * What the APK carries from other people, and the licence text each must travel with. The files
 * are the repo's `licenses/`, packaged as assets; OpenSourceNoticesTest keeps the two equal.
 */
object OpenSourceNotices {
    data class Notice(val name: String, val usedFor: String, val asset: String)

    val all = listOf(
        Notice("Lora", "The serif in the wordmarks", "Lora-OFL.txt"),
        Notice("Lucide and Feather", "Icons", "Lucide-ISC.txt"),
        Notice("AndroidX, Jetpack Compose, Material Icons and Kotlin", "The app's framework and libraries", "Apache-2.0.txt"),
    )
}
