package com.example.domain.model

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Dynamic weather conditions that transform Nooki's sky background and 3D desk-pet island environment.
 */
enum class WeatherCondition(
    val id: String,
    val displayName: String,
    val badgeEmoji: String,
    val skyTopHex: Long,
    val skyHorizonHex: Long,
    val islandSurfaceTopHex: Long,
    val islandRimGlowHex: Long,
    val weatherParticleHex: Long,
    val environmentDescription: String,
    val petReactionLine: String,
    val moodHappinessBonus: Int
) {
    SUNLIT_BREEZE(
        id = "SUNLIT_BREEZE",
        displayName = "Sunlit Breeze",
        badgeEmoji = "☀️",
        skyTopHex = 0xFF1A3A5CL,
        skyHorizonHex = 0xFF2E5B88L,
        islandSurfaceTopHex = 0xFF5D4037L,
        islandRimGlowHex = 0xFFFBBF24L,
        weatherParticleHex = 0xFFFDE68AL,
        environmentDescription = "Warm sunbeams & floating pollen motes on Nooki's mossy desk island",
        petReactionLine = "Warm sunbeams are tickling my ears on the desk island!",
        moodHappinessBonus = 5
    ),

    MONSOON_RAIN(
        id = "MONSOON_RAIN",
        displayName = "Cozy Monsoon Rain",
        badgeEmoji = "🌧️",
        skyTopHex = 0xFF0F1E36L,
        skyHorizonHex = 0xFF1E3A5FL,
        islandSurfaceTopHex = 0xFF3E3B39L,
        islandRimGlowHex = 0xFF38BDF8L,
        weatherParticleHex = 0xFF7DD3FCL,
        environmentDescription = "Falling raindrops, water ripples on the 3D desk island & leaf umbrella shelter",
        petReactionLine = "Listen to the cozy rain pattering on my leaf umbrella!",
        moodHappinessBonus = 6
    ),

    GOLDEN_TWILIGHT(
        id = "GOLDEN_TWILIGHT",
        displayName = "Golden Hour Dusk",
        badgeEmoji = "🌅",
        skyTopHex = 0xFF2A163BL,
        skyHorizonHex = 0xFF6B2D46L,
        islandSurfaceTopHex = 0xFF5A352CL,
        islandRimGlowHex = 0xFFFB923CL,
        weatherParticleHex = 0xFFFDBA74L,
        environmentDescription = "Amber sunset rim-light & early dusk fireflies rising around Nooki's paw",
        petReactionLine = "Golden hour light makes every knot on our thread glow!",
        moodHappinessBonus = 5
    ),

    STARRY_NIGHT_DEW(
        id = "STARRY_NIGHT_DEW",
        displayName = "Starry Night Dew",
        badgeEmoji = "🌌",
        skyTopHex = 0xFF090D24L,
        skyHorizonHex = 0xFF161E4AL,
        islandSurfaceTopHex = 0xFF3B2838L,
        islandRimGlowHex = 0xFFA5B4FCL,
        weatherParticleHex = 0xFFE0E7FFL,
        environmentDescription = "Twinkling constellations, crescent moonbeam & dewy night glow on the desk island",
        petReactionLine = "The stars are out! My bell is glowing softly in the night dew.",
        moodHappinessBonus = 4
    ),

    MISTY_MOUNTAIN_SNOW(
        id = "MISTY_MOUNTAIN_SNOW",
        displayName = "Misty Alpine Snow",
        badgeEmoji = "❄️",
        skyTopHex = 0xFF162938L,
        skyHorizonHex = 0xFF294861L,
        islandSurfaceTopHex = 0xFF4B5563L,
        islandRimGlowHex = 0xFFBAE6FDL,
        weatherParticleHex = 0xFFF0F9FFL,
        environmentDescription = "Drifting 3D snowflakes & frosted snow-cap rim on Nooki's desk island",
        petReactionLine = "Fluffy snowflakes are landing right on my nose!",
        moodHappinessBonus = 6
    );

    companion object {
        fun fromId(id: String?): WeatherCondition {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: SUNLIT_BREEZE
        }
    }
}

/**
 * Snapshot of the local weather environment derived from the device's Location API
 * (or user preview override) and applied to the background sky and 3D desk-pet stage.
 */
data class LocalWeatherState(
    val condition: WeatherCondition = WeatherCondition.SUNLIT_BREEZE,
    val temperatureCelsius: Int = 26,
    val humidityPercent: Int = 64,
    val windSpeedKmh: Int = 11,
    val latitude: Double = 19.0760,
    val longitude: Double = 72.8777,
    val altitudeMeters: Double = 14.0,
    val localityName: String = "Mumbai Coast",
    val isFromLiveLocationApi: Boolean = false,
    val hasLocationPermission: Boolean = false,
    val isManualOverride: Boolean = false,
    val lastUpdatedMillis: Long = System.currentTimeMillis()
) {
    val formattedCoordinates: String
        get() {
            val latDir = if (latitude >= 0) "N" else "S"
            val lonDir = if (longitude >= 0) "E" else "W"
            val latAbs = ((abs(latitude) * 100.0).roundToInt() / 100.0)
            val lonAbs = ((abs(longitude) * 100.0).roundToInt() / 100.0)
            return "$latAbs°$latDir, $lonAbs°$lonDir"
        }

    val sourceBadgeText: String
        get() = when {
            isManualOverride -> "Preview • ${condition.displayName}"
            isFromLiveLocationApi -> "Live GPS • $localityName ($formattedCoordinates)"
            else -> "Local Solar • $localityName"
        }
}

/**
 * Pure Kotlin calculator that derives realistic local weather, temperature, humidity,
 * and 3D desk-pet environment state from GPS coordinates (`latitude`, `longitude`, `altitudeMeters`)
 * and local time, operating 100% on-device without requiring network requests.
 */
