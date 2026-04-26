package com.example.pdr4; // 确保包名与你的一致

/**
 * 坐标系转换工具类
 * WGS84 (GPS原生) -> GCJ02 (高德/腾讯) -> BD09 (百度)
 */
public class CoordinateConverter {

    private static final double pi = 3.1415926535897932384626;
    private static final double a = 6378245.0; // 卫星椭球坐标投影到平面地图坐标系的投影因子
    private static final double ee = 0.00669342162296594323; // 椭球的偏心率
    private static final double x_pi = 3.14159265358979324 * 3000.0 / 180.0;

    /**
     * 【终极转换】将硬件获取的 WGS84 转化为 百度地图 BD09
     * @param lng 经度 (Longitude)
     * @param lat 纬度 (Latitude)
     * @return double[0]是经度，double[1]是纬度
     */
    public static double[] wgs84ToBaidu(double lng, double lat) {
        // 第一步：WGS84 转 GCJ02
        double[] gcj = wgs84ToGcj02(lng, lat);
        // 第二步：GCJ02 转 BD09
        return gcj02ToBd09(gcj[0], gcj[1]);
    }

    // --- 内部转换核心算法 ---

    private static double[] wgs84ToGcj02(double lng, double lat) {
        if (outOfChina(lng, lat)) {
            return new double[]{lng, lat};
        }
        double dLat = transformLat(lng - 105.0, lat - 35.0);
        double dLng = transformLng(lng - 105.0, lat - 35.0);
        double radLat = lat / 180.0 * pi;
        double magic = Math.sin(radLat);
        magic = 1 - ee * magic * magic;
        double sqrtMagic = Math.sqrt(magic);
        dLat = (dLat * 180.0) / ((a * (1 - ee)) / (magic * sqrtMagic) * pi);
        dLng = (dLng * 180.0) / (a / sqrtMagic * Math.cos(radLat) * pi);
        double mgLat = lat + dLat;
        double mgLng = lng + dLng;
        return new double[]{mgLng, mgLat};
    }

    private static double[] gcj02ToBd09(double lng, double lat) {
        double z = Math.sqrt(lng * lng + lat * lat) + 0.00002 * Math.sin(lat * pi);
        double theta = Math.atan2(lat, lng) + 0.000003 * Math.cos(lng * pi);
        double bd_lng = z * Math.cos(theta) + 0.0065;
        double bd_lat = z * Math.sin(theta) + 0.006;
        return new double[]{bd_lng, bd_lat};
    }

    private static double transformLat(double lng, double lat) {
        double ret = -100.0 + 2.0 * lng + 3.0 * lat + 0.2 * lat * lat + 0.1 * lng * lat + 0.2 * Math.sqrt(Math.abs(lng));
        ret += (20.0 * Math.sin(6.0 * lng * pi) + 20.0 * Math.sin(2.0 * lng * pi)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(lat * pi) + 40.0 * Math.sin(lat / 3.0 * pi)) * 2.0 / 3.0;
        ret += (160.0 * Math.sin(lat / 12.0 * pi) + 320 * Math.sin(lat * pi / 30.0)) * 2.0 / 3.0;
        return ret;
    }

    private static double transformLng(double lng, double lat) {
        double ret = 300.0 + lng + 2.0 * lat + 0.1 * lng * lng + 0.1 * lng * lat + 0.1 * Math.sqrt(Math.abs(lng));
        ret += (20.0 * Math.sin(6.0 * lng * pi) + 20.0 * Math.sin(2.0 * lng * pi)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(lng * pi) + 40.0 * Math.sin(lng / 3.0 * pi)) * 2.0 / 3.0;
        ret += (150.0 * Math.sin(lng / 12.0 * pi) + 300.0 * Math.sin(lng / 30.0 * pi)) * 2.0 / 3.0;
        return ret;
    }

    private static boolean outOfChina(double lng, double lat) {
        // 判断是否在国内，不在国内则不进行偏移
        return (lng < 72.004 || lng > 137.8347) || (lat < 0.8293 || lat > 55.8271);
    }
}