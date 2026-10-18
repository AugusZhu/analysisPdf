package com.feyfey.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * PDF 转换相关的可配置边界。
 *
 * <p>允许目录为空表示不限制服务端路径；配置后根目录必须已存在，校验时解析符号链接。</p>
 */
@Component
@ConfigurationProperties(prefix = "pdf")
public class PdfProperties {

    /**
     * 允许读取的服务端输入根目录；为空表示沿用当前接口行为。
     */
    private String allowedInputRoot;

    /**
     * 允许写入的服务端输出根目录；为空表示沿用当前接口行为。
     */
    private String allowedOutputRoot;

    /**
     * 单个 PDF 文件大小上限，默认 100MB。
     */
    private long maxFileSize = 100L * 1024 * 1024;

    /**
     * 单次转换的页数上限；不替代文件大小、单页像素及转换并发限制。
     */
    private int maxPages = 1000;

    /**
     * 允许的最大 DPI，配置范围为 72–300；单页实际像素数另行校验。
     */
    private int maxDpi = 300;

    /** 单页位图像素上限，防止大页面在渲染时耗尽内存。 */
    private long maxImagePixels = 25_000_000;

    /**
     * 免费 Spire 的 DOCX 页数上限，配置范围为 1–3；调高配置不能突破引擎限制。
     */
    private int freeDocxMaxPages = 3;

    /**
     * 获取输入根目录配置。
     */
    public String getAllowedInputRoot() {
        return allowedInputRoot;
    }

    public void setAllowedInputRoot(String allowedInputRoot) {
        this.allowedInputRoot = allowedInputRoot;
    }

    /**
     * 获取输出根目录配置。
     */
    public String getAllowedOutputRoot() {
        return allowedOutputRoot;
    }

    public void setAllowedOutputRoot(String allowedOutputRoot) {
        this.allowedOutputRoot = allowedOutputRoot;
    }

    /**
     * 获取单个 PDF 文件大小上限。
     */
    public long getMaxFileSize() {
        return maxFileSize;
    }

    // 配置绑定经过 setter 校验，非法边界直接阻止绑定，避免以无效配置继续运行。
    public void setMaxFileSize(long maxFileSize) {
        if (maxFileSize <= 0) {
            throw new IllegalArgumentException("文件大小上限必须大于0");
        }
        this.maxFileSize = maxFileSize;
    }

    /**
     * 获取单次任务允许处理的页数上限。
     */
    public int getMaxPages() {
        return maxPages;
    }

    public void setMaxPages(int maxPages) {
        if (maxPages <= 0) {
            throw new IllegalArgumentException("页数上限必须大于0");
        }
        this.maxPages = maxPages;
    }

    /**
     * 获取允许的最大 DPI。
     */
    public int getMaxDpi() {
        return maxDpi;
    }

    public void setMaxDpi(int maxDpi) {
        // 允许收紧渲染精度，但不能通过配置突破服务约定的 72–300 范围。
        if (maxDpi < 72 || maxDpi > 300) {
            throw new IllegalArgumentException("DPI上限必须在72-300之间");
        }
        this.maxDpi = maxDpi;
    }

    /**
     * 获取免费版 DOCX 转换允许的最大页数。
     */
    public int getFreeDocxMaxPages() {
        return freeDocxMaxPages;
    }

    public void setFreeDocxMaxPages(int freeDocxMaxPages) {
        // 该上限来自免费引擎能力；它独立于所有格式共用的 maxPages 限制。
        if (freeDocxMaxPages < 1 || freeDocxMaxPages > 3) {
            throw new IllegalArgumentException("免费DOCX页数上限必须在1-3之间");
        }
        this.freeDocxMaxPages = freeDocxMaxPages;
    }
    /**
     * 获取单页渲染像素上限。
     */
    public long getMaxImagePixels() {
        return maxImagePixels;
    }

    public void setMaxImagePixels(long maxImagePixels) {
        if (maxImagePixels <= 0) {
            throw new IllegalArgumentException("渲染像素上限必须大于0");
        }
        this.maxImagePixels = maxImagePixels;
    }
}
