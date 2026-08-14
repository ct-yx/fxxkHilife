# OpenFreebuds 上游参考快照

## 固定来源

| 字段 | 值 |
|---|---|
| 项目 | OpenFreebuds |
| URL | <https://github.com/melianmiko/OpenFreebuds> |
| 分支 | `main` |
| 固定 commit | `035bf2a87cdda9e9c8e8e90c662b7fa61270c6ee` |
| commit 日期 | 2026-08-10 |
| commit 摘要 | `[packaging] Use precompiled winsdk to drop VisualStudio dependency (#124)` |
| 许可证 | GPL-3.0，见 `reference/openfreebuds/LICENSE` |
| 本地路径 | `reference/openfreebuds/` |
| 用途 | 协议、型号能力和 Handler 行为的可追溯参考资料 |

该 commit 是本项目当前固定的上游研究基线。快照包含该 commit 的 Git 跟踪源文件，但不包含 `.git` 对象；构建缓存、虚拟环境、`build`、`dist`、`__pycache__` 等生成物不纳入版本控制。

## 与当前 Android 实现的路径映射

| 上游路径 | 研究内容 | 当前实现对应位置 |
|---|---|---|
| `openfreebuds/driver/generic/spp.py` | RFCOMM/SPP 驱动启动、端口和收包循环 | `app/src/main/java/com/freebuds/controller/core/transport/RfcommSppTransport.kt`、`bluetooth/SppDriver.kt` |
| `openfreebuds/driver/huawei/package.py` | Huawei 5A 包编码、参数解析、CRC16 | `app/src/main/java/com/freebuds/controller/protocol/HuaweiSppPackage.kt`、`adapter/huawei/protocol/HuaweiSppProtocol.kt` |
| `openfreebuds/driver/huawei/driver/generic.py` | Huawei driver 的 pending response、Handler 注册和初始化语义 | `adapter/huawei/protocol/HuaweiCommandClient.kt`、`HuaweiPendingResponseManager.kt`、`HuaweiHandlerInitializer.kt`、`bluetooth/SppDriver.kt` |
| `openfreebuds/driver/huawei/constants/spp_commands.py` | Huawei 命令常量 | `app/src/main/java/com/freebuds/controller/adapter/huawei/protocol/HuaweiCommandCatalog.kt` |
| `openfreebuds/driver/huawei/handler/anc.py` | ANC 模式/级别读写和状态处理 | `app/src/main/java/com/freebuds/controller/bluetooth/HuaweiNoiseControlHandlers.kt` |
| `openfreebuds/driver/huawei/handler/battery.py` | 电量及充电状态 | `app/src/main/java/com/freebuds/controller/bluetooth/BatteryHandler.kt` |
| `openfreebuds/driver/huawei/handler/config_auto_pause.py` | 自动暂停 | `app/src/main/java/com/freebuds/controller/bluetooth/HuaweiAudioHandlers.kt` |
| `openfreebuds/driver/huawei/handler/low_latency.py` | 低延迟/游戏模式 | `app/src/main/java/com/freebuds/controller/bluetooth/HuaweiAudioHandlers.kt` |
| `openfreebuds/driver/huawei/handler/sound_quality_preference.py` | 音质/连接偏好 | `app/src/main/java/com/freebuds/controller/bluetooth/HuaweiAudioHandlers.kt` |
| `openfreebuds/driver/huawei/handler/config_equalizer.py` | EQ 预设和 Custom EQ 数据 | `app/src/main/java/com/freebuds/controller/bluetooth/HuaweiEqualizerHandler.kt` |
| `openfreebuds/driver/huawei/handler/dual_connect/` | 双设备枚举和控制 | `app/src/main/java/com/freebuds/controller/bluetooth/HuaweiDualConnectHandler.kt` |
| `openfreebuds/driver/huawei/handler/action_*.py` | 双击、三击、长按、滑动、电源键 | `app/src/main/java/com/freebuds/controller/bluetooth/HuaweiGestureHandlers.kt` |
| `openfreebuds/driver/huawei/handler/info.py` | 设备信息、固件、序列号 | `app/src/main/java/com/freebuds/controller/bluetooth/HuaweiDeviceInfoHandlers.kt` |
| `openfreebuds/driver/huawei/handler/state_in_ear.py` | 佩戴状态 | `app/src/main/java/com/freebuds/controller/bluetooth/HuaweiDeviceInfoHandlers.kt` |
| `openfreebuds/driver/huawei/driver/per_model/` | 型号端口和 Handler 能力组合 | `app/src/main/java/com/freebuds/controller/protocol/DeviceCapability.kt`、`adapter/huawei/HuaweiOpenFreebudsAdapter.kt` |
| `openfreebuds/driver/huawei/test/` | 上游协议/Handler 单元测试及固定十六进制样本 | `app/src/test/resources/fixtures/huawei_spp/`、`app/src/test/java/com/freebuds/controller/protocol/HuaweiSppPackageFixtureTest.kt`；其余行为由当前协议/策略测试覆盖 |

