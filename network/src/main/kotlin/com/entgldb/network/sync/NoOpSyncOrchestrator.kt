package com.entgldb.network.sync

/**
 * No-operation sync orchestrator for server scenarios.
 */
class NoOpSyncOrchestrator : ISyncOrchestrator {
    override fun start() {
        // Do nothing
    }

    override fun stop() {
        // Do nothing
    }
}
