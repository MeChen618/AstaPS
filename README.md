# LunaGC-7.0.0 开发中版本

## 维护者注
这是 girluh 的 LunaGC (https://github.com/girluh/LunaGC) 的一个分支。非常早期开发中，因此预计会有很多 bug。

## Grasscutters 的更新版本，实现了一些新功能。
如果你需要帮助，请在此仓库中创建 issue，我会尽力提供帮助。

本 PS 的功能和特性不保证可用，请自行尝试哪些可用哪些不可用（大部分是坏的）。
这可能是唯一公开的、拥有更新怪物和装置生成数据的 PS！（最高支持到 5.4 版本）

如果你愿意/有能力，欢迎贡献代码……

# 阅读手册（handbook.md）！

# 搭建指南
- 请阅读以下内容，足以让服务器和客户端运行起来。

## 主要需求

- 获取 Java 17 (https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
- 获取 MongoDB Community Server (https://www.mongodb.com/try/download/community)
- 获取 NodeJS (https://nodejs.org/dist/v20.15.0/node-v20.15.0-x64.msi)（用于手册生成）
- 获取游戏版本 REL7.0.0
- 确保已安装 Java 并设置好环境变量。
- 编译服务器（参考本指南中的"编译实际服务器"部分。）
- 下载资源文件 (https://github.com/capyb2222/LunaGC-Resources)，在下载的 LunaGC 文件夹中新建一个名为 resources 的文件夹，然后将资源文件解压到该新文件夹中。
- 将 useEncryption、Questing 和 useInRouting 设置为 false（默认应为 false，如果不是请修改）。
- 修补游戏（见下文）
- 启动服务器（可以使用 Cultivation 或 Fiddler）和游戏客户端，确保也在 LunaGC 控制台中创建账户（否则无法登录）！
- 玩得开心（或者不）

### 修补游戏
- 安装 Rust (https://rust-lang.org/learn/get-started/) 和 Cargo（随 rustup 一起安装）
- 进入 patch/ 文件夹（确保克隆此仓库时使用了 --recurse-submodules 标志）。如果该文件夹为空，请运行 git submodule update --init 或自行克隆 animegamepatch (https://github.com/capyb2222/animegamepatch)
- 运行 cargo build --release 在 target/release 目录下生成 DLL 文件 ext.dll
- 将 DLL 注入游戏。你可以将修补文件重命名为 Astrolabe.dll 并将其放入游戏目录的 GenshinImpact_Data/Plugins 文件夹中。请确保备份 plugins 文件夹中原来的 Astrolabe.dll。

### 开始之前

- 克隆仓库（先安装 Git (https://git-scm.com)）

  git clone --recurse-submodules https://github.com/capyb2222/LunaGC.git

- 现在你可以继续执行下面的步骤。

### 编译实际服务器

需求：
Java Development Kit 17 | JDK (https://oracle.com/java/technologies/javase/jdk17-archive-downloads.html) 或更高版本

- 附注：手册生成在某些系统上可能会失败。要禁用手册生成，请在 gradlew jar 命令后追加 -PskipHandbook=1 参数。

- 对于 Windows：
  .\gradlew.bat
  .\gradlew.bat jar

- 对于 Linux：
  chmod +x gradlew
  ./gradlew
  ./gradlew jar

### 你可以在项目根目录中找到输出的 JAR 文件。

### 手动编译手册
  ./gradlew generateHandbook

## 圣遗物商店

所有官方五星圣遗物部件 —— 共 290 件，覆盖全部 62 个已发布套装的五个部位 —— 在杂货店（蒙德城喷泉旁边的"第二生命"）出售。

每次购买都会重新生成一件圣遗物，而不是提供固定副本，就像圣遗物秘境那样：主词条从该部位的实际词条池中抽取，副词条则来自游戏自身的词缀表，因此圣遗物上的每个数字都是游戏本身可能生成的数值。圣遗物到货时为 +20 级，带有 9 次副词条强化，且权重倾向于暴击率、暴击伤害、攻击力百分比、元素精通和各类伤害加成，同时也倾向于各词条的高档位数值。一次性购买多件将分别独立生成多件圣遗物。

在 config.json 中的 server.game.gameOptions.artifactShop 下进行配置：

选项                   默认值    功能
enabled                true     完全关闭该商品列表。
shopId                 1004     哪个商店出售该物品。1001 是星辉兑换，直接出现在商店菜单中。
costMora / costPrimogems  20000 / 0  每件圣遗物的价格。
costItemId / costItemCount  0 / 0  除摩拉和原石外额外收取的道具。
buyLimit               0        每名玩家每件圣遗物的限购次数。0 表示无限制。
artifactLevel          20       圣遗物到货时的强化等级，范围 0-20。
critWeight             8        暴击率和暴击伤害的权重乘数。1 表示与游戏原生概率相同。
damageWeight           3        攻击力百分比、元素精通和各类伤害加成的权重乘数。
highRollBias           3        各项词条偏向四档数值中最高档的强度。0 表示均匀随机。

将最后三项设置为 1、1 和 0 即可获得普通、无加权的秘境圣遗物掉落。

## 故障排除

- 确保将 useEncryption 和 useInRouting 都设置为 false，否则可能会遇到错误。
- 要使用 windy，请确保将你的 luac 文件放入 C:\Windy（如果该文件夹不存在则新建）。
- 如果遇到与 MongoDB 连接超时相关的错误，请检查 mongodb 服务是否正在运行。在 Windows 上：按 Windows 键和 R，输入 services.msc，查找 mongodb 服务器，如果未启动，则右键点击并启动。在 Linux 上，可以使用 systemctl status mongod 查看是否正在运行，如果没有运行，则输入 systemctl start mongod。然而，如果在 Linux 上遇到错误 14，请更改 mongodb 文件夹和 .sock 文件的所有者（sudo chown -R mongodb:mongodb /var/lib/mongodb 和 sudo chown mongodb:mongodb /tmp/mongodb-27017.sock），然后再次尝试启动服务。

# 鸣谢

girluh 的 LunaGC (https://github.com/girluh/LunaGC)
kitkat 的 patch (https://github.com/capyb2222/animegamepatch)
Terax 提供的 nt
liuwei is gay