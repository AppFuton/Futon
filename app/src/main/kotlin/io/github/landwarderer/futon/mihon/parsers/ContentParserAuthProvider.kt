package io.github.landwarderer.futon.mihon.parsers

/**
 * Implement this in your parser for authorization support
 */
interface ContentParserAuthProvider {

    /**
     * Return link to the login page, which will be opened in browser.
     * Must be an absolute url
     */
    val authUrl: String

    /**
     * Quick check if user is logged in.
     * In most case you should check for cookies in [ContentLoaderContext.cookieJar].
     */
    suspend fun isAuthorized(): Boolean

    /**
     * Fetch and return current user`s name or login.
     * Normally should not be called if [isAuthorized] returns false
     * @throws [AuthRequiredException] if user is not logged in or authorization is expired
     * @throws [ParseException] on parsing error
     */
    suspend fun getUsername(): String
}
