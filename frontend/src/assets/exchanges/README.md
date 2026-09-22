# 交易所标识来源

原始官方文件本地保存，不重画商标，不从外部 CDN 在运行时加载。
仅用于标识对应交易所，不表示合作、背书、已启用或交易授权。

| 文件 | 来源 | SHA256 |
| --- | --- | --- |
| okx-official.png | https://www.okx.com/cdn/assets/imgs/291/89001A8FD7AB4038904A203EAECB68E6.png | BF0AB57FBB01883E8BCA8D8EA4DD351B68907F730B0EAE988BBF1154063D9F63 |
| binance-official.ico | https://bin.bnbstatic.com/static/images/common/favicon.ico | 8318EBBCB1CB4729EB0F78BB058DC618C3B63F9F9F0070A1A7A3265FDC79B833 |

2026-09-22 核对：OKX 首页 shortcut icon 指向上述文件；Binance 静态 CDN 返回原始 ICO（HTTP 200，image/vnd.microsoft.icon）。Binance 主站与品牌页返回 HTTP 202 空内容，因此未将此次获取表述为品牌许可审查。
商标权属于相应权利人；重新分发和对外发布仍需遵守权利人的使用条款。
PAPER 是内部模拟模式，使用既有组件库图标，不冒充交易所品牌。未知交易所保留原始代码和通用数据库图标。
