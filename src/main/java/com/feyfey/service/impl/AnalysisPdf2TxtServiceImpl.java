package com.feyfey.service.impl;

import com.feyfey.service.AnalysisPdf2TxtService;
import com.spire.pdf.PdfDocument;
import com.spire.pdf.PdfPageBase;
import freemarker.template.Configuration;
import freemarker.template.Template;
import org.apache.poi.util.StringUtil;
import org.dom4j.Document;
import org.dom4j.io.SAXReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


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
     * @param resultFileName 解析后生成的文件径路
     * @throws Exception
     */
    @Override
    public void analysicPdf2Txt(String filePath, String resultFileName) throws Exception {
        try {
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
                    writer = new FileWriter(resultFileName);
                    writer.write(sb.toString());
                    writer.flush();
                } catch (IOException e) {
                    logger.error("写入文件失败：", e.getMessage());
                    throw new Exception("写入文件失败");
                }
                doc.close();
            }
        } catch (Exception e1) {
            logger.error("解析异常：", e1.getMessage());
            throw new Exception("解析异常");
        }
    }

   /* public static Configuration getConfiguration() {
        //创建配置实例
        Configuration configuration = new Configuration(Configuration.VERSION_2_3_28);
        //设置编码
        configuration.setDefaultEncoding("utf-8");
        configuration.setClassForTemplateLoading(AnalysisPdf2TxtServiceImpl.class, "/templates");//此处设置模板存放文件夹名称
        return configuration;
    }

    public static ByteArrayInputStream getFreemarkerContentInputStream(Map dataMap, String templateName) {
        ByteArrayInputStream in = null;
        try {
            //获取模板
            Template template = getConfiguration().getTemplate(templateName);
            StringWriter swriter = new StringWriter();
            //生成文件
            template.process(dataMap, swriter);
            in = new ByteArrayInputStream(swriter.toString().getBytes("utf-8"));//这里一定要设置utf-8编码 否则导出的word中中文会是乱码
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("模板生成错误！");
        }
        return in;
    }

    //outputStream 输出流可以自己定义 浏览器或者文件输出流
    public static void createDocx(Map dataMap, OutputStream outputStream) throws IOException {
        ZipOutputStream zipout = null;
        try {
            //内容模板,传值生成新的文件输入流documentInput
            ByteArrayInputStream documentInput = AnalysisPdf2TxtServiceImpl.getFreemarkerContentInputStream(dataMap, "document.xml");
            //最初设计的模板,原word文件生成File对象
            File docxFile = new File(WordUtils.class.getClassLoader().getResource("templates/demo.docx").getPath());//模板文件名称
            if (!docxFile.exists()) {
                docxFile.createNewFile();
            }
            ZipFile zipFile = new ZipFile(docxFile);//获取原word文件的zip文件对象,相当于解压缩了word文件
            Enumeration<? extends ZipEntry> zipEntrys = zipFile.entries();//获取压缩文件内部所有内容
            zipout = new ZipOutputStream(outputStream);
            //开始覆盖文档------------------
            int len = -1;
            byte[] buffer = new byte[1024];
            while (zipEntrys.hasMoreElements()) {//遍历zip文件内容
                ZipEntry next = zipEntrys.nextElement();
                InputStream is = zipFile.getInputStream(next);
                if (next.toString().indexOf("media") < 0) {
                    zipout.putNextEntry(new ZipEntry(next.getName()));//这步相当于创建了个文件，下面是将流写入这个文件
                    if ("word/document.xml".equals(next.getName())) {//如果是word/document.xml由我们输入
                        if (documentInput != null) {
                            while ((len = documentInput.read(buffer)) != -1) {
                                zipout.write(buffer, 0, len);
                            }
                            documentInput.close();
                        }
                    } else {
                        while ((len = is.read(buffer)) != -1) {
                            zipout.write(buffer, 0, len);
                        }
                        is.close();
                    }
                } else {//这里设置图片信息，针对要显示的图片
                    zipout.putNextEntry(new ZipEntry(next.getName()));
                    while ((len = is.read(buffer)) != -1) {
                        zipout.write(buffer, 0, len);
                    }
                    is.close();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("word导出失败:" + e.getStackTrace());
        } finally {
            if (zipout != null) {
                try {
                    zipout.close();
                } catch (IOException e) {
                    logger.error("io异常");
                }
            }
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    logger.error("io异常");
                }
            }
        }
    }*/
}
