# Android PDR & GNSS Data Logger

本项目是一个基于 Android 平台的行人航位推算（PDR, Pedestrian Dead Reckoning）与多传感器数据采集工具。系统结合了移动设备的惯性传感器（加速度计、陀螺仪、磁力计）与 GNSS 数据，实现了实时的步态检测、航向估算、轨迹绘制以及高频数据同步采集。
## 部署方法：
- 在新版Android Studio里可以直接克隆仓库并运行。方法：File-->New-->Project from Version Control
- 也可以在release里下载最新版的apk直接使用。
**注意**：底图使用OSM底图，国内需要梯子才能加载
## 主要特性

* **实时 PDR 推算**：内置动态阈值步态检测算法与 Weinberg 步长估算模型，支持根据用户身高动态调整计算参数。
* **航向融合与滤波**：通过 `HeadingEstimator` 将四元数解析的设备偏航角与加速度矢量进行融合计算，并应用环形平滑滤波，解决了 Android 传感器物理坐标系与地理方位角之间的对齐问题。
* **高精度数据采集**：采用零阶保持（ZOH）策略，以加速度计为触发时钟（约 50Hz），同步记录 14 项九轴/四元数数据，并与后台独立运行的 1Hz 高频 GPS 真值进行对齐。
* **无偏地图渲染**：集成 Osmdroid 引擎，直接使用 WGS-84 坐标系（OpenStreetMap）映射推算轨迹。
* **模块化设计**：数据采集、UI 渲染与 PDR 核心算法解耦，提供独立的 `Transer` 坐标转换类与 `TrajectoryView` 自定义相对坐标绘制视图供二次开发使用。

## 代码结构

```text
app/src/main/java/com/example/pdr4/
├── algorithm/                  
│   ├── HeadingEstimator.java   # 航向估算、水平投影与加权融合滤波
│   ├── StepDetector.java       # 基于 SMA 滑动窗口的步态检测
│   └── WeinbergModel.java      # 基于加速度极值的步长模型
├── DataLogger.java             # 异步数据采集控制、CSV 文件读写与独立 GPS 监听
├── GPSPoint.java               # 经纬度实体类
├── MainActivity.java           # 应用入口，负责生命周期、地图加载与传感器注册
└── TrajectoryView.java         # 基于 Canvas 的自定义相对坐标系轨迹绘制面板
