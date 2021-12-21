package com.feyfey.service.impl;

import com.feyfey.service.AnalysisPdfService;
import com.lowagie.text.pdf.PdfReader;
import com.spire.pdf.FileFormat;
import com.spire.pdf.PdfDocument;
import com.spire.pdf.PdfPageBase;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;


/**
 * @author zhuxianfei
 * @date 2021/12/16 17:44
 */
@Service
public class AnalysisPdfServiceImpl implements AnalysisPdfService {
    private static final Logger logger = LoggerFactory.getLogger(AnalysisPdfServiceImpl.class);

    @Value("${pdf.parseFileType}")
    private String parseFileType;


    /**
     * @param filePath 读取pdf的文件路径
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
     * PDF文件转PNG/JPEG图片
     *
     * @param PdfFilePath  pdf完整路径
     * @param dstImgFolder 图片存放的文件夹
     * @param dpi          越大转换后越清晰，相对转换速度越慢,一般电脑默认96dpi
     */
    @Override
    public void analysicPdf2Image(String PdfFilePath,
                                  String dstImgFolder, int dpi) throws Exception {
        if (!fileIsExists(PdfFilePath)) {
            logger.error("待转换文件不存在");
            throw new Exception("待转换文件不存在");
        }
        File file = new File(PdfFilePath);
        PDDocument pdDocument;
        try {
            String imgPDFPath = file.getParent();
            int dot = file.getName().lastIndexOf('.');
            // 获取图片文件名
            String imagePDFName = file.getName().substring(0, dot);
            String imgFolderPath = null;
            if (dstImgFolder.equals("")) {
                // 获取图片存放的文件夹路径
                imgFolderPath = imgPDFPath + File.separator + imagePDFName;
            } else {
                imgFolderPath = dstImgFolder + File.separator + imagePDFName;
            }
            long start = System.currentTimeMillis();
            if (createDirectory(imgFolderPath)) {
                pdDocument = PDDocument.load(file);
                PDFRenderer renderer = new PDFRenderer(pdDocument);
                PdfReader reader = new PdfReader(PdfFilePath);
                int pages = reader.getNumberOfPages();// 获取PDF页数
                logger.info("PDF文档页数为:" + pages);
                StringBuffer imgFilePath = null;
                for (int i = 0; i < pages; i++) {
                    String imgFilePathPrefix = imgFolderPath
                            + File.separator + imagePDFName;
                    imgFilePath = new StringBuffer();
                    imgFilePath.append(imgFilePathPrefix);
                    imgFilePath.append("_");
                    imgFilePath.append(String.valueOf(i + 1));
                    imgFilePath.append(".png");// PNG
                    File dstFile = new File(imgFilePath.toString());
                    BufferedImage image = renderer.renderImageWithDPI(i, dpi);
                    ImageIO.write(image, "png", dstFile);// PNG
                }
                pdDocument.close();
                long end = System.currentTimeMillis();
                logger.info("PDF文档转PNG图片成功！,总共耗时：" + (end - start) + "毫秒");
            } else {
                logger.error("PDF文档转PNG图片失败");
                throw new Exception("创建" + imgFolderPath + "失败");
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new Exception("转换文件处理异常");
        }
    }

    /**
     * PDF转换成Doc  注意,由于使用免费版jar,只可转换10页及以内的文档
     *
     * @param PdfFilePath  pdf文件的完全路径
     * @param docImgFolder 生成word的路径
     * @throws Exception
     */
    @Override
    public void analysicPdf2Doc(String PdfFilePath, String docImgFolder) throws Exception {
        if (!fileIsExists(PdfFilePath)) {
            logger.error("待转换文件不存在");
            throw new Exception("待转换文件不存在");
        }
        File file = new File(PdfFilePath);
        try {
            String docPDFPath = file.getParent();
            int dot = file.getName().lastIndexOf('.');
            // 获取图片文件名
            String docPDFName = file.getName().substring(0, dot);
            String docFolderPath = null;
            if (docImgFolder.equals("")) {
                // 获取图片存放的文件夹路径
                docFolderPath = docPDFPath + File.separator + docPDFName + ".doc";
            } else {
                docFolderPath = docImgFolder + File.separator + docPDFName + ".doc";
            }
            long start = System.currentTimeMillis();
            if (createDirectory(docFolderPath)) {
                PdfDocument pdf = new PdfDocument(PdfFilePath);
                pdf.saveToFile(docFolderPath, FileFormat.DOCX);
                long end = System.currentTimeMillis();
                logger.info("PDF文档转WORD成功！,总共耗时：" + (end - start) + "毫秒");
            } else {
                logger.error("PDF文档转WORD失败");
                throw new Exception("创建" + docFolderPath + "失败");
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new Exception("转换文件处理异常");
        }
    }


    /**
     * 创建文件夹
     *
     * @param folder
     * @return
     */
    private static boolean createDirectory(String folder) {
        File dir = new File(folder);
        if (dir.exists()) {
            return true;
        } else {
            return dir.mkdirs();
        }
    }


    /**
     * 判断是否路径参数中的文件是否存在
     *
     * @param filePath
     * @return
     */
    public static boolean fileIsExists(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            return false;
        } else {
            return true;
        }
    }

}
