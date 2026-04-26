package com.example.pdr4.algorithm;
/**
 * 1D 卡尔曼滤波器
 * 用于平滑航向角并过滤突发噪声
 */
public class KalmanFilter {

    private double x; // 状态估计值 (Estimated state)
    private double p; // 估计协方差 (Error covariance)
    private double q; // 过程噪声协方差 (Process noise covariance) - 代表系统演变的稳定性
    private double r; // 测量噪声协方差 (Measurement noise covariance) - 代表传感器数据的可信度

    /**
     * @param q 过程噪声 (建议 1e-4 到 1e-2)
     * @param r 测量噪声 (建议 0.01 到 0.1)
     */
    public KalmanFilter(double q, double r) {
        this.q = q;
        this.r = r;
        this.p = 1.0; 
        this.x = 0.0; // 初始状态
    }

    /**
     * 更新滤波器
     * @param measurement 新的测量值（弧度）
     * @return 滤波后的值
     */
    public double update(double measurement) {
        // 1. 预测过程 (Prediction)
        // x_k = x_{k-1} (假设航向短时间内不变)
        // p_k = p_{k-1} + q
        p = p + q;

        // 2. 特殊处理：角度环绕 (Handling Angle Wrapping)
        // 确保测量值与当前估计值的差在 [-PI, PI] 之间，防止滤波器走反方向
        double diff = measurement - x;
        while (diff > Math.PI)  diff -= 2 * Math.PI;
        while (diff < -Math.PI) diff += 2 * Math.PI;

        // 3. 更新过程 (Update)
        // 计算卡尔曼增益 K
        double k = p / (p + r);
        // 更新估计值 x
        x = x + k * diff;
        // 更新协方差 p
        p = (1 - k) * p;

        // 规范化输出范围在 [-PI, PI]
        while (x > Math.PI)  x -= 2 * Math.PI;
        while (x < -Math.PI) x += 2 * Math.PI;

        return x;
    }

    public void setState(double x) {
        this.x = x;
    }
}