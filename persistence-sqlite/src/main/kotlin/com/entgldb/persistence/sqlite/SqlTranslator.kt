package com.entgldb.persistence.sqlite

import com.entgldb.core.query.QueryNode

object SqlTranslator {
    fun translate(query: QueryNode): Pair<String, Array<String>> {
        val args = mutableListOf<String>()
        val clause = buildClause(query, args)
        return clause to args.toTypedArray()
    }

    private fun buildClause(node: QueryNode, args: MutableList<String>): String {
        return when (node) {
            is QueryNode.And -> {
                if (node.children.isEmpty()) return "1=1"
                "(" + node.children.joinToString(" AND ") { buildClause(it, args) } + ")"
            }
            is QueryNode.Or -> {
                if (node.children.isEmpty()) return "1=0"
                "(" + node.children.joinToString(" OR ") { buildClause(it, args) } + ")"
            }
            is QueryNode.Eq -> buildBinaryOp("=", node.property, node.value, args)
            is QueryNode.Ne -> buildBinaryOp("<>", node.property, node.value, args)
            is QueryNode.Gt -> buildBinaryOp(">", node.property, node.value, args)
            is QueryNode.Lt -> buildBinaryOp("<", node.property, node.value, args)
            is QueryNode.Gte -> buildBinaryOp(">=", node.property, node.value, args)
            is QueryNode.Lte -> buildBinaryOp("<=", node.property, node.value, args)
            // PropertyNode is sealed but data classes are final.
        }
    }

    private fun buildBinaryOp(op: String, property: String, value: Any?, args: MutableList<String>): String {
        // Use property name directly as JSON path key. Assumes properties are at root of JsonData.
        val column = "json_extract(JsonData, '$.\"$property\"')"
        
        return when (value) {
            is Number -> "$column $op $value"
            is Boolean -> {
                // SQLite has no boolean, uses 0/1. json_extract might return 0/1 or 'true'/'false' depending on storage?
                // EntglDb stores as JSON text. json_extract returns values.
                // If stored as true/false in JSON, sqlite extract might return them as text?
                // Actually SQLite `json_extract` returns semantic values.
                // Safest to compare against 1/0 if we store as such, or match flexible.
                // Let's assume standard JSON boolean.
                val boolVal = if (value) 1 else 0
                "$column $op $boolVal" 
            }
            null -> {
                if (op == "=") "$column IS NULL"
                else if (op == "<>") "$column IS NOT NULL"
                else "$column $op NULL"
            }
            else -> {
                args.add(value.toString())
                "$column $op ?"
            }
        }
    }
}
