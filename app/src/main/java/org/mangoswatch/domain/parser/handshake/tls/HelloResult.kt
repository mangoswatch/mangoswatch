package org.mangoswatch.domain.parser.handshake.tls

sealed interface HelloResult

object NeedMore : HelloResult               // The buffer holds a valid prefix of a ClientHello; feed more bytes.
object NotTls : HelloResult                 // The stream does not start with a TLS ClientHello (or it is malformed).
data class ClientHello(val sni: String?) : HelloResult
