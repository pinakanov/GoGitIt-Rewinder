package com.gogitit.rewinder.audio

/**
 * Lets each platform show its "Rewinder in progress" surface:
 * Android starts a microphone foreground service with an ongoing notification,
 * iOS will start a Live Activity (implement this interface in Swift).
 */
interface SessionHooks {
    fun onSessionStarted()
    fun onSessionStopped()
}

object NoopSessionHooks : SessionHooks {
    override fun onSessionStarted() = Unit
    override fun onSessionStopped() = Unit
}
