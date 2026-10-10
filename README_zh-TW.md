# AstaPS

[English](README.md) · [简体中文](README_zh-CN.md) · 繁體中文

一個基於 Grasscutter 的《原神》**7.1.0** 私人伺服器。

> 這是一個研究與保存性質的專案，與 HoYoverse / miHoYo 沒有任何從屬、背書或關聯，亦不作商業用途。

如果你可以修復錯誤，請幫助我。

## 這是什麼

- **內容跟得上版本。** 怪物與裝置的生成資料對齊 7.1.0，深境螺旋輪換、秘境、聖遺物商店、戰令等營運內容都在。
- **撐得住出事的那天。** 寫庫拆成四個有界執行緒池，塞滿時回壓而不是把玩家的存檔丟掉；某個世界在 tick 裡拋例外不再讓其他所有人的世界一起停。
- **出問題看得見。** 狀態日誌定時輸出 CPU、記憶體、GC 和每個執行緒池的佇列深度，`/api/status` 也以 HTTP 提供同一份數字。
- **全英文。** 原始碼、註解、提交訊息、指令輸出皆然。

## 需求

| | |
|---|---|
| Java | **JDK 21** 用於編譯，**Java 21** 用於執行。AstaPS 會使用虛擬執行緒等 Java 21 API。 |
| MongoDB | Community Server，啟動伺服器前必須先跑起來。 |
| 遊戲客戶端 | 原神 7.1.0。官方客戶端會校驗 region 的簽名，要連私服需要另外打客戶端補丁，例如 [hk4e-patch-universal](https://github.com/capyb2222/animegamepatch)。AstaPS 本身不附帶補丁。 |
| 資源檔 | 7.1.0 的資源包，解壓到伺服器目錄下的 `resources/`。如果你沒有資源檔，可以透過[該連結](https://github.com/MeChen618/AstaPS-Resource)下載。 |

## 編譯

編譯前先確認兩個 Java 指令都指向 21：

```
java -version
javac -version
```

接著執行：

```
./gradlew jar -PskipHandbook=1
```

`grasscutter-7.1.0.jar` 會產生在專案根目錄。拿掉 `-PskipHandbook=1` 會一併編譯遊戲內手冊，那一步需要 NodeJS，沒有就會失敗。

Windows 用 `.\gradlew.bat`，或直接執行 `gradlew-jar.bat`。

## 執行

1. 啟動 MongoDB。
2. 把 7.1.0 資源包放進 `resources/`。
3. 先跑一次 jar。它會寫出 `config.json`，缺少必要東西時會停下來。
4. 再跑一次。Dispatch 預設監聽 `8088`，遊戲伺服器 `22101`。
5. 把客戶端指向 dispatch。用 Fiddler、mitmproxy 之類的代理可以，客戶端補丁也可以。

### 帳號

沒有註冊網頁。建立帳號有兩條路：

- **從主控台。** `account create <使用者名稱> [<密碼>] [@UID]`（密碼與 UID 均可選，新帳號僅使用設定中的預設權限，不會自動取得 `*` 管理員權限）。
- **登入時直接註冊。** 用一個沒人占用的名字登入就等於註冊。開啟 `account.useIntegrationPassword` 後，在使用者名稱欄填 `帳號&&密碼`、密碼欄留空即可，很方便在代理一堆客戶端時用。

設定的密碼會以 BCrypt 雜湊儲存。不指定密碼時，帳號暫不驗證密碼，可稍後使用 `account resetpassword` 設定。主控台需要 `server.game.enableConsole` 設為 `true` 才會接受輸入。

## 指令

內建指令已改用 **Picocli**，支援位置參數、具名選項與子指令。輸入 `help` 可列出指令及別名，`help <指令>` 查看指令語法，或輸入 `help <指令> <子指令>` 查看子指令語法（例如 `help teleport pos`）。參數輸入錯誤時會顯示原因及對應語法。互動式伺服器主控台使用 JLine 提供 **Tab 自動補齊**。遊戲內指令以 `/` 開頭，主控台則不需要。

**玩家選擇器（`playerSelector`）：** `@UID`（玩家 UID）、`username@`（帳號使用者名稱）或 `username@UID`（使用者名稱和 UID 均須相符）。指定既有玩家時使用此語法。

| 指令 | 用途 |
|---|---|
| `give` | 發放角色、武器、聖遺物和材料；支援 `--amount`、`--level` 等選項，預設等級 100。 |
| `account create / clone / delete / resetpassword` | 建立、複製、刪除帳號和重設密碼，**僅限伺服器主控台**。`account passwd` 是別名。 |
| `ban <playerSelector> [endTime] [原因...]` | 停權帳號，需要 `server.ban` 權限；停權其他帳號還需要 `server.ban.others`。`endTime` 為 Unix 時間戳記。 |
| `ban <IPv4> [原因...]` / `unban <IPv4>` | 使用 `server.banip` 權限管理永久 IP 封鎖，無須輸入 keystore 金鑰。 |
| `unban <playerSelector>` | 解除帳號停權，需要 `server.ban` 權限；處理其他帳號還需要 `server.ban.others`。 |
| `kick <playerSelector>` | 使用 `server.kick` 權限踢出線上玩家；已移除 `restart` 別名與金鑰參數。 |
| `mail send` / `mail system` | 向指定玩家或所有玩家寄送郵件、管理系統郵件，取代舊的 `sysmail`。 |
| `announce send <content...>` / `announce template <templateId>` | 發送臨時公告或發佈公告範本，`announce tpl` 是 `template` 的別名。 |
| `say <message...>` | 傳送伺服器訊息，舊命令 `sendMessage` 已移除。 |
| `player list [--uid]` | 列出線上玩家；`--uid` 同時顯示 UID，取代舊命令 `list [uid]` 及 `players`。 |
| `coop [guestSelector] <hostSelector>` | 將訪客送入線上房主的世界；省略訪客時使用目前命令目標（遊戲內預設自己）。 |

伺服器主控台範例：

```text
help give
help teleport pos
give 202 --amount 3 @10001
tp pos 1000 200 300 3 @10001
account create alice
account create bob secret @10001
account clone alice alice-copy @10002
```

在 `account create` 和 `account clone` 中，可選的 `@UID` 指定**新帳號的 UID**。複製前來源玩家必須離線，好友關係和共用音遊譜面不會複製。重設密碼使用 `account resetpassword <使用者名稱> <新密碼>`（別名 `account passwd`），會撤銷舊登入權杖和工作階段權杖，並中斷玩家連線。

詳細說明請見 [CLI 遷移文件](docs/cli-picocli-migration.md)；外掛作者請參閱 [指令 API v5 文件](docs/plugin-command-api-v5.md)。

## TPS 射擊玩法（7.1）

至冬的第三人稱射擊玩法可以玩：槍械和手榴彈裝備在角色原本的武器旁邊，在 TPS 秘境裡瞄準射擊。指令需要 `player.tps` 和 `player.enterdungeon` 權限。

**1. 取得武器**

```
/tps give
/tps give 224001
/tps accessory
```

第一條指令發放全部八把 TPS 武器（224001–224008）；第二條只發放 224001；第三條解鎖已擁有武器的配件。

**2. 進入 TPS 秘境**

```
/dungeon 10955
/dungeon 10953
/dungeon 10960
```

每行都是獨立指令。10955 是射擊靶場，10953 和 10960–10964 是灰原（Emerged Grey Field）各關。

進入後，隊伍會換成 TPS 旅行者（與你的旅行者同性別，20 級），裝備你的 TPS 配裝，第一次進入時是 224001。在秘境裡換的武器會保存成你的配裝。離開秘境後隊伍會恢復原狀。

**3. 秘境外**

任何角色都能裝備 TPS 武器，方便試用：

```
/tps wear 224001 224004
/tps refill
```

第一條指令裝備步槍和手榴彈（最多兩把槍、一顆手榴彈），第二條補滿彈藥。

彈藥處理仍有部分屬於實驗性質。伺服器端的實作細節、`/tps ammo` 的切換選項，以及尚未確定的部分，見 [docs/tps/README.md](docs/tps/README.md)。

## 授權

本專案採用 **GNU General Public License v3.0**，見 [`LICENSE`](LICENSE)。

`LICENSE-ClassGraph.txt` 不是本專案的授權條款。ClassGraph 是一個 MIT 授權的相依套件，它的 class 會被打包進 `grasscutter-7.1.0.jar`，而 MIT 只要求該聲明隨之一起帶著。

## 致謝

本伺服器基於 **Grasscutter**。參考專案：**LunaGC**、**HunkyMeow**。

「需求」中提到的客戶端補丁 [hk4e-patch-universal](https://github.com/capyb2222/animegamepatch) 由 **capyb2222** 維護，基於 [xeondev](https://git.xeondev.com/reversedrooms/hk4e-patch) 的原始 hk4e-patch 與 [oureveryday](https://github.com/oureveryday/) 的原始 hk4e-patch-universal。它是獨立專案，以自己的 GPL-3.0 授權發布。

本倉庫根部的匯入提交中，以姓名列出了它所承載的各位作者。

## Proto 來源

協議定義來自 [genshin-protocol](https://gitlab.com/kitkat-multiverse/genshin-protocol)。
