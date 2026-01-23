package com.entgldb.core

import kotlinx.serialization.Serializable

enum class CausalityRelation {
    Equal,
    StrictlyAhead,
    StrictlyBehind,
    Concurrent
}

@Serializable
class VectorClock(
    val clock: MutableMap<String, HlcTimestamp> = mutableMapOf()
) {



    val nodeIds: Set<String> get() = clock.keys

    fun getTimestamp(nodeId: String): HlcTimestamp? = clock[nodeId]

    fun setTimestamp(nodeId: String, timestamp: HlcTimestamp) {
        clock[nodeId] = timestamp
    }

    fun merge(other: VectorClock) {
        for ((nodeId, otherTs) in other.clock) {
            val currentTs = clock[nodeId]
            if (currentTs == null || otherTs > currentTs) {
                clock[nodeId] = otherTs
            }
        }
    }

    fun compareTo(other: VectorClock): CausalityRelation {
        var thisAhead = false
        var otherAhead = false
        
        val allNodes = clock.keys + other.clock.keys

        for (nodeId in allNodes) {
            val thisTs = clock[nodeId]
            val otherTs = other.clock[nodeId]

            var cmp = 0
            if (thisTs != null && otherTs != null) {
                cmp = thisTs.compareTo(otherTs)
            } else if (thisTs != null && otherTs == null) {
                cmp = 1
            } else if (thisTs == null && otherTs != null) {
                cmp = -1
            }

            if (cmp > 0) thisAhead = true
            if (cmp < 0) otherAhead = true

            if (thisAhead && otherAhead) return CausalityRelation.Concurrent
        }

        if (thisAhead && !otherAhead) return CausalityRelation.StrictlyAhead
        if (otherAhead && !thisAhead) return CausalityRelation.StrictlyBehind
        return CausalityRelation.Equal
    }

    fun getNodesWithUpdates(other: VectorClock): List<String> {
        val result = mutableListOf<String>()
        val allNodes = clock.keys + other.clock.keys // Actually we care about nodes OTHER has that WE need.

        // If OTHER has a node we don't, or OTHER is ahead -> we need updates FROM other for that node.
        // Wait, logic in Node.js:
        // "Nodes where remote (other) is ahead"
        
        for (nodeId in other.nodeIds) {
            val thisTs = clock[nodeId]
            val otherTs = other.clock[nodeId] // always exists since iterating other.nodeIds

             if (otherTs != null) {
                if (thisTs == null || otherTs > thisTs) {
                    result.add(nodeId)
                }
            }
        }
        return result
    }

    fun getNodesToPush(other: VectorClock): List<String> {
         val result = mutableListOf<String>()
         
         // If WE have a node other doesn't, or WE are ahead -> we push updates TO other.
         for (nodeId in nodeIds) {
             val thisTs = clock[nodeId]
             val otherTs = other.clock[nodeId]
             
             if (thisTs != null) {
                 if (otherTs == null || thisTs > otherTs) {
                     result.add(nodeId)
                 }
             }
         }
         return result
    }
    
    // Helper to clone safely if needed, though mostly used transiently
    fun clone(): VectorClock {
        return VectorClock(HashMap(clock))
    }
    
    override fun toString(): String {
        return clock.entries.joinToString(", ", "{", "}") { "${it.key}:${it.value}" }
    }
}
