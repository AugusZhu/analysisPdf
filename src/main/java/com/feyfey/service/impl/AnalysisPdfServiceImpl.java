package com.feyfey.service.impl;

import com.feyfey.config.PdfProperties;
import com.feyfey.service.AnalysisPdfService;
import com.spire.pdf.FileFormat;
import com.spire.pdf.PdfDocument;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.LinkOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * PDF 转换服务实现。
 *
 * <p>TXT 与 PNG 统一使用 PDFBox 3；DOCX 继续使用免费版 Spire，
 * 超出免费页数时明确报错，禁止只转换部分页面后返回成功。
 * 三种转换共用实例锁串行执行，限制单实例内存峰值并避免同名结果并发覆盖。
 * 此锁不协调多个服务实例或其他进程，部署时应避免共享输出路径。</p>
 */
@Service
public class AnalysisPdfServiceImpl implements AnalysisPdfService {
    private static final Logger logger = LoggerFactory.getLogger(AnalysisPdfServiceImpl.class);

    private static final String PDF_EXTENSION = "pdf";
    private static final String TEXT_EXTENSION = ".txt";
    private static final String DOCX_EXTENSION = ".docx";
    private static final String PNG_EXTENSION = ".png";
    private static final int MIN_DPI = 72;

    private final PdfProperties properties;

    public AnalysisPdfServiceImpl(PdfProperties properties) {
        this.properties = properties;
    }

