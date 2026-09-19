# AstaPS

[English](README.md) · 繁體中文

一個基於 Grasscutter 的《原神》**7.0.0** 私人伺服器。

> 這是一個研究與保存性質的專案，與 HoYoverse / miHoYo 沒有任何從屬、背書或關聯，亦不作商業用途。

## 這是什麼

AstaPS 的血統是 Grasscutter → LunaGC → Chiori，並在此之上繼續走：一邊把伺服器補到 7.0.0 的內容，一邊把底下那些零件重寫，而不是繞著它們打補丁。

- **內容跟得上版本。** 怪物與裝置的生成資料對齊 7.0.0，深境螺旋輪換、秘境、聖遺物商店、戰令等營運內容都在。
- **撐得住出事的那天。** 寫庫拆成四個有界執行緒池，塞滿時回壓而不是把玩家的存檔丟掉；某個世界在 tick 裡拋例外不再讓其他所有人的世界一起停住；MongoDB 連不上時看門狗會壓住 tick，而不是讓遊戲繼續跑在一個根本記不下來的資料庫上。
- **出問題看得見。** 狀態日誌定時輸出 CPU、記憶體、GC 和每個執行緒池的佇列深度，`/api/status` 也以 HTTP 提供同一份數字。
- **全英文。** 原始碼、註解、提交訊息、指令輸出皆然。

## 接下來

不出意外的話：更新到 **7.1**，接著改掉 UID 格式，再把預設加入的任務移除。

## 需求

| | |
|---|---|
| Java | 編譯需要 21。原始碼目標是 17，但虛擬執行緒等 21 的 API 是對著 JDK 自身的類別編譯的。 |
| MongoDB | Community Server，啟動伺服器前必須先跑起來。 |
| 遊戲客戶端 | 原神 7.0.0 |
| 資源檔 | 7.0.0 的資源包，解壓到伺服器目錄下的 `resources/`。本專案不提供。 |

## 編譯

```
./gradlew jar -PskipHandbook=1
```

`grasscutter.jar` 會產生在專案根目錄。拿掉 `-PskipHandbook=1` 會一併編譯遊戲內手冊，那一步需要 NodeJS，沒有就會失敗。

Windows 用 `.\gradlew.bat`，或直接執行 `gradlew-jar.bat`。

## 執行

1. 啟動 MongoDB。
2. 把 7.0.0 資源包放進 `resources/`。
3. 先跑一次 jar。它會寫出 `config.json`，缺少必要東西時會停下來。
4. 再跑一次。Dispatch 預設監聽 `8088`，遊戲伺服器 `22101`。
5. 把客戶端指向 dispatch。用 Fiddler、mitmproxy 之類的代理可以，客戶端補丁也可以。

### 帳號

沒有註冊網頁。建立帳號有兩條路：

- **從主控台。** `account create <使用者名稱> [uid] [密碼]`
- **登入時直接註冊。** 用一個沒人占用的名字登入就等於註冊。開啟 `account.useIntegrationPassword` 後，在使用者名稱欄填 `帳號&&密碼`、密碼欄留空即可 —— 適合啟動器密碼欄不能用的情況。閉服可用 `account.autoCreate` 關掉整套機制。

密碼以 BCrypt 雜湊儲存。主控台需要 `server.game.enableConsole` 設為 `true` 才會接受輸入。

## 設定

全部在首次執行產生的 `config.json` 裡。比較值得知道的幾項：

| | |
|---|---|
| `server.game.enableConsole` | 主控台是否接受指令。關閉時連提示符都不會出現。 |
| `server.game.watchdog` | 定時 ping MongoDB，連不上時壓住遊戲 tick。定時重啟預設關閉 —— 它就是直接結束行程，需要外部有東西把伺服器再拉起來。 |
| `server.threadPools` | 寫庫執行緒池的個別調參。每一項預設都是 `-1`，意思是「這個不要動」。 |
| `account.maxPlayer` | 登入時實際生效。`-1` 為不限。 |
| `server.game.gameOptions.artifactShop` | 聖遺物商店，見下。 |

### 聖遺物商店

全部 290 件五星聖遺物 —— 已實裝 62 套的每一個部位 —— 在雜貨店出售。每次購買都會重新擲出一件，而不是給你一份固定副本，跟聖遺物秘境一樣：主詞條從該部位真正的詞條池抽，副詞條來自遊戲自身的詞綴表，所以聖遺物上的每個數字都是遊戲本身擲得出來的。

到手時為 +20、九次副詞條強化，權重偏向暴擊率、暴擊傷害、攻擊力%、元素精通與各類傷害加成，且在各檔位中偏高。把 `critWeight`、`damageWeight`、`highRollBias` 設為 `1`、`1`、`0` 就是普通未加權的秘境掉落。

## 指令

`help` 會列出全部。幾個常用的：

| | |
|---|---|
| `give` | 角色、武器、聖遺物、材料。預設 100 級。 |
| `account` | 建立、刪除帳號，重設密碼。 |
| `banip` / `unbanip` | 封禁位址。封 IP 會連帶封掉從該位址登入的帳號。 |
| `sysmail` | 對全體玩家發送系統郵件。 |

## 授權

本專案採用 **GNU General Public License v3.0**，見 [`LICENSE`](LICENSE)。

`LICENSE-ClassGraph.txt` 不是本專案的授權條款。ClassGraph 是一個 MIT 授權的相依套件，它的 class 會被打包進 `grasscutter.jar`，而 MIT 只要求該聲明隨之一起散布，所以這個檔案保留。

## 致謝

Grasscutter，以及建立在它之上的 LunaGC 與 Chiori。本倉庫根部的匯入提交中以姓名列出了各位作者。
