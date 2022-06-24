package com.suelee.maps

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.util.Patterns
import android.widget.Button
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    //定位client
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    private var map:GoogleMap? = null

    private var currentLocation : Location? = null
    //当前定位marker点
    private var currentMarker: Marker? = null

    val REQUEST_PHOTO_CODE = 3002 //获取权限
    val current = LocalDateTime.now()
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm:ss")
    var FILE_NAME = "network_info_" + current.format(formatter) +".txt"
    val URL_FILE = "host_names.txt"
    val PERIOD_IN_SECOND: Long = 30 //查询时间间隔
    var urls = "video-edge-87b0fa.jkt02.abs.hls.ttvnw.net"
    var enablePing = true
    lateinit var inputEditText : TextInputEditText
    lateinit var module : PyObject
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main);
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        val mapFragment : SupportMapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        initAndSetListener()
        val filetext = File(this@MainActivity.externalCacheDir?.path, URL_FILE)
        filetext.writeText("www.baidu.com, www.sina.com")
        if (!Python.isStarted()) {
            Python.start(Python.getPlatform())
        }
        FILE_NAME = this@MainActivity.externalCacheDir?.path+"/"+FILE_NAME
        val py = Python.getInstance()
        module = py.getModule("tcping")
    }

    @SuppressLint("MissingPermission")
    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        googleMap.mapType = GoogleMap.MAP_TYPE_HYBRID

        val permission = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        requestPermission(permission, REQUEST_PHOTO_CODE)
        googleMap.isIndoorEnabled = true
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates(){
        fusedLocationProviderClient.requestLocationUpdates(
            LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)//设置高精度
                .setInterval(PERIOD_IN_SECOND * 1000), //PERIOD_IN_SECOND秒一次定位请求
            locationCallback,
            Looper.getMainLooper())
    }

    private fun initAndSetListener() {
        inputEditText = findViewById(R.id.url_input)
        inputEditText.inputType= InputType.TYPE_CLASS_TEXT
        inputEditText.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(charSequence: CharSequence?, start: Int, before: Int, count: Int) {
                enablePing = false
            }

            override fun afterTextChanged(editable: Editable) {
                var content:String=editable.toString()
                if(content.contains("\r") || content.contains("\n") || content.contains(" ")){
                    //去掉回车换行空格
                    content = content.replace("\r","").replace("\n","").replace(" ", "")
                }
                urls = content
                Timer().schedule(object : TimerTask() {
                    override fun run() {
                        enablePing = true
                    }
                }, 10 * 1000)
            }
        })

    }
    @Synchronized
    private fun runPing(url : String, location: Location) {
        if(enablePing) {
            module.callAttr("my_ping", url, FILE_NAME, location.latitude.toString(), location.longitude.toString())
        }
    }
    private fun setLocation(location: Location) {
        if(enablePing) {
            GlobalScope.launch{
                val urlVec = urls?.split(",")
                for (url in urlVec.orEmpty()) {
                    if (Patterns.WEB_URL.matcher(url).matches()) {
                        runPing(url, location)
                    }
                }
            }
        }
    }
    //定位回调

    private val locationCallback = object : LocationCallback(){
        override fun onLocationResult(locationResult: LocationResult) {
            if (!enablePing) {
                return
            }
            for (location in locationResult.locations){
                drawLocationMarker(location, LatLng(location.latitude,location.longitude))
                setLocation(location)
            }
        }
    }


    @SuppressLint("NewApi")
    private fun drawLocationMarker(location: Location, latLng: LatLng) {
        if (currentLocation == null){//第一次定位画定位marker
            currentMarker = map?.addMarker(
                MarkerOptions()
                .position( latLng).title("Marker")
                //.icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_vehicle_location))
            )
            map?.moveCamera(
                CameraUpdateFactory.newLatLngZoom(
                latLng,14f
            ))
        }else{
            val deltaTime = location.time - currentLocation!!.time
            //有方位精度
            if (location.hasBearingAccuracy()){
                if (deltaTime <= 0){
                    map?.animateCamera(CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder()
                            .target(latLng)
                            .zoom(map?.cameraPosition!!.zoom)
                            .bearing(location.bearing)
                            .build()
                    ))
                }else{
                    map?.animateCamera(CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder()
                            .target(latLng)
                            .zoom(map?.cameraPosition!!.zoom)
                            .bearing(location.bearing)
                            .build()
                    ), deltaTime.toInt(),null)
                }
                currentMarker?.rotation = 0f
            }else{
                if (deltaTime <= 0){
                    map?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng,map?.cameraPosition!!.zoom))
                }else{
                    map?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng,map?.cameraPosition!!.zoom), deltaTime.toInt(), null)
                }
                //设置marker的指针方向
                currentMarker?.rotation = location.bearing - (map?.cameraPosition?.bearing ?:0f)
            }

        }
        currentLocation = location
        Log.e(TAG, "currentLocation="+currentLocation.toString())
    }

    private fun stopLocationUpdates(){
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()

    }
    ///----------
    /**
     * 动态获权
     * */
    /**
     * 动态获权请求值
     */
    private var REQUEST_CODE_PERMISSION = 0x00099
    protected val TAG = this.javaClass.simpleName

    /**
     * 请求权限
     * 动态获权
     * @param permissions 请求的权限
     * @param requestCode 请求权限的请求码
     */
    open fun requestPermission(
        permissions: Array<String>,
        requestCode: Int
    ) {
        REQUEST_CODE_PERMISSION = requestCode
        if (checkPermissions(permissions)) {
            permissionSuccess(REQUEST_CODE_PERMISSION)
        } else {
            try {
                val needPermissions =
                    getDeniedPermissions(permissions)
                ActivityCompat.requestPermissions(
                    this,
                    needPermissions.toTypedArray(),
                    REQUEST_CODE_PERMISSION
                )
            } catch (e: Exception) {
                Log.e("BaseActivity", "获取权限失败 Exception = $e")
            }
        }
    }

    /**
     * 检测所有的权限是否都已授权
     */
    fun checkPermissions(permissions: Array<String>): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }
        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        return true
    }

    /**
     * 获取权限集中需要申请权限的列表
     */
    fun getDeniedPermissions(permissions: Array<String>): List<String> {
        val needRequestPermissionList: MutableList<String> =
            ArrayList()
        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) !=
                PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.shouldShowRequestPermissionRationale(this, permission)
            ) {
                needRequestPermissionList.add(permission)
            }
        }
        return needRequestPermissionList
    }

    /**
     * 系统请求权限回调
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSION) {
            if (verifyPermissions(grantResults)) {
                permissionSuccess(REQUEST_CODE_PERMISSION)
            } else {
                permissionFail(REQUEST_CODE_PERMISSION)
            }
        }
    }

    /**
     * 确认所有的权限是否都已授权
     */
    fun verifyPermissions(grantResults: IntArray): Boolean {
        for (grantResult in grantResults) {
            if (grantResult != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    /**
     * 获取权限成功
     */
    open fun permissionSuccess(requestCode: Int) {
        Log.e(TAG, "获取权限成功=$requestCode")

        startLocationUpdates()
    }

    /**
     * 权限获取失败
     */
    open fun permissionFail(requestCode: Int) {
        Log.e(TAG, "获取权限失败=$requestCode")
    }

    //-----
    //反向地理编码
    private fun latlngToAddress(lat: Double,lng: Double){
        val geocoder = Geocoder(this)
        try {
            val result = geocoder.getFromLocation(lat,lng,1)
            if (result != null && result.isNotEmpty()){
                val addressName = result[0].featureName
            }
        }catch (e: Exception){

        }
    }

}
