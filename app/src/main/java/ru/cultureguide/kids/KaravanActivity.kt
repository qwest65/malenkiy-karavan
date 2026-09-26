package ru.cultureguide.kids

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import ru.cultureguide.data.CatalogDatabase
import ru.cultureguide.kids.audio.ClipPlayer
import ru.cultureguide.kids.content.KidsPathsLoader
import ru.cultureguide.kids.content.KidsRouteLoader
import ru.cultureguide.kids.map.ApproachRouter
import ru.cultureguide.kids.map.HeadingSensor
import ru.cultureguide.kids.map.KaravanMap
import ru.cultureguide.kids.photo.PhotoStore
import ru.cultureguide.kids.ui.KaravanApp
import ru.cultureguide.kids.ui.KaravanTheme
import ru.cultureguide.kids.ui.MapHooks
import ru.cultureguide.kids.ui.PhotoHooks
import java.io.File
import ru.cultureguide.location.LocationTracker

class KaravanActivity : ComponentActivity() {
    private lateinit var controller: KaravanController
    private lateinit var karavanMap: KaravanMap
    private lateinit var tracker: LocationTracker
    private lateinit var compass: HeadingSensor
    private var mapView: MapView? = null
    private lateinit var photos: PhotoStore
    /** Точка, для которой сейчас снимают или выбирают фото. */
    private var photoStop = -1
    private var cameraUri: Uri? = null

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = cameraUri
        if (saved && uri != null && photoStop >= 0) importPhoto(photoStop, uri)
    }

    private val pickPicture = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && photoStop >= 0) importPhoto(photoStop, uri)
    }

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
        // Пока открыта камера или галерея, Android может закрыть приложение — помним, для какой точки фото.
        photoStop = savedInstanceState?.getInt(STATE_PHOTO_STOP, -1) ?: -1
        cameraUri = savedInstanceState?.getString(STATE_CAMERA_URI)?.let(Uri::parse)
        enableEdgeToEdge()
        MapLibre.getInstance(this)

        val route = KidsRouteLoader.load(this)
        // Координаты и подробные справки точек — из каталога мест (core/src/main/assets/catalog.json).
        val db = CatalogDatabase(applicationContext).apply { ensureBundledCatalog() }
        // В базе у мест свои номера, поэтому место ищем по названию из каталога.
        val catalogNames = JSONObject(assets.open(CATALOG_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() })
            .getJSONArray("places").let { a -> (0 until a.length()).associate { a.getJSONObject(it).getLong("id") to a.getJSONObject(it).getString("name").trim() } }
        val dbPlaces = db.places(route.cityId)
        val byName = dbPlaces.associateBy { it.name }
        val byId = dbPlaces.associateBy { it.id }
        val places = route.stops.map { stop ->
            checkNotNull(catalogNames[stop.placeId]?.let(byName::get) ?: byId[stop.placeId]) {
                "В каталоге нет объекта ${stop.placeId} для точки «${stop.title}»"
            }
        }

        val paths = KidsPathsLoader.load(this, route)

        photos = PhotoStore(this)
        controller = KaravanController(this, route, places, paths, ClipPlayer(this), ApproachRouter(), photos)
        karavanMap = KaravanMap(this, route.stops, places, paths)
        compass = HeadingSensor(this, controller::onCompass)
        controller.onHeading = karavanMap::setHeading
        tracker = LocationTracker(this) { location ->
            compass.setLocation(location.latitude, location.longitude)
            controller.onLocation(location)
        }

        val hooks = MapHooks(
            onCreated = ::attachMap,
            onReleased = ::detachMap,
            onUpdate = karavanMap::update,
            onFitAll = karavanMap::showAll,
            isFollowing = { karavanMap.following },
            onFollow = karavanMap::follow
        )
        val photoHooks = PhotoHooks(takePhoto = ::takePhoto, pickPhoto = ::pickPhoto, shareCollage = ::shareCollage)
        setContent {
            KaravanTheme {
                KaravanApp(controller, ensureLocation = ::ensureTracking, map = hooks, photo = photoHooks)
            }
        }
    }

    private fun takePhoto(stop: Int) {
        photoStop = stop
        val file = File(cacheDir, "camera/shot.jpg").apply { parentFile?.mkdirs() }
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        cameraUri = uri
        try {
            takePicture.launch(uri)
        } catch (_: ActivityNotFoundException) {
            toast("На телефоне нет приложения камеры")
        }
    }

    private fun pickPhoto(stop: Int) {
        photoStop = stop
        pickPicture.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun importPhoto(stop: Int, uri: Uri) {
        photos.import(stop, uri) { ok ->
            if (ok) controller.onPhotoSaved() else toast("Не удалось сохранить фото")
        }
    }

    private fun shareCollage() {
        val file = photos.collageFile
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/jpeg")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(Intent.createChooser(send, "Коллаж «Маленький караван»"))
        } catch (_: ActivityNotFoundException) {
            toast("Нет приложения, чтобы отправить коллаж")
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_PHOTO_STOP, photoStop)
        cameraUri?.let { outState.putString(STATE_CAMERA_URI, it.toString()) }
    }

    override fun onStart() {
        super.onStart()
        mapView?.onStart()
        if (tracker.hasPermission()) tracker.start()
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
        compass.start()
    }

    override fun onPause() {
        compass.stop()
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

    private companion object {
        const val CATALOG_ASSET = "catalog.json"
        const val STATE_PHOTO_STOP = "photo_stop"
        const val STATE_CAMERA_URI = "camera_uri"
    }
}
