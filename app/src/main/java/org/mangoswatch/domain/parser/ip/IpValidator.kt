package org.mangoswatch.domain.parser.ip

object IpValidator {
    fun isValidIpv4(address: String): Boolean {
        val octets = address.split('.')
        return octets.size == 4 && octets.all { octet ->
            octet.isNotEmpty() &&
                octet.length <= 3 &&
                octet.all { it in '0'..'9' } &&
                octet.toIntOrNull()?.let { it in 0..255 } == true
        }
    }

    fun isValidIpv6(address: String): Boolean {
        return !(!address.contains(':') || address.contains('%')) && try {
            val bytes = java.net.InetAddress.getByName(address).address
            bytes.size == 16
        } catch (_: Exception) {
            false
        }
    }
}
