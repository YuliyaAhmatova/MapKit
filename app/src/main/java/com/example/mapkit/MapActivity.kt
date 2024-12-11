package com.example.mapkit

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.Location
import android.os.Bundle
import android.os.PersistableBundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.mapkit.databinding.ActivityMapBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.yandex.mapkit.Animation
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.layers.GeoObjectTapListener
import com.yandex.mapkit.map.CameraListener
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.CameraUpdateReason
import com.yandex.mapkit.map.GeoObjectSelectionMetadata
import com.yandex.mapkit.map.InputListener
import com.yandex.mapkit.map.Map
import com.yandex.mapkit.map.MapObjectCollection
import com.yandex.mapkit.map.MapObjectTapListener
import com.yandex.mapkit.map.PlacemarkMapObject
import com.yandex.mapkit.search.Address
import com.yandex.mapkit.search.Response
import com.yandex.mapkit.search.SearchFactory
import com.yandex.mapkit.search.SearchManager
import com.yandex.mapkit.search.SearchManagerType
import com.yandex.mapkit.search.SearchOptions
import com.yandex.mapkit.search.Session
import com.yandex.mapkit.search.ToponymObjectMetadata
import com.yandex.mapkit.traffic.TrafficLayer
import com.yandex.runtime.Error
import com.yandex.runtime.image.ImageProvider

class MapActivity : AppCompatActivity(), CameraListener {

    private lateinit var trafficLayer: TrafficLayer

    lateinit var searchManager: SearchManager
    lateinit var searchSession: Session

    private lateinit var binding: ActivityMapBinding
    private val startLocation = Point(53.2122, 50.1438)
    private var zoomValue: Float = 16.5f

    private lateinit var mapObjectCollection: MapObjectCollection
    private lateinit var placemarkMapObject: PlacemarkMapObject

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
        const val ZOOM_BOUNDARY = 16.4f
    }

    private val mapObjectTapListener = MapObjectTapListener { mapObject, point ->
        Toast.makeText(applicationContext, "Ракета", Toast.LENGTH_SHORT).show()
        true
    }

    private val tapListener = GeoObjectTapListener { geoObjectTapEvent ->
        val selectionMetadata: GeoObjectSelectionMetadata = geoObjectTapEvent
            .geoObject
            .metadataContainer
            .getItem(GeoObjectSelectionMetadata::class.java)

        binding.mapView.map.selectGeoObject(selectionMetadata)
        false
    }

    private val searchListener = object : Session.SearchListener {
        override fun onSearchResponse(response: Response) {
            val street = response.collection.children.firstOrNull()?.obj
                ?.metadataContainer
                ?.getItem(ToponymObjectMetadata::class.java)
                ?.address
                ?.components
                ?.firstOrNull { it.kinds.contains(Address.Component.Kind.STREET) }
                ?.name ?: "Информация не найдена"

            Toast.makeText(applicationContext, street, Toast.LENGTH_SHORT).show()
        }

        override fun onSearchError(p0: Error) {}

    }

    private val inputListener = object : InputListener {
        override fun onMapTap(map: Map, point: Point) {
            searchSession = searchManager.submit(point, 20, SearchOptions(), searchListener)
        }

        override fun onMapLongTap(p0: Map, p1: Point) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setApiKey(savedInstanceState)
        MapKitFactory.initialize(this)
        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        binding.mapView.map.move(
            CameraPosition(startLocation, zoomValue, 0.0f, 0.0f),
            Animation(Animation.Type.SMOOTH, 5f),
            null
        )
        setMarkerInStartLocation()
        binding.mapView.map.addCameraListener(this)
        placemarkMapObject.addTapListener(mapObjectTapListener)
        binding.mapView.map.addTapListener(tapListener)
        searchManager = SearchFactory.getInstance().createSearchManager(SearchManagerType.ONLINE)
        binding.mapView.map.addInputListener(inputListener)

        trafficLayer = MapKitFactory.getInstance().createTrafficLayer(binding.mapView.mapWindow)
        trafficLayer.isTrafficVisible = false
        binding.buttonTrafficBTN.setOnClickListener {
            if (trafficLayer.isTrafficVisible) {
                trafficLayer.isTrafficVisible = false
                Toast.makeText(this, "Трафик выключен", Toast.LENGTH_SHORT).show()
            } else {
                trafficLayer.isTrafficVisible = true
                Toast.makeText(this, "Трафик включен", Toast.LENGTH_SHORT).show()
            }
        }

        binding.buttonBTN.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestLocationPermission()
            } else {
                getCurrentLocation()
            }
        }
    }

    override fun onCameraPositionChanged(
        map: Map,
        cameraPosition: CameraPosition,
        cameraUpdateReason: CameraUpdateReason,
        finished: Boolean
    ) {
        if (finished) {
            when {
                cameraPosition.zoom >= ZOOM_BOUNDARY && zoomValue <= ZOOM_BOUNDARY -> {
                    placemarkMapObject.setIcon(ImageProvider.fromBitmap(createBitmapFromVector(R.drawable.ic_pin_blue)))
                }

                cameraPosition.zoom <= ZOOM_BOUNDARY && zoomValue >= ZOOM_BOUNDARY -> {
                    placemarkMapObject.setIcon(ImageProvider.fromBitmap(createBitmapFromVector(R.drawable.ic_pin_red)))
                }
            }
            zoomValue = cameraPosition.zoom
        }
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocation() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                val currentLocation = Point(it.latitude, it.longitude)
                moveToLocation(currentLocation)
                setMarker(currentLocation)
            }
        }
    }

    private fun setMarker(location: Point) {
        mapObjectCollection.clear()
        placemarkMapObject = mapObjectCollection.addPlacemark(
            location,
            ImageProvider.fromBitmap(createBitmapFromVector(R.drawable.ic_pin)!!)
        )
        placemarkMapObject.opacity = 0.5f
    }

    private fun moveToLocation(location: Point) {
        binding.mapView.map.move(
            CameraPosition(location, zoomValue, 0.0f, 0.0f),
            Animation(Animation.Type.SMOOTH, 5f),
            null
        )
    }

    private fun setMarkerInStartLocation() {
        val marker = createBitmapFromVector(R.drawable.ic_pin)
        mapObjectCollection = binding.mapView.map.mapObjects
        placemarkMapObject = mapObjectCollection.addPlacemark(
            startLocation,
            ImageProvider.fromBitmap(marker)
        )
        placemarkMapObject.opacity = 0.5f
    }

    private fun createBitmapFromVector(art: Int): Bitmap? {
        val drawable = ContextCompat.getDrawable(
            this,
            art
        ) ?: return null
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicWidth,
            Bitmap.Config.ARGB_8888
        ) ?: return null
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun setApiKey(savedInstanceState: Bundle?) {
        val haveApiKey = savedInstanceState?.getBoolean("haveApiKey") == true
        if (!haveApiKey) MapKitFactory.setApiKey(BuildConfig.MAPKIT_API_KEY)
    }

    override fun onSaveInstanceState(outState: Bundle, outPersistentState: PersistableBundle) {
        super.onSaveInstanceState(outState, outPersistentState)
        outState.putBoolean("haveApiKey", true)
    }

    override fun onStart() {
        super.onStart()
        MapKitFactory.getInstance().onStart()
        binding.mapView.onStart()
    }

    override fun onStop() {
        binding.mapView.onStop()
        MapKitFactory.getInstance().onStop()
        super.onStop()
    }
}