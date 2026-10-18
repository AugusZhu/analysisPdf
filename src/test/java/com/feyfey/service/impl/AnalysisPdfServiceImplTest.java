package com.feyfey.service.impl;

import com.feyfey.config.PdfProperties;
import com.feyfey.service.AnalysisPdfService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import javax.imageio.ImageIO;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Assumptions;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 转换核心回归测试。
 */
class AnalysisPdfServiceImplTest {

    @TempDir
    Path tempDir;

    private AnalysisPdfService service;

    @BeforeEach
    void setUp() {
        PdfProperties properties = new PdfProperties();
        properties.setAllowedInputRoot(tempDir.toString());
        properties.setAllowedOutputRoot(tempDir.toString());
        service = new AnalysisPdfServiceImpl(properties);
    }

    @Test
    void pdfConversionsGenerateExpectedFiles() throws Exception {
        Path pdfPath = createPdf("sample.pdf", 1);
        Path docDir = tempDir.resolve("docs");
        Path imageDir = tempDir.resolve("images");

        service.analysicPdf2Txt(pdfPath.toString());
        assertTrue(Files.exists(tempDir.resolve("sample.txt")));

        service.analysicPdf2Image(pdfPath.toString(), imageDir.toString(), 96);
        Path png = imageDir.resolve("sample").resolve("sample_1.png");
        assertTrue(Files.exists(png));

        service.analysicPdf2Doc(pdfPath.toString(), docDir.toString());
        assertTrue(Files.isRegularFile(docDir.resolve("sample.docx")));
    }

    @Test
    void extractsEveryPageWithoutFreeVersionTruncation() throws Exception {
        for (int pages : new int[]{1, 3, 4, 10, 11}) {
            Path pdf = createPdf("text-" + pages + ".pdf", pages);
            service.analysicPdf2Txt(pdf.toString());
            String text = Files.readString(tempDir.resolve("text-" + pages + ".txt"));
            for (int i = 1; i <= pages; i++) {
                assertTrue(text.contains("PAGE_MARKER_" + i), "缺少第" + i + "页正文");
            }
            assertFalse(text.contains("Free version"));
        }
    }

    @Test
    void defaultDirectoriesSupportRelativeChinesePaths() throws Exception {
        Path pdf = createPdf("中文样本.pdf", 1);
        String relative = Paths.get("").toAbsolutePath().relativize(pdf).toString();
        service.analysicPdf2Image(relative, null, 72);
        service.analysicPdf2Doc(relative, "");
        assertTrue(Files.isRegularFile(tempDir.resolve("中文样本/中文样本_1.png")));
        assertTrue(Files.isRegularFile(tempDir.resolve("中文样本.docx")));
    }

