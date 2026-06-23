package com.findit.app.ui.help

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val JSON_TEMPLATE = """{
  "add": [
    {
      "name": "螺丝刀",
      "tags": ["工具"],
      "location": "书架第二层3号收纳盒",
      "note": "十字头，中号"
    }
  ],
  "update": [
    {
      "name": "螺丝刀",
      "newLocation": "工具箱上层",
      "newNote": "已移至工具箱"
    }
  ],
  "delete": [
    {
      "name": "旧充电线"
    }
  ],
  "query": [
    {
      "query": "感冒药",
      "keywords": ["感冒", "药", "胶囊"],
      "tags": ["药品", "常备药"],
      "location": "药箱"
    }
  ]
}"""

private const val AI_PROMPT = """你是 FindIt 收纳管理 App 的自然语言整理助手。用户会用自然语言描述收纳、移动、修改、删除或查找需求。你需要根据用户意图输出适合 FindIt 使用的内容。

你必须先判断用户意图：
1. 如果用户是在新增、修改、删除或查询物品记录，输出合法 JSON。
2. 查询需求也要输出 JSON，使用 query 字段，这样 FindIt 可以直接执行查询。
3. 不要向用户反问，尽量根据用户已有描述生成结果。

一、生成 JSON 的情况

FindIt 支持以下 JSON 结构：

{
  "add": [
    {
      "name": "物品名称",
      "tags": ["标签1", "标签2", "标签3"],
      "location": "地点",
      "note": "备注"
    }
  ],
  "update": [
    {
      "name": "原物品名称",
      "newName": "新物品名称",
      "newTags": ["新标签1", "新标签2"],
      "newLocation": "新地点",
      "newNote": "新备注"
    }
  ],
  "delete": [
    {
      "name": "物品名称"
    }
  ],
  "query": [
    {
      "query": "主要查询词",
      "keywords": ["可能的名称子串1", "可能的名称子串2"],
      "tags": ["可能标签1", "可能标签2"],
      "location": "可能地点"
    }
  ]
}

JSON 输出规则：
- 只输出用户本次实际需要的顶层字段，不要输出空数组。
- 必须是合法 JSON，必须使用英文双引号。
- 不要用 Markdown 代码块包裹。
- 不要在 JSON 中添加注释。
- 不要输出 JSON 之外的解释。

新增规则：
- 当用户说“厨房顶柜里有沙拉酱和番茄酱”“我把螺丝刀、卷尺放在工具箱”等，识别为 add。
- 提取所有物品名称。
- 提取完整地点，例如“厨房的顶柜”“书房第二个抽屉”。
- 多个物品在同一地点时，为每个物品分别生成一个对象。
- 为每个新增物品根据常识生成 3-5 个标签。
- 标签应帮助搜索，可包含类别、用途、场景、属性、材质等。
- 标签不要包含地点，不要编造品牌、价格、日期、数量。
- note 只有用户明确提供备注、数量、状态、保质期等信息时才填写。

新增示例：
用户：现在厨房的顶柜里有沙拉酱和番茄酱
输出：
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

修改规则：
- 当用户说“把沙拉酱移到冰箱门架”“番茄酱改名为亨氏番茄酱”“给护照加上证件和出国标签”，识别为 update。
- 原物品名写入 name。
- 新名称写入 newName。
- 新地点写入 newLocation。
- 新标签写入 newTags。
- 新备注写入 newNote。

删除规则：
- 当用户说“删除沙拉酱”“番茄酱已经用完了，删掉”“这条旧充电线记录不要了”，识别为 delete。

二、查找需求的情况

当用户表达“我想找……”“……放哪了”“有没有……”“找一个能……的东西”“哪里有……”“我需要……但不知道叫什么”等查找意图时，输出 query JSON。

query 字段规则：
- query 是最推荐优先搜索的词。
- keywords 是可能匹配物品名称的关键词或子字符串。
- tags 是这个物品可能带有的标签。
- location 是用户提到或最可能出现的地点；不确定时可以省略。
- 不要编造确定存在的物品，只能生成搜索条件。
- 关键词要短，适合直接搜索。

查找示例：
用户：我想找一个能拧小螺丝的东西，不知道叫什么
输出：
{
  "query": [
    {
      "query": "螺丝",
      "keywords": ["螺丝", "起子", "螺丝刀", "批头"],
      "tags": ["工具", "维修", "五金", "小工具"],
      "location": "工具箱"
    }
  ]
}

查找示例：
用户：我之前放的感冒药在哪
输出：
{
  "query": [
    {
      "query": "感冒",
      "keywords": ["感冒", "药", "胶囊", "片"],
      "tags": ["药品", "常备药", "医疗", "退烧"],
      "location": "药箱"
    }
  ]
}

三、语音助手或快捷方式调用

如果用户需要通过语音助手、快捷方式或 URL 打开 FindIt 执行操作，可以把 JSON 进行 URL 编码后放入以下格式：

findit://json?payload=URL编码后的JSON

也可以使用：

findit://batch?json=URL编码后的JSON

例如 JSON 为：
{"query":[{"query":"感冒","keywords":["感冒","药"],"tags":["药品"]}]}

URL 编码后可拼成：
findit://json?payload=%7B%22query%22%3A%5B%7B%22query%22%3A%22%E6%84%9F%E5%86%92%22%2C%22keywords%22%3A%5B%22%E6%84%9F%E5%86%92%22%2C%22%E8%8D%AF%22%5D%2C%22tags%22%3A%5B%22%E8%8D%AF%E5%93%81%22%5D%7D%5D%7D
"""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("使用说明") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // About
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("关于 findIt", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "findIt 是一款家庭物品管理工具，帮助你记录物品的存放位置，方便日后查找。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // How to use
            Section(title = "基本操作") {
                Tip("添加物品", "点击首页右上角 + 按钮，输入名称、标签、位置和备注")
                Tip("搜索物品", "点击首页搜索图标，支持按名称、标签、位置模糊搜索")
                Tip("编辑/删除", "点击物品进入详情页，可编辑或删除")
            }

            Section(title = "地点管理") {
                Tip("添加地点", "在底部「地点管理」页面手动添加常用地点")
                Tip("合并地点", "如果你有多个名称指向同一个地方（如「书柜」和「卧室柜子」），可以长按拖动一个到另一个上进行合并。合并后搜索任一名称都能找到对应物品。也可以点击合并按钮选择目标。")
            }

            Section(title = "AI整理（批量导入）") {
                Text(
                    "点击首页底部「开始AI整理」按钮，会优先读取剪切板中的 JSON 并弹窗确认执行；取消后可进入文本框手动编辑。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Section(title = "AI 大模型提示词") {
                Text(
                    "把下面的提示词复制给其他 AI 大模型，可以让它把自然语言整理成 FindIt 可用 JSON；新增、查询、修改、删除都可以直接交给 App 执行。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        text = AI_PROMPT,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 13.sp),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("findIt AI提示词", AI_PROMPT))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("复制 AI 提示词到剪贴板")
                }
            }

            Section(title = "数据导出") {
                Tip("导出 Excel", "首页右上角菜单 → 导出 Excel，可分享给他人查看")
                Tip("备份数据库", "首页右上角菜单 → 备份数据库，导出 SQLite 文件用于数据恢复或跨平台迁移")
            }

            // JSON Template
            Section(title = "JSON 模板") {
                Text(
                    "以下模板可直接复制使用，根据实际需求修改内容；query 可用于查询物品。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        text = JSON_TEMPLATE,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("findIt JSON模板", JSON_TEMPLATE))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("复制模板到剪贴板")
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun Tip(title: String, description: String) {
    Text(
        "• $title：$description",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
