package tech.conceptualarts.maiflow.api

enum class MAIFlowErrorKind {
    AUTHENTICATION,
    PAID_PLAN,
    PERMISSION,
    VALIDATION,
    NOT_FOUND,
    RATE_LIMITED,
    SERVER,
    NETWORK,
    PROTOCOL,
    CONFIGURATION,
}

class MAIFlowApiError(
    val status: Int?,
    override val message: String,
    val kind: MAIFlowErrorKind,
    val canRetryRead: Boolean = false,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    companion object {
        fun fromStatus(status: Int, upstreamMessage: String? = null): MAIFlowApiError {
            val detail = sanitize(upstreamMessage).takeIf { it.isNotBlank() }
            val message = when (status) {
                401 -> "MAIFlow token is invalid, revoked, or missing."
                402 -> "MAIFlow MCP access requires an active Personal or Team plan."
                403 -> "MAIFlow denied this operation because the account, verification, organization membership, role, or entitlement does not allow it${detail?.let { ": $it" } ?: "."}"
                400 -> "MAIFlow rejected the request${detail?.let { ": $it" } ?: "."}"
                404 -> "The MAIFlow task or flow no longer exists or is inaccessible."
                429 -> "MAIFlow is rate-limiting requests. Please retry shortly."
                in 500..599 -> "MAIFlow is temporarily unavailable. Please retry."
                else -> "MAIFlow returned an unexpected HTTP status ($status)."
            }
            val kind = when (status) {
                401 -> MAIFlowErrorKind.AUTHENTICATION
                402 -> MAIFlowErrorKind.PAID_PLAN
                403 -> MAIFlowErrorKind.PERMISSION
                400 -> MAIFlowErrorKind.VALIDATION
                404 -> MAIFlowErrorKind.NOT_FOUND
                429 -> MAIFlowErrorKind.RATE_LIMITED
                in 500..599 -> MAIFlowErrorKind.SERVER
                else -> MAIFlowErrorKind.SERVER
            }
            return MAIFlowApiError(status, message, kind, status == 429 || status in 500..599)
        }

        fun network(message: String, cause: Throwable? = null): MAIFlowApiError =
            MAIFlowApiError(null, message, MAIFlowErrorKind.NETWORK, canRetryRead = true, cause = cause)

        fun configuration(message: String): MAIFlowApiError =
            MAIFlowApiError(null, message, MAIFlowErrorKind.CONFIGURATION, canRetryRead = false)

        fun protocol(message: String, cause: Throwable? = null): MAIFlowApiError =
            MAIFlowApiError(null, "MAIFlow returned an unexpected protocol response. Please retry or contact support.", MAIFlowErrorKind.PROTOCOL, canRetryRead = true, cause = cause)

        private fun sanitize(value: String?): String = value
            ?.replace(Regex("mf_live_[A-Za-z0-9_-]+"), "mf_live_[redacted]")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?: ""
    }
}
