package io.plady.moimyeon.client.webpush

import com.google.auth.oauth2.GoogleCredentials
import java.io.InputStream
import java.nio.charset.StandardCharsets

internal fun loadFcmCredentials(
    serviceAccountJson: String?,
    applicationDefault: () -> GoogleCredentials = GoogleCredentials::getApplicationDefault,
    fromStream: (InputStream) -> GoogleCredentials = GoogleCredentials::fromStream,
): GoogleCredentials {
    val json = serviceAccountJson?.takeIf { it.isNotBlank() }
        ?: return applicationDefault()

    return json.byteInputStream(StandardCharsets.UTF_8).use(fromStream)
}
