package com.lhstack.actions.self;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.lhstack.FileChooser;
import com.lhstack.Icons;
import com.lhstack.utils.NotifyUtils;
import com.lhstack.utils.PemUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public class ExportCertificateAction extends AnAction {

    private static final int TYPE_CERTIFICATE = 0;
    private static final int TYPE_PRIVATE_KEY = 1;

    private final Supplier<String> pemSupplier;
    private final Project project;
    private final String exportFileName;
    private final Integer type;

    public ExportCertificateAction(Integer type, String exportFileName, Supplier<String> pemSupplier, Project project) {
        super(() -> type == TYPE_CERTIFICATE ? "导出证书" : "导出私钥", Icons.EXPORT);
        this.pemSupplier = pemSupplier;
        this.project = project;
        this.exportFileName = exportFileName;
        this.type = type;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        String text = this.pemSupplier.get();
        if (StringUtils.isBlank(text)) {
            NotifyUtils.notifyWarning("内容为空,请先生成或导入证书", project);
            return;
        }

        String[] extensions = Objects.equals(type, TYPE_CERTIFICATE)
                ? new String[]{"pem", "crt", "cer", "der"}
                : new String[]{"pem", "key", "der"};

        FileChooser.chooseSaveFile("导出", this.exportFileName, project, virtualFile -> {
            String extension = Optional.ofNullable(virtualFile.getExtension()).orElse("pem").toLowerCase();
            try {
                switch (extension) {
                    case "pem":
                        Files.write(virtualFile.toNioPath(), text.getBytes(StandardCharsets.UTF_8));
                        NotifyUtils.notify("已导出为PEM格式", project);
                        break;
                    case "crt":
                    case "cer":
                    case "der":
                        if (Objects.equals(type, TYPE_CERTIFICATE)) {
                            Certificate certificate = CertificateFactory.getInstance("X.509")
                                    .generateCertificate(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
                            Files.write(virtualFile.toNioPath(), certificate.getEncoded());
                            NotifyUtils.notify("已导出为DER格式证书", project);
                        } else {
                            exportPrivateKey(text, virtualFile.toNioPath().toString());
                        }
                        break;
                    case "key":
                        exportPrivateKey(text, virtualFile.toNioPath().toString());
                        break;
                    default:
                        NotifyUtils.notifyWarning("不支持的导出格式: " + extension, project);
                        FileUtil.delete(new File(virtualFile.getPresentableUrl()));
                }
            } catch (Throwable e) {
                NotifyUtils.notifyError("导出失败: " + e.getMessage(), project);
                FileUtil.delete(new File(virtualFile.getPresentableUrl()));
            }
        }, extensions);
    }

    private void exportPrivateKey(String pemContent, String path) throws Exception {
        PrivateKey privateKey = PemUtils.readPrivateKey(pemContent);
        if (privateKey != null) {
            Files.write(java.nio.file.Paths.get(path), new PKCS8EncodedKeySpec(privateKey.getEncoded()).getEncoded());
            NotifyUtils.notify("已导出为PKCS8格式私钥", project);
        } else {
            throw new RuntimeException("无法解析私钥内容");
        }
    }
}
