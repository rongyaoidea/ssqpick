# 签名说明

本应用的 release 包使用固定证书签发。**换签名密钥等于换应用身份**：
Google Play 会拒绝覆盖安装，已安装用户也无法直接升级（必须卸载重装，
丢数据）。因此这套流程做了两道强制校验：

1. 每次发布前比对当前密钥库指纹与本文档记录的上次发布指纹，**不一致直接中止构建**；
2. 产物构建完成后，用 `keytool -printcert` 直接读 APK 签名块，确认它确由预期证书签发。

## 证书信息

```
类型        PKCS12 (JKS)
算法        RSA 2048 / SHA384withRSA
Alias       ssqpick
Subject     CN=ssqpick, OU=Android, O=rongyaoidea, L=Beijing, C=CN
有效期      2026-10-10 至 2054-02-25（10000 天，超过 Google Play 要求的 25 年）
SHA-256     68:A9:7A:B2:CE:65:74:60:07:A2:E0:B4:40:C3:8E:04:FD:D4:51:51:43:79:10:F4:86:09:22:9A:11:E7:C5:3B
```

首次发布后，`docs/SIGNING.md` 里的这个指纹会被后续发布当作基准。
**如果确实要轮换密钥**（例如私钥泄露），需要同步更新这里的指纹并明确知晓升级代价。

## 密钥在哪里

| 密钥 | 位置 | 是否敏感 |
|---|---|---|
| release 密钥库 | GitHub Secrets `KEYSTORE_BASE64`（base64 编码） | 是，**绝不入库** |
| release 密码 | GitHub Secrets `KEYSTORE_PASSWORD` / `KEY_PASSWORD` | 是，**绝不入库** |
| alias | GitHub Secrets `KEY_ALIAS` | 否 |
| debug 密钥库 | 仓库内 `app/debug.keystore` | 否（Android 公知的默认凭证） |

Release 构建时由 `app/build.gradle.kts` 读取环境变量 `KEYSTORE_FILE` /
`KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`；变量缺失时**跳过** release 签名，
构建仍然成功（产出未签名 APK），以免外部贡献者的 PR 因拿不到 Secrets 而失败。

## 本地签发 release 包

```bash
export KEYSTORE_FILE=/path/to/ssqpick-release.jks
export KEYSTORE_PASSWORD=...
export KEY_ALIAS=ssqpick
export KEY_PASSWORD=...
./gradlew assembleRelease
```

## 验证一个 APK 是谁签的

```bash
keytool -printcert -jarfile app-release.apk | grep SHA256
```

输出必须与上面的 `SHA-256` 完全一致。
