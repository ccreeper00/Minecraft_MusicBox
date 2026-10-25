# 悦音盒 MusicBox

Minecraft Java 版 **1.21.11** 的 NeoForge **21.11.45** 客户端模组：扫描本地音乐，用 GUI 选曲、播放、暂停、切歌、拖动进度和调节音量。

## 支持版本

- **Minecraft：1.21.11**
- **NeoForge：21.11.45 及以上、低于 21.12**

## 1.5.0 更新

- **音量快捷键**：`PageDown` 音量 -，`PageUp` 音量 +，每步 5%；可在按键绑定里改。
- **只在世界内播放**：离开世界/回到标题界面时立即停止并清空当前曲目；主菜单不会再响，也不能从主菜单启动播放。
- 处于聊天、背包、菜单等界面时，快捷键不会误触。

## 1.4.0 更新

- **播放队列固定为全部歌曲**：搜索或“收藏”页只过滤列表显示；双击/回车播放后，上一首/下一首和自动连播仍按全部歌曲顺序，不会困在搜索结果里。
- **HUD 可调整大小**：在 `HUD` 定位界面拖动右下角，或滚轮、`-`/`+` 按钮缩放；`hudScale` 保存在配置中。

## 1.3.0 更新

- **正在播放 HUD**：游戏内显示当前歌曲、歌手、播放状态、进度和专辑缩略图。
- **HUD 位置自定义**：音乐界面左下角点 `HUD`，拖动即可定位；位置按屏幕比例保存，窗口缩放后不会跑偏。
- **快捷键**：`K` 播放/暂停、`[` 上一首、`]` 下一首（可在“控制 → 按键绑定 → 悦音盒”改键）。
- HUD 在打开聊天/背包/音乐界面时自动隐藏，避免遮挡。

## 1.2.1 修复

- 修复专辑封面只显示左上角一小块、看起来像灰色色块的问题。
- 现在按封面纹理的完整宽高采样并缩放到右侧封面框；PNG/JPEG 都正常。

## 1.2.0 更新

- **导入图片即添加背景**：把任意 `.png/.jpg/.jpeg` 放进 `musicbox/backgrounds/` 就会自动使用，文件名不限。
- **界面内换背景**：左下角新增“背景”按钮；多张图片时点击循环切换，并自动保存选择。
- 旧的 `musicbox/background.png/jpg/jpeg` 仍然兼容，会排在最前面。
- **左侧歌曲栏可拖动**：拖动中间分隔条调整列表宽度；拖动列表可滚动；滚动条滑块可拖。
- **自定义界面背景**：支持全屏背景图，自动变暗并让主面板半透明。
- **专辑封面**：
  - 同目录侧边图：`歌曲名.png/jpg`、`cover.png/jpg`、`folder.png/jpg`、`front.png/jpg`；
  - MP3 内嵌 ID3v2 `APIC`（PNG/JPEG）；
  - OGG `METADATA_BLOCK_PICTURE`。
- **布局改为**：左侧搜索/全部/收藏/歌曲列表，右侧上方专辑图，右侧下方进度条、音量与播放控制。
- **修复“上一首”**：现在无论当前播放到第几秒，都会切换到上一首，不会重播当前曲。
- MP3/JPEG 封面现在通过 ImageIO 解码；Minecraft 1.21.11 自带的 `NativeImage.read` 只能读 PNG。

## 功能

- `M` 打开/关闭选曲界面；`K` 播放/暂停；`[` / `]` 上一首/下一首；`PageDown` / `PageUp` 调音量（可在“选项 → 控制 → 按键绑定 → 悦音盒”中改键）。
- 游戏内“正在播放”HUD，可在音乐界面点 `HUD` 后拖动定位。
- 自动扫描 `musicbox` 文件夹及子文件夹中的 `.mp3`、`.ogg`、`.wav`。
- 全部 / 收藏两个列表，支持搜索。
- 双击歌曲或按回车播放；`↑` / `↓` 选择；空格暂停/继续。
- 上一首、播放/暂停、下一首；进度条点击/拖动跳转；音量滑条。
- 一首结束自动下一首，播放时游戏内行动栏提示曲名。
- 纯客户端、纯本地：没有 HTTP 请求、没有账号、没有遥测。

