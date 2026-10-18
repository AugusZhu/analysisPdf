package com.feyfey.controller;

import com.feyfey.constant.JsonReturnResult;
import com.feyfey.service.AnalysisPdfService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * PDF 转换接口，接收服务端文件路径，委托转换服务执行同步任务。
 * 转换及参数解析异常沿用 HTTP 200 和 FAILURE 响应体契约。
 */
@RestController
@RequestMapping("/pdf")
public class AnalysisPdfController {
    private static final Logger logger = LoggerFactory.getLogger(AnalysisPdfController.class);

    private final AnalysisPdfService analysisPdfService;

    public AnalysisPdfController(AnalysisPdfService analysisPdfService) {
        this.analysisPdfService = analysisPdfService;
    }

    /**
     * 将 PDF 转换为与源文件同目录、同基础名的 TXT 文件。
     *
     * @param filePath PDF 文件路径
     * @return 统一成功或失败响应
     */
    @PostMapping("/analysisPdf2Text")
    public JsonReturnResult analysisPdf2Text(@RequestParam(required = false) String filePath) {
        try {
            // 参数允许为空以进入统一响应流程；路径合法性及转换边界由服务层集中检查。
            analysisPdfService.analysicPdf2Txt(filePath);
            return JsonReturnResult.success("解析成功");
        } catch (Exception e) {
            // 日志保留完整异常堆栈；响应沿用历史约定，仅携带异常消息。
            logger.error("解析文件出错", e);
            return JsonReturnResult.fail(e.getMessage());
        }
    }

    /**
     * 将 PDF 逐页渲染为 PNG 图片。
     *
     * @param pdfFilePath  服务端 PDF 路径；HTTP 参数名保留为大小写敏感的 PdfFilePath
     * @param dstImgFolder 输出目录，空值使用源 PDF 所在目录
     * @param dpi          渲染精度字符串，默认 96；在方法内解析以统一返回参数错误
     * @return 统一成功或失败响应
     */
    @PostMapping("/analysisPdf2Image")
    public JsonReturnResult analysisPdf2Image(
            @RequestParam(name = "PdfFilePath", required = false) String pdfFilePath,
            @RequestParam(required = false, defaultValue = "") String dstImgFolder,
            @RequestParam(required = false, defaultValue = "96") String dpi) {
        try {
            // 将整数解析放在 try 内，非数字 DPI 与转换异常都按原 FAILURE 响应契约返回。
            analysisPdfService.analysicPdf2Image(pdfFilePath, dstImgFolder, Integer.parseInt(dpi));
            return JsonReturnResult.success("转换成功");
        } catch (Exception e) {
            // 日志保留完整异常堆栈；响应沿用历史约定，仅携带异常消息。
            logger.error("转换pdf文件出错", e);
            return JsonReturnResult.fail(e.getMessage());
        }
    }

    /**
     * 将 PDF 转换为 DOCX，超出免费版页数限制时返回明确错误。
     *
     * @param pdfFilePath 服务端 PDF 路径；HTTP 参数名保留为大小写敏感的 PdfFilePath
     * @param docFolder   Word 输出目录，空值使用源 PDF 所在目录
     * @return 统一成功或失败响应
     */
    @PostMapping("/analysisPdf2Doc")
    public JsonReturnResult analysisPdf2Doc(
            @RequestParam(name = "PdfFilePath", required = false) String pdfFilePath,
            @RequestParam(required = false, defaultValue = "") String docFolder) {
        try {
            // 服务层先检查真实页数；超出免费版能力时抛异常，不生成截断的 DOCX。
            analysisPdfService.analysicPdf2Doc(pdfFilePath, docFolder);
            return JsonReturnResult.success("转换成功");
        } catch (Exception e) {
            // 日志保留完整异常堆栈；响应沿用历史约定，仅携带异常消息。
            logger.error("转换pdf文件出错", e);
            return JsonReturnResult.fail(e.getMessage());
        }
    }
}
