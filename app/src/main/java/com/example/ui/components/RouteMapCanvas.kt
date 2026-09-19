package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color as AndroidColor
import android.location.LocationManager
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.domain.model.TrackPointModel
import com.example.ui.theme.*

private const val TAG = "RouteMapCanvas"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RouteMapCanvas(
    trackPoints: List<TrackPointModel>,
    currentLat: Double,
    currentLon: Double,
    currentHeading: Float,
    isLive: Boolean = true,
    modifier: Modifier = Modifier,
    showProfileOverlay: Boolean = true,
    cursorPosition: Pair<Double, Double>? = null,
    centerOnCursorTrigger: Int = 0,
    onMapCoordinateSelected: ((Double, Double) -> Unit)? = null,
    onMapPointSelected: ((Int) -> Unit)? = null
) {
    val context = LocalContext.current

    // 1. Strictly validate coordinates (finite, non-zero, valid range)
    val validPoints = remember(trackPoints) {
        trackPoints.filter {
            it.latitude.isFinite() && it.longitude.isFinite() &&
            (it.latitude != 0.0 || it.longitude != 0.0) &&
            it.latitude in -85.0..85.0 && it.longitude in -180.0..180.0
        }
    }

    // Determine initial center
    val initialCenter = remember {
        val firstValid = trackPoints.firstOrNull {
            it.latitude.isFinite() && it.longitude.isFinite() &&
            (it.latitude != 0.0 || it.longitude != 0.0) &&
            it.latitude in -85.0..85.0 && it.longitude in -180.0..180.0
        }
        if (currentLat.isFinite() && currentLon.isFinite() && (currentLat != 0.0 || currentLon != 0.0)) {
            Pair(currentLat, currentLon)
        } else if (firstValid != null) {
            Pair(firstValid.latitude, firstValid.longitude)
        } else {
            try {
                val locManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                val gpsLoc = try { locManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER) } catch (e: Exception) { null }
                val netLoc = try { locManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } catch (e: Exception) { null }
                val best = gpsLoc ?: netLoc
                if (best != null && best.latitude != 0.0 && best.latitude.isFinite()) {
                    Pair(best.latitude, best.longitude)
                } else {
                    Pair(40.4168, -3.7038) // Madrid default
                }
            } catch (e: Exception) {
                Pair(40.4168, -3.7038)
            }
        }
    }

    // Read bundled Leaflet CSS & JS from assets (guaranteed offline, synchronous, no CDN latency or failure)
    val leafletCss = remember {
        try {
            context.assets.open("leaflet.css").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading leaflet.css from assets", e)
            ""
        }
    }

    val leafletJs = remember {
        try {
            context.assets.open("leaflet.js").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading leaflet.js from assets", e)
            ""
        }
    }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isMapReady by remember { mutableStateOf(false) }
    var followUser by remember { mutableStateOf(isLive) }

    // Build the self-contained HTML page embedding Leaflet & OpenStreetMap tiles
    val htmlContent = remember(initialCenter, isLive) {
        buildLeafletHtml(
            leafletCss = leafletCss,
            leafletJs = leafletJs,
            initLat = initialCenter.first,
            initLon = initialCenter.second,
            isLive = isLive
        )
    }

    // Convert valid points to compact JSON
    val pointsJson = remember(validPoints) {
        validPoints.joinToString(prefix = "[", postfix = "]") { pt ->
            "{\"lat\":${pt.latitude},\"lon\":${pt.longitude}}"
        }
    }

    // Push route updates to Leaflet when map is ready
    LaunchedEffect(pointsJson, isMapReady) {
        if (isMapReady) {
            webViewRef?.evaluateJavascript("if (window.setPoints) { window.setPoints($pointsJson); }", null)
        }
    }

    // Push live rider location updates to Leaflet
    LaunchedEffect(currentLat, currentLon, currentHeading, isMapReady) {
        if (isMapReady && currentLat.isFinite() && currentLon.isFinite() && (currentLat != 0.0 || currentLon != 0.0)) {
            webViewRef?.evaluateJavascript("if (window.updateRider) { window.updateRider($currentLat, $currentLon, $currentHeading); }", null)
        }
    }

    // Follow user sync
    LaunchedEffect(followUser, isMapReady) {
        if (isMapReady) {
            webViewRef?.evaluateJavascript("if (window.setFollowUser) { window.setFollowUser($followUser); }", null)
        }
    }

    // Graph -> Map cursor synchronization
    LaunchedEffect(cursorPosition, isMapReady) {
        if (isMapReady) {
            if (cursorPosition != null && cursorPosition.first.isFinite() && cursorPosition.second.isFinite()) {
                webViewRef?.evaluateJavascript("if (window.setCursorMarker) { window.setCursorMarker(${cursorPosition.first}, ${cursorPosition.second}); }", null)
            } else {
                webViewRef?.evaluateJavascript("if (window.clearCursorMarker) { window.clearCursorMarker(); }", null)
            }
        }
    }

    LaunchedEffect(centerOnCursorTrigger, isMapReady) {
        if (centerOnCursorTrigger > 0 && isMapReady) {
            webViewRef?.evaluateJavascript("if (window.centerOnCursor) { window.centerOnCursor(); }", null)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(VeloDarkBg)
            .border(1.dp, VeloDarkCardBorder, RoundedCornerShape(12.dp))
    ) {
        // Android WebView hosting Leaflet + OpenStreetMap
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(AndroidColor.parseColor("#0A0D12"))

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        databaseEnabled = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        cacheMode = WebSettings.LOAD_DEFAULT
                        // OpenStreetMap policy compliant User-Agent
                        userAgentString = "VelosenseApp/1.0 (Android; contact: dexel.studioss@gmail.com)"
                        setSupportZoom(false)
                        builtInZoomControls = false
                        displayZoomControls = false
                    }

                    // JavaScript interface for 2-way event bridge
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onMapLoaded() {
                            isMapReady = true
                        }

                        @JavascriptInterface
                        fun onUserPanned() {
                            followUser = false
                        }

                        @JavascriptInterface
                        fun onMapCoordinateClicked(lat: Double, lon: Double) {
                            if (onMapCoordinateSelected != null) {
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    onMapCoordinateSelected.invoke(lat, lon)
                                }
                            }
                        }

                        @JavascriptInterface
                        fun onMapPointSelected(index: Int) {
                            if (onMapPointSelected != null) {
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    onMapPointSelected.invoke(index)
                                }
                            }
                        }
                    }, "AndroidBridge")

                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                            Log.d("LeafletOSM", "${consoleMessage?.message()} [line ${consoleMessage?.lineNumber()}]")
                            return true
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            view?.evaluateJavascript("if (window.ensureMap) { window.ensureMap(); }", null)
                        }
                    }

                    // Load with https origin to avoid mixed-content and cross-origin restrictions
                    loadDataWithBaseURL("https://velosense.app/", htmlContent, "text/html", "UTF-8", null)
                    webViewRef = this
                }
            },
            update = { webView ->
                // Ensure map container invalidates if size changes
                webView.evaluateJavascript("if (window.map) { window.map.invalidateSize(); }", null)
            },
            onRelease = { webView ->
                webView.stopLoading()
                webView.removeJavascriptInterface("AndroidBridge")
                webView.webChromeClient = null
                webView.webViewClient = WebViewClient()
                webView.destroy()
                webViewRef = null
            }
        )

        // GPS Satellite search banner if live and waiting for first coordinates
        val hasCoordinates = (currentLat.isFinite() && currentLon.isFinite() && (currentLat != 0.0 || currentLon != 0.0)) || validPoints.isNotEmpty()
        if (isLive && !hasCoordinates) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
                shape = RoundedCornerShape(10.dp),
                color = VeloDarkSurface.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, VeloDarkCardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = ElectricLime
                    )
                    Text(
                        text = "Buscando señal GPS...",
                        style = VeloTypography.bodySmall,
                        color = VeloTextSecondary
                    )
                }
            }
        }

        // Empty state overlay for saved rides with no valid GPS points
        if (!isLive && validPoints.isEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
                shape = RoundedCornerShape(10.dp),
                color = VeloDarkSurface.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, VeloDarkCardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOff,
                        contentDescription = null,
                        tint = VeloTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Sin datos de ruta GPS",
                        style = VeloTypography.bodySmall,
                        color = VeloTextSecondary
                    )
                }
            }
        }

        // Mini elevation profile overlay along bottom edge (for saved rides or long routes)
        val validAltPoints = remember(validPoints) {
            validPoints.filter { it.altitude.isFinite() }
        }
        if (showProfileOverlay && validAltPoints.size >= 5) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .align(Alignment.BottomCenter)
            ) {
                drawMiniElevationStrip(validAltPoints, size.width, size.height)
            }
        }

        // Floating Map Controls (Top Right: Recenter, Zoom In, Zoom Out)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Recenter Button
            FilledIconButton(
                onClick = {
                    followUser = true
                    webViewRef?.evaluateJavascript(
                        "if (window.recenterMap) { window.recenterMap(); } else if (window.fitBoundsSafe) { window.fitBoundsSafe(); }",
                        null
                    )
                },
                modifier = Modifier.size(34.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (followUser && isLive) ElectricLime else VeloDarkCard.copy(alpha = 0.92f),
                    contentColor = if (followUser && isLive) VeloDarkBg else VeloTextPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Centrar mapa",
                    modifier = Modifier.size(18.dp)
                )
            }

            // Quick Action: Center on selected cursor point (if active)
            if (cursorPosition != null) {
                FilledIconButton(
                    onClick = {
                        webViewRef?.evaluateJavascript("if (window.centerOnCursor) { window.centerOnCursor(); }", null)
                    },
                    modifier = Modifier.size(34.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color(0xFF00E5FF).copy(alpha = 0.92f),
                        contentColor = VeloDarkBg
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Centrar en selección",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Zoom In (+)
            FilledIconButton(
                onClick = {
                    webViewRef?.evaluateJavascript("if (window.map) { window.map.zoomIn(); }", null)
                },
                modifier = Modifier.size(34.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = VeloDarkCard.copy(alpha = 0.92f),
                    contentColor = VeloTextPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Acercar",
                    modifier = Modifier.size(18.dp)
                )
            }

            // Zoom Out (-)
            FilledIconButton(
                onClick = {
                    webViewRef?.evaluateJavascript("if (window.map) { window.map.zoomOut(); }", null)
                },
                modifier = Modifier.size(34.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = VeloDarkCard.copy(alpha = 0.92f),
                    contentColor = VeloTextPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Alejar",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// Builds the complete self-contained HTML running Leaflet with OpenStreetMap tiles
private fun buildLeafletHtml(
    leafletCss: String,
    leafletJs: String,
    initLat: Double,
    initLon: Double,
    isLive: Boolean
): String {
    return """
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
    <style>
        $leafletCss
        html, body {
            width: 100%;
            height: 100%;
            margin: 0;
            padding: 0;
            background: #0A0D12;
            overflow: hidden;
            -webkit-user-select: none;
            user-select: none;
        }
        #map {
            width: 100%;
            height: 100%;
            background: #0A0D12;
        }
        /* OpenStreetMap Tiles with subtle dark theme filter to match Velosense aesthetic */
        .leaflet-tile-pane {
            filter: brightness(0.7) invert(1) contrast(3) hue-rotate(200deg) saturate(0.3) brightness(0.78);
        }
        .leaflet-control-attribution {
            background: rgba(10, 13, 18, 0.85) !important;
            color: #8E9BAE !important;
            font-size: 8px !important;
            padding: 2px 5px !important;
            border-top-left-radius: 4px;
        }
        .leaflet-control-attribution a {
            color: #C8FF00 !important;
            text-decoration: none;
        }
        @keyframes veloPulse {
            0% { transform: scale(0.8); opacity: 0.8; }
            100% { transform: scale(2.5); opacity: 0; }
        }
        @keyframes veloCursorPulse {
            0% { transform: scale(0.9); opacity: 0.85; }
            50% { transform: scale(1.6); opacity: 0.3; }
            100% { transform: scale(0.9); opacity: 0.85; }
        }
    </style>
</head>
<body>
    <div id="map"></div>
    <script>
        $leafletJs
    </script>
    <script>
        var map = null;
        var routePolyline = null;
        var startMarker = null;
        var endMarker = null;
        var riderMarker = null;
        var cursorMarker = null;
        var isLive = ${if (isLive) "true" else "false"};
        var followUser = ${if (isLive) "true" else "false"};
        var hasAutoFitted = false;
        var pendingPoints = [];
        var lastRiderLat = $initLat;
        var lastRiderLon = $initLon;

        function ensureMap() {
            var el = document.getElementById('map');
            if (!el || el.clientWidth === 0 || el.clientHeight === 0) {
                return false;
            }
            if (map) {
                map.invalidateSize();
                return true;
            }
            try {
                map = L.map('map', {
                    zoomControl: false,
                    attributionControl: true
                }).setView([$initLat, $initLon], 15);

                // OpenStreetMap Official Tile Layer with Attribution
                L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
                    maxZoom: 19,
                    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
                    crossOrigin: true
                }).addTo(map);

                map.on('dragstart', function() {
                    followUser = false;
                    if (window.AndroidBridge && window.AndroidBridge.onUserPanned) {
                        window.AndroidBridge.onUserPanned();
                    }
                });

                map.on('click', function(e) {
                    if (e && e.latlng) {
                        handleMapClick(e.latlng.lat, e.latlng.lng);
                    }
                });

                if (window.AndroidBridge && window.AndroidBridge.onMapLoaded) {
                    window.AndroidBridge.onMapLoaded();
                }

                if (pendingPoints.length > 0) {
                    setPoints(pendingPoints);
                }

                setTimeout(function() {
                    if (map) map.invalidateSize();
                }, 150);

                return true;
            } catch (err) {
                console.error("Leaflet init error:", err);
                return false;
            }
        }

        // ResizeObserver to detect when map container has true non-zero layout
        if (window.ResizeObserver) {
            var ro = new ResizeObserver(function(entries) {
                for (var i = 0; i < entries.length; i++) {
                    if (entries[i].contentRect.width > 0 && entries[i].contentRect.height > 0) {
                        if (!map) {
                            ensureMap();
                        } else {
                            map.invalidateSize();
                        }
                    }
                }
            });
            ro.observe(document.getElementById('map'));
        }

        // Robust timeouts to ensure map initialization
        setTimeout(ensureMap, 50);
        setTimeout(ensureMap, 200);
        setTimeout(ensureMap, 600);
        setTimeout(ensureMap, 1200);

        function setPoints(pts) {
            if (!pts || !Array.isArray(pts)) return;
            pendingPoints = pts;
            if (!map) {
                ensureMap();
                if (!map) return;
            }

            var validCoords = [];
            for (var i = 0; i < pts.length; i++) {
                var p = pts[i];
                if (p && typeof p.lat === 'number' && !isNaN(p.lat) && typeof p.lon === 'number' && !isNaN(p.lon)) {
                    if (p.lat >= -85 && p.lat <= 85 && p.lon >= -180 && p.lon <= 180) {
                        validCoords.push([p.lat, p.lon]);
                    }
                }
            }

            // Draw GPS Route Polyline
            if (validCoords.length >= 2) {
                if (routePolyline) {
                    routePolyline.setLatLngs(validCoords);
                } else {
                    routePolyline = L.polyline(validCoords, {
                        color: '#C8FF00',
                        weight: 4.5,
                        opacity: 0.95,
                        lineJoin: 'round',
                        lineCap: 'round'
                    }).addTo(map);
                    routePolyline.on('click', function(e) {
                        if (e && e.latlng) {
                            handleMapClick(e.latlng.lat, e.latlng.lng);
                        }
                    });
                }
            } else if (routePolyline) {
                map.removeLayer(routePolyline);
                routePolyline = null;
            }

            // Start Pin Marker
            if (validCoords.length >= 1) {
                var startPt = validCoords[0];
                var startHtml = '<div style="width:13px;height:13px;background:#C8FF00;border:2.5px solid #FFFFFF;border-radius:50%;box-shadow:0 0 6px rgba(0,0,0,0.85);"></div>';
                var startIcon = L.divIcon({
                    className: 'velo-pin-start',
                    html: startHtml,
                    iconSize: [13, 13],
                    iconAnchor: [6.5, 6.5]
                });
                if (startMarker) {
                    startMarker.setLatLng(startPt);
                } else {
                    startMarker = L.marker(startPt, { icon: startIcon, interactive: false }).addTo(map);
                }
            }

            // End Pin Marker (for completed / saved ride)
            if (!isLive && validCoords.length >= 2) {
                var endPt = validCoords[validCoords.length - 1];
                var endHtml = '<div style="width:13px;height:13px;background:#EF4444;border:2.5px solid #FFFFFF;border-radius:50%;box-shadow:0 0 6px rgba(0,0,0,0.85);"></div>';
                var endIcon = L.divIcon({
                    className: 'velo-pin-end',
                    html: endHtml,
                    iconSize: [13, 13],
                    iconAnchor: [6.5, 6.5]
                });
                if (endMarker) {
                    endMarker.setLatLng(endPt);
                } else {
                    endMarker = L.marker(endPt, { icon: endIcon, interactive: false }).addTo(map);
                }
            }

            // Auto fit bounds for saved ride
            if (!hasAutoFitted) {
                fitBoundsSafe();
                if (validCoords.length > 0) {
                    hasAutoFitted = true;
                }
            }
        }

        function updateRider(lat, lon, heading) {
            if (!lat || !lon || isNaN(lat) || isNaN(lon)) return;
            if (lat < -85 || lat > 85 || lon < -180 || lon > 180) return;
            lastRiderLat = lat;
            lastRiderLon = lon;

            if (!map) {
                ensureMap();
                if (!map) return;
            }

            var riderPt = [lat, lon];
            var riderHtml = '<div style="position:relative;width:24px;height:24px;transform:translate(-12px,-12px);">' +
                '<div style="position:absolute;top:0;left:0;width:24px;height:24px;background:rgba(200,255,0,0.3);border-radius:50%;animation:veloPulse 1.8s infinite ease-out;"></div>' +
                '<div style="position:absolute;top:5px;left:5px;width:14px;height:14px;background:#C8FF00;border:2.5px solid #FFFFFF;border-radius:50%;box-shadow:0 0 8px rgba(0,0,0,0.9);"></div>' +
                '</div>';

            var riderIcon = L.divIcon({
                className: 'velo-pin-rider',
                html: riderHtml,
                iconSize: [24, 24],
                iconAnchor: [12, 12]
            });

            if (riderMarker) {
                riderMarker.setLatLng(riderPt);
            } else {
                riderMarker = L.marker(riderPt, { icon: riderIcon, interactive: false }).addTo(map);
            }

            if (followUser && isLive) {
                map.panTo(riderPt, { animate: true, duration: 0.4 });
            }
        }

        function fitBoundsSafe() {
            if (!map) return;
            map.invalidateSize();
            if (pendingPoints.length >= 2) {
                var latlngs = [];
                for (var i = 0; i < pendingPoints.length; i++) {
                    var p = pendingPoints[i];
                    if (p && typeof p.lat === 'number' && !isNaN(p.lat) && typeof p.lon === 'number' && !isNaN(p.lon)) {
                        latlngs.push([p.lat, p.lon]);
                    }
                }
                if (latlngs.length >= 2) {
                    var bounds = L.latLngBounds(latlngs);
                    if (bounds.isValid()) {
                        map.fitBounds(bounds, { padding: [36, 36], maxZoom: 17 });
                    }
                }
            } else if (pendingPoints.length === 1) {
                var single = pendingPoints[0];
                map.setView([single.lat, single.lon], 15.5);
            }
        }

        function recenterMap() {
            if (!map) return;
            map.invalidateSize();
            if (isLive && lastRiderLat && lastRiderLon) {
                followUser = true;
                map.setView([lastRiderLat, lastRiderLon], 16);
            } else {
                fitBoundsSafe();
            }
        }

        function setFollowUser(follow) {
            followUser = !!follow;
            if (followUser && isLive && lastRiderLat && lastRiderLon && map) {
                map.setView([lastRiderLat, lastRiderLon], 16);
            }
        }

        function handleMapClick(clickLat, clickLon) {
            if (window.AndroidBridge && window.AndroidBridge.onMapCoordinateClicked) {
                window.AndroidBridge.onMapCoordinateClicked(clickLat, clickLon);
            }
            if (!pendingPoints || pendingPoints.length === 0) return;
            var bestIdx = 0;
            var bestDistSq = 1e9;
            for (var i = 0; i < pendingPoints.length; i++) {
                var p = pendingPoints[i];
                var dLat = p.lat - clickLat;
                var dLon = p.lon - clickLon;
                var distSq = dLat * dLat + dLon * dLon;
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    bestIdx = i;
                }
            }
            if (window.AndroidBridge && window.AndroidBridge.onMapPointSelected) {
                window.AndroidBridge.onMapPointSelected(bestIdx);
            }
        }

        function centerOnCursor() {
            if (cursorMarker && map) {
                map.panTo(cursorMarker.getLatLng(), { animate: true, duration: 0.35 });
            }
        }

        function setCursorMarker(lat, lon) {
            if (!lat || !lon || isNaN(lat) || isNaN(lon)) return;
            if (!map) {
                ensureMap();
                if (!map) return;
            }
            var pt = [lat, lon];
            var cursorHtml = '<div style="position:relative;width:28px;height:28px;transform:translate(-14px,-14px);">' +
                '<div style="position:absolute;top:0;left:0;width:28px;height:28px;background:rgba(0,229,255,0.4);border-radius:50%;animation:veloCursorPulse 1.5s infinite ease-in-out;"></div>' +
                '<div style="position:absolute;top:6px;left:6px;width:16px;height:16px;background:#00E5FF;border:2.5px solid #FFFFFF;border-radius:50%;box-shadow:0 0 10px rgba(0,229,255,0.9);"></div>' +
                '</div>';
            var cursorIcon = L.divIcon({
                className: 'velo-pin-cursor',
                html: cursorHtml,
                iconSize: [28, 28],
                iconAnchor: [14, 14]
            });
            if (cursorMarker) {
                cursorMarker.setLatLng(pt);
            } else {
                cursorMarker = L.marker(pt, { icon: cursorIcon, interactive: false, zIndexOffset: 1000 }).addTo(map);
            }
        }

        function clearCursorMarker() {
            if (cursorMarker && map) {
                map.removeLayer(cursorMarker);
                cursorMarker = null;
            }
        }
    </script>
</body>
</html>
    """.trimIndent()
}

// Mini elevation profile drawn along the bottom edge of the map
private fun DrawScope.drawMiniElevationStrip(
    points: List<TrackPointModel>,
    canvasW: Float,
    canvasH: Float
) {
    if (points.size < 2) return

    val stripH = 34.dp.toPx()
    val topY = canvasH - stripH

    // Translucent glass gradient backing
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0x000A0D12), Color(0xD90A0D12)),
            startY = topY,
            endY = canvasH
        ),
        topLeft = Offset(0f, topY),
        size = Size(canvasW, stripH)
    )

    // Border line separating strip
    drawLine(
        color = Color(0x33242F3E),
        start = Offset(0f, topY),
        end = Offset(canvasW, topY),
        strokeWidth = 1.dp.toPx()
    )

    val validAlts = points.map { it.altitude }.filter { it.isFinite() }
    if (validAlts.size < 2) return

    val minAlt = validAlts.minOrNull() ?: 0.0
    val maxAlt = validAlts.maxOrNull() ?: 1.0
    val altSpan = (maxAlt - minAlt).coerceAtLeast(5.0)

    val path = Path()
    val fillPath = Path()
    fillPath.moveTo(0f, canvasH)

    points.forEachIndexed { index, pt ->
        val alt = if (pt.altitude.isFinite()) pt.altitude else minAlt
        val x = (index.toFloat() / (points.size - 1)) * canvasW
        val norm = ((alt - minAlt) / altSpan).toFloat().coerceIn(0f, 1f)
        val y = canvasH - (norm * (stripH - 6.dp.toPx())) - 2.dp.toPx()

        if (index == 0) {
            path.moveTo(x, y)
            fillPath.lineTo(x, y)
        } else {
            path.lineTo(x, y)
            fillPath.lineTo(x, y)
        }
    }

    fillPath.lineTo(canvasW, canvasH)
    fillPath.close()

    // Elevation gradient fill
    drawPath(
        path = fillPath,
        brush = Brush.verticalGradient(
            colors = listOf(ElectricLime.copy(alpha = 0.25f), ElectricLime.copy(alpha = 0.02f)),
            startY = topY,
            endY = canvasH
        )
    )

    // Elevation top stroke
    drawPath(
        path = path,
        color = ElectricLime.copy(alpha = 0.8f),
        style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
    )
}
