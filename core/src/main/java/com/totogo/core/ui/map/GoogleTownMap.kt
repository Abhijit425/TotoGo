package com.totogo.core.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.totogo.core.model.GeoPoint
import com.totogo.core.ui.theme.LocalPalette

private fun GeoPoint.ll() = LatLng(lat, lng)

/**
 * Google Maps renderer for the same [MapScene]. Only used when MAPS_API_KEY is set in
 * local.properties (see README → Production configuration). Without a key, [TownMap] is used.
 */
@Composable
fun GoogleTownMap(scene: MapScene, modifier: Modifier = Modifier) {
    val pal = LocalPalette.current
    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(scene.focus.ll(), 14.5f) }
    LaunchedEffect(scene.focus) { camera.position = CameraPosition.fromLatLngZoom(scene.focus.ll(), camera.position.zoom) }
    GoogleMap(
        modifier = modifier,
        cameraPositionState = camera,
        properties = MapProperties(isMyLocationEnabled = false),
        uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false),
    ) {
        scene.user?.let { Marker(state = MarkerState(position = it.ll()), title = "You", icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)) }
        scene.pickup?.let { Marker(state = MarkerState(position = it.ll()), title = "Pickup", icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)) }
        scene.destination?.let { Marker(state = MarkerState(position = it.ll()), title = "Destination", icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)) }
        scene.trackedDriver?.let { Marker(state = MarkerState(position = it.ll()), title = "Your Toto", rotation = scene.trackedHeading, flat = true) }
        scene.drivers.forEach { d ->
            Marker(state = MarkerState(position = d.point.ll()), title = if (d.isDemo) "Toto (DEMO)" else "Toto", alpha = if (d.busy) 0.5f else 1f, icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW))
        }
        if (scene.route.size >= 2) Polyline(points = scene.route.map { it.ll() }, color = pal.primary, width = 14f)
        scene.radiusKm?.let { km ->
            Circle(center = (scene.pickup ?: scene.focus).ll(), radius = km * 1000, strokeColor = pal.primary, fillColor = pal.primary.copy(alpha = 0.08f), strokeWidth = 3f)
        }
    }
}

/** Picks Google Maps when a key is configured, otherwise the key-free town map. */
@Composable
fun SmartMap(scene: MapScene, useGoogle: Boolean, modifier: Modifier = Modifier, bengali: Boolean = false, onLandmarkTap: ((com.totogo.core.model.Place) -> Unit)? = null) {
    if (useGoogle) GoogleTownMap(scene, modifier) else TownMap(scene, modifier, bengali, onLandmarkTap)
}

@Suppress("unused") private val unusedColor = Color.Unspecified
