package com.example.pdr4; // 确保这里是你的包名

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * 数据采集与真值记录工具类
 * 负责：1. 创建和写入 CSV 文件
 * 2. 开启独立的高频 GPS 监听，获取坐标真值
 */
public class DataLogger {

    private final Context context;
    private final LocationManager locationManager;

    private boolean isRecording = false;
    private BufferedWriter csvWriter;
    private int recordedRows = 0;

    // --- 真值缓存 ---
    private double truthLat = 0.0;
    private double truthLon = 0.0;
    private float truthAcc = 0.0f;
    private LocationListener continuousGpsListener;

    public DataLogger(Context context, LocationManager locationManager) {
        this.context = context;
        this.locationManager = locationManager;
    }

    public boolean isRecording() {
        return isRecording;
    }

    public int getRecordedRows() {
        return recordedRows;
    }

    /**
     * 开始录制
     * @param filename 文件名
     * @return 是否成功开启
     */
    public boolean startRecording(String filename) {
        if (isRecording) return false;

        File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        File file = new File(dir, filename + ".csv");

        try {
            csvWriter = new BufferedWriter(new FileWriter(file, false));
            // 写入 14列九轴数据 + 3列GPS真值
            csvWriter.write("time,acce_x,acce_y,acce_z,gyro_x,gyro_y,gyro_z,magnet_x,magnet_y,magnet_z,rv_w,rv_x,rv_y,rv_z,GPS_Lat,GPS_Lon,GPS_Accuracy\n");

            isRecording = true;
            recordedRows = 0;

            // 启动高频连续 GPS 监听 (1Hz)
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                continuousGpsListener = new LocationListener() {
                    @Override
                    public void onLocationChanged(@NonNull Location location) {
                        truthLat = location.getLatitude();
                        truthLon = location.getLongitude();
                        truthAcc = location.getAccuracy();
                    }
                    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
                    @Override public void onProviderEnabled(@NonNull String provider) {}
                    @Override public void onProviderDisabled(@NonNull String provider) {}
                };
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, continuousGpsListener);
            }

            Toast.makeText(context, "录制开始，保存在: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
            return true;

        } catch (IOException e) {
            Toast.makeText(context, "文件创建失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    /**
     * 写入一行数据 (由 MainActivity 50Hz 驱动)
     */
    public void writeRow(long timestamp, double ax, double ay, double az,
                         float[] gyro, float[] mag, float[] quat) {
        if (!isRecording || csvWriter == null) return;

        try {
            String line = String.format("%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.7f,%.7f,%.2f\n",
                    timestamp, ax, ay, az,
                    gyro[0], gyro[1], gyro[2],
                    mag[0], mag[1], mag[2],
                    quat[0], quat[1], quat[2], quat[3],
                    truthLat, truthLon, truthAcc); // 追加真值

            csvWriter.write(line);
            recordedRows++;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 停止录制并释放资源
     */
    public void stopRecording() {
        if (!isRecording) return;
        isRecording = false;

        // 1. 关闭文件流
        try {
            if (csvWriter != null) {
                csvWriter.flush();
                csvWriter.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 2. 移除 GPS 监听，省电
        if (continuousGpsListener != null && locationManager != null) {
            locationManager.removeUpdates(continuousGpsListener);
            continuousGpsListener = null;
        }
    }
}