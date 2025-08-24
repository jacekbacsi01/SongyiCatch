package com.rayden.songyicatch

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import kotlin.math.*
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioGroup
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress



class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
//-------map variables
    private lateinit var compassOverlay: CompassOverlay
    private lateinit var map: MapView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private lateinit var myLocationOverlay: MyLocationNewOverlay
    private lateinit var orientationListener: OrientationEventListener
    private lateinit var lastgeopoint: GeoPoint
    private var sondLocationMarker: Marker? = null
    private val predictionOverlays = mutableListOf<org.osmdroid.views.overlay.Overlay>()
    private var currentLandingMarker: Marker? = null
    private var tMarker: Marker? = null
//-------sonde info panel variables
    private lateinit var textSeriall: TextView
    private lateinit var textFrame: TextView
    private lateinit var textSats: TextView
    private lateinit var textAscent: TextView
    private lateinit var textDescent: TextView
    private lateinit var textBurst: TextView
    private lateinit var textAltitude: TextView
    private lateinit var textSpeed: TextView
    private lateinit var textTemperature: TextView
    private lateinit var textCoordinates: TextView
    private lateinit var textSelfAlt: TextView
    private lateinit var textFreq: TextView
    private lateinit var textConnect: TextView
    private lateinit var textDistance: TextView
    private lateinit var textTime: TextView
//-------sonde info panel end
    private val DEVICE_NAME = "RDZ_TTGO"
    private val PERMISSION_REQUEST_CODE = 1001
   //tcp conn json
   private lateinit var nsdManager: NsdManager
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var resolveListener: NsdManager.ResolveListener? = null

    private var clientSocket: Socket? = null
    private var connectJob: Job? = null

    private var host: String? = null
    private var port: Int? = null
   // val existingMarkers = mutableMapOf<String, Marker>()

    // --- Sonda data tömb a több szonda kezeléséhez ---
    data class SondaData(
        val lat: Double,
        val lon: Double,
        val alt: Double,
        val vs: Double,
        val hs: Double,
        val climb: Double,
        val speed: Double,
        val dir: Double,
        val temp: Double,
        val humidity: Double,
        val pressure: Double,
        val type: String,
        val id: String,
        val ser: String,
        val frame: Int,
        val vframe: Int,
        val time: Long,
        val sats: Int,
        val freq: Double,
        val rssi: Double,
        val afc: Int,
        val launchKT: Int,
        val burstKT: Int,
        val countKT: Int,
        val crefKT: Int,
        val launchsite: String,
        val res: Int,
        val batt: Double,
        val active: Int,
        val validId: Int,
        val validPos: Int,
        val gpslat: Double,
        val gpslon: Double,
        val gpsalt: Double,
        val gpsacc: Int,
        val gpsdir: Int
    )

    val sondData = mutableMapOf<String, SondaData>()
    var selectedSondaId: String? = null

    private val serviceType = "_http._tcp."  // vagy amilyen az rdzsonde szolgáltatás típusa
    //json tcp end

//=============ON CREATE=============================
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    // nsdManager = getSystemService(NSD_SERVICE) as NsdManager
   // startServiceDiscovery()
    //val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager

    // Log.d("BLE", "Bluetooth állapot: ${bluetoothAdapter.state}")

    onBackPressedDispatcher.addCallback(this) { // vissza gomb megnyomása
            // új viselkedés, pl. Drawer bezárása
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                finish() // vagy super.onBackPressed() ha tovább akarod adni
            }
        }
    val file = File(this.getExternalFilesDir(""), "bejovo-kimenet.json")
        loadTawhiriSettingsFromJson()
        // osmdroid konfiguráció betöltése és userAgent beállítása
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        Configuration.getInstance().userAgentValue = packageName

        // Layout betöltése (figyelj, hogy ez legyen a megfelelő xml fájl)
        setContentView(R.layout.main_activity)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.INTERNET,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.CHANGE_WIFI_MULTICAST_STATE,
            Manifest.permission.ACCESS_WIFI_STATE
        )

        requestPermissions(permissions, PERMISSION_REQUEST_CODE)
    }
    // Engedélyek kérése futás közben
    requestPermissionsIfNecessary(
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.INTERNET,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.CHANGE_WIFI_MULTICAST_STATE,
            Manifest.permission.ACCESS_WIFI_STATE
        )
    )
    checkAndRequestPermissions()
        // -----MapView inicializálása INNENTŐL LEHET A MAP VÁLTOZÓT MEGHÍNI-----

    map = findViewById(R.id.map)
        // Toolbar beállítása (feltételezve, hogy van toolbar a layoutban)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
    nsdManager = getSystemService(NSD_SERVICE) as NsdManager
    startDiscovery()
        // DrawerLayout és NavigationView
        drawerLayout = findViewById(R.id.drawer_layout)
        val navView = findViewById<NavigationView>(R.id.nav_view)

        drawerToggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()

        navView.setNavigationItemSelectedListener(this)

        // Helymeghatározás overlay létrehozása
        myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), map)
        myLocationOverlay.enableMyLocation()  // elindítja a helyfigyelést

        //myLocationOverlay.enableFollowLocation() // EZ KÖVETI KÖZÉPEN A TÉRKÉPEN SAJÁT POZIT

        compassOverlay = CompassOverlay(
            this, // context
            InternalCompassOrientationProvider(this),
            map
        )

        compassOverlay.enableCompass()
        map.overlays.add(compassOverlay)

    setupSondeInfoPanel() //be kell initelni mert crashel

        map.overlays.add(object : Overlay(this) { // Hosszú lenyomás Overlay
            override fun onLongPress(event: MotionEvent?, mapView: MapView?): Boolean {
                if (event == null || mapView == null) return false

                val screenX = event.x.toInt()
                val screenY = event.y.toInt()

                Toast.makeText(this@MainActivity, "Hosszú nyomás", Toast.LENGTH_SHORT).show() // Felugró debug szöveg ha van longpress

                val iGeoPoint = mapView.projection.fromPixels(screenX, screenY)
                val geoPoint = GeoPoint(iGeoPoint.latitude, iGeoPoint.longitude)

                lastgeopoint = geoPoint // innen tudja a mi van a közelben függvény a pozíciónkat
                tMarker = Marker(map)
                tMarker!!.position = lastgeopoint
                tMarker!!.icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.pin)
                tMarker!!.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                tMarker!!.title = "Ideiglenes hely"
                map.overlays.add(tMarker)

                map.invalidate()
                showModernPopup()
                return true
            }
        })
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.setBuiltInZoomControls(true)
    simulatePrediction()

    if (!file.exists()){ // bejovo-kimenet.json -- DEBUG
        file.writeText("")  // üres fájl
    }

        simulatePrediction()

        myLocationOverlay.runOnFirstFix {
            runOnUiThread {
                val location = myLocationOverlay.myLocation
                if (location != null) {
                    map.controller.setZoom(18.0)
                    map.controller.setCenter(location)
                }
            }
        }

        map.overlays.add(myLocationOverlay)
        map.controller.setCenter(myLocationOverlay.myLocation)

        orientationListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return

                // Itt frissítsd a térképet, pl. ha elfordul a telefon
                map.invalidate()
                sondLocationMarker?.showInfoWindow()
            }
        }
    }
