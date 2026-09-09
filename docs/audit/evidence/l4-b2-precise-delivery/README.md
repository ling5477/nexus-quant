# B2 precise delivery evidence formatting

仅按本轮明确授权整理4个evidence文件的trailing whitespace或EOF空行，源码、测试结果、命令内容、P0/P1与结论不变。`git diff -w --ignore-blank-lines -- <affected evidence files>`相对整理前已暂存候选为空。

[原始字节归档](pre-format-originals.zip)按原repo路径保存4个文件，历史manifest及其SHA字段保持原样，其身份对应归档中的原字节；不把历史hash冒充格式整理后文件的hash。[交付绑定](format-bindings.json)单独记录原始/整理后的raw SHA-256和Git canonical blob。其他evidence的原manifest绑定不变。

七个production文件fingerprint仍为`7982a8d51e21332e7474be15b24e468249ba0e19a647d651bdd8a5020305910a`。本次机械整理不构成新production candidate，不重跑Full Maven、48场景或Independent Review。CI结果以本次commit自己的exact-head run为准，此附录不提前声明CI绿色。

另：历史production identity的算法文字误写BOM；实际清单字节为UTF-8无BOM、CRLF，重新计算与上述hash完全一致。保留历史文案，复核时以原始清单字节和逐文件hash为准。
