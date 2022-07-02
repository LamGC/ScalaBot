# scalabot-meta

本模块用于将 ScalaBot 的一些配置相关内容发布出去，以便于其他项目使用。

主要是配置类和相应的 Gson 序列化器（如果有，或者必要）。

## 关于序列化器

强烈建议使用序列化器！由于 Kotlin 与 Gson 之间的一些兼容性问题
（参见[本提交](https://github.com/LamGC/ScalaBot/commit/084280564af58d1af22db5b57c67577d93bd820e)），
如果直接让 Gson 解析 Kotlin Data 类，将会出现一些潜在的问题（比如无法使用默认值）。  
部分序列化器也可以帮助检查字段值是否合法，以防止因字段值不正确导致出现更多的问题
（例如 BotAccount 中，如果 `token` 的格式有误，那么获取 `id` 时将引发 `NumberFormatException` 异常）。
