package com.entgldb.core

import kotlinx.serialization.Serializable

@Serializable
data class HlcTimestamp(
    val physicalTime: Long,
    val logicalCounter: Int,
    val nodeId: String
) : Comparable<HlcTimestamp> {

    init {
        require(nodeId.isNotEmpty()) { "nodeId must not be empty" }
    }

    override fun compareTo(other: HlcTimestamp): Int {
        val timeComparison = physicalTime.compareTo(other.physicalTime)
        if (timeComparison != 0) return timeComparison

        val counterComparison = logicalCounter.compareTo(other.logicalCounter)
        if (counterComparison != 0) return counterComparison

        return nodeId.compareTo(other.nodeId)
    }

    override fun toString(): String = "$physicalTime:$logicalCounter:$nodeId"
}
