package org.mangoswatch.domain.model

enum class EndpointTaxonomyCategory {
    CLEARCUT_TELEMETRY,
    DEVICE_FINGERPRINT,
    LOCATION_SERVICES,
    FIREBASE_ANALYTICS,
    ADVERTISING_ID,
    BACKUP_SYNC,
    NOTIFICATION_PUSH,
    AUTHENTICATION,
    UPDATE_CHECK,
    CONFIG_A_B_TESTING,
    PLAY_STORE_COMMUNICATION,
    SEARCH_USAGE,
    VOICE_AUDIO,
    UNKNOWN;

    val displayName: String
        get() = name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
}