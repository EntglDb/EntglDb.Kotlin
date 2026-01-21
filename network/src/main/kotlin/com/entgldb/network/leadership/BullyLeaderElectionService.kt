package com.entgldb.network.leadership

import android.util.Log
import com.entgldb.network.config.IPeerNodeConfigurationProvider
import com.entgldb.network.discovery.IDiscoveryService
import com.entgldb.core.network.PeerNode
import com.entgldb.core.network.PeerType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Implements the Bully Algorithm for leader election.
 * The node with the lexicographically smallest NodeID becomes the leader (Cloud Gateway).
 */
class BullyLeaderElectionService(
    private val configProvider: IPeerNodeConfigurationProvider,
    private val discoveryService: IDiscoveryService
) : ILeaderElectionService {

    companion object {
        private const val TAG = "BullyElection"
        private const val ELECTION_INTERVAL_MS = 5000L
    }

    private val _leadershipChanged = MutableStateFlow(false)
    override val leadershipChanged: StateFlow<Boolean> = _leadershipChanged.asStateFlow()

    override val isLeader: Boolean
        get() = _leadershipChanged.value

    private var electionJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun start() {
        if (electionJob != null) return

        electionJob = scope.launch {
            while (isActive) {
                runElection()
                delay(ELECTION_INTERVAL_MS)
            }
        }
        Log.i(TAG, "Bully Leader Election Service started")
    }

    override fun stop() {
        electionJob?.cancel()
        electionJob = null
        scope.cancel()
        Log.i(TAG, "Bully Leader Election Service stopped")
    }

    private fun runElection() {
        try {
            val config = configProvider.getConfiguration()
            val myNodeId = config.nodeId
            
            // Get all visible peers
            val peers = discoveryService.getActivePeers()
            
            // Filter eligible peers (e.g. only LanDiscovered or StaticRemote?)
            // Usually we only elect among LAN peers for gateway role.
            // Assuming LanDiscovered peers.
            val eligiblePeers = peers.filter { it.type == PeerType.LanDiscovered || it.type == PeerType.StaticRemote }
            
            // Find if there is any peer with smaller ID
            val hasLowerId = eligiblePeers.any { it.nodeId < myNodeId }
            
            val amILeader = !hasLowerId
            
            if (_leadershipChanged.value != amILeader) {
                Log.i(TAG, "Leadership changed: ImLeader=$amILeader (MyId=$myNodeId)")
                _leadershipChanged.value = amILeader
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error running election", e)
        }
    }
}
