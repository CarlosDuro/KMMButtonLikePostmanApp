package com.example.anonymousapp

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.util.*

class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var emergencyWebView: WebView
    private var activeIotReportID: String? = null

    private val anonymousProviderID = "anonymous-provider-id"
    private val anonymousCncID = "anonymous-cnc-id"
    private val anonymousQueueID = "anonymous-queue-id"
    private val anonymousServiceID = "anonymous-service-id"
    private val anonymousUserPhone = "+0000000000"
    private val anonymousUserEmail = "user@example.com"
    private val anonymousUserName = "Anonymous User"
    private val anonymousClientID = "anonymous-client-id"
    private val anonymousClientSecret = "anonymous-client-secret"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        emergencyWebView = findViewById(R.id.emergencyWebView)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        requestLocationPermission()
        handleIntent(intent?.data?.toString())
    }

    private fun handleIntent(intentData: String?) {
        if (intentData != null && intentData.contains("emergency")) {
            emergencyWebView.visibility = View.VISIBLE
            emergencyWebView.webViewClient = WebViewClient()
            emergencyWebView.loadUrl("https://www.anonymous-domain.com/")

            getTokenCCM { tokenCCM ->
                if (tokenCCM != null) {
                    getTokenIoT { tokenIoT ->
                        if (tokenIoT != null) {
                            sendToCCM(tokenCCM)
                            getLocationAndSendToIoT(tokenIoT)
                        }
                    }
                }
            }
        }
    }

    private fun requestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1
            )
        }
    }

    private fun getTokenCCM(callback: (String?) -> Unit) {
        val client = OkHttpClient()
        val requestBody = FormBody.Builder()
            .add("grant_type", "client_credentials")
            .add("client_id", anonymousClientID)
            .add("client_secret", anonymousClientSecret)
            .build()

        val request = Request.Builder()
            .url("https://auth.anonymous-provider.com/token")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val responseBody = response.body?.string()
                    val json = JSONObject(responseBody ?: "")
                    val accessToken = json.optString("access_token")
                    callback(accessToken)
                }
            }
        })
    }

    private fun sendToCCM(token: String) {
        val client = OkHttpClient()
        val json = JSONObject().apply {
            put("providerID", anonymousProviderID)
            put("cncID", anonymousCncID)
            put("queueID", anonymousQueueID)
            put("serviceID", anonymousServiceID)
            put("caller", JSONObject().apply {
                put("phone", anonymousUserPhone)
                put("email", anonymousUserEmail)
                put("name", anonymousUserName)
            })
        }

        val requestBody = RequestBody.create(
            MediaType.get("application/json"), json.toString()
        )

        val request = Request.Builder()
            .url("https://ccm.anonymous-provider.com/api/joinAndAnswer")
            .addHeader("Authorization", "Bearer $token")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}

            override fun onResponse(call: Call, response: Response) {}
        })
    }

    private fun getTokenIoT(callback: (String?) -> Unit) {
        val client = OkHttpClient()
        val requestBody = FormBody.Builder()
            .add("grant_type", "client_credentials")
            .add("client_id", anonymousClientID)
            .add("client_secret", anonymousClientSecret)
            .build()

        val request = Request.Builder()
            .url("https://auth.anonymous-provider.com/token")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val responseBody = response.body?.string()
                    val json = JSONObject(responseBody ?: "")
                    val accessToken = json.optString("access_token")
                    callback(accessToken)
                }
            }
        })
    }

    private fun getLocationAndSendToIoT(token: String) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val locationRequest = LocationRequest.create().apply {
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
                interval = 10000
            }

            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    val location: Location = locationResult.lastLocation
                    val geocoder = Geocoder(this@MainActivity, Locale.getDefault())

                    try {
                        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        val addressText = addresses?.firstOrNull()?.getAddressLine(0) ?: "Address unavailable"

                        startIoTReport(token) { reportID ->
                            activeIotReportID = reportID
                            updateIoTEntities(token, reportID, location, addressText)
                            Handler(Looper.getMainLooper()).postDelayed({
                                stopIoTReport(token, reportID)
                            }, 5 * 60 * 1000)
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Failed to get address", Toast.LENGTH_SHORT).show()
                    }

                    fusedLocationClient.removeLocationUpdates(this)
                }
            }

            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
        }
    }

    private fun startIoTReport(token: String, callback: (String) -> Unit) {
        val client = OkHttpClient()
        val json = JSONObject().apply {
            put("providerID", anonymousProviderID)
            put("cncID", anonymousCncID)
        }

        val requestBody = RequestBody.create(
            MediaType.get("application/json"), json.toString()
        )

        val request = Request.Builder()
            .url("https://iot.anonymous-provider.com/api/startReport")
            .addHeader("Authorization", "Bearer $token")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val responseBody = response.body?.string()
                    val json = JSONObject(responseBody ?: "")
                    val reportID = json.optString("iotReportID")
                    callback(reportID)
                }
            }
        })
    }

    private fun updateIoTEntities(token: String, reportID: String, location: Location, addressText: String) {
        val client = OkHttpClient()
        val json = JSONObject().apply {
            put("iotReportID", reportID)
            put("entities", listOf(JSONObject().apply {
                put("type", "caller")
                put("id", anonymousUserPhone)
                put("location", JSONObject().apply {
                    put("lat", location.latitude)
                    put("lon", location.longitude)
                    put("advanced_location", JSONObject().apply {
                        put("address", addressText)
                    })
                })
            }))
        }

        val requestBody = RequestBody.create(
            MediaType.get("application/json"), json.toString()
        )

        val request = Request.Builder()
            .url("https://iot.anonymous-provider.com/api/updateEntities")
            .addHeader("Authorization", "Bearer $token")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}

            override fun onResponse(call: Call, response: Response) {}
        })
    }

    private fun stopIoTReport(token: String, reportID: String) {
        val client = OkHttpClient()
        val json = JSONObject().apply {
            put("iotReportID", reportID)
        }

        val requestBody = RequestBody.create(
            MediaType.get("application/json"), json.toString()
        )

        val request = Request.Builder()
            .url("https://iot.anonymous-provider.com/api/stopReport")
            .addHeader("Authorization", "Bearer $token")
            .post(requestBody)
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

private fun hangupCCM() {
    val client = OkHttpClient()
    val callID = UUID.randomUUID().toString()
    val phone = phoneInput.text.toString()

    val body = """
        {
            "cncID": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
            "call": {
                "callID": "$callID",
                "callerID": "$phone"
            },
            "queue": {
                "queueID": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
            },
            "extension": {
                "extensionID": "${BuildConfig.EXTENSION_ID}"
            },
            "recordID": "$ccmRecordID",
            "callHangupReason": "HANGUP_BY_CALL_TAKER"
        }
    """.trimIndent().toRequestBody("application/json".toMediaType())

    val request = Request.Builder()
        .url("https://your-ccm-endpoint.com/CCMService/External-Services/PBX/HangupEventRequest")
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
