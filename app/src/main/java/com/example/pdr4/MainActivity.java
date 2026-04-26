package com.example.pdr4;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.pdr4.algorithm.StepDetector;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    // --- 传感器和 PDR ---
    private SensorManager sensorManager;
    private Sensor accelSensor, gyroSensor, magSensor, rotationSensor;
    private StepDetector stepDetector;
    private boolean isTracking = false;
    private int stepCount = 0;

    // --- 九轴缓存 ---
    private float[] latestQuat = new float[]{1.0f, 0.0f, 0.0f, 0.0f};
    private float[] latestGyro = new float[]{0f, 0f, 0f};
    private float[] latestMag = new float[]{0f, 0f, 0f};

    // --- 数据采集相关 ---
    private DataLogger dataLogger;

    // --- UI 控件 ---
    private View layoutMap, layoutCollect;
    private Button btnTabMap, btnTabCollect;
    private EditText etFilename;
    private Button btnRecordToggle;
    private TextView tvRecordStatus;

    // 原有的导航 UI
    private MapView mapView;
    private Polyline trajectoryLine;
    private Marker userMarker;
    private LocationManager locationManager;
    private double currentLat, currentLon, relX = 0, relY = 0, totalDistance = 0;
    private TextView tvRelativePos, tvTotalDist, tvMapType, tvStatus;
    private Button btnToggle, btnRefreshGps;
    // 【新增】：用于缓存并在 UI 显示的航向数据
    private double currentHeadingDegree = 0.0;
    private String currentDirStr = "未知";
    private EditText etHeight;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().load(this, getPreferences(MODE_PRIVATE));
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_main);

        // 1. 绑定所有的视图
        initViews();

        // 2. 初始化地图和权限
        setupMap();
        checkPermissions();

        // 3. 注册服务
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        }
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        dataLogger = new DataLogger(this, locationManager);
    }

    private void initViews() {
        // 页面容器
        layoutMap = findViewById(R.id.layout_map);
        layoutCollect = findViewById(R.id.layout_collect);
        etHeight = findViewById(R.id.et_height);
        // 底部 Tab
        btnTabMap = findViewById(R.id.btn_tab_map);
        btnTabCollect = findViewById(R.id.btn_tab_collect);

        // 采集页面 UI
        etFilename = findViewById(R.id.et_filename);
        btnRecordToggle = findViewById(R.id.btn_record_toggle);
        tvRecordStatus = findViewById(R.id.tv_record_status);

        // 导航页面 UI
        tvStatus = findViewById(R.id.tv_status);
        tvMapType = findViewById(R.id.tv_map_type);
        tvRelativePos = findViewById(R.id.tv_relative_pos);
        tvTotalDist = findViewById(R.id.tv_total_dist);
        btnToggle = findViewById(R.id.btn_toggle);
        btnRefreshGps = findViewById(R.id.btn_refresh_gps);
        mapView = findViewById(R.id.map_view);

        // --- 设置点击事件 ---
        btnTabMap.setOnClickListener(v -> switchTab(true));
        btnTabCollect.setOnClickListener(v -> switchTab(false));
        btnRefreshGps.setOnClickListener(v -> refreshGPS());
        btnToggle.setOnClickListener(v -> toggleTracking());
        btnRecordToggle.setOnClickListener(v -> toggleRecording());
    }

    /**
     * 切换底部导航栏页面
     */
    private void switchTab(boolean showMap) {
        if (showMap) {
            layoutMap.setVisibility(View.VISIBLE);
            layoutCollect.setVisibility(View.GONE);
            btnTabMap.setBackgroundColor(Color.parseColor("#333333"));
            btnTabMap.setTextColor(Color.WHITE);
            btnTabCollect.setBackgroundColor(Color.parseColor("#212121"));
            btnTabCollect.setTextColor(Color.parseColor("#888888"));
        } else {
            layoutMap.setVisibility(View.GONE);
            layoutCollect.setVisibility(View.VISIBLE);
            btnTabCollect.setBackgroundColor(Color.parseColor("#333333"));
            btnTabCollect.setTextColor(Color.WHITE);
            btnTabMap.setBackgroundColor(Color.parseColor("#212121"));
            btnTabMap.setTextColor(Color.parseColor("#888888"));
        }
    }

    private void toggleRecording() {
        if (!dataLogger.isRecording()) {
            String filename = etFilename.getText().toString().trim();
            if (TextUtils.isEmpty(filename)) {
                Toast.makeText(this, "请先输入文件名！", Toast.LENGTH_SHORT).show();
                return;
            }

            // 调用专门的类来开始录制
            boolean success = dataLogger.startRecording(filename);

            if (success) {
                btnRecordToggle.setText("停止录制");
                btnRecordToggle.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#4CAF50")));
                etFilename.setEnabled(false);

                // 核心联动：自动启动 PDR
                if (!isTracking) {
                    toggleTracking();
                    Toast.makeText(this, "已自动开启 PDR 导航引擎", Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            // 调用专门的类来停止录制
            dataLogger.stopRecording();

            btnRecordToggle.setText("开始录制数据");
            btnRecordToggle.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#E53935")));
            etFilename.setEnabled(true);
            tvRecordStatus.setText("状态: 已保存!\n\n最终行数: " + dataLogger.getRecordedRows());
        }
    }

    private void toggleTracking() {
        if (!isTracking) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;

            Location lastLoc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (lastLoc == null) lastLoc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

            if (lastLoc != null) {
                currentLon = lastLoc.getLongitude();
                currentLat = lastLoc.getLatitude();

                isTracking = true;
                relX = 0; relY = 0; totalDistance = 0; stepCount = 0;
                // 🚀 【新增】：安全地读取用户输入的身高，如果没填或者填错，默认用 1.75
                double userHeight = 1.75;
                try {
                    String heightStr = etHeight.getText().toString().trim();
                    if (!TextUtils.isEmpty(heightStr)) {
                        userHeight = Double.parseDouble(heightStr);
                    }
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "身高格式有误，默认使用 1.75m", Toast.LENGTH_SHORT).show();
                }

                // 将真实身高传入算法
                stepDetector = new StepDetector(userHeight);

                trajectoryLine.setPoints(new java.util.ArrayList<>());
                stepDetector = new StepDetector(1.75);

                trajectoryLine.setPoints(new java.util.ArrayList<>());
                GeoPoint startPoint = new GeoPoint(currentLat, currentLon);
                trajectoryLine.addPoint(startPoint);
                userMarker.setPosition(startPoint);
                mapView.getController().animateTo(startPoint);

                // 注册所有九轴传感器
                sensorManager.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_GAME);
                sensorManager.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_GAME);
                sensorManager.registerListener(this, magSensor, SensorManager.SENSOR_DELAY_GAME);
                sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME);

                btnToggle.setText("停止导航");
                btnToggle.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.RED));
                tvStatus.setText("PDR 运行中 (X轴已反转验证版)");
            } else {
                Toast.makeText(this, "等待 GPS 信号...", Toast.LENGTH_SHORT).show();
            }
        } else {
            isTracking = false;
            sensorManager.unregisterListener(this);
            btnToggle.setText("开始导航");
            btnToggle.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#009688")));
            tvStatus.setText("PDR 已停止");
        }
    }

    // 在类里面加一个节流阀变量，防止文字刷新过快导致屏幕闪烁
    private long lastUiRefreshTime = 0;

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!isTracking) return;

        int type = event.sensor.getType();

        // ==========================================
        // 1. 旁路缓存：高频监听并实时更新九轴的另外 6 轴 + 四元数
        // ==========================================
        if (type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getQuaternionFromVector(latestQuat, event.values);
        } else if (type == Sensor.TYPE_GYROSCOPE) {
            System.arraycopy(event.values, 0, latestGyro, 0, 3); // 👈 陀螺仪数据在此！
        } else if (type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, latestMag, 0, 3);  // 👈 磁力计数据在此！

            // ==========================================
            // 2. 主时钟触发：以加速度为基准，打包所有缓存数据写入文件
            // ==========================================
        } else if (type == Sensor.TYPE_ACCELEROMETER) {
            long timestamp = event.timestamp / 1000000;
            double ax = event.values[0], ay = event.values[1], az = event.values[2];

            // 🚀 【核心写入】：把 ax, ay, az 和缓存的 Gyro, Mag 统统交出去
            if (dataLogger != null && dataLogger.isRecording()) {
                dataLogger.writeRow(timestamp, ax, ay, az, latestGyro, latestMag, latestQuat);

                // 降低 UI 刷新频率防止卡顿
                if (dataLogger.getRecordedRows() % 10 == 0) {
                    runOnUiThread(() -> tvRecordStatus.setText("状态: 正在录制 🔴\n\n已采集数据: " + dataLogger.getRecordedRows() + " 行"));
                }
            }

            // ==========================================
            // 3. PDR 步态与航向解算
            // ==========================================
            double L = stepDetector.processSample(ax, ay, az, latestQuat[0], latestQuat[1], latestQuat[2], latestQuat[3], timestamp);

            // 读取解算后的方向 (无需再加负号，HeadingEstimator 里已经掰正了)
            double rawHeading = stepDetector.getCurrentStepHeading();
            currentHeadingDegree = Math.toDegrees(rawHeading);
            currentDirStr = getDirectionText(currentHeadingDegree);

            if (L > 0) {
                // 算出真实的物理位移
                double dx = L * Math.sin(rawHeading);
                double dy = L * Math.cos(rawHeading);

                relX += dx; relY += dy;
                totalDistance += L;
                stepCount++;

                // 米转经纬度
                double R = 6378137.0;
                currentLat += Math.toDegrees(dy / R);
                currentLon += Math.toDegrees(dx / (R * Math.cos(Math.toRadians(currentLat))));

                runOnUiThread(() -> {
                    GeoPoint newPoint = new GeoPoint(currentLat, currentLon);
                    trajectoryLine.addPoint(newPoint);
                    userMarker.setPosition(newPoint);
                    mapView.getController().animateTo(newPoint);
                });
            }

            // UI 仪表盘刷新 (150ms 节流)
            if (System.currentTimeMillis() - lastUiRefreshTime > 150) {
                lastUiRefreshTime = System.currentTimeMillis();
                updateUI();
            }
        }
    }
    private void updateUI() {
        runOnUiThread(() -> {
            // 🚀 【关键修复 2】：使用 \n 强制换行！之前字太长跑到屏幕外面去了
            if (tvRelativePos != null) {
                tvRelativePos.setText(String.format("相对原点: X=%.1fm, Y=%.1fm\n当前航向: %.1f° (%s)",
                        relX, relY, currentHeadingDegree, currentDirStr));
            }
            if (tvTotalDist != null) {
                tvTotalDist.setText(String.format("累计行走: %.2f m (%d 步)", totalDistance, stepCount));
            }
        });
    }
    // ... (保留其余未修改的 setupMap(), refreshGPS(), checkPermissions() 等方法)
    private void setupMap() {
        mapView.setMultiTouchControls(true);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        if (tvMapType != null) tvMapType.setText("当前底图：OpenStreetMap (WGS-84)");

        trajectoryLine = new Polyline();
        trajectoryLine.setColor(Color.RED);
        trajectoryLine.setWidth(10.0f);
        mapView.getOverlays().add(trajectoryLine);

        userMarker = new Marker(mapView);
        userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        userMarker.setIcon(getResources().getDrawable(android.R.drawable.ic_menu_mylocation));
        userMarker.setTitle("当前位置");
        mapView.getOverlays().add(userMarker);

        org.osmdroid.views.overlay.ScaleBarOverlay scaleBarOverlay = new org.osmdroid.views.overlay.ScaleBarOverlay(mapView);
        scaleBarOverlay.setCentred(true);
        scaleBarOverlay.setScaleBarOffset(200, 50);
        mapView.getOverlays().add(scaleBarOverlay);
        mapView.getController().setZoom(19.0);
    }

    private void refreshGPS() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        Toast.makeText(this, "正在获取精准 GPS 信号...", Toast.LENGTH_SHORT).show();
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                currentLon = location.getLongitude();
                currentLat = location.getLatitude();
                GeoPoint newPoint = new GeoPoint(currentLat, currentLon);
                userMarker.setPosition(newPoint);
                mapView.getController().animateTo(newPoint);
                if (!isTracking) btnToggle.setText("开始导航 (GPS 已就绪)");
                Toast.makeText(MainActivity.this, "位置已校准", Toast.LENGTH_SHORT).show();
                locationManager.removeUpdates(this);
            }
            @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
            @Override public void onProviderEnabled(@NonNull String provider) {}
            @Override public void onProviderDisabled(@NonNull String provider) {}
        });
    }

    private void checkPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    /**
     * 将角度转换为东南西北方向文本
     */
    private String getDirectionText(double degree) {
        // 将任何角度标准化到 0 ~ 360 度范围内
        double normalized = (degree + 360) % 360;

        if (normalized >= 337.5 || normalized < 22.5) return "北 ⬆️";
        if (normalized >= 22.5 && normalized < 67.5) return "东北 ↗️";
        if (normalized >= 67.5 && normalized < 112.5) return "东 ➡️";
        if (normalized >= 112.5 && normalized < 157.5) return "东南 ↘️";
        if (normalized >= 157.5 && normalized < 202.5) return "南 ⬇️";
        if (normalized >= 202.5 && normalized < 247.5) return "西南 ↙️";
        if (normalized >= 247.5 && normalized < 292.5) return "西 ⬅️";
        if (normalized >= 292.5 && normalized < 337.5) return "西北 ↖️";
        return "未知";
    }
}