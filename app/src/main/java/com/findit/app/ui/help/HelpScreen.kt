package com.findit.app.ui.help

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.findit.app.ui.settings.ListRenderMode
import com.findit.app.ui.settings.getAutoRenderThreshold
import com.findit.app.ui.settings.getListRenderMode
import com.findit.app.ui.settings.isAutoHideChromeEnabled
import com.findit.app.ui.settings.isColorfulTagMarkersEnabled
import com.findit.app.ui.settings.setAutoRenderThreshold
import com.findit.app.ui.settings.setAutoHideChromeEnabled
import com.findit.app.ui.settings.setColorfulTagMarkersEnabled
import com.findit.app.ui.settings.setListRenderMode

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
    var colorfulTagMarkers by remember { mutableStateOf(isColorfulTagMarkersEnabled(context)) }
    var autoHideChrome by remember { mutableStateOf(isAutoHideChromeEnabled(context)) }
    var listRenderMode by remember { mutableStateOf(getListRenderMode(context)) }
    var renderModeExpanded by remember { mutableStateOf(false) }
    var autoRenderThreshold by remember { mutableStateOf(getAutoRenderThreshold(context)) }
    var showThresholdDialog by remember { mutableStateOf(false) }
    var thresholdInput by remember { mutableStateOf(autoRenderThreshold.toString()) }

    if (showThresholdDialog) {
        AlertDialog(
            onDismissRequest = { showThresholdDialog = false },
            title = { Text("自动模式阈值") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "当物品数量小于或等于该数值时使用普通滚动；超过后使用懒加载滚动。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = thresholdInput,
                        onValueChange = { value ->
                            thresholdInput = value.filter { it.isDigit() }.take(4)
                        },
                        singleLine = true,
                        label = { Text("阈值") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val threshold = thresholdInput.toIntOrNull()?.coerceAtLeast(1) ?: 64
                        autoRenderThreshold = threshold
                        thresholdInput = threshold.toString()
                        setAutoRenderThreshold(context, threshold)
                        showThresholdDialog = false
                    }
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showThresholdDialog = false }) { Text("取消") }
            }
        )
    }

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

            Section(title = "显示设置") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "彩色 tag 分隔块",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "关闭后，物品卡片中的 tag 分隔块会统一使用固定颜色，可能减少部分设备上的滚动开销。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = colorfulTagMarkers,
                            onCheckedChange = { enabled ->
                                colorfulTagMarkers = enabled
                                setColorfulTagMarkersEnabled(context, enabled)
                            }
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "滑动时自动收起顶栏和底栏",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "默认关闭以保持更稳定的信息流手感；开启后，下滑浏览时会隐藏顶栏和底栏，上滑时恢复。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoHideChrome,
                            onCheckedChange = { enabled ->
                                autoHideChrome = enabled
                                setAutoHideChromeEnabled(context, enabled)
                            }
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "列表渲染模式",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "自动模式会在少量数据时使用普通滚动，数据变多后切换为懒加载滚动。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            ExposedDropdownMenuBox(
                                expanded = renderModeExpanded,
                                onExpandedChange = { renderModeExpanded = !renderModeExpanded }
                            ) {
                                OutlinedTextField(
                                    value = listRenderMode.label,
                                    onValueChange = {},
                                    readOnly = true,
                                    singleLine = true,
                                    label = { Text("模式") },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = renderModeExpanded)
                                    },
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = renderModeExpanded,
                                    onDismissRequest = { renderModeExpanded = false }
                                ) {
                                    ListRenderMode.entries.forEach { mode ->
                                        DropdownMenuItem(
                                            text = { Text(mode.label) },
                                            onClick = {
                                                listRenderMode = mode
                                                setListRenderMode(context, mode)
                                                renderModeExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "自动模式阈值：$autoRenderThreshold",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "默认 64，可按这台设备的实际手感调整。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = {
                                thresholdInput = autoRenderThreshold.toString()
                                showThresholdDialog = true
                            }
                        ) {
                            Text("修改")
                        }
                    }
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
