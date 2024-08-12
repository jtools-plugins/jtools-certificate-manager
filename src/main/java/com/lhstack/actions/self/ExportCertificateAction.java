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

    private final Supplier<String> pemSupplier;
    private final Project project;
    private final String exportFileName;
    //0: 证书 1: 私钥
    private final Integer type;

    public ExportCertificateAction(Integer type, String exportFileName, Supplier<String> pemSupplier, Project project) {
        super(() -> "导出证书内容", Icons.EXPORT);
        this.pemSupplier = pemSupplier;
        this.project = project;
        this.exportFileName = exportFileName;
        this.type = type;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        String text = this.pemSupplier.get();
        if (StringUtils.isBlank(text)) {
            NotifyUtils.notify("导出证书内容为空,请先生成或者导入证书", project);
            return;
        }
        String dynamicExtensions = Objects.equals(type, 0) ? "crt" : "key";
        FileChooser.chooseSaveFile("导出证书", this.exportFileName, project, virtualFile -> {
            String extension = Optional.ofNullable(virtualFile.getExtension()).orElse("");
            switch (extension) {
                case "pem":
                    Files.write(virtualFile.toNioPath(), text.getBytes(StandardCharsets.UTF_8));
                    break;
                case "crt":
                    Certificate certificate = CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
                    Files.write(virtualFile.toNioPath(), certificate.getEncoded());
                    break;
                case "key":
                    PrivateKey privateKey = PemUtils.readPrivateKey(text);
                    if (privateKey != null) {
                        Files.write(virtualFile.toNioPath(), new PKCS8EncodedKeySpec(privateKey.getEncoded()).getEncoded());
                    } else {
                        NotifyUtils.notify("导出失败,不支持的私钥类型", project);
                        FileUtil.delete(new File(virtualFile.getPresentableUrl()));
                    }
            }
        }, "pem", dynamicExtensions);
    }
}