// MDNS DISCOVERY - JSON TCP
override fun onDestroy() {
    super.onDestroy()
    stopDiscovery()
    disconnect()
    connectJob?.cancel()
}
    val job = CoroutineScope(Dispatchers.Main).launch {
        while (isActive) {
           // simulatePrediction()
            delay(1000) // 1 másodperc
        }
    }
    private fun startDiscovery() {
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.d("RDZ", "mDNS discovery started")
                Toast.makeText(this@MainActivity, "mDNS Feltréképezés elindult!", Toast.LENGTH_SHORT).show() // Felugró debug szöveg ha van longpress
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d("RDZ", "Service found: ${serviceInfo.serviceName}")
                if (serviceInfo.serviceName.contains("rdzsonde")) {
                    Log.d("RDZ", "Found rdzsonde service, resolving...")
                    Toast.makeText(this@MainActivity, "rdzsonde szolgálatást találtam! Csatlakozás...", Toast.LENGTH_SHORT).show() // Felugró debug szöveg ha van longpress
                    resolveService(serviceInfo)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d("RDZ", "Service lost: ${serviceInfo.serviceName}")
                Toast.makeText(this@MainActivity, "mDNS LOST!", Toast.LENGTH_SHORT).show() // Felugró debug szöveg ha van longpress
                disconnect()
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d("RDZ", "Discovery stopped")
                Toast.makeText(this@MainActivity, "mDNS Feltréképezés STOPP!", Toast.LENGTH_SHORT).show() // Felugró debug szöveg ha van longpress
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e("RDZ", "Discovery start failed: $errorCode")
                Toast.makeText(this@MainActivity, "mDNS Feltérképezés HIBA!", Toast.LENGTH_SHORT).show() // Felugró debug szöveg ha van longpress
                nsdManager.stopServiceDiscovery(this)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e("RDZ", "Discovery stop failed: $errorCode")
                nsdManager.stopServiceDiscovery(this)
            }
        }

        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    private fun stopDiscovery() {
        discoveryListener?.let { nsdManager.stopServiceDiscovery(it) }
        discoveryListener = null
    }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        resolveListener = object : NsdManager.ResolveListener {
            override fun onServiceResolved(resolvedServiceInfo: NsdServiceInfo) {
                host = resolvedServiceInfo.host.hostAddress
                port = resolvedServiceInfo.port
                Log.d("RDZ", "Resolved service: $host:$port")
                Toast.makeText(this@MainActivity, "RDZ Szolgáltatás feloldva! Start connloop..", Toast.LENGTH_SHORT).show() // Felugró debug szöveg ha van longpress
                startConnectLoop()
            }

            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                Log.e("RDZ", "Resolve failed: $errorCode")
                Toast.makeText(this@MainActivity, "RDZ Service Feloldás hiba!", Toast.LENGTH_SHORT).show() // Felugró debug szöveg ha van longpress

            }
        }

        nsdManager.resolveService(serviceInfo, resolveListener)
    }

    private fun startConnectLoop() {
        try {

         /*   if(connectJob?.isActive == true){
                connectJob?.cancel()
            }*/

       // connectJob = CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                try {
                    port = 14570
                    val h = host ?: break
                    val p = port ?: break

                    Log.d("RDZ", "Trying to connect to $h:$p")
                    Toast.makeText(this@MainActivity, "rdz connect probe to: $h:$p", Toast.LENGTH_SHORT).show() // Felugró debug szöveg ha van longpress

                    clientSocket = Socket()
                    clientSocket!!.connect(InetSocketAddress(h, 14570), 5000)
                    Log.d("RDZ", "Connected to $h:$p")
                    Toast.makeText(this@MainActivity, "rdz connected! host: $h:$p", Toast.LENGTH_SHORT).show() // Felugró debug szöveg ha van longpress

                    val reader = BufferedReader(InputStreamReader(clientSocket!!.getInputStream()))


                    while (true) {
                        val line = reader.readLine() ?: break
                        updateSonda(JSONObject(line))
                        Log.d("RDZ", "Received JSON: $line")
                        val file = File(this.getExternalFilesDir(""), "bejovo-kimenet.json")
                        file.appendText(line.trim() + "\n")
                        // Toast.makeText(this@MainActivity, "JSON Adat érkezett! OUT: ${line}", Toast.LENGTH_SHORT).show() // Felugró debug szöveg ha van longpress
                        // Itt parseolhatod és tárolhatod a JSON-t változókba, ha kell
                    }
                    Log.d("RDZ", "Connection closed by server")
                    Toast.makeText(this@MainActivity, "connection closed by server!", Toast.LENGTH_SHORT).show() // Felugró debug szöveg ha van longpress
                } catch (e: Exception) {
                    Log.e("RDZ", "Connection error: ${e.message}")
                    Toast.makeText(this@MainActivity, "Kapcsolat hiba!", Toast.LENGTH_SHORT).show() // Felugró debug szöveg ha van longpress

                } finally {
                    disconnect()
                }
                Thread.sleep(1000) // 1 másodperc várakozás újracsatlakozás előtt
            }
       // }
        }catch (ex: Exception){

            Toast.makeText(this@MainActivity, "OnConnect try1 fucks! e: ${ex.message}", Toast.LENGTH_LONG).show() // Felugró debug szöveg ha van longpress
            Log.e("RDZ", "Connection error: ${ex.message}")
        }
    }

    private fun disconnect() {
        try {
            clientSocket?.close()
        } catch (e: Exception) {
            // ignore
        }
        clientSocket = null
        Log.d("RDZ", "Disconnected")
    }
    /**
     *
     * MDNS TCP - JSON DISCOVER END
     *
     */


    fun distanceInMeters(point1: GeoPoint?, point2: GeoPoint?): Float {
        if (point1 == null || point2 == null) return -1f

        val results = FloatArray(1)
        Location.distanceBetween(
            point1.latitude, point1.longitude,
            point2.latitude, point2.longitude,
            results
        )
        return results[0] // Távolság méterben
    }

    // --- Tesztelő függvény, predikció a szonda helyéről ---
    private fun simulatePrediction() { // 47.85962368110185, 19.697043299674988
        val currentLat = 47.85962368110185
        val currentLon = 19.697043299674988
        //updateSondaPosition(currentLat, currentLon)
        //Log.d("BLE", "Bluetooth állapot: ${bluetoothAdapter.state}")
       // fetchPredictionFromTawhiri(currentLat, currentLon, 140.0,5.0,5.0,34000.0)
        val distFromMe = distanceInMeters(myLocationOverlay.myLocation, sondLocationMarker?.position)
        var myalt: Double
        val location = myLocationOverlay.myLocation
        if (location != null && !location.altitude.isNaN()) {
            myalt = location.altitude // Méterben, Double
            println("Tengerszint feletti magasság: $myalt m")
        } else {
            myalt = -0.0
            println("Magasság nem elérhető.")
        }
        //updateSondeInfo("🟠 n/A - Offline","N/A","0","0",0.0,0.0,0.0,0.0,0.0,-0.0,currentLat,currentLon,myalt, 0.0, distFromMe)
        val jso = """
{
  "lat": 47.51360,
  "lon": 19.28762,
  "alt": 22523.7,
  "vs": 5.5,
  "hs": 1.2,
  "climb": 5.5,
  "speed": 1.2,
  "dir": 242.7,
  "temp": -55.9,
  "humidity": 1.4,
  "pressure": 39.9,
  "type": "RS41-SGP",
  "id": "X1433033",
  "ser": "X1433033",
  "frame": 5288,
  "vframe": 5288,
  "time": 1755346973,
  "sats": 9,
  "freq": 403.70,
  "rssi": 211.0,
  "afc": 488,
  "launchKT": 65535,
  "burstKT": 30600,
  "countKT": 65535,
  "crefKT": 5253,
  "launchsite": "Budapest",
  "res": 0,
  "batt": 2.6,
  "active": 1,
  "validId": 1,
  "validPos": 127,
  "gpslat": 47.51360,
  "gpslon": 19.28762,
  "gpsalt": 194,
  "gpsacc": 0,
  "gpsdir": 39
}
""".trimIndent()
        //47.94050949058958, 21.812548467085207
        val jso2 = """
{
  "lat": 47.94050,
  "lon": 21.81254,
  "alt": 21000.7,
  "vs": 5.5,
  "hs": 1.2,
  "climb": 5.5,
  "speed": 1.2,
  "dir": 242.7,
  "temp": -55.9,
  "humidity": 1.4,
  "pressure": 39.9,
  "type": "RS41-SGP",
  "id": "X2233034",
  "ser": "X2233034",
  "frame": 5288,
  "vframe": 5288,
  "time": 1755346973,
  "sats": 9,
  "freq": 403.70,
  "rssi": 211.0,
  "afc": 488,
  "launchKT": 65535,
  "burstKT": 30600,
  "countKT": 65535,
  "crefKT": 5253,
  "launchsite": "Budapest",
  "res": 0,
  "batt": 2.6,
  "active": 0,
  "validId": 1,
  "validPos": 127,
  "gpslat": 47.51360,
  "gpslon": 19.28762,
  "gpsalt": 194,
  "gpsacc": 0,
  "gpsdir": 39
}
""".trimIndent()
        val jsonObjtmp = JSONObject(jso)
        updateSonda(jsonObjtmp)
        val jsonObjtmp2 = JSONObject(jso2)
        updateSonda(jsonObjtmp2)
    }

    fun updateSondeInfo(
        status: String,
        serial: String,
        frame: String,
        sats: String,
        ascent: Double,
        descent: Double,
        burstAlt: Double,
        nowAlt: Double,
        speed: Double,
        temperature: Double,
        lat: Double,
        lon: Double,
        selfAlt: Double,
        freq: Double,
        distance_from_me: Float,
    ) {
        //textConnect.text = "\uD83D\uDFE0 RS41 - Offline"
        //textConnect.text = "\uD83D\uDFE2 RS41 - Online"
        textConnect.text = "$status"
        textSeriall.text = "\uD83C\uDD94 Serial: $serial"
        textFrame.text = "#\uFE0F⃣ Frame: #$frame"
        textAltitude.text = "\uD83C\uDFD4\uFE0F Magasság: %.1f m".format(nowAlt)
        textSpeed.text = "\uD83D\uDE80 Sebesség: %.1f m/s".format(speed)
        textTemperature.text = "🌡 Hőmérséklet: %.1f °C".format(temperature)
        textCoordinates.text = "📍 Lat: %.5f, Lon: %.5f".format(lat, lon)
        textSats.text = "\uD83D\uDEF0\uFE0F Műholdak: $sats"
        textAscent.text = "⬆\uFE0F Emelkedés: %.1f m/s".format(ascent)
        textDescent.text = "⬇\uFE0F Süllyedés: %.1f m/s".format(descent)
        textBurst.text = "\uD83D\uDCA5 Eldurranás: %.1f m".format(burstAlt)
        textSelfAlt.text = "\uD83D\uDCC8 Saját Alt: %.1f m".format(selfAlt)
        textFreq.text = "\uD83D\uDCE1 Freq: %.3f Mhz".format(freq)
        textDistance.text = "\uD83D\uDCCF Távolság: %.2f m".format(distance_from_me)
       // val isoTime = java.time.Instant.now().toString()
       // val isoTime = java.time.LocalDateTime.now().toString()
        val currentTime = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val timeString = dateFormat.format(Date(currentTime))
        textTime.text = "⏰ Idő: $timeString"
    }

    private fun copyCurrentPositionToClipboard(lat: Double, lon: Double) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val textToCopy = "$lat, $lon"
        val clip = ClipData.newPlainText("Szonda helye", textToCopy)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Pozíció másolva: $textToCopy", Toast.LENGTH_SHORT).show()
    }

    private fun showModernPopup() {
        val inflater = LayoutInflater.from(this)
        val popupView = inflater.inflate(R.layout.popup_menu, null)

        val popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popupWindow.setOnDismissListener {
            // Ez fut le, amikor a popup bezárul: ideiglenes tű marker törlés logic
            // - kívülre kattintáskor
            // - ESC / BACK gombra
            // - vagy programból popupWindow.dismiss() hívásra
            map.overlays.remove(tMarker)
            map.invalidate()
        }
        popupWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popupWindow.isOutsideTouchable = true

        val zoomHere = popupView.findViewById<TextView>(R.id.menu_item_zoom_here)
        val nearby = popupView.findViewById<TextView>(R.id.menu_item_nearby)
        val setmarker = popupView.findViewById<TextView>(R.id.menu_item_marker_here)
        val copypos = popupView.findViewById<TextView>(R.id.menu_item_copy_pos_clicked)
        val distmee = popupView.findViewById<TextView>(R.id.menu_item_distbetweenpoint)

        zoomHere.setOnClickListener { // Zoom ide
            Toast.makeText(this, "Zoom ide", Toast.LENGTH_SHORT).show()
            map.controller.setZoom(21.0)
            map.overlays.remove(tMarker)
            map.invalidate()
            popupWindow.dismiss()
        }

        nearby.setOnClickListener { // Mi van a közelben?
            Toast.makeText(this, "Helyek lekérdezése az internetről...", Toast.LENGTH_SHORT).show()
            fetchNearbyPlaces(lastgeopoint.latitude, lastgeopoint.longitude)
            popupWindow.dismiss()
        }

        copypos.setOnClickListener { // coord másol
            copyCurrentPositionToClipboard(lastgeopoint.latitude, lastgeopoint.longitude)
            popupWindow.dismiss()
        }

        distmee.setOnClickListener { // coord másol
            //copyCurrentPositionToClipboard(lastgeopoint.latitude, lastgeopoint.longitude)
            val dist = distanceInMeters(lastgeopoint, myLocationOverlay.myLocation)
            Toast.makeText(this, "Távolság: $dist", Toast.LENGTH_LONG).show()
            popupWindow.dismiss()
        }

        setmarker.setOnClickListener { // kocsi marker kattintás lekezelés
            val marker = Marker(map)
            marker.position = GeoPoint(lastgeopoint.latitude, lastgeopoint.longitude)
            marker.icon = ContextCompat.getDrawable(this, R.drawable.car) // itt az ikon
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.setOnMarkerClickListener { clickedMarker, mapView ->
                val options = arrayOf("Marker törlése")
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setItems(options) { dialog, which ->
                        if (which == 0) {
                            mapView.overlays.remove(clickedMarker)
                            mapView.invalidate()
                        }
                    }
                    .show()
                true
            }
            map.overlays.add(marker)
            popupWindow.dismiss()
        }
        val rootView = findViewById<View>(android.R.id.content)

        // Először megjelenítjük a popupot
        popupWindow.showAtLocation(rootView, Gravity.CENTER, 0, 0)

        // Ezután kérjük, hogy a háttér homályos legyen
        rootView.post {
            dimBehind(popupWindow)
        }
    }
