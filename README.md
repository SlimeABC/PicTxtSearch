# PicTxtSearch

[简体中文](README.md) · [English](README.en.md)

PicTxtSearch 是一款 Android 图片文字搜索工具：识别图片中的文字，并像搜索网页一样在图片上定位、高亮关键词。

## 功能

- **文字识别（OCR）**：对图片进行文字识别，支持中文等内容
- **关键词搜索**：支持正则表达式、搜索纯文本、区分大小写
- **高亮定位**：命中的文字在图片与识别结果中高亮显示，可在匹配结果间快速跳转
- **导出图片**：将带高亮标记的图片保存到相册
- **识别记录**：自动保存每次识别结果，支持重命名、多选删除
- **多种输入**：直接拍照或从相册导入图片
- **外观自定义**：深浅色、动态取色、主题色与高亮颜色/不透明度可调
- **多语言界面**：简体中文 / English

## 截图

| | |
|---|---|
| ![搜索界面示例 1](images/1.jpg) | ![设置界面局部](images/2.jpg) |
| 搜索界面示例 1 | 设置界面局部 |
| ![搜索界面示例 2](images/3.jpg) | ![主页面](images/4.jpg) |
| 搜索界面示例 2 | 主页面 |

## 下载

前往 [Releases](https://github.com/SlimeABC/PicTxtSearch/releases) 下载最新的 `PicTxtSearch.apk` 安装即可。

## 技术栈

Kotlin · Jetpack Compose · Material 3 · CameraX · ML Kit 文字识别 · DataStore
