package com.shopmandate.capture

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URLDecoder

/**
 * RFC 8252 loopback catcher. Listens on the device's localhost:[port] for the OAuth redirect
 * (Zepto/Swiggy only allow localhost redirect URIs), returns the (code, state) from the query,
 * and shows a "wapas app pe jao" page in the browser.
 */
object OAuthLoopback {
    const val PORT = 8971   // must match backend OAUTH_LOOPBACK_PORT

    data class Result(val code: String?, val state: String?)

    suspend fun await(timeoutMs: Int = 180_000): Result? = withContext(Dispatchers.IO) {
        var server: ServerSocket? = null
        try {
            server = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), PORT))
                soTimeout = timeoutMs
            }
            server.accept().use { socket ->
                val requestLine = socket.getInputStream().bufferedReader().readLine() ?: ""
                // "GET /callback?code=..&state=.. HTTP/1.1"
                val query = requestLine.substringAfter('?', "").substringBefore(' ')
                val params = query.split('&').mapNotNull { kv ->
                    val i = kv.indexOf('=')
                    if (i <= 0) null
                    else kv.substring(0, i) to runCatching {
                        URLDecoder.decode(kv.substring(i + 1), "UTF-8")
                    }.getOrDefault("")
                }.toMap()

                // Bounce the browser straight back to the app via a deep link (shopmandate://oauth/done).
                // Chrome may auto-launch it; the button is the guaranteed one-tap fallback.
                val deepLink = "shopmandate://oauth/done"
                val html =
                    "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'>" +
                        "<script>window.location.replace('$deepLink');" +
                        "setTimeout(function(){try{window.close()}catch(e){}},400);</script></head>" +
                        "<body style='font-family:system-ui;text-align:center;padding-top:80px;background:#F7F5FF'>" +
                        "<h2 style='color:#5B3DF5'>Connected ✓</h2>" +
                        "<p style='color:#555'>App pe wapas ja rahe hain…</p>" +
                        "<p><a href='$deepLink' style='display:inline-block;margin-top:16px;padding:14px 30px;" +
                        "background:#5B3DF5;color:#fff;border-radius:999px;text-decoration:none;font-weight:600'>" +
                        "App kholo</a></p></body></html>"
                socket.getOutputStream().apply {
                    write(
                        (
                            "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: text/html; charset=utf-8\r\n" +
                                "Content-Length: ${html.toByteArray().size}\r\n" +
                                "Connection: close\r\n\r\n" + html
                            ).toByteArray(),
                    )
                    flush()
                }
                Result(params["code"], params["state"])
            }
        } catch (e: Exception) {
            null
        } finally {
            runCatching { server?.close() }
        }
    }
}
