# findIt

findIt 是一款面向家庭和个人物品管理的 Android App。它用于记录“什么东西放在哪里”，并通过名称、标签、地点和备注帮助用户快速找回物品。

项目目前已经支持手动录入、搜索、地点管理、AI JSON 批量整理、数据导出，以及可被语音助手或快捷方式调用的 URL 深链接接口。

## 功能概览

- 物品管理：新增、编辑、删除物品，记录名称、标签、地点和备注。
- 搜索物品：支持按物品名称、标签、地点进行模糊搜索。
- 地点管理：支持添加地点，并通过拖拽或菜单把一个地点合并到另一个地点。
- AI 整理：粘贴 JSON 后批量新增、查询、修改、删除物品。
- 剪切板导入：进入 AI 整理页面时，会优先读取剪切板中的 JSON 并提示是否执行。
- 使用说明：内置 JSON 模板和 AI 大模型提示词，可一键复制。
- 数据导出：支持导出 Excel 和备份数据库。
- 语音助手/快捷方式：支持通过 `findit://` URL 直接调用 JSON 操作。

## 技术栈

- Kotlin
- Jetpack Compose
- Room
- Navigation Compose
- Android Gradle Plugin

## 运行环境

- 最低系统版本：Android 7.0（API 24）
- 编译 SDK：Android 15（API 35）
- 推荐 JDK：JDK 21
- Gradle：使用项目自带 Gradle Wrapper

如果本机默认 Java 版本过高，建议在 Android Studio 中把 Gradle JDK 设置为 JDK 21，或在本机的 `~/.gradle/gradle.properties` 中配置：

```properties
org.gradle.java.home=/Users/yinkong/java/jdk-21.0.10.jdk/Contents/Home
```

不要把带有本机绝对路径的 JDK 配置提交到公开仓库。

## 构建

在项目根目录执行：

```bash
./gradlew :app:assembleDebug
```

构建产物位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

安装到已连接的 Android 设备：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## AI JSON 格式

findIt 的 AI 整理功能支持四类操作：`add`、`query`、`update`、`delete`。

### 新增物品

```json
{
  "add": [
    {
      "name": "沙拉酱",
      "tags": ["调味品", "酱料", "食品", "西餐", "厨房用品"],
      "location": "厨房的顶柜",
      "note": "未开封"
    }
  ]
}
```

### 查询物品

```json
{
  "query": [
    {
      "query": "感冒药",
      "keywords": ["感冒", "药", "胶囊"],
      "tags": ["药品", "常备药"],
      "location": "药箱"
    }
  ]
}
```

`query` 会复用 App 内部搜索逻辑，把 `query`、`keywords`、`tags`、`location` 中的词作为搜索条件，并合并展示匹配结果。

### 修改物品

```json
{
  "update": [
    {
      "name": "沙拉酱",
      "newLocation": "冰箱门架",
      "newNote": "已开封"
    }
  ]
}
```

### 删除物品

```json
{
  "delete": [
    {
      "name": "旧充电线"
    }
  ]
}
```

### 混合操作

```json
{
  "add": [
    {
      "name": "蚝油",
      "tags": ["调味品", "酱料", "中餐", "烹饪"],
      "location": "厨房顶柜"
    }
  ],
  "query": [
    {
      "query": "螺丝",
      "keywords": ["螺丝", "螺丝刀", "起子"],
      "tags": ["工具", "维修"]
    }
  ],
  "delete": [
    {
      "name": "旧番茄酱"
    }
  ]
}
```

## 语音助手和快捷方式

App 支持 URL 深链接调用，外部应用、语音助手或系统快捷方式可以通过 URL 传入 JSON。

支持的格式：

```text
findit://json?payload=URL编码后的JSON
findit://batch?json=URL编码后的JSON
```

示例 JSON：

```json
{"query":[{"query":"感冒","keywords":["感冒","药"],"tags":["药品"]}]}
```

URL 编码后可调用：

```text
findit://json?payload=%7B%22query%22%3A%5B%7B%22query%22%3A%22%E6%84%9F%E5%86%92%22%2C%22keywords%22%3A%5B%22%E6%84%9F%E5%86%92%22%2C%22%E8%8D%AF%22%5D%2C%22tags%22%3A%5B%22%E8%8D%AF%E5%93%81%22%5D%7D%5D%7D
```

在语音助手支持打开 URL 的情况下，可以把自然语言交给大模型生成 JSON，再由快捷方式把 JSON 编码并打开 `findit://` 链接，从而完成语音添加、查询、删除物品。

## 给大模型的提示词

App 的“使用说明”页面内置了完整提示词，并提供“一键复制 AI 提示词到剪贴板”按钮。

提示词的目标是让大模型把用户的自然语言转换为 findIt 可执行 JSON。例如：

用户说：

```text
现在厨房的顶柜里有沙拉酱和番茄酱
```

大模型应生成：

```json
{
  "add": [
    {
      "name": "沙拉酱",
      "tags": ["调味品", "酱料", "食品", "西餐", "厨房用品"],
      "location": "厨房的顶柜"
    },
    {
      "name": "番茄酱",
      "tags": ["调味品", "酱料", "食品", "蘸料", "厨房用品"],
      "location": "厨房的顶柜"
    }
  ]
}
```

## 数据说明

findIt 使用本地 Room 数据库存储数据。物品、标签、地点和物品-标签关系均保存在设备本地。

地点合并时，App 会把源地点相关的物品更新到目标地点，并删除源地点，避免同一位置出现多个重复名称。

## 项目状态

项目已经具备基础可用性，适合继续迭代以下方向：

- 更稳定的语音助手快捷方式集成。
- 更完整的数据恢复流程。
- 更细致的物品分类和标签建议。
- 更友好的查询结果展示。
- 发布版签名和应用商店发布配置。

## 许可证

本项目使用 MIT License。详情见 `LICENSE` 文件。
