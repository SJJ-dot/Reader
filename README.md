# 一个完全离线的小说阅读器
## kotlin 协程、rhino、Javascript

| <img src="img/1.jpg" width = "180" height = "360"/>        | <img src="img/2.jpg" width = "180" height = "360"/>   |  <img src="img/Screenshot_1.jpg" width = "180" height = "360"/>  |
| - | - |- |

## 1.0.0
完善书源管理功能，支持导入网络书源

# 书源规则参考 
书源js脚本需包含3个方法参考：
[默认书源地址](https://github.com/SJJ-dot/reader-repo/blob/main/BookSource/default.json)
```
脚本模板还没写……
```
脚本导入
```
{
  "group": "默认书源",
  "bookSource": [
    {
      "source": "古古小说网（55小说网）",
      "js": "base64 js",
      "version": 2,
      "original": false,
      "enable": true,
      "requestDelay": -1,
      "website": ""
    }
  ]
}
```