package com.example.pdr4.algorithm;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class StepDetector {

    // --- 1. 核心算法参数 (保留了你的调优值) ---
    private static final int SMA_WINDOW_SIZE = 8; 
    private static final double MIN_PEAK_THRESHOLD = 10.5; 
    private static final long MIN_TIME_BETWEEN_STEPS_MS = 250;

    // --- 2. 状态变量 ---
    private LinkedList<Double> slidingWindow = new LinkedList<>(); 
    private double sumWindow = 0.0; 
    private double prevSmoothedNorm = 9.8;
    private double prevPrevSmoothedNorm = 9.8;
    private long lastStepTime = 0;
    private long prevTimestamp = 0;
    private double dynamicThreshold = MIN_PEAK_THRESHOLD; 

    // --- 3. 外部模型引用 ---
    private WeinbergModel stepLengthModel; 
    private HeadingEstimator headingEstimator; // 新增：方向估算器
    
    private double windowMax = Double.MIN_VALUE; 
    private double windowMin = Double.MAX_VALUE;

    // --- 4. 航向累加器 (用于水平投影) ---
    private double stepAccelSumX = 0;
    private double stepAccelSumY = 0;
    private double currentStepHeading = 0; // 记录迈步瞬间的航向角

    // 历史记录
    private List<Double> rawHistory = new ArrayList<>();
    private List<Double> smoothedHistory = new ArrayList<>();

    public StepDetector(double userHeight) {
        this.stepLengthModel = new WeinbergModel(userHeight);
        this.headingEstimator = new HeadingEstimator(); // 初始化航向估算器
    }

    /**
     * 实时处理单个数据点 (增加了四元数输入)
     */
    public double processSample(double ax, double ay, double az, 
                                double qw, double qx, double qy, double qz, 
                                long timestamp) {
        
        // --- A. 原始数据记录 ---
        double rawNorm = Math.sqrt(ax * ax + ay * ay + az * az);
        rawHistory.add(rawNorm); 

        // --- B. 【新增】水平投影：将加速度投影到世界坐标系平面 ---
        double[] horizAccel = headingEstimator.projectToWorldHorizontal(ax, ay, az, qw, qx, qy, qz);
        stepAccelSumX += horizAccel[0];
        stepAccelSumY += horizAccel[1];

        // --- C. 滑动窗口滤波 (SMA) ---
        slidingWindow.addLast(rawNorm);
        sumWindow += rawNorm;
        if (slidingWindow.size() > SMA_WINDOW_SIZE) {
            sumWindow -= slidingWindow.removeFirst();
        }
        
        double smoothedNorm = sumWindow / slidingWindow.size();
        smoothedHistory.add(smoothedNorm); 

        if (slidingWindow.size() < SMA_WINDOW_SIZE) {
            return 0.0;
        }

        // 记录周期极值
        if (smoothedNorm > windowMax) windowMax = smoothedNorm;
        if (smoothedNorm < windowMin) windowMin = smoothedNorm;

        double currentStepLength = 0.0;

        // --- D. 探峰逻辑 ---
        boolean isLocalMaximum = (prevSmoothedNorm > prevPrevSmoothedNorm) && (prevSmoothedNorm > smoothedNorm);

        if (isLocalMaximum) {
            if (prevSmoothedNorm > dynamicThreshold && (prevTimestamp - lastStepTime > MIN_TIME_BETWEEN_STEPS_MS)) {

                lastStepTime = prevTimestamp;

                // 1. 计算步长
                currentStepLength = stepLengthModel.calculateStepLength(windowMax, windowMin);

                // 2. 【核心新增】计算航向：利用这一步内的加速度矢量和确定行走方向
                // 这种方法比直接读单帧 Yaw 角更稳健，能抵消手部晃动
                double rawAccHeading = Math.atan2(stepAccelSumX, stepAccelSumY);
                double phoneYaw = headingEstimator.calculatePhoneYaw(qw, qx, qy, qz);
                this.currentStepHeading = headingEstimator.getFusedAndFilteredHeading(rawAccHeading, phoneYaw);
                // 3. 重置步周期变量
                windowMax = Double.MIN_VALUE;
                windowMin = Double.MAX_VALUE;
                stepAccelSumX = 0; // 重置累加器
                stepAccelSumY = 0;

                // 4. 更新动态阈值 (保留了你的 0.8/0.2 权重)
                dynamicThreshold = (dynamicThreshold * 0.8) + (prevSmoothedNorm * 0.2);
                dynamicThreshold = Math.max(MIN_PEAK_THRESHOLD, Math.min(dynamicThreshold, 14.0));
                // dynamicThreshold = 10;
            }
        } else {
            dynamicThreshold = Math.max(MIN_PEAK_THRESHOLD, dynamicThreshold - 0.008);
        }

        prevPrevSmoothedNorm = prevSmoothedNorm;
        prevSmoothedNorm = smoothedNorm;
        prevTimestamp = timestamp;

        return currentStepLength;
    }

    // 获取当前步对应的航向角 (弧度)
    public double getCurrentStepHeading() {
        return currentStepHeading;
    }

    public List<Double> getRawHistory() { return rawHistory; }
    public List<Double> getSmoothedHistory() { return smoothedHistory; }
}