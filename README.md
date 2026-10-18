# analysisPdf

基于本机 Java 11 的 PDF 转换服务，提供 TXT、PNG 和 DOCX 三个同步 POST 接口。
使用 Spring Boot 2.7.18、PDFBox 3.0.4 和免费 Spire.PDF 9.13.0，不要求升级 JDK。
Spring Boot 2.7 已结束开源维护；本项目保留该版本以兼容 Java 11 和现有 Servlet 部署环境。

## 构建与启动

```sh
java -version
sh ./mvnw clean verify
java -jar target/analyzepdf-0.0.1-SNAPSHOT.war
```

默认地址为 `http://localhost:8088/utils`。Windows 使用 `mvnw.cmd`。
WAR 也可部署到支持 `javax.servlet` 的 Tomcat 9；外部容器的访问前缀由部署配置决定。
首次构建需要下载 Maven 和依赖。代码按 Java 11 编译，所有验证也使用 Java 11。

## 接口与输出

参数是服务端本机文件路径，使用 query 或表单传参，不是文件上传。
保留原来的大小写敏感参数名 `PdfFilePath`、接口路径及 `returnType/content` 响应字段。

| POST 路径（含默认前缀） | 参数 | 输出规则 |
|---|---|---|
| `/utils/pdf/analysisPdf2Text` | `filePath` | PDF 同目录、同基础名的 `.txt`，UTF-8 |
| `/utils/pdf/analysisPdf2Image` | `PdfFilePath`、`dstImgFolder`（可省略）、`dpi`（默认 96） | `<输出目录>/<PDF基础名>/<PDF基础名>_N.png`，页码从 1 开始 |
| `/utils/pdf/analysisPdf2Doc` | `PdfFilePath`、`docFolder`（可省略） | `<输出目录>/<PDF基础名>.docx` |

省略或传空输出目录时，使用源 PDF 所在目录；相对输入路径按服务进程工作目录解析。
PNG 的同名子目录由转换服务管理，重新转换时整体替换，删除旧结果中多余的页。
若该目录含其他文件或符号链接，拒绝覆盖，以免误删用户文件。
TXT/DOCX 同名文件在完整转换并关闭资源后替换；PNG 全部生成后发布目录，发布失败尝试恢复旧目录。
覆盖已有 TXT、DOCX、图片目录及同名页图片时，保留 POSIX 权限位；支持 ACL 的非 POSIX 平台保留 ACL。
不复制所有者及所属组。首次生成的 TXT/DOCX 和图片目录沿用 Java 临时产物的默认权限，
在 POSIX 平台通常分别为 `0600`、`0700`；新增 PNG 页按系统默认权限创建。需要其他账号读取时应由部署方授权。
临时文件、暂存目录及备份目录使用短名称，支持文件系统允许范围内的长文件名。
进程异常退出或断电不属于跨文件系统事务保证；临时目录、备份目录可能需要人工检查。

```sh
curl -X POST -G 'http://localhost:8088/utils/pdf/analysisPdf2Text' \
  --data-urlencode 'filePath=/data/中文文档.pdf'
curl -X POST -G 'http://localhost:8088/utils/pdf/analysisPdf2Image' \
  --data-urlencode 'PdfFilePath=/data/中文文档.pdf' \
  --data-urlencode 'dstImgFolder=/data/images' --data-urlencode 'dpi=96'
curl -X POST -G 'http://localhost:8088/utils/pdf/analysisPdf2Doc' \
  --data-urlencode 'PdfFilePath=/data/中文文档.pdf' \
  --data-urlencode 'docFolder=/data/docs'
```

成功：`{"returnType":"SUCCESS","content":"转换成功"}`（TXT 为“解析成功”）。
转换及参数错误保持原有 HTTP 200 + `FAILURE` 契约，例如：
`{"returnType":"FAILURE","content":"DOCX免费版仅支持转换3页以内的文档，当前共4页"}`。

## 转换能力与资源边界

- TXT 和 PNG 使用 PDFBox，支持全部页面，不受 Spire 免费转换页数限制。
- DOCX 使用免费 Spire，最多完整转换 3 页；超过限制明确失败，保留已有结果。
  不通过提高配置突破引擎能力。需要完整多页 DOCX 时，必须更换有相应能力的引擎或授权版本。
- TXT 提取 PDF 中已有文本，不提供扫描件 OCR；DOCX 的排版保真取决于原文档及引擎。
- 缺少密码的加密 PDF、损坏文件和非 PDF 输入返回失败。
- 单进程同时执行一个转换任务，其他调用等待，防止大文档并发占满内存。
  多进程实例不应写入同一输出目录；同步转换没有独立的强制执行超时。

`src/main/resources/application.yml` 提供以下边界：

| 配置 | 默认值 | 含义 |
|---|---|---|
| `pdf.allowed-input-root` | 空 | 允许读取的根目录 |
| `pdf.allowed-output-root` | 空 | 允许写入的根目录 |
| `pdf.max-file-size` | 104857600 | PDF 大小上限，字节 |
| `pdf.max-pages` | 1000 | 页面数上限 |
| `pdf.max-dpi` | 300 | 可调低的 DPI 上限，范围 72–300 |
| `pdf.max-image-pixels` | 25000000 | 单页渲染像素上限，在创建图片前检查 |
| `pdf.free-docx-max-pages` | 3 | 可调低的 DOCX 页数上限，范围 1–3 |

目录限制为空时保留原有服务端路径访问能力；配置后根目录必须已存在，校验解析符号链接后的实际路径。
输入输出目录应仅由可信本机用户管理，避免本机其他进程在校验后替换目录。
默认不提供身份认证，部署时应限制访问来源，并根据需要设置输入输出根目录。

## 代码注释约定

代码注释使用中文，接口注释明确服务端路径、参数大小写和默认值；转换实现注释说明
页数与资源边界、输出权限、原子发布的适用条件及恢复限制。方法内部按执行顺序说明
路径规范化与真实目录校验、暂存写入与关闭资源、逐页渲染与像素检查、权限继承及目录备份恢复，
重点解释分支存在的原因和失败后保留的状态。配置说明与实际校验保持一致。

## 回归验证

`sh ./mvnw verify` 验证 Spring 上下文、旧接口参数及响应契约、默认 DPI、
多页 TXT 正文、三页 DOCX 正文、超限拒绝、中文相对路径、图片尺寸与旧页清理、
失败时保留已有结果、合法长文件名、已有输出权限保持，以及目录和资源边界。实际 DOCX 复杂版式仍需要用业务文档人工验收。
