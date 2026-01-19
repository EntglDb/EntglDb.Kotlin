---
layout: default
title: Querying
---

# Querying

EntglDb Kotlin provides a Type-Safe DSL for querying local collections, allowing you to write queries that feel like native Kotlin code.

## Basic Querying

You can query documents using the `find` method on a `PeerCollection` with the DSL lambda.

```kotlin
val users = peerStore.getCollection("users")

// Precise match
val fabio = users.find { "firstName" eq "Fabio" }

// Comparisons
val adults = users.find { "age" gte 18 }

// Logical Operators
val activeAdmins = users.find {
    "isActive" eq true
    and { "role" eq "Admin" }
}
```

## Serialization Consistency

EntglDb Kotlin handles property name mapping automatically locally. It follows the convention that property names used in the DSL correspond to the keys in the stored JSON documents.

If your persistence layer or serializer converts `firstName` to `first_name`, you should ensure your queries match the persisted format OR rely on the internal `SqlTranslator` which attempts to map `camelCase` queries to `snake_case` database fields automatically.

```kotlin
// Query "firstName" -> DB Checks json_extract(data, '$.first_name')
val result = users.find { "firstName" eq "Fabio" }
```

## Supported Operators

- `eq` (Equal)
- `ne` (Not Equal)
- `gt` (Greater Than)
- `lt` (Less Than)
- `gte` (Greater Than or Equal)
- `lte` (Less Than or Equal)
- `and { ... }` (AND)
- `or { ... }` (OR)
