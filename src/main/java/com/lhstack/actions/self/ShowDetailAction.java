package com.lhstack.actions.self;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.lhstack.Icons;
import com.lhstack.components.TextFieldDialog;
import com.lhstack.utils.NotifyUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.function.Supplier;

public class ShowDetailAction extends AnAction {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final Supplier<String> certificatePemSupplier;
    private final Project project;

    public ShowDetailAction(Supplier<String> certificatePemSupplier, Project project) {
        super(() -> "查看证书详情", Icons.SHOW);
        this.certificatePemSupplier = certificatePemSupplier;
        this.project = project;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        try {
            String text = certificatePemSupplier.get();
            if (StringUtils.isBlank(text)) {
                NotifyUtils.notifyWarning("证书内容为空,请先生成或导入证书", project);
                return;
            }

            X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));

            StringBuilder sb = new StringBuilder();
            sb.append("═══════════════════════════════════════════════════════════════\n");
            sb.append("                        证书详细信息\n");
            sb.append("═══════════════════════════════════════════════════════════════\n\n");

            sb.append("【基本信息】\n");
            sb.append("───────────────────────────────────────────────────────────────\n");
            sb.append(String.format("主题(Subject): %s\n", certificate.getSubjectX500Principal().getName()));
            sb.append(String.format("颁发者(Issuer): %s\n", certificate.getIssuerX500Principal().getName()));
            sb.append(String.format("版本: V%d\n", certificate.getVersion()));
            sb.append(String.format("序列号: %s\n", certificate.getSerialNumber().toString(16).toUpperCase()));

            sb.append("\n【有效期】\n");
            sb.append("───────────────────────────────────────────────────────────────\n");
            sb.append(String.format("生效时间: %s\n", DATE_FORMAT.format(certificate.getNotBefore())));
            sb.append(String.format("过期时间: %s\n", DATE_FORMAT.format(certificate.getNotAfter())));

            // 检查有效性
            try {
                certificate.checkValidity();
                sb.append("状态: 有效\n");
            } catch (Exception e) {
                sb.append("状态: 已过期或未生效\n");
            }

            sb.append("\n【签名信息】\n");
            sb.append("───────────────────────────────────────────────────────────────\n");
            sb.append(String.format("签名算法: %s\n", certificate.getSigAlgName()));
            sb.append(String.format("签名算法OID: %s\n", certificate.getSigAlgOID()));

            sb.append("\n【公钥信息】\n");
            sb.append("───────────────────────────────────────────────────────────────\n");
            sb.append(String.format("公钥算法: %s\n", certificate.getPublicKey().getAlgorithm()));
            sb.append(String.format("公钥格式: %s\n", certificate.getPublicKey().getFormat()));

            sb.append("\n【完整证书内容】\n");
            sb.append("───────────────────────────────────────────────────────────────\n");
            sb.append(certificate.toString());

            String title = "证书详情 - " + extractCN(certificate.getSubjectX500Principal().getName());
            new TextFieldDialog(title, sb.toString(), project).setVisible(true);
        } catch (Throwable e) {
            NotifyUtils.notifyError("查看证书详情出错: " + e.getMessage(), project);
        }
    }

    /**
     * 从DN中提取CN
     */
    private String extractCN(String dn) {
        if (dn == null) return "未知";
        for (String part : dn.split(",")) {
            String trimmed = part.trim();
            if (trimmed.toUpperCase().startsWith("CN=")) {
                return trimmed.substring(3);
            }
        }
        return dn.length() > 50 ? dn.substring(0, 47) + "..." : dn;
    }
}
