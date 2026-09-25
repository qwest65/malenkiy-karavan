package ru.cultureguide.model

data class City(
    val id: Long,
    val name: String,
    val country: String,
    val lat: Double,
    val lon: Double
)

data class Place(
    val id: Long,
    val name: String,
    val category: String,
    val description: String,
    val address: String,
    val lat: Double,
    val lon: Double,
    val sourceUrl: String = "",
    val imageUrl: String = ""
)

/** Тематический маршрут: упорядоченный список объектов города. */
data class RouteLine(
    val id: Long,
    val name: String,
    val description: String,
    val placeIds: List<Long>
)
