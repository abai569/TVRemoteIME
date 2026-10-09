# 项目规则（TVRemoteIME 小盒精灵）

本文件是项目级强制规则，所有版本开发、发布操作必须遵守。

## 一、发布纪律（最高优先级，强制）

1. 每个新版本必须走完整发布链路：代码合并 → 推送 master → 打 tag（形如 `2.x.y`）→ CI 构建签名 → 发布 GitHub Release（含 `IMEService-<版本>.apk` asset）。
2. **CI 未成功的版本号不得跳过**：无论构建失败、Release 未产出还是 asset 缺失，必须修复问题后用**同一版本号**重新触发发布；只有该版本成功发布后才能推进下一个版本号。
3. **严禁跳版本发布**：不允许在 2.2.1/2.2.2 失败的情况下直接发布 2.2.3。跳版本会造成在线更新版本跳变、APK 下载 404、用户困惑（历史教训：2.2.1/2.2.2 两次 CI 失败未发布，version.json 已指向 2.2.1 导致电视更新全 404）。
4. 发布成功定义（三者缺一不可）：
   - GitHub Actions run 结论为 `success`；
   - Release 页面存在该 tag，且 asset 为 `IMEService-<版本>.apk`；
   - App 在线更新接口（GitHub 最新 Release API）能返回该 tag。
5. 发布完成后必须验证：查 Actions run 结果、Release asset 列表、readme 下载链接与网页端版本号一致性。

## 二、版本号规则（强制）

6. versionCode 必须单调递增，公式 `major*10000 + minor*100 + patch`（如 2.2.3 → 20203）。禁止使用旧公式 `10+patch`（在 2.2.x 上会倒退，导致电视覆盖安装报降级失败）。
7. versionName 与 tag 完全一致（如 tag `2.2.3` → versionName `2.2.3`）。
8. 版本号同步位置（每次发布必须全部一致）：
   - `IMEService/build.gradle`（versionCode / versionName）
   - `IMEService/src/main/res/raw/index.html`
   - `readme.md`（更新日志新增一条）
   - `CLAUDE.md`（版本记录）

## 三、在线更新机制（强制）

9. App 在线更新**直接读取 GitHub 最新 Release**（`GET https://api.github.com/repos/abai569/TVRemoteIME/releases/latest`），**不再依赖 released/version.json**；`released/` 目录不再维护 APK（CI 也不得再向 master 提交 APK）。
10. 下载走 3 个代理（git-proxy.abai.eu.org / gh-proxy.com / ghfast.top）+ 直连兜底，逐源记录失败原因，失败弹窗必须显示具体错误。
11. APK 下载后必须做完整性校验（getPackageArchiveInfo），半截文件不得安装。

## 四、签名与密钥（强制）

12. 正式签名密钥只存 GitHub Secret（TVREMOTEIME_KEYSTORE_BASE64 / STORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD），CI 构建时还原；本地 keystore 与密码文件禁止入库。
13. 发布 APK 必须使用正式签名（SHA-256 `678c2bfaa61c74ca65a9e3f371dcce4e98b97343019718d4da4147f5483a2d44`，CN=abai569），否则电视覆盖安装失败。

## 五、GitHub 授权（强制）

14. 推送 `.github/workflows/` 修改必须使用具备 `workflow` scope 的授权（Windows 本机 git 凭据已具备，云 VM 设备码登录默认不具备）。**优先在 Windows 本机推送全部改动**。
15. 授权要长期有效（本机凭据/PAT），禁止依赖一次性设备码做发布操作。

## 六、Release 更新说明（强制）

16. **每次发布 Release 必须带更新说明（body）**：升级弹窗的“更新内容”取自 Release body，body 为空时用户只看到版本号、不知道改了什么。CI 发布完成后立即用 `gh api -X PATCH repos/abai569/TVRemoteIME/releases/{id} -f body=...` 补写说明（云端 gh 即可，无需 workflow scope）。
17. 版本号同步**禁止用 sed 全局替换**（会把 readme 日志历史条目标题一并改掉、造成版本记录错乱）；只允许精确替换 build.gradle 的 versionCode/versionName 与各文件中的当前版本串。
18. readme 更新日志只允许**在日志区头部插入新条目**，历史条目禁止改动。
