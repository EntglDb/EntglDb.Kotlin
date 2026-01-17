package com.entgldb.core.sync

import com.entgldb.core.Document
import com.entgldb.core.OplogEntry

data class ConflictResolutionResult(
    val shouldApply: Boolean,
    val mergedDocument: Document?
) {
    companion object {
        fun apply(document: Document) = ConflictResolutionResult(true, document)
        fun ignore() = ConflictResolutionResult(false, null)
    }
}

interface IConflictResolver {
    fun resolve(local: Document?, remote: OplogEntry): ConflictResolutionResult
}
