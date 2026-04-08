package com.evac.app.util

import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object OsrmRouter {

    data class RouteResult(
        val distanceMeters: Double,
        val durationSeconds: Double,
        val points: List<GeoPoint>
    )

    suspend fun getRoute(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double): RouteResult? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // OSRM coordinates are in format: lon,lat
                val urlString = "https://router.project-osrm.org/route/v1/driving/$fromLng,$fromLat;$toLng,$toLat?overview=full&geometries=geojson"
                val url = URL(urlString)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext null
                }

                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = reader.readText()
                reader.close()

                val json = JSONObject(response)
                val routes = json.optJSONArray("routes")
                if (routes == null || routes.length() == 0) return@withContext null

                val route = routes.getJSONObject(0)
                val distance = route.optDouble("distance", 0.0)
                val duration = route.optDouble("duration", 0.0)

                val geometry = route.optJSONObject("geometry")
                val coordinates = geometry?.optJSONArray("coordinates")
                val points = mutableListOf<GeoPoint>()

                if (coordinates != null) {
                    for (i in 0 until coordinates.length()) {
                        val pt = coordinates.getJSONArray(i)
                        val lon = pt.getDouble(0)
                        val lat = pt.getDouble(1)
                        points.add(GeoPoint(lat, lon))
                    }
                }

                RouteResult(distance, duration, points)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
