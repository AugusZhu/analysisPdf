package com.feyfey.service.impl;

import com.feyfey.service.AnalysisPdf2TxtService;
import com.spire.pdf.PdfDocument;
import com.spire.pdf.PdfPageBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;



/**
 * @author zhuxianfei
 * @date 2021/12/16 17:44
 */
@Service
public class AnalysisPdf2TxtServiceImpl implements AnalysisPdf2TxtService {
    private static final Logger logger = LoggerFactory.getLogger(AnalysisPdf2TxtServiceImpl.class);

    @Value("${pdf.parseFileType}")
    private String parseFileType;

    /**
     * @param filePath       读取pdf的文件路径
     * @throws Exception
     */
    @Override
    public void analysicPdf2Txt(String filePath) throws Exception {
        if (!fileIsExists(filePath)) {
            logger.error("待解析文件不存在");
            throw new Exception("待解析文件不存在");
        }
        String parseFileName = filePath.substring(0, filePath.lastIndexOf(".")) + ".txt";
        String suffix = filePath.substring(filePath.lastIndexOf(".") + 1).toUpperCase();
        if (!parseFileType.equals(suffix)) {
            logger.error("传入文件类型不正确");
            throw new Exception("传入文件类型不正确");
        } else {
            //创建PdfDocument实例
            PdfDocument doc = new PdfDocument();
            //加载PDF文件
            doc.loadFromFile(filePath);
            //创建StringBuilder实例
            StringBuilder sb = new StringBuilder();
            PdfPageBase page;
            //遍历PDF页面，获取每个页面的文本并添加到StringBuilder对象
            for (int i = 0; i < doc.getPages().getCount(); i++) {
                page = doc.getPages().get(i);
                sb.append(page.extractText(true));
            }
            FileWriter writer;
            try {
                //将StringBuilder对象中的文本写入到文本文件
                writer = new FileWriter(parseFileName);
                writer.write(sb.toString());
                writer.flush();
            } catch (IOException e) {
                logger.error("写入文件失败：", e.getMessage());
                throw new Exception("写入文件失败");
            }
            doc.close();
        }
    }


    /**
     * 判断是否路径参数中的文件是否存在
     * @param filePath
     * @return
     */
    public boolean fileIsExists(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            return false;
        } else {
            return true;
        }
    }

}