/**
KÖZELI HELYEKEN....
 */

private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val R = 6371.0 // Föld sugara km-ben
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2.0) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2).pow(2.0)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return R * c // Távolság km-ben
}


private fun showNearbyPlacesList(jsonResponse: String, centerLat: Double, centerLon: Double) {
    try {
        val jsonObj = org.json.JSONObject(jsonResponse)
        val elements = jsonObj.getJSONArray("elements")

        val placeNames = ArrayList<String>()
        var validCount = 0

        for (i in 0 until elements.length()) {
            val element = elements.getJSONObject(i)

            val lat = element.optDouble("lat", Double.NaN)
            val lon = element.optDouble("lon", Double.NaN)

            // Skip if lat/lon not found (pl. way vagy relation típus)
            if (lat.isNaN() || lon.isNaN()) continue

            // Számoljuk ki a távolságot (durván, Haversine nélkül, de jó közelítés)
            val distance = haversine(centerLat, centerLon, lat, lon)
            if (distance > 0.5) continue  // csak 500m-en belüliek

            val tags = element.optJSONObject("tags") ?: continue
            val name = tags.optString("name", "Név nélküli hely")
            val amenity = tags.optString("amenity", "Ismeretlen kategória")

            placeNames.add("• $name\n   ($amenity, ~${"%.0f".format(distance * 1000)} m)")
            validCount++
        }

        if (placeNames.isEmpty()) {
            Toast.makeText(this, "Nincs találat 500m-en belül :(", Toast.LENGTH_SHORT).show()
            return
        }

        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Találatok: $validCount hely 500 m-en belül")
        builder.setItems(placeNames.toTypedArray(), null)
        builder.setPositiveButton("Bezár") { dialog, _ -> dialog.dismiss() }
        builder.show()

    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(this, "Hiba az adatok feldolgozásakor", Toast.LENGTH_SHORT).show()
    }
}

