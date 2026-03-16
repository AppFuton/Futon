package io.github.landwarderer.futon.core.network

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import io.github.landwarderer.futon.BuildConfig
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug
import okhttp3.CipherSuite
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.TlsVersion
import okhttp3.tls.HandshakeCertificates
import org.conscrypt.Conscrypt
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

@SuppressLint("CustomX509TrustManager, TrustAllX509TrustManager")
fun OkHttpClient.Builder.applyTlsConfiguration() = also { builder ->
    runCatching {
        val trustAllCerts = object : X509TrustManager {
            override fun checkClientTrusted(p0: Array<out X509Certificate>?, p1: String?) {}
            override fun checkServerTrusted(p0: Array<out X509Certificate>?, p1: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }

        val sslContext = SSLContext.getInstance("TLS", Conscrypt.newProvider())
        sslContext.init(null, arrayOf(trustAllCerts), SecureRandom())

        val spec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .tlsVersions(
                TlsVersion.TLS_1_3,
                TlsVersion.TLS_1_2,
                TlsVersion.TLS_1_1,
                TlsVersion.TLS_1_0,
            )
            .cipherSuites(
                CipherSuite.TLS_AES_128_GCM_SHA256,
                CipherSuite.TLS_AES_256_GCM_SHA384,
                CipherSuite.TLS_CHACHA20_POLY1305_SHA256,
                CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
                CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
                CipherSuite.TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256,
                CipherSuite.TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256,
            )
            .build()

        builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts)
        builder.connectionSpecs(listOf(spec, ConnectionSpec.CLEARTEXT))
        builder.hostnameVerifier { _, _ -> true }

        // "Fake" browser to avoid 403s/Handshake blocks because Cloudflare checks the User-Agent
        // against the TLS Fingerprint and if they don't match, it triggers a challenge or a block.
        builder.addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36",
                )
                .header("Sec-CH-UA", "\"Chromium\";v=\"146\", \"Not(A:Brand\";v=\"24\", \"Google Chrome\";v=\"146\"")
                .header("Sec-CH-UA-Mobile", "?0")
                .header("Sec-CH-UA-Platform", "\"Windows\"")
                .build()
            chain.proceed(request)
        }
    }.onFailure { it.printStackTraceDebug("SSLUtils::applyTlsConfiguration") }
}

fun OkHttpClient.Builder.installExtraCertificates(context: Context) = also { builder ->
    val certificatesBuilder = HandshakeCertificates.Builder()
        .addPlatformTrustedCertificates()
    val assets = context.assets.list("").orEmpty()
    for (path in assets) {
        if (path.endsWith(".pem")) {
            val cert = loadCert(context, path) ?: continue
            certificatesBuilder.addTrustedCertificate(cert)
        }
    }
    val certificates = certificatesBuilder.build()
    builder.sslSocketFactory(certificates.sslSocketFactory(), certificates.trustManager)
}

private fun loadCert(context: Context, path: String): X509Certificate? = runCatching {
    val cf = CertificateFactory.getInstance("X.509")
    context.assets.open(path, AssetManager.ACCESS_STREAMING).use {
        cf.generateCertificate(it)
    } as X509Certificate
}.onFailure { it.printStackTraceDebug("SSLUtils::loadCert") }
    .onSuccess {
        if (BuildConfig.DEBUG) {
            Log.i("ExtraCerts", "Loaded cert $path")
        }
    }.getOrNull()
