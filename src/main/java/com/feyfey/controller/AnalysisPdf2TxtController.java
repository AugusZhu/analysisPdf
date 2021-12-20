package com.feyfey.controller;


import com.feyfey.constant.JsonReturnResult;
import com.feyfey.constant.JsonReturnResultTypeEnum;
import com.feyfey.service.AnalysisPdf2TxtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author zhuxianfei
 * @date 2021/12/16 17:21
 */
@Controller
@RequestMapping(value = "/pdf")
public class AnalysisPdf2TxtController {
    private static final Logger logger = LoggerFactory.getLogger(AnalysisPdf2TxtController.class);

    @Autowired
    private AnalysisPdf2TxtService analysisPdf2TxtService;

    /**
     * @param request
     * @param response
     * @return 解析结果 pdf文件转换成text文件
     * @throws Exception
     */
    @RequestMapping(value = "/analysisPdf2Text", method = RequestMethod.POST)
    @ResponseBody
    public JsonReturnResult analysisPdf2Text(HttpServletRequest request,
                                        HttpServletResponse response) throws Exception {
        try {
            String filePath=request.getParameter("filePath");
            analysisPdf2TxtService.analysicPdf2Txt(filePath);
            return new JsonReturnResult(JsonReturnResultTypeEnum.SUCCESS, "解析成功");
        } catch (Exception e) {
            logger.error("解析文件出错：", e);
            return new JsonReturnResult(JsonReturnResultTypeEnum.FAILURE, e.getMessage());

        }
    }

    /**
     *
     * @param request
     * @return 将pdf转换成png格式的图片
     * @throws Exception
     */
    @RequestMapping(value = "/analysisPdf2Image", method = RequestMethod.POST)
    @ResponseBody
    public JsonReturnResult analysisPdf2Image(HttpServletRequest request) throws Exception {
        try {
            String PdfFilePath=request.getParameter("PdfFilePath");
            String dstImgFolder=request.getParameter("dstImgFolder");
            Integer dpi=Integer.parseInt(request.getParameter("dpi"));
            analysisPdf2TxtService.analysicPdf2Image(PdfFilePath,dstImgFolder,dpi);
            return new JsonReturnResult(JsonReturnResultTypeEnum.SUCCESS, "转换成功");
        } catch (Exception e) {
            logger.error("转换pdf文件出错：", e);
            return new JsonReturnResult(JsonReturnResultTypeEnum.FAILURE, e.getMessage());

        }
    }


}