## 安装

1. 安装 Minecraft 1.21.11 + NeoForge 21.11.45。
2. 把 `dist/musicbox-1.5.0.jar` 放进游戏实例的 `mods` 文件夹。
3. 启动游戏，按 `M`。

> PCL2/HMCL 开启版本隔离时，`musicbox` 文件夹在**当前版本实例目录**下，例如：
> `D:\PCL2\.minecraft\versions\1.21.11-NeoForge_21.11.45\musicbox`
> 不是全局 `.minecraft\musicbox`。

## 添加音乐与收藏

支持的格式：

| 格式 | 说明 |
| --- | --- |
| `.mp3` | 内置 JLayer 解码，支持 CBR/VBR |
| `.ogg` | OGG Vorbis（不是 Opus） |
| `.wav` | PCM WAV，非 16-bit 会尝试转换 |

不支持 FLAC、M4A/AAC、Opus、加密的 QQ 音乐缓存。若你有权转换自己的文件，可用 ffmpeg：

```bash
ffmpeg -i input.flac -codec:a libmp3lame -q:a 2 output.mp3
ffmpeg -i input.m4a -codec:a libvorbis -q:a 5 output.ogg
```

收藏列表 `musicbox/favorites.m3u`：

```m3u
#EXTM3U
Artist - Song.mp3
subfolder/Another Song.ogg
D:\Music\Legal Copy\Track.wav
```

- 相对路径按 `musicbox` 文件夹解析。
- 也可以只写文件名，模组会按文件名匹配。
- 同时支持 `favorites.txt`，一行一个文件。
- 改完列表后在界面点“刷新”。

曲名/歌手默认从文件名推断：`Artist - Title.mp3`。不读取 ID3 文本标签，但会读取 MP3 的 APIC 封面。

## 背景图

**最简单：直接把图片丢进 `musicbox/backgrounds/`。**

```text
musicbox/
  backgrounds/
    我的壁纸.png
    another-wallpaper.jpg
    anything.jpeg
```

- 文件名随便取，**不需要改配置**。
- 多张图片按文件名排序，进入界面后点左下角 **“背景”** 循环切换；选择会写入配置。
- 如果 `backgrounds` 里没有图，点“背景”会打开这个文件夹。
- 旧的 `musicbox/background.png/jpg/jpeg` 仍然兼容，并会优先使用。

也可以手动指定路径：

```json
{
  "backgroundImage": "D:\\Pictures\\wallpaper.jpg",
  "backgroundDim": 0.35,
  "panelOpacity": 0.82,
  "listWidthRatio": 0.55
}
```

- 路径可以是绝对路径，也可以是相对游戏目录的路径。
- `backgroundDim`：背景变暗程度，0–0.9。
- `panelOpacity`：主面板不透明度，0.55–1.0；想更清楚看到背景就调低。
- `listWidthRatio`：歌曲栏宽度比例，拖动分隔条后会自动保存。
- PNG/JPEG 都支持。

## 专辑封面

模组按以下顺序查找：

1. 音频同目录的侧边图（`歌曲名.png/jpg`、`cover.*`、`folder.*`、`front.*`）；
2. MP3 的 ID3v2 `APIC` 帧；
3. OGG Vorbis 的 `METADATA_BLOCK_PICTURE`。

没有封面时显示占位图。MP3/JPEG 封面已验证可读取；实际显示效果取决于音频里内嵌图片本身。

## 正在播放 HUD

