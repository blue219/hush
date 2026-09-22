package com.blue.hush.session

import java.util.concurrent.CopyOnWriteArraySet

/** Lightweight process-local bridge between the foreground service and Compose. */
object SessionRuntime {
    private val listeners = CopyOnWriteArraySet<(SessionState) -> Unit>()

    @Volatile
    var current: SessionState = SessionState()
        private set

    fun publish(state: SessionState) {
        current = state
        listeners.forEach { it(state) }
    }

    fun subscribe(listener: (SessionState) -> Unit): () -> Unit {
        listeners += listener
        listener(current)
        return { listeners -= listener }
    }
}
