---
name: nq-research-reproducibility
description: 将 NQ research/backtest 实验提升为复用模块，或修复影响数据、时间、随机性和数值结果可复现性的工程问题时使用；一次性脚本和普通 Python package 修改无需使用。
---
# Research 可复现性

明确输入数据版本、时间与时区、随机种子、计算顺序、精度和输出契约。识别实验代码进入正式库或交易边界时需要补足的能力；保留研究工具与生产运行时分离。

沿用现有 package、依赖管理和测试入口，不因存在 pyproject.toml 或新增 tests 强制另一套流程。按实际问题处理 look-ahead bias、缺失值、资源生命周期和并发界限。

以可重复样本和合理数值容差证明结果；只运行受影响测试与已有相关质量门禁。完成时交付可复用实现或评审结论、复现条件、真实结果与限制。

复杂工程问题按需读取 [工程边界](references/engineering.md)。任何真实交易或生产副作用仍受 NQ 根约束限制。
