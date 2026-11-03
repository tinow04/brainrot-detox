package dev.tinow.brainrotstopper.domain.model

import kotlinx.serialization.json.Json

/** One JSON configuration for all files: unknown keys are skipped (forward compat), defaults are written out. */
val BsJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = false
}
