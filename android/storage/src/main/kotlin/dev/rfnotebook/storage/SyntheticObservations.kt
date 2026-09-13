package dev.rfnotebook.storage

import android.content.Context
import dev.rfnotebook.domain.Observation

object SyntheticObservations {
    val route = listOf(
        Observation(40.71280, -74.00600, 6f, -63f),
        Observation(40.71295, -74.00570, 9f, -51f),
        Observation(40.71315, -74.00535, 14f, -43f),
        Observation(40.71335, -74.00505, 22f, -58f),
    )

    fun loadPersisted(context: Context): List<Observation> {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val encoded = preferences.getString(KEY, null) ?: encode(route).also { preferences.edit().putString(KEY, it).apply() }
        return decode(encoded)
    }

    internal fun encode(observations: List<Observation>): String = observations.joinToString(";") {
        "${it.latitude},${it.longitude},${it.horizontalAccuracyM},${it.relativePowerDbfs}"
    }

    internal fun decode(encoded: String): List<Observation> = encoded.split(';').filter { it.isNotBlank() }.map { row ->
        val fields = row.split(',')
        require(fields.size == 4) { "Malformed synthetic observation" }
        Observation(fields[0].toDouble(), fields[1].toDouble(), fields[2].toFloat(), fields[3].toFloat())
    }

    private const val PREFERENCES = "m0-synthetic-observations"
    private const val KEY = "route-v1"
}
