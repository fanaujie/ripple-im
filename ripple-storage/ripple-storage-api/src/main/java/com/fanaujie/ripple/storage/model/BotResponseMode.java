package com.fanaujie.ripple.storage.model;

public enum BotResponseMode {
    /**
     * Push delta events as they arrive, plus final DONE event. Provides real-time streaming effect
     * for AI chatbots.
     */
    STREAMING,

    /**
     * Only push DONE event with complete response. Delta events are accumulated locally but not
     * pushed. Suitable for bots that don't need streaming effect.
     */
    BATCH
}