    /**
     * 提取 PDF 全部文本，并以 UTF-8 写入与源 PDF 同目录、同基础名的 TXT 文件。
     *
     * @param filePath PDF 文件路径
     * @throws Exception 输入不可用、页面超限或写入失败时抛出
     */
    @Override
    public synchronized void analysicPdf2Txt(String filePath) throws Exception {
        Path source = validateReadablePdf(filePath);
        String baseName = baseNameWithoutExtension(source);
        // 使用校验后真实源路径的父目录，TXT 始终与实际源文件同目录、同基础名。
        Path target = source.getParent().resolve(baseName + TEXT_EXTENSION);
        validateOutputDirectory(target.getParent());

        // 先写同目录临时文件，直到转换完成才替换目标，失败时不截断已有结果。
        Path temporary = createTemporaryFile(target);
        try {
            try (PDDocument document = loadPdf(source);
                 Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                requirePageLimit(document.getNumberOfPages());
                // 逐页写入临时文件，避免同时在内存中持有整份文本。
                new PDFTextStripper().writeText(document, writer);
            }
            // 到此转换器及文件句柄已关闭，再发布结果，避免未写完或占用文件时移动。
            publishTemporaryFile(temporary, target);
        } finally {
            // 成功移动后临时路径已不存在；失败时删除本次产物，不删除已有目标文件。
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * 按页渲染 PDF 为 PNG。默认输出到源 PDF 同名目录，也可指定目标目录。
     *
     * @param pdfFilePath  PDF 文件路径
     * @param dstImgFolder 图片输出目录；为空时使用源 PDF 所在目录
     * @param dpi          调用方传入的渲染精度，范围 72 至配置上限；HTTP 接口默认 96
     * @throws Exception 参数、路径、页面或渲染异常
     */
    @Override
    public synchronized void analysicPdf2Image(String pdfFilePath, String dstImgFolder, int dpi) throws Exception {
        requireDpi(dpi);
        Path source = validateReadablePdf(pdfFilePath);
        String baseName = baseNameWithoutExtension(source);
        // 图片统一放进独立同名子目录，例如 report.pdf → report/report_1.png。
        Path outputDir = resolveOutputDirectory(dstImgFolder, source.getParent()).resolve(baseName);
        validateOutputDirectory(outputDir);
        // 后续会整体替换该目录，必须先确认其中没有用户自建文件或符号链接。
        validateImageDirectory(outputDir, baseName);

        Files.createDirectories(outputDir.getParent());
        // 暂存目录与最终目录互为兄弟，便于同文件系统移动；短前缀避免长文件名超限。
        Path stagingDir = Files.createTempDirectory(outputDir.getParent(), ".pdf-images-");
        try {
            try (PDDocument document = loadPdf(source)) {
                requirePageLimit(document.getNumberOfPages());
                PDFRenderer renderer = new PDFRenderer(document);
                int pages = document.getNumberOfPages();
                logger.info("PDF文档页数为: {}", pages);
                for (int page = 0; page < pages; page++) {
                    // PDFBox 页索引从 0 开始，对外输出文件的页码从 1 开始。
                    Path stagedFile = stagingDir.resolve(baseName + "_" + (page + 1) + PNG_EXTENSION);
                    // 分配位图前检查页面像素；仅限制 DPI 无法拦住物理尺寸异常大的页面。
                    requireImageSize(document, page, dpi);
                    BufferedImage image = renderer.renderImageWithDPI(page, dpi);
                    try {
                        writePng(image, stagedFile);
                    } finally {
                        // 每页写入后释放图像缓存资源；后续页面不需要继续使用当前位图。
                        image.flush();
                    }
                }
            }
            // 所有页生成且 PDF 已关闭后才发布，避免失败时新旧页混杂或暴露部分结果。
            publishDirectory(stagingDir, outputDir);
        } finally {
            // 发布成功后原暂存路径已消失；渲染或发布失败时清理尚未发布的页图片。
            deleteRecursively(stagingDir);
        }
    }

    /**
     * 将 PDF 转换为 DOCX。
     *
     * <p>当前免费 Spire 最多支持 3 页，实际限制取配置中的更低上限，超页时直接报错，
     * 避免生成缺少后续页面的“成功”文件。</p>
     *
     * @param pdfFilePath PDF 文件路径
     * @param docFolder   DOCX 输出目录；为空时使用源 PDF 所在目录
     * @throws Exception 参数、路径、页面限制或转换异常
     */
    @Override
    public synchronized void analysicPdf2Doc(String pdfFilePath, String docFolder) throws Exception {
        Path source = validateReadablePdf(pdfFilePath);
        String baseName = baseNameWithoutExtension(source);
        Path target = resolveOutputDirectory(docFolder, source.getParent())
                .resolve(baseName + DOCX_EXTENSION);
        validateOutputDirectory(target.getParent());

        // 使用 PDFBox 检查真实页数，不能依赖受免费版加载限制影响的 Spire 页数。
        try (PDDocument check = loadPdf(source)) {
            int pages = check.getNumberOfPages();
            // 先应用通用页数限制，再检查 DOCX 引擎上限；不允许超限文档进入转换阶段。
            requirePageLimit(pages);
            if (pages > properties.getFreeDocxMaxPages()) {
                throw new IllegalStateException("DOCX免费版仅支持转换"
                        + properties.getFreeDocxMaxPages() + "页以内的文档，当前共" + pages + "页");
            }
        }
        // 先写同目录临时文件，直到转换完成才替换目标，失败时不截断已有结果。
        Path temporary = createTemporaryFile(target);
        try {
            PdfDocument document = new PdfDocument();
            try {
                document.loadFromFile(source.toString());
                // 临时文件以 .tmp 结尾，因此显式指定 DOCX 格式，不依赖扩展名推断。
                document.saveToFile(temporary.toString(), FileFormat.DOCX);
            } finally {
                // Spire 未使用自动关闭语法，加载或保存抛异常时也必须尝试释放文档资源。
                document.close();
            }
            // 临时文件预先已创建，存在性不足以证明转换有效；此处至少排除空输出。
            if (Files.size(temporary) == 0) {
                throw new IOException("DOCX转换未生成有效文件");
            }
            // 到此转换器及文件句柄已关闭，再发布结果，避免未写完或占用文件时移动。
            publishTemporaryFile(temporary, target);
        } finally {
            // 成功移动后临时路径已不存在；失败时删除本次产物，不删除已有目标文件。
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * 校验输入路径为可读的普通 PDF 文件，并应用配置中的目录、大小限制。
     */
    private Path validateReadablePdf(String pdfFilePath) throws Exception {
        if (pdfFilePath == null || pdfFilePath.trim().isEmpty()) {
            throw new IllegalArgumentException("文件路径不能为空");
        }
        // 相对路径按进程工作目录解析，并整理 . 和 ..，后续再解析符号链接。
        Path source = Paths.get(pdfFilePath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(source) || !Files.isReadable(source)) {
            throw new IllegalStateException("待解析文件不存在");
        }
        // 扩展名只做入口筛选，PDF 内容是否合法仍由后续解析器确认。
        if (!isPdfExtension(source.getFileName().toString())) {
            throw new IllegalArgumentException("传入文件不是PDF文档,请检查!");
        }
        // 边界检查使用符号链接解析后的真实路径，防止通过允许目录内的链接读取外部文件。
        Path realSource = source.toRealPath();
        Path inputRoot = resolveConfiguredRoot(properties.getAllowedInputRoot());
        // Path.startsWith 按路径段比较，避免把 /data-extra 错当作 /data 的子目录。
        if (inputRoot != null && !realSource.startsWith(inputRoot)) {
            throw new SecurityException("PDF路径不在允许的输入目录内");
        }
        // 在启动 PDF 解析前检查磁盘文件大小，减少明显超限输入的资源消耗。
        long fileSize = Files.size(realSource);
        if (fileSize > properties.getMaxFileSize()) {
            throw new IllegalStateException("PDF文件超过大小限制：" + fileSize + " 字节");
        }
        return realSource;
    }

    /**
     * 解析输出根目录；未指定时使用源 PDF 所在目录。
     */
    private Path resolveOutputDirectory(String requestedDir, Path defaultDir) throws Exception {
        // null、空串和纯空白统一使用默认目录；非空路径不裁剪，保留合法路径中的空格。
        Path outputDir = (requestedDir == null || requestedDir.trim().isEmpty())
                ? defaultDir : Paths.get(requestedDir).toAbsolutePath().normalize();
        validateOutputDirectory(outputDir);
        return outputDir;
    }

    /**
     * 按实际祖先路径校验输出目录边界，存在的目标必须为目录。
     * 此检查不防御其他本机进程在校验后替换目录，目录应由可信用户管理。
     */
    private void validateOutputDirectory(Path outputDir) throws Exception {
        Path root = resolveConfiguredRoot(properties.getAllowedOutputRoot());
        // 目标目录可能尚未创建，不能直接 toRealPath；先解析最近已存在祖先的实际位置。
        Path actual = resolveExistingAncestor(outputDir);
        if (root != null && !actual.startsWith(root)) {
            throw new SecurityException("输出路径不在允许的输出目录内");
        }
        if (Files.exists(outputDir) && !Files.isDirectory(outputDir)) {
            throw new IOException("输出目录不是文件夹：" + outputDir);
        }
    }

    /**
     * 对未创建的路径解析最近的已存在祖先，防止目录符号链接绕过根目录限制。
     */
    private Path resolveExistingAncestor(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Path ancestor = absolute;
        // 不跟随链接判断存在性：遇到断链时停下，让 toRealPath 明确报错，而不是跳过断链。
        while (!Files.exists(ancestor, LinkOption.NOFOLLOW_LINKS)) {
            ancestor = ancestor.getParent();
        }
        // 保留尚未创建的相对后缀，再接到祖先真实路径上，例如 link/new → 实际目录/new。
        return ancestor.toRealPath().resolve(ancestor.relativize(absolute)).normalize();
    }

    /**
     * 空配置表示不限制目录；非空根目录必须可解析，失效配置不得默认为放行。
     */
    private Path resolveConfiguredRoot(String configuredRoot) throws IOException {
        if (configuredRoot == null || configuredRoot.trim().isEmpty()) {
            return null;
        }
        Path root = Paths.get(configuredRoot).toAbsolutePath().normalize();
        // 配置根目录与待检查路径均使用真实路径，兼容 /var 与 /private/var 等目录别名。
        return root.toRealPath();
    }

    /**
     * 限制 DPI 范围，避免渲染出超大规模位图。
     */
    private void requireDpi(int dpi) {
        if (dpi < MIN_DPI || dpi > properties.getMaxDpi()) {
            throw new IllegalArgumentException("dpi 必须在 " + MIN_DPI + "-" + properties.getMaxDpi() + " 之间");
        }
    }

    /**
     * 限制单次转换处理页数，避免超大文档拖垮内存。
     */
    private void requirePageLimit(int pages) {
        if (pages < 1) {
            throw new IllegalArgumentException("PDF没有可转换的页面");
        }
        if (pages > properties.getMaxPages()) {
            throw new IllegalStateException("PDF页数超过限制：" + pages);
        }
    }

    /**
     * 通过 PDFBox 加载 PDF；调用方负责关闭返回的 PDDocument。
     */
    private PDDocument loadPdf(Path source) throws IOException {
        return Loader.loadPDF(source.toFile());
    }

    /**
     * 判断扩展名是否为 PDF，大小写不敏感。
     */
    private boolean isPdfExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        // 使用固定语言环境比较扩展名，避免操作系统语言设置影响大小写转换。
        String suffix = dot >= 0 ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        return PDF_EXTENSION.equals(suffix);
    }

    /**
     * 获取不含扩展名的文件名，用于生成同名输出。
     */
    private String baseNameWithoutExtension(Path file) {
        String fileName = file.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(0, dot) : fileName;
    }

    /**
     * 在目标路径同目录以短前缀创建临时文件，避免合法长文件名被随机后缀撑超长度限制。
     */
    private Path createTemporaryFile(Path target) throws IOException {
        // 只允许覆盖普通文件，避免最终目标链接到其他位置或与目录发生冲突。
        if (Files.isSymbolicLink(target)
                || (Files.exists(target) && !Files.isRegularFile(target))) {
            throw new IOException("输出目标不是普通文件：" + target);
        }
        Files.createDirectories(target.getParent());
        return Files.createTempFile(target.getParent(), ".pdf-convert-", ".tmp");
    }

    /**
     * 在分配位图前限制单页像素数，DPI 上限本身不能约束超大页面。
     */
    private void requireImageSize(PDDocument document, int page, int dpi) {
        PDRectangle box = document.getPage(page).getCropBox();
        // PDF 页面尺寸以 1/72 英寸为基准，乘 DPI/72 换算为位图像素，并向下取整。
        double width = Math.floor(box.getWidth() * dpi / 72.0);
        double height = Math.floor(box.getHeight() * dpi / 72.0);
        // 同时拦住非有限数、空尺寸、整数尺寸溢出及总像素超限；乘积使用 double 避免 int 溢出。
        if (!Double.isFinite(width) || !Double.isFinite(height) || width < 1 || height < 1
                || width > Integer.MAX_VALUE || height > Integer.MAX_VALUE
                || width * height > properties.getMaxImagePixels()) {
            throw new IllegalArgumentException("第" + (page + 1) + "页渲染像素超过限制或页面尺寸无效");
        }
    }

    /**
     * 写入 PNG 文件，写入失败时抛出异常。
     */
    private void writePng(BufferedImage image, Path target) throws IOException {
        boolean written = ImageIO.write(image, "png", target.toFile());
        // ImageIO 找不到对应编码器时可能返回 false 而不抛异常，也应按写入失败处理。
        if (!written) {
            throw new IOException("PNG写入失败：" + target);
        }
    }

    /**
     * 保留已有目标的访问权限后发布文件，优先使用同文件系统原子移动。
     * 文件系统不支持原子移动时退回普通替换，该分支不提供原子性保证。
     */
    private void publishTemporaryFile(Path temporary, Path target) throws IOException {
        // 先恢复旧目标权限再替换；权限复制失败时保留原目标，不发布权限不符的新文件。
        copyAccessPermissions(target, temporary);
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            // 仅对“不支持原子移动”降级，其他 I/O 错误直接上抛，避免掩盖实际故障。
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * 覆盖已有结果时保留 POSIX 权限位，或在支持 ACL 的平台保留访问控制列表。
     * 首次输出沿用临时产物的默认权限，不主动扩大访问范围；不更改所有者及所属组。
     */
    private void copyAccessPermissions(Path previous, Path replacement) throws IOException {
        // 首次生成没有旧权限可继承，保留新产物自身的创建权限。
        if (!Files.exists(previous, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        PosixFileAttributeView posix = Files.getFileAttributeView(previous,
                PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        // 类 Unix 文件系统优先复制权限位；不改变临时产物的所有者及所属组。
        if (posix != null) {
            Files.setPosixFilePermissions(replacement, posix.readAttributes().permissions());
        } else {
            // 非 POSIX 平台尝试复制 ACL；不提供该属性视图时保持新产物默认权限。
            AclFileAttributeView acl = Files.getFileAttributeView(previous,
                    AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
            if (acl != null) {
                Files.getFileAttributeView(replacement, AclFileAttributeView.class).setAcl(acl.getAcl());
            }
        }
    }

    /**
     * 图片目录仅允许包含本服务的页图片，拒绝覆盖其他文件或符号链接。
     */
    private void validateImageDirectory(Path outputDir, String baseName) throws IOException {
        if (Files.isSymbolicLink(outputDir)) {
            throw new IOException("图片输出目录不能是符号链接");
        }
        if (!Files.exists(outputDir)) {
            return;
        }
        // 基础名按字面量匹配，避免文件名中的括号、点等字符被当作正则表达式。
        Pattern name = Pattern.compile(Pattern.quote(baseName) + "_[1-9][0-9]*\\.png");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(outputDir)) {
            for (Path file : stream) {
                // 允许“同名基础名_正整数.png”的普通文件；拒绝链接、子目录及其他文件。
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                        || !name.matcher(file.getFileName().toString()).matches()) {
                    throw new IOException("图片目录含非转换文件，拒绝覆盖：" + file.getFileName());
                }
            }
        }
    }

    /**
     * 先备份旧目录，再发布完整暂存目录，成功后清理旧页。
     * 发布失败时尝试恢复旧目录；恢复失败则保留备份并记录位置。
     * 两次目录移动不是单个原子操作，进程退出或断电后的恢复需人工检查。
     */
    private void publishDirectory(Path stagingDir, Path outputDir) throws IOException {
        // 暂存目录名称已随机化，用其派生短备份名，避免与其他任务或长原文件名冲突。
        Path backup = stagingDir.resolveSibling(stagingDir.getFileName() + "-backup");
        boolean hadPrevious = Files.exists(outputDir);
        if (hadPrevious) {
            // 同名页保留旧权限；新增页沿用正常创建权限，最后再应用目录权限。
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(stagingDir)) {
                for (Path file : stream) {
                    copyAccessPermissions(outputDir.resolve(file.getFileName()), file);
                }
            }
            copyAccessPermissions(outputDir, stagingDir);
            // 先把旧结果完整移到备份，腾出最终目录名；发布新目录前始终保留可恢复副本。
            Files.move(outputDir, backup);
        }
        try {
            // 直接发布整份结果，旧文档页数较多时，多余旧页也不会残留在新目录中。
            Files.move(stagingDir, outputDir);
        } catch (IOException e) {
            if (hadPrevious) {
                try {
                    // 新目录发布失败，尝试恢复原目录；恢复成功后仍继续上抛本次转换失败。
                    Files.move(backup, outputDir);
                } catch (IOException restoreError) {
                    // 同时保留发布与恢复两项异常，排障时可追溯完整故障链。
                    e.addSuppressed(restoreError);
                    logger.error("旧图片目录恢复失败，备份保留在：{}", backup, restoreError);
                }
            }
            throw e;
        }
        if (hadPrevious) {
            // 只有新结果发布成功才删除旧备份；清理失败由清理方法记录日志。
            deleteRecursively(backup);
        }
    }

    /**
     * 清理仅含直接子文件的暂存或备份目录，不递归删除嵌套目录。
     * 清理失败仅记录日志，不掩盖转换或恢复操作的原始结果。
     */
    private void deleteRecursively(Path dir) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path path : stream) {
                // 仅清理当前目录的直接条目；不进入子目录，也不沿符号链接删除其他位置的内容。
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            logger.warn("清理临时目录失败：{}", dir, e);
        }
        try {
            Files.deleteIfExists(dir);
        } catch (IOException e) {
            logger.warn("删除临时目录失败：{}", dir, e);
        }
    }
}
