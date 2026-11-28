# prism_ui 来源说明

- 上游仓库：<https://github.com/chen08209/prism_ui>
- 上游版本：`v0.1.3`
- 上游提交：`7831ade349ede896aedb8a5757dc9c3fe88081d5`
- 本地用途：作为 fxxkHilife Flutter 新 UI 的固定依赖，避免云端构建随着 pub.dev 内容漂移。

## 本地兼容修复

Flutter 3.47 已通过 `flutter/widgets.dart` 导出 `ScrollCacheExtent`，但上游的
`prism_list.dart` 和 `route_motion_hold.dart` 没有显式导入其实际来源
`flutter/rendering.dart`。本地只补充这两个导入，框架行为和 API 保持不变。

上游的 `LICENSE` 和 `NOTICE` 随源码保留。
