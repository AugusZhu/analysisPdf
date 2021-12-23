# analysisPdf
解析PDF文件并生成TXT/PNG/WORD

## 1.解析PDF文件为TXT文件
 - 调用接口
 `service_ip:port/utils/pdf/analysisPdf2Text`
 - 请求类型
 `POST`
 - 接口参数
 `filePath`
 - 完整示例
 `service_ip:port/utils/pdf/analysisPdf2Text?filePath=D:/Others/XXX统-20210511.pdf`
 - 返回示例
 `{
    "returnType": "SUCCESS",
    "content": "解析成功"
}`

## 2.解析PDF文件为PNG图片
 - 调用接口
 `service_ip:port/utils/pdf/analysisPdf2Image`
 - 请求类型
 `POST`
 - 接口参数
 
  |参数名|参数实例|参数解释|
  |-----|-----|-----|
  |PdfFilePath|D:/Others/Java开发手册.pdf|待转换pdf的具体路径|
  |dstImgFolder|D:/Others/images|转后图片存放的路径|
  |dpi|96|DPI参数 默认96|
 
 - 完整示例
 `service_ip:port/utils/pdf/analysisPdf2Image?PdfFilePath=D:/Others/Java开发手册.docx&dstImgFolder=D:/Others/images&dpi=96`
 - 返回示例
 `{
    "returnType": "FAILURE",
    "content": "传入文件不是PDF文档,请检查！"
}`

## 3.解析PDF文件为Word文档
 - 调用接口
 `service_ip:port/utils/pdf/analysisPdf2Doc`
 - 请求类型
 `POST`
 - 接口参数
 
  |参数名|参数实例|参数解释|
  |-----|-----|-----|
  |PdfFilePath|D:/Others/Java开发手册.pdf|待转换pdf的具体路径|
  |docFolder|D:/Others/doc|转后word存放的路径|
 
 - 完整示例
 `service_ip:port/utils/pdf/analysisPdf2Doc?PdfFilePath=D:/Others/Java开发手册.pdf&docFolder=D:/Others/doc`
 - 返回示例
 `{"returnType":"SUCCESS","content":"转换成功"}`
 
- 特殊说明
`转word使用的jar为基础免费版本,只可转换10页及以内的文档内容`
