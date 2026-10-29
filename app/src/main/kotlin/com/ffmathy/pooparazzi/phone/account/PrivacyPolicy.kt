package com.ffmathy.pooparazzi.phone.account

/**
 * Where the privacy policy is read (docs/PRIVACY.md): the project's website in builds with a Firebase configuration
 * (`https://<project-id>.web.app/privacy`), else the policy in the repository.
 */
object PrivacyPolicy {
    const val REPOSITORY_URL = "https://github.com/ffMathy/pooparazzi/blob/main/docs/PRIVACY.md"

    fun url(hostingDomain: String?): String = hostingDomain?.let { "https://$it/privacy" } ?: REPOSITORY_URL
}
