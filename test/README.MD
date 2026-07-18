## 《修仙》是一款书源驱动的免费小说阅读器📕，可通过AI🤖自动完成书源新建、更新
## 如果喜欢就动动小手点个star，或者推荐给你的朋友吧😁😁😁😁
>本应用使用 `chaquopy` `Python` 脚本引擎，对于本教程以外的Python脚本，本应用无法识别脚本是否安全，需要用户自行分辨
## 制作书源
- 如果不会编程也不会使用AI辅助编程，建议使用书源教程中的书源或者使用[`开源阅读`](https://github.com/search?q=%E9%98%85%E8%AF%BB%E4%B9%A6%E6%BA%90&type=repositories)的书源。
#### 书源接口介绍（`Python`）
```python
def verify():(可选)，不支持搜索功能时提供，避免验证书源失败
def getSiteUrl():(可选)，站点地址，直接导入书籍链接时用来匹配站点，书城`加书架`时匹配书源
def search(query):(可选)，搜索书籍，`search`和`getSiteUrl`至少需要一个否则书源即使导入也无法使用
def getDetails(url):(必选)，加载书籍详情信息和章节列表（重要）
def getChapterContent(url):(比选)，加载章节内容
```
#### 自定义书源
1. 模版选择：
   - 有人机验证选择[顶点小说](https://github.com/SJJ-dot/Reader/blob/BookSource/test/py/%E9%A1%B6%E7%82%B9%E5%B0%8F%E8%AF%B4.py)并打开APP设置页的测试接口，测试接口默认关闭，开启时可在局域网内调用APP内的人机验证接口
   - 无人机验证选择[小原文学网](https://github.com/SJJ-dot/Reader/blob/BookSource/test/py/%E5%B0%8F%E5%8E%9F%E6%96%87%E5%AD%A6%E7%BD%91.py)”。
2. 如果有`AI`，将对应站点网址给到`AI`，让`AI`根据模板修改即可；如果没有`AI`则需要配置`Python3.12`开发环境
#### 导入自定义书源
   - 单个书源可以在APP新建书源直接复制粘贴书源源码
   - 使用打包的脚本将书源压缩为ZIP放在`GitHub`使用`链接`导入或者`手机本地`直接导入`文件`
   - 例如本教程书源文件导入链接示例：https://raw.githubusercontent.com/SJJ-dot/Reader/refs/heads/BookSource/test/BookSource/default.json.gzip
   - 或者使用jsdelivr加速：https://cdn.jsdelivr.net/gh/SJJ-dot/Reader/test/BookSource/default.json.gzip


