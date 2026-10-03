package com.kellian.wifilunettes

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.location.LocationManager
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

@Suppress("DEPRECATION")
class MainActivity : Activity() {

    /** Un réseau connu : nom + mot de passe ("" = réseau ouvert ou enregistré système). */
    data class Reseau(val ssid: String, val password: String, val systemId: Int = -1)

    private val vert = Color.rgb(80, 255, 120)
    private val gris = Color.rgb(140, 140, 140)

    private lateinit var wifi: WifiManager
    private lateinit var header: TextView
    private lateinit var status: TextView
    private lateinit var diag: TextView
    private lateinit var list: LinearLayout
    private val buttons = mutableListOf<Button>()

    private var reseauxFichier: List<Reseau> = emptyList()
    private var reseauxSysteme: List<Reseau> = emptyList()

    private val handler = Handler(Looper.getMainLooper())
    private var suivi: Runnable? = null

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) = afficher()
    }

    // ------------------------------------------------------------------ cycle de vie

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        construireEcran()
        reseauxFichier = lireFichier()

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(scanReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        rafraichir()
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(scanReceiver) } catch (_: Exception) {}
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        rafraichir()
    }

    // ------------------------------------------------------------------ interface

    private fun construireEcran() {
        val racine = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(24, 24, 24, 24)
        }
        header = texte(22f, vert, gras = true)
        status = texte(18f, Color.WHITE)
        diag = texte(13f, gris)

        val btnRefresh = bouton("↻  ACTUALISER").apply { setOnClickListener { rafraichir() } }

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply {
            addView(list)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        racine.addView(header)
        racine.addView(status)
        racine.addView(btnRefresh)
        racine.addView(scroll)
        racine.addView(diag)
        setContentView(racine)
        buttons.add(btnRefresh)
    }

    private fun texte(taille: Float, couleur: Int, gras: Boolean = false) = TextView(this).apply {
        textSize = taille
        setTextColor(couleur)
        setPadding(0, 6, 0, 6)
        if (gras) typeface = Typeface.DEFAULT_BOLD
    }

    private fun bouton(label: String) = Button(this).apply {
        text = label
        textSize = 18f
        isAllCaps = false
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.rgb(25, 25, 25))
        isFocusable = true
        isFocusableInTouchMode = true
        setOnFocusChangeListener { v, f ->
            v.setBackgroundColor(if (f) Color.rgb(0, 90, 40) else Color.rgb(25, 25, 25))
        }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 6, 0, 6) }
    }

    // ------------------------------------------------------------------ données

    /** Lit assets/reseaux.txt — une ligne par réseau : NOM;motdepasse */
    private fun lireFichier(): List<Reseau> = try {
        assets.open("reseaux.txt").bufferedReader().readLines()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
            .map { ligne ->
                val i = ligne.indexOf(';')
                if (i < 0) Reseau(ligne.trim(), "")
                else Reseau(ligne.substring(0, i).trim(), ligne.substring(i + 1))
            }
    } catch (_: Exception) { emptyList() }

    /** Test : réseaux déjà enregistrés dans le système des lunettes (souvent vide sur Android 10+). */
    private fun lireSysteme(): List<Reseau> = try {
        wifi.configuredNetworks.orEmpty()
            .mapNotNull { c -> c.SSID?.trim('"')?.takeIf { it.isNotBlank() }?.let { Reseau(it, "", c.networkId) } }
    } catch (_: Exception) { emptyList() }

    private fun reseauxConnus(): List<Reseau> {
        val parNom = LinkedHashMap<String, Reseau>()
        reseauxFichier.forEach { parNom[it.ssid] = it }
        reseauxSysteme.forEach { s ->
            val f = parNom[s.ssid]
            parNom[s.ssid] = f?.copy(systemId = s.systemId) ?: s
        }
        return parNom.values.toList()
    }

    private fun ssidActuel(): String? {
        val s = wifi.connectionInfo?.ssid?.trim('"') ?: return null
        return if (s.isBlank() || s == "<unknown ssid>") null else s
    }

    // ------------------------------------------------------------------ actions

    private fun rafraichir() {
        if (!wifi.isWifiEnabled) {
            try { wifi.isWifiEnabled = true } catch (_: Exception) {}
        }
        reseauxSysteme = lireSysteme()
        try { wifi.startScan() } catch (_: Exception) {}
        afficher()
    }

    private fun afficher() {
        val actuel = ssidActuel()
        header.text = if (actuel != null) "Connecté : $actuel" else "Non connecté"

        val scan = try { wifi.scanResults.orEmpty() } catch (_: Exception) { emptyList() }
        val signal = scan.filter { !it.SSID.isNullOrBlank() }
            .groupBy { it.SSID }
            .mapValues { (_, l) -> l.maxOf { it.level } }

        val tries = reseauxConnus().sortedWith(
            compareByDescending<Reseau> { it.ssid == actuel }
                .thenByDescending { signal.containsKey(it.ssid) }
                .thenByDescending { signal[it.ssid] ?: -999 }
        )

        // garder le focus sur le même réseau après rafraîchissement
        val focusNom = (currentFocus as? Button)?.tag as? String

        list.removeAllViews()
        buttons.retainAll { it.tag == null }
        var aFocus: Button? = null

        if (tries.isEmpty()) {
            list.addView(texte(16f, gris).apply {
                text = "Aucun réseau dans la liste.\nAjoute-les dans le secret GitHub WIFI_LIST puis recompile."
            })
        }

        for (r in tries) {
            val niveau = signal[r.ssid]
            val label = when {
                r.ssid == actuel -> "✓  ${r.ssid}   (connecté)"
                niveau != null -> "${barres(niveau)}  ${r.ssid}"
                else -> "      ${r.ssid}   – hors de portée"
            }
            val b = bouton(label).apply {
                tag = r.ssid
                if (niveau == null && r.ssid != actuel) setTextColor(gris)
                setOnClickListener { connecter(r) }
            }
            list.addView(b)
            buttons.add(b)
            if (r.ssid == focusNom) aFocus = b
        }

        (aFocus ?: buttons.getOrNull(1) ?: buttons.firstOrNull())?.let {
            if (currentFocus == null || aFocus != null) it.requestFocus()
        }

        val loc = try {
            (getSystemService(Context.LOCATION_SERVICE) as LocationManager)
                .isProviderEnabled(LocationManager.NETWORK_PROVIDER) ||
                (getSystemService(Context.LOCATION_SERVICE) as LocationManager)
                    .isProviderEnabled(LocationManager.GPS_PROVIDER)
        } catch (_: Exception) { true }

        diag.text = buildString {
            append("Android ${Build.VERSION.RELEASE} · liste : ${reseauxFichier.size} · ")
            append("système : ${reseauxSysteme.size} · à portée : ${signal.size}")
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                append("\n⚠ Permission localisation refusée : le scan ne marchera pas.")
            else if (signal.isEmpty() && !loc)
                append("\n⚠ Localisation désactivée : aucun réseau détecté.")
        }
    }

    private fun barres(dbm: Int): String = when (WifiManager.calculateSignalLevel(dbm, 4)) {
        3 -> "▮▮▮▮"
        2 -> "▮▮▮▯"
        1 -> "▮▮▯▯"
        else -> "▮▯▯▯"
    }

    private fun connecter(r: Reseau) {
        if (r.ssid == ssidActuel()) {
            status.text = "Déjà connecté à ${r.ssid}"
            return
        }
        status.text = "Connexion à ${r.ssid}…"
        if (!wifi.isWifiEnabled) try { wifi.isWifiEnabled = true } catch (_: Exception) {}

        var id = -1
        // 1) réseau qu'on a mot de passe pour : on (re)crée la config, ça garantit le bon mot de passe
        if (r.password.isNotEmpty() || r.systemId == -1) {
            id = try { wifi.addNetwork(config(r)) } catch (_: Exception) { -1 }
        }
        // 2) sinon / échec : réseau déjà enregistré dans le système
        if (id == -1) id = r.systemId
        if (id == -1) id = lireSysteme().firstOrNull { it.ssid == r.ssid }?.systemId ?: -1

        val ok = id != -1 && try {
            wifi.disconnect()
            val e = wifi.enableNetwork(id, true)
            wifi.reconnect()
            e
        } catch (_: Exception) { false }

        if (!ok) {
            planB(r)
            return
        }
        suivre(r.ssid)
    }

    private fun config(r: Reseau) = WifiConfiguration().apply {
        SSID = "\"${r.ssid}\""
        if (r.password.isEmpty()) {
            allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
        } else {
            preSharedKey = "\"${r.password}\""
        }
    }

    /** Si la connexion directe est refusée : on propose le réseau au système (Android 10+). */
    private fun planB(r: Reseau) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && r.password.length in 8..63) {
            try {
                val s = WifiNetworkSuggestion.Builder()
                    .setSsid(r.ssid)
                    .setWpa2Passphrase(r.password)
                    .build()
                wifi.removeNetworkSuggestions(listOf(s))
                val res = wifi.addNetworkSuggestions(listOf(s))
                status.text = if (res == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS)
                    "Connexion directe refusée.\nRéseau proposé au système : il s'y connectera tout seul à portée."
                else "Échec (code $res) : connexion refusée par le système."
                suivre(r.ssid)
                return
            } catch (e: Exception) {
                status.text = "Échec : ${e.message}"
                return
            }
        }
        status.text = "Échec : le système refuse la connexion à ${r.ssid}."
    }

    /** Vérifie pendant 15 s si la connexion aboutit. */
    private fun suivre(ssid: String) {
        suivi?.let { handler.removeCallbacks(it) }
        var t = 0
        val r = object : Runnable {
            override fun run() {
                t++
                val actuel = ssidActuel()
                val ip = wifi.connectionInfo?.ipAddress ?: 0
                when {
                    actuel == ssid && ip != 0 -> {
                        status.text = "Connecté à $ssid ✓"
                        afficher()
                    }
                    t >= 15 -> {
                        status.text = "Échec : $ssid ne répond pas.\nVérifie le mot de passe ou la portée."
                        afficher()
                    }
                    else -> {
                        status.text = "Connexion à $ssid… ${t}s"
                        handler.postDelayed(this, 1000)
                    }
                }
            }
        }
        suivi = r
        handler.postDelayed(r, 1000)
    }

    // ------------------------------------------------------------------ pavé tactile des lunettes

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val i = buttons.indexOf(currentFocus)
        val cible = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_TAB ->
                buttons.getOrNull(i + 1)
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP ->
                buttons.getOrNull(i - 1)
            else -> null
        }
        if (cible != null) {
            cible.requestFocus()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            (currentFocus as? View)?.performClick()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
