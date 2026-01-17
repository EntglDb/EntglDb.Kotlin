package com.entgldb.sample.models

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val name: String? = null,
    val age: Int = 0,
    val address: Address? = null
)

@Serializable
data class Address(
    val city: String? = null
)
