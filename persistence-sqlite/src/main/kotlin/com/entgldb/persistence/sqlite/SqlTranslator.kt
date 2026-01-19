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
        val fieldName = toSnakeCase(property)
        // json_extract usage
        val column = "json_extract(data, '$.$fieldName')"
        
        return when (value) {
            is Number -> "$column $op $value"
            is Boolean -> {
                val boolVal = if (value) 1 else 0
                "$column $op $boolVal"
            }
            null -> {
                if (op == "=") "$column IS NULL"
                else if (op == "<>") "$column IS NOT NULL"
                else "$column $op NULL" // Usually null comparisons yield null/false
            }
            else -> {
                args.add(value.toString())
                "$column $op ?"
            }
        }
    }

    private fun toSnakeCase(str: String): String {
        return str.replace(Regex("([a-z])([A-Z]+)"), "$1_$2").lowercase()
    }
}
