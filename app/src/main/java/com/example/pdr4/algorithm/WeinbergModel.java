package com.example.pdr4.algorithm;
/**
 * 修改后的步长计算器 (支持动态调参)
 */
public class WeinbergModel {

    private double K; // 步长比例常数

    // 保留原有的基于身高的估算方法
    public WeinbergModel(double heightInMeters) {
        this.K = 0.4470 + (heightInMeters - 1.70) * 0.1;
    }

    // 新增：专门用于机器学习调参的构造函数
    public void setK(double optimizedK) {
        this.K = optimizedK;
    }

    public double getK() {
        return this.K;
    }

    public double calculateStepLength(double aMax, double aMin) {
        if (aMax <= aMin) return 0.0;
        double accelDifference = aMax - aMin;
        double stepLength = K * Math.pow(accelDifference, 0.25);
        return Math.max(0.4, Math.min(stepLength, 1.2)); // 放宽一点限制
    }
}