    @Test
    void docxContainsAllThreePages() throws Exception {
        Path pdf = createPdf("three.pdf", 3);
        service.analysicPdf2Doc(pdf.toString(), tempDir.toString());
        try (ZipFile zip = new ZipFile(tempDir.resolve("three.docx").toFile())) {
            String xml = new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(),
                    StandardCharsets.UTF_8);
            for (int i = 1; i <= 3; i++) {
                assertTrue(xml.contains("PAGE_MARKER_" + i));
            }
        }
    }

    @Test
    void imageRerunRemovesStalePagesAndPreservesResultOnFailure() throws Exception {
        Path pdf = createPdf("rerun.pdf", 3);
        service.analysicPdf2Image(pdf.toString(), null, 72);
        Path output = tempDir.resolve("rerun");
        assertEquals(612, ImageIO.read(output.resolve("rerun_1.png").toFile()).getWidth());
        createPdf("rerun.pdf", 1);
        service.analysicPdf2Image(pdf.toString(), null, 72);
        assertFalse(Files.exists(output.resolve("rerun_2.png")));
        byte[] original = Files.readAllBytes(output.resolve("rerun_1.png"));
        Files.writeString(pdf, "broken PDF");
        assertThrows(Exception.class, () -> service.analysicPdf2Image(pdf.toString(), null, 72));
        assertArrayEquals(original,
                Files.readAllBytes(output.resolve("rerun_1.png")));
    }

    @Test
    void outputRootRejectsSymlinkEscape() throws Exception {
        Path pdf = createPdf("escape.pdf", 1);
        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        Path allowed = Files.createDirectory(tempDir.resolve("allowed"));
        Files.createSymbolicLink(allowed.resolve("link"), outside);
        PdfProperties properties = new PdfProperties();
        properties.setAllowedOutputRoot(allowed.toString());
        AnalysisPdfService restricted = new AnalysisPdfServiceImpl(properties);
        assertThrows(SecurityException.class,
                () -> restricted.analysicPdf2Image(pdf.toString(), allowed.resolve("link").toString(), 72));
        assertFalse(Files.exists(outside.resolve("escape")));
    }

    @Test
    void rejectsInvalidFilesAndDpi() throws Exception {
        Path txtFile = Files.writeString(tempDir.resolve("not-pdf.txt"), "text");
        assertThrows(IllegalArgumentException.class, () -> service.analysicPdf2Txt(txtFile.toString()));
        assertThrows(IllegalArgumentException.class, () -> service.analysicPdf2Txt(null));
        Path pdfPath = createPdf("dpi.pdf", 1);
        for (int dpi : new int[]{0, 71, 301}) {
            assertThrows(IllegalArgumentException.class,
                    () -> service.analysicPdf2Image(pdfPath.toString(), null, dpi));
        }
    }

    @Test
    void rejectsTooManyPagesForDocxWithoutReplacingExistingFile() throws Exception {
        Path pdfPath = createPdf("long.pdf", 4);
        Path target = Files.writeString(tempDir.resolve("long.docx"), "existing result");
        Exception error = assertThrows(IllegalStateException.class,
                () -> service.analysicPdf2Doc(pdfPath.toString(), tempDir.toString()));
        assertTrue(error.getMessage().contains("3页"));
        assertEquals("existing result", Files.readString(target));
    }

    @Test
    void oversizedLaterPagePreservesImagesAndCleansStaging() throws Exception {
        Path pdf = createPdf("large.pdf", 1);
        service.analysicPdf2Image(pdf.toString(), null, 72);
        Path image = tempDir.resolve("large/large_1.png");
        byte[] original = Files.readAllBytes(image);
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.addPage(new PDPage(new PDRectangle(100000, 100000)));
            document.save(pdf.toFile());
        }
        assertThrows(IllegalArgumentException.class,
                () -> service.analysicPdf2Image(pdf.toString(), null, 72));
        assertArrayEquals(original, Files.readAllBytes(image));
        try (java.util.stream.Stream<Path> files = Files.list(tempDir)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().contains("staging")));
        }
    }

    @Test
    void refusesToDeleteUnrelatedImageDirectoryContents() throws Exception {
        Path pdf = createPdf("owned.pdf", 1);
        Path output = Files.createDirectory(tempDir.resolve("owned"));
        Path userFile = Files.writeString(output.resolve("notes.txt"), "keep");
        assertThrows(java.io.IOException.class,
                () -> service.analysicPdf2Image(pdf.toString(), null, 72));
        assertEquals("keep", Files.readString(userFile));
    }

    @Test
    void rejectsInputSymlinkOutsideRoot() throws Exception {
        Path pdf = createPdf("outside.pdf", 1);
        Path allowed = Files.createDirectory(tempDir.resolve("input"));
        Path link = Files.createSymbolicLink(allowed.resolve("linked.pdf"), pdf);
        PdfProperties properties = new PdfProperties();
        properties.setAllowedInputRoot(allowed.toString());
        AnalysisPdfService restricted = new AnalysisPdfServiceImpl(properties);
        assertThrows(SecurityException.class, () -> restricted.analysicPdf2Txt(link.toString()));
    }

    @Test
    void enforcesFileAndPageLimits() throws Exception {
        Path pdf = createPdf("limits.pdf", 2);
        PdfProperties properties = new PdfProperties();
        properties.setMaxFileSize(1);
        AnalysisPdfService restricted = new AnalysisPdfServiceImpl(properties);
        assertThrows(IllegalStateException.class, () -> restricted.analysicPdf2Txt(pdf.toString()));
        properties.setMaxFileSize(1024 * 1024);
        properties.setMaxPages(1);
        assertThrows(IllegalStateException.class, () -> restricted.analysicPdf2Txt(pdf.toString()));
        assertFalse(Files.exists(tempDir.resolve("limits.txt")));
    }

    @Test
    void corruptAndEncryptedPdfPreserveExistingText() throws Exception {
        Path pdf = createPdf("invalid.pdf", 1);
        Path target = Files.writeString(tempDir.resolve("invalid.txt"), "keep");
        Files.writeString(pdf, "broken PDF");
        assertThrows(java.io.IOException.class, () -> service.analysicPdf2Txt(pdf.toString()));
        assertEquals("keep", Files.readString(target));
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.protect(new StandardProtectionPolicy("owner", "user", new AccessPermission()));
            document.save(pdf.toFile());
        }
        assertThrows(java.io.IOException.class, () -> service.analysicPdf2Txt(pdf.toString()));
        assertEquals("keep", Files.readString(target));
    }

    @Test
    void rejectsConfigurationThatExceedsEngineCapabilities() {
        PdfProperties properties = new PdfProperties();
        assertThrows(IllegalArgumentException.class, () -> properties.setFreeDocxMaxPages(10));
        assertThrows(IllegalArgumentException.class, () -> properties.setMaxDpi(301));
        assertThrows(IllegalArgumentException.class, () -> properties.setMaxPages(0));
        assertThrows(IllegalArgumentException.class, () -> properties.setMaxFileSize(-1));
        assertThrows(IllegalArgumentException.class, () -> properties.setMaxImagePixels(0));
    }

    @Test
    void longLegalFileNamesWorkForEveryConversionAndImageReplacement() throws Exception {
        String name = "a".repeat(240);
        Path pdf = createPdf(name + ".pdf", 1);
        service.analysicPdf2Txt(pdf.toString());
        service.analysicPdf2Doc(pdf.toString(), null);
        service.analysicPdf2Image(pdf.toString(), null, 72);
        // 再次转换覆盖目录，验证备份名称同样不依赖原文件名长度。
        service.analysicPdf2Image(pdf.toString(), null, 72);
        assertTrue(Files.readString(tempDir.resolve(name + ".txt")).contains("PAGE_MARKER_1"));
        assertTrue(Files.isRegularFile(tempDir.resolve(name + ".docx")));
        assertTrue(Files.isRegularFile(tempDir.resolve(name).resolve(name + "_1.png")));
    }

    @Test
    void replacementsPreserveExistingPosixPermissions() throws Exception {
        Assumptions.assumeTrue(Files.getFileStore(tempDir).supportsFileAttributeView("posix"));
        Path pdf = createPdf("permissions.pdf", 1);
        Path txt = Files.writeString(tempDir.resolve("permissions.txt"), "old text");
        Path docx = Files.writeString(tempDir.resolve("permissions.docx"), "old word");
        Path images = Files.createDirectory(tempDir.resolve("permissions"));
        Path png = Files.writeString(images.resolve("permissions_1.png"), "old image");
        Files.setPosixFilePermissions(txt, PosixFilePermissions.fromString("rw-r-----"));
        Files.setPosixFilePermissions(docx, PosixFilePermissions.fromString("rw-r--r--"));
        Files.setPosixFilePermissions(images, PosixFilePermissions.fromString("rwxr-x---"));
        Files.setPosixFilePermissions(png, PosixFilePermissions.fromString("rw-------"));
        service.analysicPdf2Txt(pdf.toString());
        service.analysicPdf2Doc(pdf.toString(), null);
        service.analysicPdf2Image(pdf.toString(), null, 72);
        assertEquals("rw-r-----", PosixFilePermissions.toString(Files.getPosixFilePermissions(txt)));
        assertEquals("rw-r--r--", PosixFilePermissions.toString(Files.getPosixFilePermissions(docx)));
        assertEquals("rwxr-x---", PosixFilePermissions.toString(Files.getPosixFilePermissions(images)));
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(png)));
    }

    private Path createPdf(String name, int pageCount) throws Exception {
        Path pdfPath = tempDir.resolve(name);
        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < pageCount; i++) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(50, 700);
                    content.showText("PAGE_MARKER_" + (i + 1));
                    content.endText();
                }
            }
            document.save(pdfPath.toFile());
        }
        return pdfPath;
    }
}
