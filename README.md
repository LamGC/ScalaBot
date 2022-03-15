# ScalaBot

基于 [rubenlagus/TelegramBots](https://github.com/rubenlagus/TelegramBots) 的可扩展机器人服务器。 Extensible robot server based
on [rubenlagus/TelegramBots](https://github.com/rubenlagus/TelegramBots).

## 背景

当初开发 Telegram 机器人的时候，发现 [rubenlagus/TelegramBots](https://github.com/rubenlagus/TelegramBots)
是按 Bot 融入应用的方式设计的， 且 AbilityExtension 对 ReplyFlow 不太支持（因为无法获取所属 AbilityBot 的 StateDB）， 所以我尝试提供了一个 Factory 接口，在创建
AbilityExtension 对象时提供扩展将要服务的 AbilityBot 对象，这样 AbilityExtension 就可以不受限的实现功能了。

## 开发版本警告

当前应用处于开发版本状态，在 1.0.0 发布前，任何功能都可能存在不兼容更改，在升级版本前，请仔细阅读更新日志， （如果有）按照迁移指南迁移数据后方可升级；  
由于不遵循迁移指南而导致的损失，本项目相关开发人员不会对此负责。

### 版本号

本项目遵循 SemVer 版本号规范，但在正式版（1.0.0）发布前，可能会存在次版本号更新不向下兼容的问题，请仔细阅读迁移指南进行升级！

## 使用

1. （如果没有准备机器人账号）首先，在 Telegram 中联系 [BotFather](https://t.me/BotFather) ，申请机器人账号。
2. 运行环境需要安装好 Java 11（或更高版本）；
3. 下载 [最新版本](https://github.com/LamGC/ScalaBot/releases/latest) 的 ScalaBot 发行包， 将发行包解压到某个目录中，然后准备一个用于存储 ScalaBot
   运行数据的目录；
4. （可选）如果有需要在非运行目录的路径上运行 ScalaBot（例如以 Service 形式启动，或者使用 Docker），可通过环境变量 `BOT_DATA_PATH` 指定 ScalaBot 的运行目录；
5. 在作为数据存储位置的目录中，执行从分发包中解压出来的 `bin/ScalaBot` 脚本以打开 ScalaBot。 由于首次启动缺少配置文件，ScalaBot 将会初始化配置文件（`config.json` 和 `bot.json`
   ），可按照 [配置文件示例](https://github.com/LamGC/ScalaBot/wiki/Configuration) 进行配置。
6. 将配置文件配置好后，如已下载好需要使用的扩展包，将扩展包移至 `extensions` 文件夹即可。（无需下载的扩展包将由 ScalaBot 自动下载）
7. 如果一切正常，ScalaBot 正常运行，绑定好的 Telegram Bot 账号将会对消息有所反应。

## 开发扩展包

ScalaBot 基于 TelegramBots 开发，所以开发 ScalaBot 的扩展与开发 TelegramBots 的扩展大致相同，  
唯一的差别就是 TelegramBots 要求 `AbilityBot` 手动添加 `AbilityExtension`，而 ScalaBot 通过 SPI 从扩展包中寻找
`BotExtensionFactory`，并通过 `BotExtensionFactory` 创建 `AbilityExtension` 对象，然后添加到 ScalaBot（本质上是一个 `AbilityBot`）。

只需要按照 TelegramBots 的文档开发出 `AbilityExtension`，然后实现 `BotExtensionFactory` 并注册到对应的 SPI 文件中就可以在 ScalaBot 中使用了。

详细文件见项目 Wiki。

## 许可证

ScalaBot 遵循 MIT 许可证开源。

```
Copyright 2022 LamGC

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
```