private fun fetchNearbyPlaces(lat: Double, lon: Double) {
    // Példa Overpass API query: az adott pont körül 500m-es körben minden node ami "amenity" taggel rendelkezik
    val overpassQuery = """
    [out:json][timeout:25];
    (
      node(around:500,$lat,$lon);
      way(around:500,$lat,$lon);
      relation(around:500,$lat,$lon);
    );
    out center;
""".trimIndent()

    val url = "https://overpass-api.de/api/interpreter"

    Thread {
        try {
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            val postData = "data=${java.net.URLEncoder.encode(overpassQuery, "UTF-8")}"
            conn.outputStream.use { it.write(postData.toByteArray()) }

            val response = conn.inputStream.bufferedReader().readText()

            // Parse JSON (egyszerűen), és UI thread-en mutasd meg
            runOnUiThread {
                showNearbyPlacesList(response, lat, lon)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread {
                Toast.makeText(this, "Hiba a lekérés során", Toast.LENGTH_SHORT).show()
            }
        }
    }.start()
}

    private fun openInMapApp(lat: Double, lon: Double) { // Megnyitja a GoogleMaps-ban
        val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon(Rádiószonda+pozíció)")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.setPackage("com.google.android.apps.maps") // csak ha Google Maps-szel akarod

        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            // fallback: bármely térképes app megnyitása
            val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=$lat,$lon"))
            startActivity(fallbackIntent)
        }
    }

