package com.ffmathy.pooparazzi.phone.launch

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/** An App Link on the project's website that opened the app (BABIES_AND_BACKUP §3.3, §3.5). */
sealed interface AppLink {
    /** `https://<project-id>.web.app/join/<code>`: an invite to a family. */
    data class Invite(val code: String) : AppLink

    /** `https://<project-id>.web.app/__/auth/links?…` (or `/signin…`): an email sign-in link, whole. */
    data class EmailSignIn(val link: String) : AppLink
}

/**
 * The App Links of the website (docs/BACKEND.md › App Links): on `<project-id>.web.app` and
 * `<project-id>.firebaseapp.com`, `/join/<code>`, `/__/auth/links` and `/signin`. The manifest declares them only in
 * builds with a Firebase configuration.
 */
object AppLinks {
    /** The link [uri] is, for the site [hostingDomain] (`<project-id>.web.app`); null for anything else. */
    fun parse(uri: Uri?, hostingDomain: String?): AppLink? {
        if (uri == null || hostingDomain == null || uri.scheme != "https") return null
        if (uri.host?.lowercase() !in hostsOf(hostingDomain)) return null
        val path = uri.path.orEmpty()
        val segments = uri.pathSegments
        return when {
            segments.size == 2 && segments[0] == "join" -> AppLink.Invite(segments[1])
            path.startsWith("/__/auth/links") || path == "/signin" || path.startsWith("/signin/") ->
                AppLink.EmailSignIn(uri.toString())
            else -> null
        }
    }

    /** The site's two hosts: `<id>.web.app` and `<id>.firebaseapp.com`. */
    fun hostsOf(hostingDomain: String): Set<String> {
        val domain = hostingDomain.lowercase()
        return setOf(domain, domain.removeSuffix(".web.app") + ".firebaseapp.com")
    }
}

/**
 * The App Links waiting to be handled: an invite until it is joined or put aside, an email link until the Account (or
 * Welcome) screen takes it. Kept in memory for the process.
 */
class AppLinkInbox {
    private val pendingInvite = MutableStateFlow<String?>(null)
    private val pendingEmailLink = MutableStateFlow<String?>(null)

    /** The code of an invite link that opened the app, until handled. */
    val invite: StateFlow<String?> = pendingInvite.asStateFlow()

    /** An email sign-in link that opened the app, until taken. */
    val emailLink: StateFlow<String?> = pendingEmailLink.asStateFlow()

    fun post(link: AppLink) = when (link) {
        is AppLink.Invite -> pendingInvite.value = link.code
        is AppLink.EmailSignIn -> pendingEmailLink.value = link.link
    }

    /** The invite was joined, or put aside ("Not now"). */
    fun inviteHandled() {
        pendingInvite.value = null
    }

    /** Takes the waiting email link, once. */
    fun takeEmailLink(): String? = pendingEmailLink.getAndUpdate { null }

    companion object {
        /** The app's inbox. */
        val shared = AppLinkInbox()
    }
}
