package org.mangoswatch.domain.model

data class DnsSettings(
    val dnsMode: DnsMode = DnsMode.SYSTEM,
    val dnsV4: String = "",
    val dnsV6: String = ""
)

enum class DnsMode {
    SYSTEM,
    CUSTOM
}