- 播放歌曲后，游戏左上角（默认）会显示 HUD：专辑缩略图、曲名、歌手、播放状态、进度和时间。
- 打开音乐界面，点左下角 **`HUD`** 进入定位界面。
- 按住 HUD 拖动到任意位置；`Esc` 或“完成”保存，`R` / “恢复默认”回到左上角。
- 拖动右下角、滚轮或 `-`/`+` 按钮调整大小（60%–200%）。
- 位置和大小按比例/倍率保存，改变窗口大小或 GUI 缩放后仍然相对固定。
- 打开聊天、背包、音乐界面时 HUD 自动隐藏。
- 想临时关闭 HUD，把 `config/musicbox.json` 里的 `"showHud"` 改成 `false`，重启游戏。

## 快捷键

默认：

| 功能 | 按键 |
| --- | --- |
| 打开/关闭音乐界面 | `M` |
| 播放/暂停 | `K` |
| 上一首 | `[` |
| 下一首 | `]` |
| 音量 - | `PageDown` |
| 音量 + | `PageUp` |

都可以在“选项 → 控制 → 按键绑定 → 悦音盒”里改键。快捷键只在没有打开其他界面时生效。

## 配置

首次启动生成 `config/musicbox.json`，完整示例：

```json
{
  "musicFolder": "musicbox",
  "volume": 0.7,
  "showNowPlaying": true,
  "backgroundImage": "",
  "backgroundDim": 0.35,
  "panelOpacity": 0.82,
  "listWidthRatio": 0.55,
  "showHud": true,
  "hudX": 0.02,
  "hudY": 0.10,
  "hudScale": 1.0
}
```

`hudX` / `hudY` 是 HUD 左上角相对屏幕的比例（0.0–1.0），`hudScale` 是 0.6–2.0 的缩放倍率；一般用界面里的 `HUD` 拖动/缩放来改，不用手写。`musicFolder` 可以是相对游戏目录的路径，也可以是绝对路径。修改后重进游戏或点“刷新”。

## 从源码构建

需要 JDK 21。项目使用 [ModDevGradle](https://github.com/neoforged/ModDevGradle) 2.0.148：

```bash
./gradlew build
```

产物在 `build/libs/musicbox-1.5.0.jar`。

> 构建需要能访问 `maven.neoforged.net` 获取 NeoForge 21.11.45 工件。如果网络无法访问，直接使用本仓库 `dist/` 中已经编译好的 jar；它已在 1.21.11 + NeoForge 21.11.45 实例中实际启动、开界面、播放 MP3，并验证过内嵌 JPEG 封面。

## 常见问题

**列表是空的**
- 确认文件在实例目录的 `musicbox` 文件夹，而不是全局 `.minecraft`。
- 点“刷新”。
- 看 `logs/latest.log` 里是否有 `musicbox` 相关错误。

**没有声音**
- 模组使用 Java Sound 输出，和 Minecraft 的音量设置相互独立；先检查系统输出设备和音量。
- 看日志中 `MusicBox/Audio` 的警告。
- 某些极低/极高采样率的 WAV 可能不被系统混音器支持，转成 44.1kHz/48kHz 16-bit 即可。

**专辑图不显示**
- 放一张 `cover.jpg` 到歌曲同目录最稳。
- 内嵌封面必须能被 ImageIO 解码（JPEG/PNG）；某些罕见 ID3 变体可能不支持。
- 看日志中 `Could not decode image` 警告。

**背景图不显示**
- 确认是 `musicbox/background.png/jpg/jpeg`，或 `backgroundImage` 路径存在。
- 重新打开界面（背景在打开界面时加载）。
- `panelOpacity` 太高会几乎遮住背景，可调到 0.7 左右。

**按键 M 冲突**
- 在“选项 → 控制 → 按键绑定 → 悦音盒”里改键。

## 许可

- 模组代码：MIT，见 `LICENSE`。
- 内置 MP3 解码器 JLayer 1.0.1：LGPL-2.1，未修改，见 `THIRD_PARTY_NOTICES.md`、`LICENSE-JLayer.txt` 和 `third_party/jlayer-1.0.1-sources.jar`。


