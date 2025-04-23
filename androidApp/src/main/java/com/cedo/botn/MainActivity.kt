package com.cedo.botn

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.*
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private var ccmToken: String? = null
    private var iotToken: String? = null
    private var currentLocation: Location? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var country: String = ""
    private var state: String = ""
    private var city: String = ""
    private var street: String = ""
    private var streetNumber: String = ""
    private var activeIotReportID: String? = null
    private var reportStarted = false
    private var ccmRecordID: String? = null

    private lateinit var nameInput: EditText
    private lateinit var phoneInput: EditText
    private lateinit var emergencyWebView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        checkLocationPermission()

        nameInput = findViewById(R.id.nameInput)
        phoneInput = findViewById(R.id.phoneInput)
        emergencyWebView = findViewById(R.id.emergencyWebView)
        emergencyWebView.settings.javaScriptEnabled = true
        emergencyWebView.webViewClient = WebViewClient()

        val prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        nameInput.setText(prefs.getString("saved_name", ""))
        phoneInput.setText(prefs.getString("saved_phone", ""))

        findViewById<Button>(R.id.btnEmergency).setOnClickListener {
            val phone = phoneInput.text.toString()
            if (!phone.startsWith("+")) {
                Toast.makeText(this, "Incluye el '+' y el código de país en el número", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val editor = prefs.edit()
            editor.putString("saved_name", nameInput.text.toString())
            editor.putString("saved_phone", phone)
            editor.apply()

            Toast.makeText(this, "Emergencia enviada", Toast.LENGTH_SHORT).show()
            startLocationUpdates()
        }

        findViewById<Button>(R.id.btnStopEmergency).setOnClickListener {
            activeIotReportID?.let {
                stopIoTReport(it)
            }
        }

        intent?.data?.let { uri: Uri ->
            if (uri.host == "c-all.carbynenet.com") {
                emergencyWebView.loadUrl(uri.toString())
                emergencyWebView.visibility = WebView.VISIBLE
            }
        }
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        }
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.create().apply {
            interval = 10000
            fastestInterval = 5000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                currentLocation = locationResult.lastLocation
                currentLocation?.let { location ->
                    val geocoder = Geocoder(this@MainActivity, Locale.getDefault())
                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    addresses?.firstOrNull()?.let {
                        country = it.countryName ?: ""
                        state = it.adminArea ?: ""
                        city = it.locality ?: ""
                        street = it.thoroughfare ?: ""
                        streetNumber = it.subThoroughfare ?: ""
                    }
                    if (!reportStarted) {
                        getTokensAndSendRequests()
                        reportStarted = true
                        Handler(Looper.getMainLooper()).postDelayed({
                            activeIotReportID?.let {
                                stopIoTReport(it)
                            }
                        }, 5 * 60 * 1000)
                    } else {
                        activeIotReportID?.let {
                            updateIoTEntities(it)
                        }
                    }
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
    }

    private fun getTokensAndSendRequests() {
        getTokenCCM { token ->
            ccmToken = token
            getTokenIoT { tokenIoT ->
                iotToken = tokenIoT
                if (!ccmToken.isNullOrEmpty() && !iotToken.isNullOrEmpty()) {
                    sendToCCM(ccmToken!!)
                }
            }
        }
    }

    private fun getTokenCCM(callback: (String) -> Unit) {
        val client = OkHttpClient()
        val body = FormBody.Builder()
            .add("client_id", "external-client_984bb43e-64ec-4bdf-8c75-27db17813887_be43294c-ca46-49c3-9431-28176ef61104")
            .add("client_secret", "ce77c702-3220-4726-83da-4b02d9f34aa8")
            .add("grant_type", "client_credentials")
            .add("scope", "openid")
            .build()

        val request = Request.Builder()
            .url("https://iam-service-eu-west-1.carbyneapi.com/auth/realms/carbyne/protocol/openid-connect/token")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val token = JSONObject(response.body?.string() ?: "").getString("access_token")
                    callback(token)
                } else {
                    Log.e("MainActivity", "Failed to retrieve CCM token")
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                Log.e("MainActivity", "Error retrieving CCM token: ${e.message}")
            }
        })
    }

    private fun getTokenIoT(callback: (String) -> Unit) {
        val client = OkHttpClient()
        val body = FormBody.Builder()
            .add("client_id", "external-client_08d0a50e-258e-4bbf-b58d-fac9e66051c1_b8ed90ff-2355-44c0-b981-61b812c29900")
            .add("client_secret", "a4914d48-8061-409a-b75f-42ebe187f06f")
            .add("grant_type", "client_credentials")
            .add("scope", "openid")
            .build()

        val request = Request.Builder()
            .url("https://iam-service-eu-west-1.carbyneapi.com/auth/realms/carbyne/protocol/openid-connect/token")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val token = JSONObject(response.body?.string() ?: "").getString("access_token")
                    callback(token)
                } else {
                    Log.e("MainActivity", "Failed to retrieve IoT token")
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                Log.e("MainActivity", "Error retrieving IoT token: ${e.message}")
            }
        })
    }

    private fun sendToCCM(ccmToken: String) {
        val client = OkHttpClient()
        val callID = UUID.randomUUID().toString()
        val phone = phoneInput.text.toString()
        val body = """
            {
                "cncID": "984bb43e-64ec-4bdf-8c75-27db17813887",
                "call": {
                    "callID": "$callID",
                    "callerID": "$phone"
                },
                "queue": {
                    "queueID": "984bb43e-64ec-4bdf-8c75-27db17813887"
                },
                "extension": {
                    "extensionID": "${BuildConfig.EXTENSION_ID}"
                }
            }
        """.trimIndent().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://ccm-service-eu-west-1.carbyneapi.com/CCMService/External-Services/PBX/JoinAndAnswerEventRequest")
            .addHeader("Authorization", "Bearer $ccmToken")
            .addHeader("API-Version", "3")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val responseBodyString = response.body?.string() ?: ""
                val json = JSONObject(responseBodyString)

                if (response.isSuccessful) {
                    ccmRecordID = json.optString("recordID")

                    Log.d("MainActivity", "CCM response: ${json.toString()}")
                    Log.d("MainActivity", "RecordID: $ccmRecordID")

                    startIoTReport()
                } else {
                    Log.e("MainActivity", "CCM call failed: ${response.code}")
                    Log.d("MainActivity", "Error response body: ${json.toString()}")
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                Log.e("MainActivity", "CCM call error: ${e.message}")
            }
        })
    }

    private fun startIoTReport() {
        val client = OkHttpClient()
        val phone = phoneInput.text.toString()
        val body = """
            {
                "providerID": "08d0a50e-258e-4bbf-b58d-fac9e66051c1",
                "deviceID": "$phone"
            }
        """.trimIndent().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://iot-service.carbyneapi.com/IoTService/External-Services/IoTStartReportRequest")
            .addHeader("Authorization", "Bearer $iotToken")
            .addHeader("API-Version", "1")
            .addHeader("Trace-ID", UUID.randomUUID().toString())
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val iotReportID = JSONObject(response.body?.string() ?: "").getString("iotReportID")
                    activeIotReportID = iotReportID
                    updateIoTEntities(iotReportID)
                } else {
                    Log.e("MainActivity", "Failed to start IoT report: ${response.code}")
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                Log.e("MainActivity", "IoT start error: ${e.message}")
            }
        })
    }

    private fun updateIoTEntities(iotReportID: String) {
        val client = OkHttpClient()
        val timestamp = System.currentTimeMillis() / 1000
        val location = currentLocation ?: return
        val name = nameInput.text.toString()
        val updates = JSONArray().apply {
            put(JSONObject().apply {
                put("entityID", "b0f65b4e-b83c-451a-8114-84ba7bb1385e")
                put("entityValue", JSONObject().apply {
                    put("locationTimestamp", timestamp)
                    put("advancedGeodeticLocation", JSONObject().apply {
                        put("longitude", location.longitude)
                        put("latitude", location.latitude)
                        put("altitude", 16)
                        put("horizontalAccuracy", 11.781)
                        put("verticalAccuracy", 3.0097444)
                        put("confidenceInAccuracy", 99.9999)
                        put("bearing", 0)
                        put("speed", 0)
                    })
                    put("civicLocation", JSONObject().apply {
                        put("country", country)
                        put("state", state)
                        put("city", city)
                        put("street", "$street $streetNumber")
                        put("longitude", location.longitude)
                        put("latitude", location.latitude)
                        put("addressType", "OTHER")
                        put("label", "C5 CDMX")
                        put("formattedAddress", "$street $streetNumber, $city, $state, $country")
                    })
                    put("locationProvider", "IOT")
                    put("locationSource", "GPS")
                    put("locationIO", "UNKNOWN")
                })
                put("entityUpdateTimestamp", timestamp)
                put("entityUpdateID", UUID.randomUUID().toString())
            })
            put(JSONObject().apply {
                put("entityID", "d76de711-1578-4487-b672-a3e3c090a672")
                put("entityValue", name)
                put("entityUpdateTimestamp", 1668516498359)
                put("entityUpdateID", "0a9d78f663738a9205c1445c00001386")
            })
            put(JSONObject().apply {
                put("entityID", "3d31fde5-b620-4455-8a1e-d6f07fbc5b30")
                put("entityValue", "Mercedes")
                put("entityUpdateTimestamp", 1668516498359)
                put("entityUpdateID", "0a9d78f663738a926c905e1c00001586")
            })
            put(JSONObject().apply {
                put("entityID", "5683d0ef-628a-43e0-bb1e-db1578013104")
                put("entityValue", "Pedro Martinez")
                put("entityUpdateTimestamp", 1668516498385)
                put("entityUpdateID", "0a9d78f663738a9208c02ed800001886")
            })
            put(JSONObject().apply {
                put("entityID", "b0276cd8-f070-42c0-aed3-710767b903ca")
                put("entityValue", "Yes")
                put("entityUpdateTimestamp", 1668516498385)
                put("entityUpdateID", "0a9d78f663738a92d3aaf18700001986")
            })
            put(JSONObject().apply {
                put("entityID", "3cf507f2-41dc-4357-ac1e-ce83ff6aadbf")
                put("entityValue", "WDD213FR01")
                put("entityUpdateTimestamp", 1668516498385)
                put("entityUpdateID", "0a9d78f663738a927426f3cb00001a86")
            })
        }

        val body = JSONObject().apply {
            put("iotReportID", iotReportID)
            put("reportEntitiesUpdates", updates)
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://iot-service.carbyneapi.com/IoTService/External-Services/IoTReportEntitiesUpdatesRequest")
            .addHeader("Authorization", "Bearer $iotToken")
            .addHeader("API-Version", "1")
            .addHeader("Trace-ID", iotReportID)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                Log.d("MainActivity", "IoT Entities Updated: ${response.code}")
            }

            override fun onFailure(call: Call, e: IOException) {
                Log.e("MainActivity", "IoT Entities update error: ${e.message}")
            }
        })
    }

    private fun stopIoTReport(iotReportID: String) {
        val client = OkHttpClient()

        val body = JSONObject().apply {
            put("iotReportID", iotReportID)
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://iot-service.carbyneapi.com/IoTService/External-Services/IoTStopReportRequest")
            .addHeader("Authorization", "Bearer $iotToken")
            .addHeader("API-Version", "1")
            .addHeader("Trace-ID", iotReportID)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                Log.d("MainActivity", "IoT report stopped: ${response.code}")
                hangupCCM()
            }

            override fun onFailure(call: Call, e: IOException) {
                Log.e("MainActivity", "Stop IoT error: ${e.message}")
            }
        })
    }

    private fun hangupCCM() {
        val client = OkHttpClient()
        val callID = UUID.randomUUID().toString()
        val phone = phoneInput.text.toString()

        val body = """
            {
                "cncID": "984bb43e-64ec-4bdf-8c75-27db17813887",
                "call": {
                    "callID": "$callID",
                    "callerID": "$phone"
                },
                "queue": {
                    "queueID": "984bb43e-64ec-4bdf-8c75-27db17813887"
                },
                "extension": {
                    "extensionID": "${BuildConfig.EXTENSION_ID}"
                },
                "recordID": "$ccmRecordID",
                "callHangupReason": "HANGUP_BY_CALL_TAKER"
            }
        """.trimIndent().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://ccm-service-eu-west-1.carbyneapi.com/CCMService/External-Services/PBX/HangupEventRequest")
            .post(body)
            .addHeader("Authorization", "Bearer $ccmToken")
            .addHeader("API-Version", "3")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val responseBodyString = response.body?.string() ?: ""
                Log.d("MainActivity", "Hangup CCM response code: ${response.code}")
                Log.d("MainActivity", "Hangup CCM response body: $responseBodyString")
            }

            override fun onFailure(call: Call, e: IOException) {
                Log.e("MainActivity", "Hangup CCM error: ${e.message}")
            }
        })
    }
}
