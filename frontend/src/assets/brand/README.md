# 已确认品牌素材与发布衍生文件

用户已确认原图，并于本轮允许仅压缩、转换格式和适配图标尺寸。没有重新生成、裁切、重绘或改动 K 线走势；原 PNG 保留，不作为当前页面发布资源。

| 用途 | 发布文件 | 处理 | 大小 |
| --- | --- | --- | --- |
| 登录背景 | login-earth-kline-realistic-approved.webp | 1670×942 原尺寸，无损 WebP；解码 RGB 像素与原图完全相同 | 1,533,760 bytes |
| 品牌组件 | nq-ribbon-icon-128.png | 原图等比缩至 128×128，Lanczos3，PNG 无损编码 | 16,950 bytes |
| favicon | nq-favicon-32.png | 同一原图等比缩至 32×32，Lanczos3，PNG 无损编码 | 1,897 bytes |

处理使用 Sharp，未新增项目运行依赖。背景参数 `webp({lossless:true,effort:6})`；图标参数 `resize(size,size,{fit:'contain',kernel:'lanczos3'}).png({compressionLevel:9,adaptiveFiltering:true})`。图标缩放会重采样像素，不宣称与原尺寸逐像素相同。

原图 SHA256 保持不变：

- `login-earth-kline-realistic-approved.png`：`94f4abeb142a38afb1aeb8a707119f5b8acd626379808871f0c0e7f483e83d46`
- `nq-ribbon-icon-approved.png`：`d3d6d639b9e7f419ef4e6862a8ce63d8d55433983b14f0ff8dd43a22970ee29f`

旧版 `login-earth-kline-approved.png` 仅保留历史素材，不被页面引用。新增生成视觉仍须先预览、获得用户确认后接入。
