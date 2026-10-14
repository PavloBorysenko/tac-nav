package com.tacnav.mortar.activation

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class ActivationPayload(
    val serverUrl: String,
    val oneTimeCode: String,
)

object ActivationPayloadParser {
    private val jsonField =
        Regex("\"(server_url|activation_code|code)\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")

    fun parse(raw: String): ActivationPayload {
        val value = raw.trim()
        val payload = when {
            value.startsWith("{") -> parseJsonShape(value)
            value.contains('\n') -> {
                val lines = value.lines().map(String::trim).filter(String::isNotEmpty)
                require(lines.size == 2) { "QR code does not contain activation details." }
                ActivationPayload(lines[0], lines[1])
            }
            else -> parseUri(value)
        }
        return validate(payload)
    }

    fun fromManual(serverUrl: String, code: String): ActivationPayload =
        validate(ActivationPayload(serverUrl.trim(), code.trim()))

    private fun parseJsonShape(value: String): ActivationPayload {
        val fields = jsonField.findAll(value).associate {
            it.groupValues[1] to unescape(it.groupValues[2])
        }
        return ActivationPayload(
            serverUrl = fields["server_url"].orEmpty(),
            oneTimeCode = fields["activation_code"] ?: fields["code"].orEmpty(),
        )
    }

    private fun parseUri(value: String): ActivationPayload {
        val uri = URI(value)
        val parameters = uri.rawQuery.orEmpty()
            .split('&')
            .filter { it.contains('=') }
            .associate {
                val (key, encoded) = it.split('=', limit = 2)
                key to URLDecoder.decode(encoded, StandardCharsets.UTF_8.name())
            }
        val serverUrl = parameters["server_url"] ?: if (uri.scheme == "https") {
            "${uri.scheme}://${uri.authority}"
        } else {
            ""
        }
        return ActivationPayload(
            serverUrl = serverUrl,
            oneTimeCode = parameters["activation_code"] ?: parameters["code"].orEmpty(),
        )
    }

    private fun validate(payload: ActivationPayload): ActivationPayload {
        val normalizedUrl = payload.serverUrl.trimEnd('/')
        require(normalizedUrl.startsWith("https://")) {
            "The server URL must use HTTPS."
        }
        require(payload.oneTimeCode.isNotBlank()) {
            "Enter the one-time activation code."
        }
        return payload.copy(serverUrl = normalizedUrl)
    }

    private fun unescape(value: String): String = value
        .replace("\\/", "/")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
}
