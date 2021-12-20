package com.feyfey.service;

/**
 * @author zhuxianfei
 * @date 2021/12/16 17:42
 */
public interface AnalysisPdf2TxtService {

    /**
     * @param filePath       读取pdf的文件路径
     * @throws Exception
     */
    void analysicPdf2Txt(String filePath) throws Exception;
}