映射表描述“研究来源”和“当前 Android 派生实现”的关系，不表示两个项目的代码结构、运行时或许可证相同。

## 已纳入版本控制的协议样本

以下 fixture 来自固定 commit 的上游测试常量，保留原始字节序列和 CRC；它们只验证 Android 独立实现的封包/解包边界，不把上游 Python 代码引入构建：

| Fixture | 上游来源 | 覆盖 |
|---|---|---|
| `get_battery_request.hex`、`battery_response_base.hex`、`battery_response_legacy.hex` | `openfreebuds/driver/huawei/test/test_battery.py` | 电量请求、双耳/盒电量参数和旧版响应 |
| `get_auto_pause_request.hex`、`auto_pause_response.hex`、`set_auto_pause_on_request.hex`、`set_auto_pause_on_response.hex` | `openfreebuds/driver/huawei/test/test_config.py` | 读取、写入、ACK 参数和完整 CRC |

Android 测试还额外覆盖双字节长度、高字节非零、分片/粘包、嵌套包、非法长度、截断校验字节、参数越界和 CRC 可选校验；这些边界不代表某个型号已经完成实机能力验证。

## 许可证与派生边界

- `reference/openfreebuds/` 是 GPL-3.0 上游源码的参考快照，必须保留上游 `LICENSE` 和版权/许可证通知。
- 当前 Android 生产代码没有编译、链接或运行该 Python/Qt 快照；快照不会进入 Gradle source set、APK 或运行时 classpath。
- 当前 Kotlin 实现根据协议研究、日志和上游行为进行独立移植/重写。任何直接复制上游代码、把上游模块编译进 Android、或以可识别的派生代码替换当前实现之前，必须重新评估 GPL-3.0 的整体许可、修改标注和对应源码发布义务。
- 新增或修改快照内容时不得删除上游许可证；若未来对快照本身做修改，应在快照目录旁记录修改日期和内容，或重新导入一个新的不可变 commit 快照。

## 更新和复核流程

1. 选择新的上游 commit，不使用会漂移的 branch HEAD 作为唯一依据。
2. 使用 `git archive <commit>` 导入到临时目录，再替换 `reference/openfreebuds/`；不复制 `.git`、缓存或构建目录。
3. 同步修改本文件中的 commit、日期、摘要和路径映射，并检查快照中的 `LICENSE`。
4. 对照 GitHub API/网页确认 commit 和许可证，运行 `git diff --check`。
5. 重新检查 Kotlin 命令目录、协议 fixture、型号能力表和实机证据；上游 commit 变化本身不能把任何型号/能力从 `PARTIAL` 或 `UNKNOWN` 自动提升为 `VERIFIED`。
6. 资料快照和文档维护不提升应用版本号，不改变生产蓝牙时序；需要行为变化时另开代码变更、自动化测试和实机回归记录。
