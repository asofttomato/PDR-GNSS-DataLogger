package com.example.pdr4.algorithm;
/**
 * 优化版航向估算器：支持通用水平投影、方向融合与环形加权滤波
 */
public class HeadingEstimator {

    private double filteredHeading = 0.0;
    private boolean isFirstHeading = true;

    // --- 核心调参区 ---
    
    // 1. 滤波系数 (Alpha)：越小越平滑，越大响应越快。
    // 0.15 代表 85% 相信上一轮的历史方向，15% 相信这一步的新方向。
    // 如果你觉得转弯太慢，可以调高到 0.3；如果觉得轨迹一直在抖，调低到 0.08。
    private static final double SMOOTH_ALPHA = 0.3;

    // 2. 融合系数：相信手机朝向多一点，还是相信加速度矢量多一点？
    // 0.7 代表 70% 相信手机自身的 Yaw 角，30% 相信这一步跨出的加速度方向。
    // 如果你总是端着手机走，可以调高到 0.9；如果你习惯把手机乱放，可以调低到 0.5。
    private static final double FUSION_WEIGHT_PHONE = 0.9;

    /**
     * 【核心功能 1】：将手机加速度投影到世界坐标系的水平面上
     * @return 返回一个长度为 2 的数组 [accel_world_X, accel_world_Y]
     */
    public double[] projectToWorldHorizontal(double ax, double ay, double az, 
                                            double qw, double qx, double qy, double qz) {
        
        // 使用四元数将手机坐标系下的加速度旋转到世界坐标系
        double[] worldAccel = rotateVectorByQuaternion(ax, ay, az, qw, qx, qy, qz);

        // 在世界坐标系中，worldAccel[0] 是东向(E)，worldAccel[1] 是北向(N)
        // 水平投影即只取 X 和 Y 分量
        return new double[]{worldAccel[0], worldAccel[1]};
    }

    /**
     * 【核心功能 2】：获取平滑后的融合航向
     * @param rawAccHeading 基于加速度矢量累加算的原始方向 (弧度)
     * @param phoneYaw 基于四元数直读的手机 Yaw 角 (弧度)
     * @return 最终用于轨迹计算的航向角 (弧度)
     */
    public double getFusedAndFilteredHeading(double rawAccHeading, double phoneYaw) {
        
        // 1. 融合：将手机 Yaw 和 加速度方向按照权重结合
        double fusedHeading = fuseAngles(phoneYaw, rawAccHeading, FUSION_WEIGHT_PHONE);

        // 2. 滤波：进行环形低通平滑，消除突发抖动
        if (isFirstHeading) {
            filteredHeading = fusedHeading;
            isFirstHeading = false;
        } else {
            filteredHeading = smoothCircular(filteredHeading, fusedHeading, SMOOTH_ALPHA);
        }
        
        return filteredHeading;
    }

    /**
     * 辅助方法：使用四元数计算传统偏航角 (手机自身的朝向)
     */
    public double calculatePhoneYaw(double w, double x, double y, double z) {
        double siny_cosp = 2.0 * (w * z + x * y);
        double cosy_cosp = 1.0 - 2.0 * (y * y + z * z);
        return -Math.atan2(siny_cosp, cosy_cosp);
    }

    /**
     * 辅助方法：使用四元数旋转 3D 向量 (v' = v + 2 * r x (r x v + w * v))
     */
    private double[] rotateVectorByQuaternion(double vx, double vy, double vz, 
                                             double qw, double qx, double qy, double qz) {
        double tx = 2.0 * (qy * vz - qz * vy);
        double ty = 2.0 * (qz * vx - qx * vz);
        double tz = 2.0 * (qx * vy - qy * vx);

        double resX = vx + qw * tx + (qy * tz - qz * ty);
        double resY = vy + qw * ty + (qz * tx - qx * tz);
        double resZ = vz + qw * tz + (qx * ty - qy * tx);

        return new double[]{resX, resY, resZ};
    }

    /**
     * 辅助方法：处理两个角度的加权融合（完美处理 -PI 到 PI 的环绕跳变）
     */
    private double fuseAngles(double angle1, double angle2, double weight1) {
        double diff = angle2 - angle1;
        // 将差值规整到 [-PI, PI] 之间，确保永远走最短弧线
        while (diff > Math.PI) diff -= 2 * Math.PI;
        while (diff < -Math.PI) diff += 2 * Math.PI;
        
        return angle1 + (1.0 - weight1) * diff;
    }

    /**
     * 辅助方法：环形平滑滤波
     */
    private double smoothCircular(double oldAngle, double newAngle, double alpha) {
        double diff = newAngle - oldAngle;
        while (diff > Math.PI) diff -= 2 * Math.PI;
        while (diff < -Math.PI) diff += 2 * Math.PI;
        
        double result = oldAngle + alpha * diff;
        
        // 保持结果在 [-PI, PI] 范围内
        while (result > Math.PI) result -= 2 * Math.PI;
        while (result < -Math.PI) result += 2 * Math.PI;
        
        return result;
    }
}