// TAWHIRI ---- START -----
private fun showSondaMarkerMenu(marker: Marker) {
    // 2. Új marker létrehozása


    val options = arrayOf(
        "Zoom ide",
        "Prediktálás elkészítése",
        "Prediktálás törlése",
        "Objektum törlése",
        "Prediktálás beállítások",
        "Export térkép alkalmazásba",
        "Távolság tőlem",
        "Infó popup megjelenít"
    )

    val builder = androidx.appcompat.app.AlertDialog.Builder(this)
    builder.setTitle("Szonda objektum műveletek")
    builder.setItems(options) { dialog, which ->
        when (which) {
            0 -> { // zoom ide
                map.controller.animateTo(marker.position)
                map.controller.setZoom(14.0)
            } // make predict
            1 -> fetchPredictionFromTawhiri(marker.position.latitude, marker.position.longitude, 140.0,5.0,5.0,34000.0)
            2 -> { // clear predict
                // prediktált vonalak törlése (pl. tárold el külön és távolítsd el innen)
                clearPredictionOverlays()
            }
            3 -> { // obj del
                marker.title = null
                marker.snippet = null
                marker.infoWindow = null // vagy EmptyInfoWindow
                map.overlays.remove(marker)
                clearPredictionOverlays()
                map.invalidate()
            }
            4 -> { // predict settings
                // (később meg kell csinálni külön activity-ként)
                // Toast.makeText(this, "Beállítások megnyitása...", Toast.LENGTH_SHORT).show()
                showTawhiriSettingsDialog()
            }
            5 -> { // térkép export – képernyőkép, GPX export stb.
                Toast.makeText(this, "Exportálás folyamatban...", Toast.LENGTH_SHORT).show()
                openInMapApp(marker.position.latitude, marker.position.longitude)
            }
            6 -> { // távolság tőlem > Songyi
                val myLocation: GeoPoint? = myLocationOverlay.myLocation
                // val markerPosition = sondLocationMarker?.position  // GeoPoint típus

                if (myLocation != null) {
                    val results = FloatArray(1)
                    Location.distanceBetween(
                        myLocation.latitude, myLocation.longitude,
                       // sondLocationMarker?.position!!.latitude, sondLocationMarker?.position!!.longitude,
                        marker.position.latitude, marker.position.longitude,
                        results
                    )
                    val distanceInMeters = results[0]
                    Toast.makeText(
                        this,
                        "Távolság tőlem: %.1f m.".format(distanceInMeters),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    // println("Az aktuális hely nem elérhető!")
                    Toast.makeText(this, "Hiba! Az aktuális hely nem elérhető.", Toast.LENGTH_SHORT).show()
                }
            }
            7 -> {
                marker.showInfoWindow()
            }
        }
    }
    builder.setOnCancelListener {
        map.overlays.remove(tMarker)
        map.invalidate()
        //tempMarker = null
    }
    builder.show()
}
 /*
    // --- Szonda aktuális hely frissítése és megjelenítése ---
    private fun updateSondaPosition(lat: Double, lon: Double) {
        val point = GeoPoint(lat, lon)
        if (sondLocationMarker == null) {
            sondLocationMarker = Marker(map).apply {
                position = point
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "(TESZT)Debug Songyi\nNév: X0123456789\nALT: 140m SPD: 14km/h\n-----"
                // kék színű marker ikon beállítása (ha akarsz, alapértelmezett is jó)
                icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.songyi)
                map.overlays.add(this)
                this.showInfoWindow()
                map.controller.animateTo(this.position)
                this.setOnMarkerClickListener { _, _ ->
                    showSondaMarkerMenu(this)
                    true
                }
            }
        } else {
            sondLocationMarker?.position = point
            title = "Songyi\nNév: X0123456789\nALT: 140m SPD: 14km/h\n-----"
           // sondLocationMarker?.showInfoWindow() // ne hozza elő minden frissítéskor
        }
        map.invalidate()
    }
*/

    // id+ser -> Marker
    val sondMarkers = mutableMapOf<String, Marker>()

    private fun getmyalt(): Double
    {
        var myalt: Double
        val location = myLocationOverlay.myLocation
        if (location != null && !location.altitude.isNaN()) {
            myalt = location.altitude // Méterben, Double
            println("Tengerszint feletti magasság: $myalt m")
            return myalt
        } else {
            myalt = -0.0
            println("Magasság nem elérhető.")
            return myalt
        }
    }

    private fun updateSonda(jsonObject: JSONObject) {
        val id = jsonObject.getString("id")
        val type = jsonObject.getString("type")
        val ser = jsonObject.getString("ser")
        val validPos = jsonObject.optInt("validPos", 0)
        val validId = jsonObject.optInt("validId", 0)

        // Sonda adatok kinyerése
        val lat = jsonObject.optDouble("lat", 0.0)
        val lon = jsonObject.optDouble("lon", 0.0)
        val alt = jsonObject.optDouble("alt", 0.0)
        val speed = jsonObject.optDouble("speed", 0.0)
        val hs = jsonObject.optDouble("hs", 0.0)
        val vs = jsonObject.optDouble("vs", 0.0)
        val batt = jsonObject.optDouble("batt", 0.0)

        // Sonda objektum mentése a mapbe
        val sonda = SondaData(
            id = id,
            type = type,
            ser = ser,
            lat = lat,
            lon = lon,
            alt = alt,
            vs = vs,
            hs = hs,
            climb = jsonObject.optDouble("climb", 0.0),
            speed = speed,
            dir = jsonObject.optDouble("dir", 0.0),
            temp = jsonObject.optDouble("temp", 0.0),
            humidity = jsonObject.optDouble("humidity", 0.0),
            pressure = jsonObject.optDouble("pressure", 0.0),
            frame = jsonObject.optInt("frame", 0),
            vframe = jsonObject.optInt("vframe", 0),
            time = jsonObject.optLong("time", 0L),
            sats = jsonObject.optInt("sats", 0),
            freq = jsonObject.optDouble("freq", 0.0),
            rssi = jsonObject.optDouble("rssi", 0.0),
            afc = jsonObject.optInt("afc", 0),
            launchKT = jsonObject.optInt("launchKT", 0),
            burstKT = jsonObject.optInt("burstKT", 0),
            countKT = jsonObject.optInt("countKT", 0),
            crefKT = jsonObject.optInt("crefKT", 0),
            launchsite = jsonObject.optString("launchsite", ""),
            res = jsonObject.optInt("res", 0),
            batt = batt,
            active = jsonObject.optInt("active", 0),
            validId = validId,
            validPos = validPos,
            gpslat = jsonObject.optDouble("gpslat", lat),
            gpslon = jsonObject.optDouble("gpslon", lon),
            gpsalt = jsonObject.optDouble("gpsalt", alt),
            gpsacc = jsonObject.optInt("gpsacc", 0),
            gpsdir = jsonObject.optInt("gpsdir", 0)
        )
        sondData[id] = sonda
        if (validPos > 0 && validId > 0) {
            val point = GeoPoint(lat, lon)
            var astmp = 0.0
            var dstmp = 0.0
            if(sonda.vs > 0){ // ascent +
                astmp = sonda.vs
                dstmp = 0.0
            } else { //descent -
                astmp = 0.0
                dstmp = sonda.vs
            }
            if(sonda.active > 0){
                updateSondeInfo("\uD83D\uDFE2 ${sonda.type}", sonda.ser, sonda.frame.toString(),sonda.sats.toString(), astmp, dstmp, sonda.burstKT.toDouble(), sonda.alt, sonda.speed, sonda.temp, sonda.lat, sonda.lon, getmyalt(),sonda.freq,distanceInMeters(GeoPoint(sonda.lat, sonda.lon), myLocationOverlay.myLocation))
            } else {
                updateSondeInfo("\uD83D\uDFE1 ${sonda.type}", sonda.ser, sonda.frame.toString(),sonda.sats.toString(), astmp, dstmp, sonda.burstKT.toDouble(), sonda.alt, sonda.speed, sonda.temp, sonda.lat, sonda.lon, getmyalt(),sonda.freq,distanceInMeters(GeoPoint(sonda.lat, sonda.lon), myLocationOverlay.myLocation))
            }

            if (sondMarkers.containsKey(id)) {
                // Létező marker frissítése
                val marker = sondMarkers[id]
                marker?.position = point
                marker?.title = "${type} $id\nALT: $alt m\nSPD: $speed km/h\nHS: $hs\nVS: $vs"
               // marker?.showInfoWindow()
            } else {
                // Új marker létrehozása
                val newMarker = Marker(map).apply {
                    position = point
                    relatedObject = id
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.songyi)
                    title = "${type} $id\nALT: $alt m\nSPD: $speed km/h\nHS: $hs\nVS: $vs"

                    setOnMarkerClickListener { clickedMarker, _ ->
                        val clickedId = clickedMarker.relatedObject as? String
                        clickedId?.let {
                            sondData[it]?.let { s ->
                                Log.d("SONDA", "Kattintott szonda: ${s.id}, Alt: ${s.alt}, Lat: ${s.lat}, Lon: ${s.lon}")
                            }
                        }
                        clickedMarker.showInfoWindow()
                        showSondaMarkerMenu(clickedMarker)
                        true
                    }

                    map.overlays.add(this)
                }
                sondMarkers[id] = newMarker
            }
        } else {
            // Offline marker, csak title frissítés
            sondMarkers[id]?.title = "${type} $id - Offline"
            sondMarkers[id]?.showInfoWindow()
        }

        map.invalidate()
    }

    private fun drawBurstMarker(point: GeoPoint): Marker {
        val marker = Marker(map)
        marker.position = point
        marker.title = "Burst Pont"
        marker.icon = ContextCompat.getDrawable(this, R.drawable.burst) // saját burst ikon
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        map.overlays.add(marker)
        return marker
    }

    private fun drawLandingMarker(point: GeoPoint): Marker {
        val marker = Marker(map).apply {
            position = point
            title = "Landolási Pont\nID: X123456 Utolsó frame: #27356\nCOORD: 48.369721, 19.843234\nALT: 193m"
            icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.landing)
            setAnchor(Marker.ANCHOR_TOP, Marker.ANCHOR_BOTTOM)
        }
        map.overlays.add(marker)
        map.invalidate()
        currentLandingMarker = marker
        return marker
    }
    private fun saveTawhiriSettingsToJson(ascent: Double, descent: Double, burst: Double) {
        val settings = JSONObject()
        settings.put("ascent_rate", ascent)
        settings.put("descent_rate", descent)
        settings.put("burst_altitude", burst)

        val file = File(getExternalFilesDir(""), "tawhiri_settings.json")
        file.writeText(settings.toString())
        Toast.makeText(this, "Save success!", Toast.LENGTH_SHORT).show()

    }
    private fun loadTawhiriSettingsFromJson(): TawhiriSettings {
        val file = getSettingsFile()

        if (!file.exists()) {
            // Alapértelmezett beállítás mentése, ha nem létezik
            val default = TawhiriSettings()
            saveSettingsToJson(default)
            Toast.makeText(this, "Settings file not found, created default.", Toast.LENGTH_SHORT).show()
            return default
        }

        return try {
            val jsonText = file.readText()
            if (jsonText.isBlank()) {
                Toast.makeText(this, "Settings file is empty, loading defaults.", Toast.LENGTH_SHORT).show()
                return TawhiriSettings()
            }
            val json = JSONObject(jsonText)
            TawhiriSettings(
                ascent_rate = json.optDouble("ascent_rate", 5.0),
                descent_rate = json.optDouble("descent_rate", 5.0),
                burst_altitude = json.optDouble("burst_altitude", 31000.0)
            )
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error reading settings, loading defaults.", Toast.LENGTH_SHORT).show()
            TawhiriSettings() // fallback alapértelmezett
        }
    }
    fun setTileSourceById(id: Int) {
        val tileSource = when (id) {
            R.id.radioMapnik -> TileSourceFactory.MAPNIK
            R.id.radioUSGS -> TileSourceFactory.USGS_SAT
            R.id.radioHikeBike -> TileSourceFactory.HIKEBIKEMAP
            R.id.radioStamenToner -> TileSourceFactory.OPEN_SEAMAP
            R.id.radioStamenWatercolor -> TileSourceFactory.ChartbundleWAC
            R.id.radioEsriSat -> TileSourceFactory.USGS_TOPO
            R.id.radioEsriTopo -> TileSourceFactory.OpenTopo
            R.id.radioBase -> TileSourceFactory.DEFAULT_TILE_SOURCE
            else -> TileSourceFactory.DEFAULT_TILE_SOURCE
        }

        map.setTileSource(tileSource)
        map.invalidate()
    }

    private fun showMapLayerDialog() {
        val dialogView = layoutInflater.inflate(R.layout.map_layer_settings, null)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.radioGroupMapLayers)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Térképréteg kiválasztása")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                val selectedId = radioGroup.checkedRadioButtonId
                setTileSourceById(selectedId)
            }
            .setNegativeButton("Mégse", null)
            .create()

        dialog.show()
    }
    private fun saveSettingsToJson(settings: TawhiriSettings) {
        val file = getSettingsFile()
        val json = JSONObject().apply {
            put("ascent_rate", settings.ascent_rate)
            put("descent_rate", settings.descent_rate)
            put("burst_altitude", settings.burst_altitude)
        }
        file.writeText(json.toString())
        Toast.makeText(this, "Save success!", Toast.LENGTH_SHORT).show()


    }
    private fun getSettingsFile(): File {
        return File(getExternalFilesDir(""), "tawhiri_settings.json")
    }
    data class TawhiriSettings(
        val ascent_rate: Double = 5.0,
        val descent_rate: Double = 5.0,
        val burst_altitude: Double = 30000.0
    )
    private fun showTawhiriSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.predict_settings, null)
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(dialogView)

        val inputAscent = dialogView.findViewById<EditText>(R.id.input_ascent_rate)
        val inputDescent = dialogView.findViewById<EditText>(R.id.input_descent_rate)
        val inputBurst = dialogView.findViewById<EditText>(R.id.input_burst_altitude)

        // 🎯 Beállítások betöltése
        val (ascent, descent, burst) = loadTawhiriSettingsFromJson()
        inputAscent.setText(ascent.toString())
        inputDescent.setText(descent.toString())
        inputBurst.setText(burst.toString())

        dialogView.findViewById<Button>(R.id.button_save_tawhiri)?.setOnClickListener {
            val newAscent = inputAscent.text.toString().toDoubleOrNull() ?: 5.0
            val newDescent = inputDescent.text.toString().toDoubleOrNull() ?: 5.0
            val newBurst = inputBurst.text.toString().toDoubleOrNull() ?: 34000.0

            saveTawhiriSettingsToJson(newAscent, newDescent, newBurst)
            dialog.dismiss()
        }

        dialog.show()
    }

    // --- Tawhiri predikció lekérése, módosított payload-al ---
    private fun fetchPredictionFromTawhiri(launch_lat: Double, launch_lon: Double, launch_alt: Double, ascent_rate: Double, descent_rate: Double, burst_alt: Double) {
        val baseUrl = "https://api.v2.sondehub.org/tawhiri?"
        val isoTime = java.time.Instant.now().toString()
        sondLocationMarker?.showInfoWindow()
        val urlString = baseUrl + listOf(
            "launch_latitude=$launch_lat",
            "launch_longitude=$launch_lon",
            "launch_altitude=$launch_alt",
            "launch_datetime=$isoTime",
            "ascent_rate=$ascent_rate",
            "descent_rate=$descent_rate",
            "burst_altitude=$burst_alt",
            "profile=standard_profile"
        ).joinToString("&")

        Thread {
            try {
                val url = java.net.URL(urlString)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                val responseCode = conn.responseCode
                if (responseCode != 200) {
                    throw Exception("HTTP hiba: $responseCode")
                }
                val response = conn.inputStream.bufferedReader().readText()
                val (trajectoryPoints, burstPoint, landingPoint) = parsePredictionResponse(response)

                runOnUiThread {
                    clearPredictionOverlays()

                    val polyline = drawPredictionPath(trajectoryPoints)

                    val burstMarker = burstPoint?.let { drawBurstMarker(it) }

                    val landingMarker = landingPoint?.let { drawLandingMarker(it) }

                    addPredictionOverlays(polyline, burstMarker, landingMarker)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Hiba a Tawhiri lekérés során: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    // --- Változtatott válasz feldolgozás, burst és landing pontokkal ---
    private fun parsePredictionResponse(response: String?): Triple<List<GeoPoint>, GeoPoint?, GeoPoint?> {
        val trajectoryPoints = mutableListOf<GeoPoint>()
        var burstPoint: GeoPoint? = null
        var landingPoint: GeoPoint? = null

        if (response.isNullOrBlank()) {
            // Üres vagy null string esetén nem próbálkozunk
            return Triple(emptyList(), null, null)
        }

        try {
            val json = JSONObject(response)
            val predictionArray = json.optJSONArray("prediction") ?: return Triple(emptyList(), null, null)

            for (i in 0 until predictionArray.length()) {
                val stageObj = predictionArray.optJSONObject(i) ?: continue
                val stage = stageObj.optString("stage", "")

                val trajectory = stageObj.optJSONArray("trajectory") ?: continue

                for (j in 0 until trajectory.length()) {
                    val point = trajectory.optJSONObject(j) ?: continue
                    val lat = point.optDouble("latitude", Double.NaN)
                    val lon = point.optDouble("longitude", Double.NaN)
                    val alt = point.optDouble("altitude", Double.NaN)

                    // Csak akkor adjuk hozzá, ha nem NaN az érték
                    if (!lat.isNaN() && !lon.isNaN() && !alt.isNaN()) {
                        trajectoryPoints.add(GeoPoint(lat, lon, alt))
                    }
                }

                if (trajectory.length() > 0) {
                    val lastPoint = trajectory.optJSONObject(trajectory.length() - 1)
                    lastPoint?.let {
                        val lat = it.optDouble("latitude", Double.NaN)
                        val lon = it.optDouble("longitude", Double.NaN)
                        val alt = it.optDouble("altitude", Double.NaN)
                        if (!lat.isNaN() && !lon.isNaN() && !alt.isNaN()) {
                            val geoPoint = GeoPoint(lat, lon, alt)
                            if (stage == "ascent") {
                                burstPoint = geoPoint
                            } else if (stage == "descent") {
                                landingPoint = geoPoint
                            }
                        }
                    }
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            // Ha hiba van, akkor is visszaadjuk a jelenlegi eredményt (akár üres listát)
        }

        return Triple(trajectoryPoints, burstPoint, landingPoint)
    }

    // --- Megjelenítés a térképen ---
    private fun drawPredictionPath(points: List<GeoPoint>): org.osmdroid.views.overlay.Polyline {
        val polyline = org.osmdroid.views.overlay.Polyline().apply {
            setPoints(points)
            color = Color.RED
            width = 6f
        }
        map.overlays.add(polyline)
        map.invalidate()
        return polyline
    }

    private fun debugPrintMapOverlays(tag: String) {
        Log.d(tag, "Map overlays count: ${map.overlays.size}")
        map.overlays.forEachIndexed { index, overlay ->
            Log.d(tag, "Overlay #$index: $overlay")
        }
    }

    private fun clearPredictionOverlays() {
        debugPrintMapOverlays("MapDebug-BeforeRemoval")

        for (overlay in predictionOverlays) {
            var removedAny = false
            do {
                val removed = map.overlays.remove(overlay)
                removedAny = removedAny || removed
            } while (removed)
            Log.d("MapDebug", "Removing all instances of overlay: $overlay success at least once: $removedAny")
        }
        predictionOverlays.clear()

        map.invalidate()

        debugPrintMapOverlays("MapDebug-AfterRemoval")
    }

    private fun addPredictionOverlays(polyline: Polyline, burstMarker: Marker?, landingMarker: Marker?) {
        // Hozzáadás listához és térképhez is
        map.overlays.add(polyline)
        predictionOverlays.add(polyline)

        burstMarker?.let {
            map.overlays.add(it)
            predictionOverlays.add(it)
        }

        landingMarker?.let {
            map.overlays.add(it)
            predictionOverlays.add(it)
        }

        map.invalidate()
    }

    private fun getMidPointOnPolyline(points: List<GeoPoint>): GeoPoint? {
        if (points.isEmpty()) return null
        if (points.size == 1) return points[0]

        // Kiszámoljuk a teljes vonal hosszát
        var totalDistance = 0.0
        val distances = mutableListOf<Double>()
        for (i in 0 until points.size - 1) {
            val dist = points[i].distanceToAsDouble(points[i + 1])
            totalDistance += dist
            distances.add(totalDistance)
        }

        val halfDistance = totalDistance / 2.0

        // Megkeressük, melyik szakaszban van a középpont
        for (i in distances.indices) {
            if (distances[i] >= halfDistance) {
                val prevDistance = if (i == 0) 0.0 else distances[i - 1]
                val segmentFraction = (halfDistance - prevDistance) / (distances[i] - prevDistance)

                val lat = points[i].latitude + segmentFraction * (points[i + 1].latitude - points[i].latitude)
                val lon = points[i].longitude + segmentFraction * (points[i + 1].longitude - points[i].longitude)
                val alt = points[i].altitude + segmentFraction * (points[i + 1].altitude - points[i].altitude)

                return GeoPoint(lat, lon, alt)
            }
        }
        return points.last()
    }

// TAWHIRI ---- END -----
    /**
     * Háttér elhomályosítása PopupWindow mögött
     */
    private fun dimBehind(popupWindow: PopupWindow) {
        try {
            val container = popupWindow.contentView.parent as? View ?: return
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val p = container.layoutParams as WindowManager.LayoutParams
            p.flags = p.flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
            p.dimAmount = 0.3f
            wm.updateViewLayout(container, p)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    private fun setupSondeInfoPanel() {
        val infoPanel = findViewById<LinearLayout>(R.id.sondeInfoPanel)

        textSeriall = infoPanel.findViewById(R.id.textSerial)
        textBurst = infoPanel.findViewById(R.id.textBurst)
        textSats = infoPanel.findViewById(R.id.textSats)
        textFrame = infoPanel.findViewById(R.id.textFrame)
        textSelfAlt = infoPanel.findViewById(R.id.textSelfAlt)
        textAscent = infoPanel.findViewById(R.id.textAscent)
        textDescent = infoPanel.findViewById(R.id.textDescent)
        textAltitude = infoPanel.findViewById(R.id.textAltitude)
        textSpeed = infoPanel.findViewById(R.id.textSpeed)
        textTemperature = infoPanel.findViewById(R.id.textTemperature)
        textCoordinates = infoPanel.findViewById(R.id.textCoordinates)
        textFreq = infoPanel.findViewById(R.id.textFreq)
        textConnect = infoPanel.findViewById(R.id.textConnect)
        textDistance = infoPanel.findViewById(R.id.textDistance)
        textTime = infoPanel.findViewById(R.id.textTime)

        val layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = 20
            marginEnd = 20
        }
        if(infoPanel.visibility != View.VISIBLE){
            infoPanel.visibility = View.VISIBLE
        } else {
            infoPanel.visibility = View.GONE
        }

       // val rootLayout = findViewById<FrameLayout>(android.R.id.content)
       // rootLayout.addView(infoPanel, layoutParams)
    }
    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        val missingPermissions = permissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), PERMISSION_REQUEST_CODE)

        } else {
            //startScan()
        }
    }

    private fun requestPermissionsIfNecessary(permissions: Array<String>) {
        val permissionsToRequest = ArrayList<String>()
        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission)
            }
        }
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }


    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_home -> {
                Toast.makeText(this, "Infó panel kiválasztva", Toast.LENGTH_SHORT).show()
                setupSondeInfoPanel()
            }
            R.id.nav_map_layers -> {
                Toast.makeText(this, "Map layers kiválasztva", Toast.LENGTH_SHORT).show()
                showMapLayerDialog()
            }
            R.id.nav_customize -> {
                Toast.makeText(this, "Kinézet és megjelenés kiválasztva", Toast.LENGTH_SHORT).show()
            }
            R.id.nav_tawhiri_settings -> {
                // Toast.makeText(this, "Tawhiri Predict Beállítások kiválasztva", Toast.LENGTH_SHORT).show()
                //setContentView(R.layout.predict_settings)
                showTawhiriSettingsDialog()
            }
            R.id.nav_help -> {
                Toast.makeText(this, "Súgó kiválasztva", Toast.LENGTH_SHORT).show()
            }
            R.id.nav_about -> {
                Toast.makeText(this, "Névjegy kiválasztva", Toast.LENGTH_SHORT).show()
            }
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

/** elavult funkció **
    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
*/

    override fun onResume() {
        super.onResume()
        map.onResume()
        compassOverlay.enableCompass()
        myLocationOverlay.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
        compassOverlay.disableCompass()
        myLocationOverlay.onPause()
    }
}
