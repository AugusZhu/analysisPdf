package com.feyfey.service;

/**
 * @author zhuxianfei
 * @date 2021/12/16 17:42
 */
public interface AnalysisPdfService {

    /**
     * PDF转换成TXT文件
     * @param filePath       读取pdf的文件路径
     * @throws Exception
     */
    void analysicPdf2Txt(String filePath) throws Exception;


    /**
     * PDF文件转PNG图片
     * @param PdfFilePath  pdf文件的完全路径
     * @param dstImgFolder 生成图片的路径
     * @param dpi    越大转换后越清晰，相对转换速度越慢,一般电脑默认96dpi
     * @throws Exception
     */
    void analysicPdf2Image(String PdfFilePath,String dstImgFolder, int dpi) throws Exception;
}
