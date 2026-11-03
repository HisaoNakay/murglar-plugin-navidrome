package com.badmanners.murglar.lib.navidrome.login

import com.badmanners.murglar.lib.core.localization.MessageException
import com.badmanners.murglar.lib.core.localization.Messages.Companion.loginWith
import com.badmanners.murglar.lib.core.localization.Messages.Companion.youAreNotLoggedInWithServiceName
import com.badmanners.murglar.lib.core.login.CredentialLoginStep
import com.badmanners.murglar.lib.core.login.CredentialsLoginVariant
import com.badmanners.murglar.lib.core.login.CredentialsLoginVariant.Credential
import com.badmanners.murglar.lib.core.login.LoginResolver
import com.badmanners.murglar.lib.core.login.SuccessfulLogin
import com.badmanners.murglar.lib.core.login.WebLoginVariant
import com.badmanners.murglar.lib.core.notification.NotificationMiddleware
import com.badmanners.murglar.lib.core.webview.WebViewProvider
import com.badmanners.murglar.lib.navidrome.api.SubsonicClient
import com.badmanners.murglar.lib.navidrome.localization.NavidromeMessages


class NavidromeLoginResolver(
    private val notifications: NotificationMiddleware,
    private val client: SubsonicClient,
    private val messages: NavidromeMessages
) : LoginResolver {

    companion object {
        const val CREDENTIALS_LOGIN_VARIANT = "credentials"
        const val SERVER_CREDENTIAL = "server"
        const val USERNAME_CREDENTIAL = "username"
        const val PASSWORD_CREDENTIAL = "password"
    }

    override val isLogged: Boolean
        get() = client.hasCredentials

    override val loginInfo: String
        get() = when {
            isLogged -> "${messages.youAreLoggedIn}: ${client.username} @ ${client.serverUrl}"
            else -> messages.youAreNotLoggedIn
        }

    override val webLoginVariants = emptyList<WebLoginVariant>()

    override suspend fun webLogin(loginVariantId: String, webViewProvider: WebViewProvider): Boolean =
        throw UnsupportedOperationException("webLogin")

    override val credentialsLoginVariants = listOf(
        CredentialsLoginVariant(
            id = CREDENTIALS_LOGIN_VARIANT,
            label = { messages.loginWith(username = true) },
            credentials = listOf(
                Credential(SERVER_CREDENTIAL, messages::serverAddress),
                Credential(USERNAME_CREDENTIAL, messages::username),
                Credential(PASSWORD_CREDENTIAL, messages::password, secure = true)
            )
        )
    )

    override suspend fun credentialsLogin(loginVariantId: String, args: Map<String, String>): CredentialLoginStep {
        check(loginVariantId == CREDENTIALS_LOGIN_VARIANT) { "Unknown loginVariantId: $loginVariantId!" }
        val server = args[SERVER_CREDENTIAL]?.trim()
        val username = args[USERNAME_CREDENTIAL]?.trim()
        val password = args[PASSWORD_CREDENTIAL]
        if (server.isNullOrEmpty() || username.isNullOrEmpty() || password.isNullOrEmpty())
            throw MessageException(messages.invalidCredentials)

        try {
            client.login(server, username, password)
        } catch (e: Exception) {
            logout()
            throw e
        }
        return SuccessfulLogin
    }

    override fun logout() = client.logout()

    fun checkLogged() {
        if (isLogged)
            return
        notifications.shortNotify(messages.youAreNotLoggedInWithServiceName)
        throw MessageException(messages.youAreNotLoggedInWithServiceName)
    }
}
