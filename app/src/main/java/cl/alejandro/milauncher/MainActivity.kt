package cl.alejandro.milauncher

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    // ---------- Vistas ----------
    private lateinit var gauge: SpeedGaugeView
    private lateinit var gpsStatus: TextView
    private lateinit var albumArt: ImageView
    private lateinit var musicApp: TextView
    private lateinit var musicTitle: TextView
    private lateinit var musicArtist: TextView
    private lateinit var musicHint: TextView
    private lateinit var btnPlay: ImageButton
    private lateinit var slotsContainer: LinearLayout

    private var apps: List<AppEntry> = emptyList()
    private var slotsVersion = -1   // versión de Apps con la que se dibujó la fila de abajo
    private val handler = Handler(Looper.getMainLooper())

    /** Deslizar hacia la izquierda (de derecha a izquierda) en cualquier parte abre el cajón de apps. */
    private val swipe by lazy { SwipeDetector(this, toRight = false) { openAppsDrawer() } }

    // ---------- Logo al encender ----------
    private val splash by lazy { Splash(this) }
    private var resumed = false
    private var screenOnAt = 0L   // cuándo se encendió la pantalla (al volver del reposo con la llave)

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> {
                    screenOnAt = SystemClock.elapsedRealtime()
                    if (resumed) showSplashIfPending()
                }
                Intent.ACTION_SCREEN_OFF -> splash.cancel()
            }
        }
    }

    // ---------- Ciclo de vida ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gauge = findViewById(R.id.gauge)
        gpsStatus = findViewById(R.id.gpsStatus)
        albumArt = findViewById(R.id.albumArt)
        albumArt.clipToOutline = true
        musicApp = findViewById(R.id.musicApp)
        musicTitle = findViewById(R.id.musicTitle)
        musicArtist = findViewById(R.id.musicArtist)
        musicHint = findViewById(R.id.musicHint)
        btnPlay = findViewById(R.id.btnPlay)
        slotsContainer = findViewById(R.id.slots)

        findViewById<LinearLayout>(R.id.infoCard).setOnClickListener { onInfoCardClicked() }
        findViewById<LinearLayout>(R.id.musicCard).setOnClickListener { onMusicCardClicked() }
        findViewById<ImageButton>(R.id.btnPrev).setOnClickListener { previous() }
        findViewById<ImageButton>(R.id.btnNext).setOnClickListener { next() }
        btnPlay.setOnClickListener { playPause() }

        showFirstRunHint()

        // Pantalla encendida / apagada: la radio "despierta" al poner la llave aunque no se reinicie.
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        // Son avisos del sistema (protegidos); ninguna otra app puede enviarlos.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
        if (Splash.isNewBoot(this)) splash.show()
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
        }
        super.onDestroy()
    }

    /** Muestra el logo si la pantalla se acaba de encender (y no se mostró ya). */
    private fun showSplashIfPending() {
        if (screenOnAt != 0L && SystemClock.elapsedRealtime() - screenOnAt < 5000) {
            screenOnAt = 0L
            splash.show()
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        showSplashIfPending()
        buildSlots()
        startGps()
        startMusic()
    }

    override fun onPause() {
        super.onPause()
        resumed = false
        stopGps()
        stopMusic()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // Con el logo en pantalla, un toque solo lo salta (no abre el cajón ni aprieta botones).
        if (splash.isShowing) return super.dispatchTouchEvent(ev)
        return swipe.handle(ev) { super.dispatchTouchEvent(it) } || super.dispatchTouchEvent(ev)
    }

    private fun openAppsDrawer() {
        startActivity(Intent(this, AppsActivity::class.java))
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.slide_in_right, R.anim.stay)
    }

    /** En la pantalla de inicio, "Atrás" no hace nada. */
    @SuppressLint("MissingSuperCall")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
    }

    // =====================================================================
    // Accesos rápidos (fila inferior)
    // =====================================================================

    /** Rehace la fila de abajo solo si cambiaron las apps instaladas (o si se pide con force). */
    private fun buildSlots(force: Boolean = false) {
        apps = Apps.load(this)
        if (!force && slotsVersion == Apps.version) return
        slotsVersion = Apps.version
        slotsContainer.removeAllViews()

        for (slot in Slots.all) {
            val view = layoutInflater.inflate(R.layout.item_slot, slotsContainer, false)
            val icon = view.findViewById<ImageView>(R.id.slotIcon)
            val label = view.findViewById<TextView>(R.id.slotLabel)
            val entry = Slots.resolve(this, slot, apps)

            if (entry != null) {
                icon.setImageDrawable(Apps.icon(this, entry.component))
                label.text = Slots.labelFor(this, slot, entry)
                view.setOnClickListener { Apps.launch(this, entry.component) }
            } else {
                icon.setImageResource(R.drawable.ic_add)
                label.text = slot.label
                view.setOnClickListener { showPicker(slot) }
            }
            view.setOnLongClickListener { showPicker(slot); true }
            slotsContainer.addView(view)
        }

        // Último botón: todas las apps
        val appsButton = layoutInflater.inflate(R.layout.item_slot, slotsContainer, false)
        appsButton.findViewById<ImageView>(R.id.slotIcon).setImageResource(R.drawable.ic_apps)
        appsButton.findViewById<TextView>(R.id.slotLabel).setText(R.string.apps)
        appsButton.setOnClickListener { openAppsDrawer() }
        slotsContainer.addView(appsButton)
    }

    private fun showPicker(slot: Slot) {
        val list = apps
        val names: Array<CharSequence> =
            (listOf<CharSequence>(getString(R.string.automatic)) + list.map { it.label }).toTypedArray()

        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(getString(R.string.pick_app_title, slot.label))
            .setItems(names) { _, which ->
                Slots.setManual(this, slot, if (which == 0) null else list[which - 1].component)
                buildSlots(force = true)
            }
            .show()
    }

    private fun showFirstRunHint() {
        val prefs = getSharedPreferences("general", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("hint_shown", false)) {
            Toast.makeText(this, R.string.slot_hint, Toast.LENGTH_LONG).show()
            prefs.edit().putBoolean("hint_shown", true).apply()
        }
    }

    // =====================================================================
    // Velocímetro GPS
    // =====================================================================

    private var locationManager: LocationManager? = null
    private var listeningGps = false
    private var lastFixTime = 0L
    private var askedLocation = false

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) = onLocation(location)

        // Se implementan todos los métodos porque en Android antiguo (7–10) son obligatorios.
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        }

        override fun onProviderEnabled(provider: String) {}

        override fun onProviderDisabled(provider: String) = showGpsSearching()
    }

    /** Si pasan unos segundos sin señal, vuelve a "--". */
    private val gpsWatchdog = object : Runnable {
        override fun run() {
            if (listeningGps && SystemClock.elapsedRealtime() - lastFixTime > 6000) {
                showGpsSearching()
            }
            handler.postDelayed(this, 3000)
        }
    }

    private fun hasLocationPermission() =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /**
     * Android 12+ deja elegir "ubicación aproximada". Con eso el sistema entrega posiciones
     * borrosas y sin velocidad, así que el velocímetro no puede funcionar: hay que pedir la precisa.
     */
    private fun hasOnlyApproximateLocation() =
        !hasLocationPermission() &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Android ya no muestra la pregunta (se rechazó dos veces): solo se puede activar en Ajustes. */
    private var locationBlocked: Boolean
        get() = getSharedPreferences("general", Context.MODE_PRIVATE).getBoolean("location_blocked", false)
        set(value) = getSharedPreferences("general", Context.MODE_PRIVATE).edit()
            .putBoolean("location_blocked", value).apply()

    /** En Android 12+ hay que pedir ambas: la precisa sola es rechazada por el sistema. */
    private fun requestLocation() {
        askedLocation = true
        requestPermissions(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            REQ_LOCATION
        )
    }

    @SuppressLint("MissingPermission")
    private fun startGps() {
        if (listeningGps) return
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationManager = lm

        if (!lm.allProviders.contains(LocationManager.GPS_PROVIDER)) {
            gauge.setSpeed(null)
            gpsStatus.setText(R.string.gps_unavailable)
            return
        }
        if (!hasLocationPermission()) {
            gauge.setSpeed(null)
            if (hasOnlyApproximateLocation()) {
                // El usuario eligió "aproximada" a propósito: no se le insiste solo, se espera a que toque.
                gpsStatus.setText(R.string.gps_approximate)
                return
            }
            gpsStatus.setText(R.string.gps_no_permission)
            if (!askedLocation && !locationBlocked) requestLocation()
            return
        }
        locationBlocked = false
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locationListener, Looper.getMainLooper())
            listeningGps = true
            showGpsSearching()
            handler.post(gpsWatchdog)
        } catch (e: Exception) {
            gpsStatus.setText(R.string.gps_unavailable)
        }
    }

    private fun stopGps() {
        handler.removeCallbacks(gpsWatchdog)
        if (listeningGps) {
            try {
                locationManager?.removeUpdates(locationListener)
            } catch (e: Exception) {
            }
        }
        listeningGps = false
    }

    private fun onLocation(location: Location) {
        lastFixTime = SystemClock.elapsedRealtime()
        val kmh = if (location.hasSpeed()) location.speed * 3.6f else 0f
        // Bajo 2 km/h el GPS "baila"; se muestra 0.
        gauge.setSpeed(if (kmh < 2f) 0f else kmh)
        gpsStatus.setText(R.string.gps_ok)
    }

    private fun showGpsSearching() {
        gauge.setSpeed(null)
        gpsStatus.setText(R.string.gps_searching)
    }

    private fun onInfoCardClicked() {
        if (hasLocationPermission()) return
        if (locationBlocked) openLocationSettings() else requestLocation()
    }

    /** Abre la ficha de la app para activar la ubicación (y "Usar ubicación precisa") a mano. */
    private fun openLocationSettings() {
        Toast.makeText(this, R.string.location_settings_hint, Toast.LENGTH_LONG).show()
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_LOCATION) return
        // grantResults vacío = la pregunta se cerró sin respuesta (no cuenta como rechazo).
        if (grantResults.isNotEmpty() && !hasLocationPermission() &&
            !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
        ) {
            // Android ya no volverá a preguntar: el próximo toque abre Ajustes.
            locationBlocked = true
        }
        startGps()
    }

    // =====================================================================
    // Música
    // =====================================================================

    private var sessionManager: MediaSessionManager? = null
    private var controller: MediaController? = null
    private val listenerComponent by lazy { ComponentName(this, MediaListenerService::class.java) }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        useControllers(list ?: emptyList())
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = updateMusicUi()
        override fun onPlaybackStateChanged(state: PlaybackState?) = refreshSessions()
        override fun onSessionDestroyed() = refreshSessions()
    }

    private fun hasNotificationAccess(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return flat.split(":").any { ComponentName.unflattenFromString(it)?.packageName == packageName }
    }

    private fun startMusic() {
        if (!hasNotificationAccess()) {
            setController(null)
            return
        }
        val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        sessionManager = msm
        try {
            msm.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent)
            useControllers(msm.getActiveSessions(listenerComponent))
        } catch (e: SecurityException) {
            setController(null)
        }
    }

    private fun stopMusic() {
        try {
            sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (e: Exception) {
        }
        setController(null)
    }

    private fun refreshSessions() {
        val msm = sessionManager
        if (msm == null) {
            updateMusicUi()
            return
        }
        try {
            useControllers(msm.getActiveSessions(listenerComponent))
        } catch (e: SecurityException) {
            setController(null)
        }
    }

    /** Elige la app que está sonando; si ninguna suena, la última activa. */
    private fun useControllers(list: List<MediaController>) {
        val best = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
        if (best?.sessionToken != controller?.sessionToken) setController(best) else updateMusicUi()
    }

    private fun setController(newController: MediaController?) {
        controller?.unregisterCallback(controllerCallback)
        controller = newController
        newController?.registerCallback(controllerCallback)
        updateMusicUi()
    }

    private fun isPlaying(): Boolean {
        val state = controller?.playbackState?.state
        return state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
    }

    private fun updateMusicUi() {
        val c = controller
        if (c == null) {
            musicApp.text = ""
            musicTitle.setText(R.string.music_nothing)
            musicArtist.text = ""
            showArtPlaceholder()
            btnPlay.setImageResource(R.drawable.ic_play)
            musicHint.setText(if (hasNotificationAccess()) R.string.music_hint_open else R.string.music_hint_access)
            return
        }

        val md = c.metadata
        musicApp.text = Apps.labelOfPackage(this, c.packageName)
        musicTitle.text = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: getString(R.string.music_nothing)
        musicArtist.text = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: ""

        val art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (art != null) {
            albumArt.setPadding(0, 0, 0, 0)
            albumArt.scaleType = ImageView.ScaleType.CENTER_CROP
            albumArt.setImageBitmap(art)
        } else {
            showArtPlaceholder()
        }

        btnPlay.setImageResource(if (isPlaying()) R.drawable.ic_pause else R.drawable.ic_play)
        musicHint.text = ""
    }

    private fun showArtPlaceholder() {
        val pad = (36 * resources.displayMetrics.density).toInt()
        albumArt.setPadding(pad, pad, pad, pad)
        albumArt.scaleType = ImageView.ScaleType.CENTER_INSIDE
        albumArt.setImageResource(R.drawable.ic_music)
    }

    private fun playPause() {
        val c = controller
        if (c == null) {
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        } else if (isPlaying()) {
            c.transportControls.pause()
        } else {
            c.transportControls.play()
        }
    }

    private fun next() {
        val c = controller
        if (c == null) sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) else c.transportControls.skipToNext()
    }

    private fun previous() {
        val c = controller
        if (c == null) sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) else c.transportControls.skipToPrevious()
    }

    /** Sin acceso a notificaciones igual se puede controlar la música, como si fueran los botones del volante. */
    private fun sendMediaKey(code: Int) {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private fun onMusicCardClicked() {
        if (!hasNotificationAccess()) {
            try {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                Toast.makeText(this, R.string.music_access_missing, Toast.LENGTH_LONG).show()
            }
            return
        }
        val c = controller
        if (c != null) {
            Apps.launchPackage(this, c.packageName)
        } else {
            Slots.all.firstOrNull { it.key == "music" }
                ?.let { Slots.resolve(this, it, apps) }
                ?.let { Apps.launch(this, it.component) }
        }
    }

    companion object {
        private const val REQ_LOCATION = 10
    }
}