object LocalWeatherCalculator {

    private data class KnownRegion(
        val name: String,
        val lat: Double,
        val lon: Double,
        val baseTempC: Int,
        val baseHumidity: Int
    )

    private val referenceRegions = listOf(
        KnownRegion("Mumbai", 19.0760, 72.8777, 29, 78),
        KnownRegion("Bhubaneswar", 20.2961, 85.8245, 29, 76),
        KnownRegion("Bengaluru", 12.9716, 77.5946, 24, 65),
        KnownRegion("New Delhi", 28.6139, 77.2090, 27, 54),
        KnownRegion("Kolkata", 22.5726, 88.3639, 28, 75),
        KnownRegion("Chennai", 13.0827, 80.2707, 30, 78),
        KnownRegion("Hyderabad", 17.3850, 78.4867, 27, 58),
        KnownRegion("Pune", 18.5204, 73.8567, 25, 62),
        KnownRegion("Shimla Hills", 31.1048, 77.1734, 11, 68),
        KnownRegion("Mountain View", 37.3861, -122.0839, 19, 62),
        KnownRegion("New York", 40.7128, -74.0060, 17, 60),
        KnownRegion("London", 51.5072, -0.1276, 15, 74),
        KnownRegion("Tokyo", 35.6762, 139.6503, 20, 66)
    )

    fun resolveNearestLocality(latitude: Double, longitude: Double): String {
        val closest = referenceRegions.minByOrNull { region ->
            val dLat = region.lat - latitude
            val dLon = region.lon - longitude
            dLat * dLat + dLon * dLon
        }
        if (closest != null) {
            val distSq = (closest.lat - latitude) * (closest.lat - latitude) +
                (closest.lon - longitude) * (closest.lon - longitude)
            if (distSq <= 9.0) {
                return closest.name
            }
        }
        return when {
            abs(latitude) > 55.0 -> "Highland Sanctuary"
            abs(latitude) < 23.5 -> "Tropical Sanctuary"
            else -> "Temperate Sanctuary"
        }
    }

    fun computeFromLocation(
        latitude: Double,
        longitude: Double,
        altitudeMeters: Double = 15.0,
        nowMillis: Long = System.currentTimeMillis(),
        localityOverride: String? = null,
        isFromLiveLocationApi: Boolean = true,
        hasLocationPermission: Boolean = true,
        ambientTempSensorC: Float? = null,
        relativeHumiditySensorPct: Float? = null
    ): LocalWeatherState {
        val calendar = Calendar.getInstance(TimeZone.getDefault()).apply {
            timeInMillis = nowMillis
        }
        val hourOfDay = calendar.get(Calendar.HOUR_OF_DAY)
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)

        val locality = localityOverride?.takeIf { it.isNotBlank() }
            ?: resolveNearestLocality(latitude, longitude)

        // Diurnal solar curve (-1 at 3 AM, +1 at 3 PM)
        val diurnalWave = sin(((hourOfDay - 9) / 24.0) * 2.0 * Math.PI)
        // Latitude baseline temperature (warmer near equator, cooler near poles & high altitude)
        val latBaseTemp = 30.0 - (abs(latitude) * 0.42) - (altitudeMeters.coerceAtLeast(0.0) * 0.0065)
        val computedTemp = ambientTempSensorC?.roundToInt()
            ?: (latBaseTemp + diurnalWave * 4.5).roundToInt().coerceIn(-5, 42)

        // Micro-climate & coastal humidity factor from coordinates + day cycle
        val coordWave = cos(latitude * 0.15) * sin(longitude * 0.15)
        val computedHumidity = relativeHumiditySensorPct?.roundToInt()?.coerceIn(20, 99)
            ?: (64 + (coordWave * 18.0) - (diurnalWave * 8.0)).roundToInt().coerceIn(32, 96)

        val windSpeed = (8 + abs(sin(longitude * 0.2 + dayOfYear * 0.1) * 14.0)).roundToInt()

        val condition = when {
            // High-altitude or sub-arctic cold -> Misty Alpine Snow
            computedTemp <= 9 || altitudeMeters >= 1800.0 || abs(latitude) >= 58.0 ->
                WeatherCondition.MISTY_MOUNTAIN_SNOW

            // High humidity tropical/coastal monsoon window -> Cozy Monsoon Rain
            computedHumidity >= 76 && hourOfDay in 13..20 ->
                WeatherCondition.MONSOON_RAIN

            // Night hours (8 PM - 5 AM) -> Starry Night Dew
            hourOfDay >= 20 || hourOfDay <= 5 ->
                WeatherCondition.STARRY_NIGHT_DEW

            // Dawn (6 AM - 7 AM) or Dusk (5 PM - 7 PM) -> Golden Hour Dusk
            hourOfDay in 6..7 || hourOfDay in 17..19 ->
                WeatherCondition.GOLDEN_TWILIGHT

            // Daytime clear/breeze -> Sunlit Breeze (or Monsoon Rain if humidity is very high)
            computedHumidity >= 82 ->
                WeatherCondition.MONSOON_RAIN

            else ->
                WeatherCondition.SUNLIT_BREEZE
        }

        return LocalWeatherState(
            condition = condition,
            temperatureCelsius = computedTemp,
            humidityPercent = computedHumidity,
            windSpeedKmh = windSpeed,
            latitude = latitude,
            longitude = longitude,
            altitudeMeters = altitudeMeters,
            localityName = locality,
            isFromLiveLocationApi = isFromLiveLocationApi,
            hasLocationPermission = hasLocationPermission,
            isManualOverride = false,
            lastUpdatedMillis = nowMillis
        )
    }
}
