package com.maxrave.simpmusic.musicgen

expect fun startGenerationMonitor(
    jobId: String,
    title: String,
)

expect fun localGeneratedAudioUri(jobId: String): String?

expect fun saveGeneratedAudio(
    jobId: String,
    bytes: ByteArray,
): String

expect fun shareGeneratedAudio(
    uri: String,
    title: String,
)

expect fun exportGeneratedAudio(
    fileName: String,
    mimeType: String,
    bytes: ByteArray,
)

expect fun deleteGeneratedAudio(jobId: String)
