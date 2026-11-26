package com.mercan.maliyem.ui.lock

/** Arka planda kimlik doğrulaması istenmeden geçebilecek süre. */
enum class LockTimeout(val millis: Long) {
    IMMEDIATE(0),
    ONE_MINUTE(60_000),
    FIVE_MINUTES(300_000),
}

/** Süre dolduğu anda kilitlenir. Yeni oturumda arka plan zamanı yoktur. */
internal fun shouldLock(enabled: Boolean, timeout: LockTimeout, backgroundSince: Long?, now: Long): Boolean =
    enabled && (backgroundSince == null || now < backgroundSince || now - backgroundSince >= timeout.millis)
