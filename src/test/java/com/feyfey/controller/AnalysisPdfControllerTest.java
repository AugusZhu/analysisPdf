package com.feyfey.controller;

import com.feyfey.service.AnalysisPdfService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 旧接口的参数名、默认值及失败响应契约回归测试。
 */
@WebMvcTest(AnalysisPdfController.class)
class AnalysisPdfControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockBean
    private AnalysisPdfService service;

    @Test
    void legacyImageParameterAndDefaultDpiRemainCompatible() throws Exception {
        mvc.perform(post("/pdf/analysisPdf2Image").param("PdfFilePath", "sample.pdf"))
                .andExpect(status().isOk()).andExpect(jsonPath("returnType").value("SUCCESS"));
        verify(service).analysicPdf2Image("sample.pdf", "", 96);
    }

    @Test
    void legacyDocParameterRemainsCompatible() throws Exception {
        mvc.perform(post("/pdf/analysisPdf2Doc").param("PdfFilePath", "sample.pdf"))
                .andExpect(jsonPath("returnType").value("SUCCESS"));
        verify(service).analysicPdf2Doc("sample.pdf", "");
    }

    @Test
    void malformedDpiUsesExistingFailureEnvelope() throws Exception {
        mvc.perform(post("/pdf/analysisPdf2Image").param("PdfFilePath", "sample.pdf").param("dpi", "abc"))
                .andExpect(status().isOk()).andExpect(jsonPath("returnType").value("FAILURE"));
        verifyNoInteractions(service);
    }
}
