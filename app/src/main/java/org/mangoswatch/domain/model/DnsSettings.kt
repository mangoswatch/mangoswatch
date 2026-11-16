package org.mangoswatch.domain.model

data class DnsSettings(
    val dnsMode: DnsMode = DnsMode.CLOUDFLARE,
    val dnsV4: String = "1.1.1.1,1.0.0.1",
    val dnsV6: String = "2606:4700:4700::1111,2606:4700:4700::1001"
)

enum class DnsMode {
    CLOUDFLARE,
    SYSTEM,
    CUSTOM
}
