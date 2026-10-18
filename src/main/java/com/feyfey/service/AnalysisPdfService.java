package com.feyfey.service;

/**
 * 服务端 PDF 文件转换契约，输出路径受配置中的目录边界约束。
 *
 * @author zhuxianfei
 * @date 2021/12/16 17:42
 */
public interface AnalysisPdfService {

    /**
     * 提取 PDF 全部可提取文本，输出为同目录、同基础名的 UTF-8 TXT 文件。
     *
     * @param filePath 读取 PDF 的服务端文件路径
     * @throws Exception 输入不可访问、PDF 损坏、页面/大小超限或写入失败时抛出
     */
    void analysicPdf2Txt(String filePath) throws Exception;

    /**
     * 按页将 PDF 渲染为 PNG 图片，默认输出到源 PDF 同名目录。
     *
     * @param pdfFilePath  PDF 文件路径
     * @param dstImgFolder 图片输出目录；为空时使用源 PDF 所在目录
     * @param dpi          调用方指定的渲染精度，范围 72 至配置上限；HTTP 接口默认传入 96
     * @throws Exception 参数、路径、页面或渲染失败时抛出
     */
    void analysicPdf2Image(String pdfFilePath, String dstImgFolder, int dpi) throws Exception;

    /**
     * 将 PDF 转换为 DOCX；免费版最多支持 3 页，可配置更低上限，超限时保留已有结果。
     *
     * @param pdfFilePath PDF 文件路径
     * @param docFolder   Word 输出目录；为空时使用源 PDF 所在目录
     * @throws Exception 参数、路径、页面限制或转换失败时抛出
     */
    void analysicPdf2Doc(String pdfFilePath, String docFolder) throws Exception;
}
