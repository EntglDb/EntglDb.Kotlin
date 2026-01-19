package com.entgldb.core.query

sealed class QueryNode {
    data class And(val children: List<QueryNode>) : QueryNode()
    data class Or(val children: List<QueryNode>) : QueryNode()
    
    // Property based nodes
    sealed class PropertyNode(open val property: String, open val value: Any?) : QueryNode()
    
    data class Eq(override val property: String, override val value: Any?) : PropertyNode(property, value)
    data class Ne(override val property: String, override val value: Any?) : PropertyNode(property, value)
    data class Gt(override val property: String, override val value: Any?) : PropertyNode(property, value)
    data class Lt(override val property: String, override val value: Any?) : PropertyNode(property, value)
    data class Gte(override val property: String, override val value: Any?) : PropertyNode(property, value)
    data class Lte(override val property: String, override val value: Any?) : PropertyNode(property, value)
}
