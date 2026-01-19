package com.entgldb.core.query

import kotlin.reflect.KProperty

class QueryBuilder {
    private val nodes = mutableListOf<QueryNode>()

    infix fun String.eq(value: Any?) = nodes.add(QueryNode.Eq(this, value))
    infix fun String.neq(value: Any?) = nodes.add(QueryNode.Ne(this, value))
    infix fun String.gt(value: Any?) = nodes.add(QueryNode.Gt(this, value))
    infix fun String.lt(value: Any?) = nodes.add(QueryNode.Lt(this, value))
    infix fun String.gte(value: Any?) = nodes.add(QueryNode.Gte(this, value))
    infix fun String.lte(value: Any?) = nodes.add(QueryNode.Lte(this, value))

    // Valid only if KProperty is available (commonMain might not have full reflection support without dependency)
    // But basic property reference usually works as KProperty<*> or generic.
    infix fun KProperty<*>.eq(value: Any?) = nodes.add(QueryNode.Eq(this.name, value))
    infix fun KProperty<*>.neq(value: Any?) = nodes.add(QueryNode.Ne(this.name, value))
    infix fun KProperty<*>.gt(value: Any?) = nodes.add(QueryNode.Gt(this.name, value))
    infix fun KProperty<*>.lt(value: Any?) = nodes.add(QueryNode.Lt(this.name, value))
    infix fun KProperty<*>.gte(value: Any?) = nodes.add(QueryNode.Gte(this.name, value))
    infix fun KProperty<*>.lte(value: Any?) = nodes.add(QueryNode.Lte(this.name, value))

    fun and(init: QueryBuilder.() -> Unit) {
        val builder = QueryBuilder()
        builder.init()
        nodes.add(QueryNode.And(builder.buildNodes()))
    }

    fun or(init: QueryBuilder.() -> Unit) {
        val builder = QueryBuilder()
        builder.init()
        nodes.add(QueryNode.Or(builder.buildNodes()))
    }

    fun build(): QueryNode {
        return if (nodes.size == 1) {
            nodes[0]
        } else {
            QueryNode.And(nodes)
        }
    }
    
    // Internal helper to get list
    internal fun buildNodes() = nodes.toList()
}

fun query(init: QueryBuilder.() -> Unit): QueryNode {
    val builder = QueryBuilder()
    builder.init()
    return builder.build()
}
