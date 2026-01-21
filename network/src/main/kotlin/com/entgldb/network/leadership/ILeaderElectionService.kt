package com.entgldb.network.leadership

import kotlinx.coroutines.flow.Flow

interface ILeaderElectionService {
    val isLeader: Boolean
    val leadershipChanged: Flow<Boolean>

    fun start()
    fun stop()
}
