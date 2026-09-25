package ru.cultureguide.kids

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import ru.cultureguide.audio.AudioGuide
import ru.cultureguide.data.CatalogDatabase
import ru.cultureguide.kids.audio.ClipPlayer
import ru.cultureguide.kids.content.KidsPathsLoader
import ru.cultureguide.kids.content.KidsRouteLoader
import ru.cultureguide.kids.map.ApproachRouter
import ru.cultureguide.kids.map.KaravanMap
import ru.cultureguide.kids.ui.KaravanApp
import ru.cultureguide.kids.ui.KaravanTheme
import ru.cultureguide.kids.ui.MapHooks
import ru.cultureguide.location.LocationTracker

class KaravanActivity : ComponentActivity() {
    private lateinit var controller: KaravanController
    private lateinit var karavanMap: KaravanMap
    private lateinit var tracker: LocationTracker
    private var mapView: MapView? = null

    private val permissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted.values.any { it }) {
                startTracking()
            } else {
                toast("Без геолокации нажимайте «Мы на месте!», когда подойдёте к точке")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MapLibre.getInstance(this)

        val route = KidsRouteLoader.load(this)
        // Координаты и подробные справки точек — из каталога мест (core/src/main/assets/catalog.json).
        val db = CatalogDatabase(applicationContext).apply { ensureBundledCatalog() }
        val byId = db.places(route.cityId).associateBy { it.id }
        val places = route.stops.map { stop ->
            checkNotNull(byId[stop.placeId]) { "В каталоге нет объекта ${stop.placeId} для точки «${stop.title}»" }
        }

        val paths = KidsPathsLoader.load(this, route)

        controller = KaravanController(this, route, places, paths, ClipPlayer(this), AudioGuide(this), ApproachRouter())
        karavanMap = KaravanMap(this, route.stops, places, paths)
        tracker = LocationTracker(this, controller::onLocation)

        val hooks = MapHooks(
            onCreated = ::attachMap,
            onReleased = ::detachMap,
            onUpdate = karavanMap::update,
            onFitAll = karavanMap::fitAll
        )
        setContent {
            KaravanTheme {
                KaravanApp(controller, ensureLocation = ::ensureTracking, map = hooks)
            }
        }
    }

    private fun ensureTracking() {
        if (tracker.hasPermission()) {
            startTracking()
        } else {
            permissionRequest.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    private fun startTracking() {
        if (!tracker.start() && !tracker.isProviderEnabled()) toast("Включите геолокацию в настройках телефона")
    }

    private fun attachMap(view: MapView) {
        mapView = view
        karavanMap.attach(view)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) view.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view.onResume()
    }

    private fun detachMap(view: MapView) {
        karavanMap.detach()
        view.onPause()
        view.onStop()
        view.onDestroy()
        if (mapView === view) mapView = null
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    override fun onStart() {
        super.onStart()
        mapView?.onStart()
        if (tracker.hasPermission()) tracker.start()
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        mapView?.onPause()
        super.onPause()
    }

    override fun onStop() {
        tracker.stop()
        mapView?.onStop()
        super.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

    override fun onDestroy() {
        controller.dispose()
        super.onDestroy()
    }
}
