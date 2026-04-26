package com.example.pdr4; // 确保这里的包名和你的一致

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * 自定义 PDR 轨迹绘制 View
 * 支持：实时打点画线、单指拖拽平移、双指捏合缩放
 */
public class TrajectoryView extends View {

    private Paint pathPaint;
    private Paint currentPointPaint;
    private Paint startPointPaint;
    private Paint gridPaint;

    private List<PointF> trajectoryPoints = new ArrayList<>();
    private Path drawPath = new Path();

    // --- 缩放与平移变量 ---
    private float scaleFactor = 1.0f; // 默认缩放比例
    private float translateX = 0f;    // X 轴平移量
    private float translateY = 0f;    // Y 轴平移量
    private float lastTouchX, lastTouchY;

    // 手势检测器
    private ScaleGestureDetector scaleDetector;

    // 物理世界 1 米在屏幕上对应的基础像素
    private static final float BASE_PIXELS_PER_METER = 50f;

    public TrajectoryView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        // 初始化画笔 (轨迹线 - 青色)
        pathPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pathPaint.setColor(Color.CYAN);
        pathPaint.setStyle(Paint.Style.STROKE);
        pathPaint.setStrokeWidth(5f);
        pathPaint.setStrokeJoin(Paint.Join.ROUND);

        // 初始化画笔 (当前位置 - 红色)
        currentPointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        currentPointPaint.setColor(Color.RED);
        currentPointPaint.setStyle(Paint.Style.FILL);

        // 初始化画笔 (起点 - 绿色)
        startPointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        startPointPaint.setColor(Color.GREEN);
        startPointPaint.setStyle(Paint.Style.FILL);

        // 初始化画笔 (网格 - 暗灰色)
        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(Color.parseColor("#333333"));
        gridPaint.setStrokeWidth(2f);

        // 初始化双指缩放检测器
        scaleDetector = new ScaleGestureDetector(getContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                scaleFactor *= detector.getScaleFactor();
                // 限制缩放范围 (0.1倍 到 10倍)
                scaleFactor = Math.max(0.1f, Math.min(scaleFactor, 10.0f));
                invalidate(); // 触发重绘
                return true;
            }
        });
    }

    /**
     * 【对外接口】添加新的 PDR 坐标点
     * @param x 物理 X 坐标 (米)
     * @param y 物理 Y 坐标 (米)
     */
    public void addPoint(float x, float y) {
        trajectoryPoints.add(new PointF(x, y));
        invalidate(); // 告诉系统：数据变了，赶紧重新画！
    }

    /**
     * 【对外接口】清空轨迹
     */
    public void clearTrajectory() {
        trajectoryPoints.clear();
        translateX = 0f;
        translateY = 0f;
        scaleFactor = 1.0f;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.parseColor("#1E1E1E")); // 科技感暗色背景

        int width = getWidth();
        int height = getHeight();

        canvas.save(); // 保存当前画布状态

        // 1. 将画布原点移动到屏幕中心，并应用拖拽的偏移量
        canvas.translate(width / 2f + translateX, height / 2f + translateY);
        // 2. 应用缩放比例
        canvas.scale(scaleFactor, scaleFactor);

        // 3. 画辅助网格 (可选)
        drawGrid(canvas, width, height);

        // 4. 绘制轨迹
        if (!trajectoryPoints.isEmpty()) {
            drawPath.reset();
            PointF startP = trajectoryPoints.get(0);

            // 注意：PDR的Y轴向前(上)为正，但Android屏幕Y轴向下为正，所以这里的 Y 要加负号翻转
            drawPath.moveTo(startP.x * BASE_PIXELS_PER_METER, -startP.y * BASE_PIXELS_PER_METER);

            for (int i = 1; i < trajectoryPoints.size(); i++) {
                PointF p = trajectoryPoints.get(i);
                drawPath.lineTo(p.x * BASE_PIXELS_PER_METER, -p.y * BASE_PIXELS_PER_METER);
            }
            canvas.drawPath(drawPath, pathPaint);

            // 画起点
            canvas.drawCircle(startP.x * BASE_PIXELS_PER_METER, -startP.y * BASE_PIXELS_PER_METER, 8f, startPointPaint);

            // 画当前位置
            PointF currentP = trajectoryPoints.get(trajectoryPoints.size() - 1);
            canvas.drawCircle(currentP.x * BASE_PIXELS_PER_METER, -currentP.y * BASE_PIXELS_PER_METER, 10f, currentPointPaint);
        }

        canvas.restore(); // 恢复画布状态
    }

    private void drawGrid(Canvas canvas, int width, int height) {
        float gridSpacing = BASE_PIXELS_PER_METER; // 每1米画一条网格线
        // 扩大网格绘制范围以适应缩放和平移
        int maxLines = 100;
        for (int i = -maxLines; i <= maxLines; i++) {
            canvas.drawLine(i * gridSpacing, -maxLines * gridSpacing, i * gridSpacing, maxLines * gridSpacing, gridPaint);
            canvas.drawLine(-maxLines * gridSpacing, i * gridSpacing, maxLines * gridSpacing, i * gridSpacing, gridPaint);
        }
    }

    // --- 触摸事件处理 (拖拽平移) ---
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // 先让缩放检测器处理，如果是双指捏合，它会消费掉事件
        scaleDetector.onTouchEvent(event);

        // 如果不是在缩放，那就处理单指拖拽
        if (!scaleDetector.isInProgress()) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    lastTouchX = event.getX();
                    lastTouchY = event.getY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getX() - lastTouchX;
                    float dy = event.getY() - lastTouchY;
                    translateX += dx;
                    translateY += dy;
                    lastTouchX = event.getX();
                    lastTouchY = event.getY();
                    invalidate(); // 平移后重绘
                    break;
            }
        }
        return true;
    }
}