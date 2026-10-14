package com.bigwizard.aria.util

/**
 * Spoken app names → package names. Used as a fallback when an app can't be
 * matched by its launcher label (see CommandExecutor.openApp). Single source of
 * truth for both the parser and the executor.
 */
object KnownApps {

    private val PACKAGES = mapOf(
        "youtube"    to "com.google.android.youtube",
        "youtube music" to "com.google.android.apps.youtube.music",
        "maps"       to "com.google.android.apps.maps",
        "google maps" to "com.google.android.apps.maps",
        "calculator" to "com.android.calculator2",
        "calendar"   to "com.android.calendar",
        "contacts"   to "com.android.contacts",
        "messages"   to "com.google.android.apps.messaging",
        "clock"      to "com.android.deskclock",
        "camera"     to "com.android.camera2",
        "spotify"    to "com.spotify.music",
        "whatsapp"   to "com.whatsapp",
        "telegram"   to "org.telegram.messenger",
        "instagram"  to "com.instagram.android",
        "twitter"    to "com.twitter.android",
        "x"          to "com.twitter.android",
        "facebook"   to "com.facebook.katana",
        "gmail"      to "com.google.android.gm",
        "chrome"     to "com.android.chrome",
        "firefox"    to "org.mozilla.firefox",
        "netflix"    to "com.netflix.mediaclient",
        "tiktok"     to "com.zhiliaoapp.musically",
        "snapchat"   to "com.snapchat.android",
        "reddit"     to "com.reddit.frontpage",
        "discord"    to "com.discord",
        "files"      to "com.android.documentsui",
        "settings"   to "com.android.settings",
        "photos"     to "com.google.android.apps.photos",
        "gallery"    to "com.android.gallery3d",
        "drive"      to "com.google.android.apps.docs",
        "docs"       to "com.google.android.apps.docs.editors.docs",
        "sheets"     to "com.google.android.apps.docs.editors.sheets"
    )

    fun packageFor(appName: String): String? = PACKAGES[appName.lowercase().trim()]

    fun isKnown(appName: String): Boolean = packageFor(appName) != null
}
