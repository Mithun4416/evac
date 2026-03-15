package com.evac.app.util

import com.evac.app.model.DisasterType
import com.evac.app.model.SafeSpot
import com.evac.app.model.SpotType

object SafeSpotProvider {
    fun getBangaloreSpots(): List<SafeSpot> {
        val spots = mutableListOf<SafeSpot>()

        // Fake data for Bangalore
        // Schools (Cyclone/General)
        spots.add(SafeSpot("St. Joseph's Boys High School", SpotType.SCHOOL, 12.9698, 77.5996, 500, listOf(DisasterType.CYCLONE, DisasterType.GENERAL)))
        spots.add(SafeSpot("Bishop Cotton Boys' School", SpotType.SCHOOL, 12.9691, 77.6010, 800, listOf(DisasterType.CYCLONE, DisasterType.GENERAL, DisasterType.FLOOD)))
        spots.add(SafeSpot("National Public School, Indiranagar", SpotType.SCHOOL, 12.9710, 77.6360, 600, listOf(DisasterType.CYCLONE, DisasterType.GENERAL)))

        // Hospitals (Medical/General)
        spots.add(SafeSpot("Bowring and Lady Curzon Hospital", SpotType.HOSPITAL, 12.9816, 77.6046, 1200, listOf(DisasterType.GENERAL, DisasterType.FIRE, DisasterType.EARTHQUAKE, DisasterType.CYCLONE, DisasterType.FLOOD)))
        spots.add(SafeSpot("Victoria Hospital", SpotType.HOSPITAL, 12.9642, 77.5750, 1500, listOf(DisasterType.GENERAL, DisasterType.FIRE, DisasterType.EARTHQUAKE, DisasterType.CYCLONE, DisasterType.FLOOD)))

        // High Ground (Flood)
        spots.add(SafeSpot("Nandi Hills View Point", SpotType.HIGH_GROUND, 13.3702, 77.6836, 1000, listOf(DisasterType.FLOOD)))
        spots.add(SafeSpot("Banashankari Hilltop Park", SpotType.HIGH_GROUND, 12.9254, 77.5463, 300, listOf(DisasterType.FLOOD)))
        spots.add(SafeSpot("Mount Carmel College Hill", SpotType.HIGH_GROUND, 12.9906, 77.5846, 400, listOf(DisasterType.FLOOD)))

        // Stadiums/Open Parks (Earthquake, Fire)
        spots.add(SafeSpot("M. Chinnaswamy Stadium", SpotType.STADIUM, 12.9788, 77.5996, 10000, listOf(DisasterType.EARTHQUAKE, DisasterType.FIRE, DisasterType.GENERAL)))
        spots.add(SafeSpot("Sree Kanteerava Stadium", SpotType.STADIUM, 12.9695, 77.5936, 8000, listOf(DisasterType.EARTHQUAKE, DisasterType.FIRE, DisasterType.GENERAL)))
        spots.add(SafeSpot("Cubbon Park Open Ground", SpotType.STADIUM, 12.9779, 77.5952, 5000, listOf(DisasterType.EARTHQUAKE, DisasterType.FIRE)))

        // Police Stations (General, Fire)
        spots.add(SafeSpot("Cubbon Park Police Station", SpotType.POLICE, 12.9760, 77.5955, 100, listOf(DisasterType.GENERAL, DisasterType.FIRE)))
        spots.add(SafeSpot("Indiranagar Police Station", SpotType.POLICE, 12.9761, 77.6391, 150, listOf(DisasterType.GENERAL, DisasterType.FIRE)))

        return spots
    }
}
