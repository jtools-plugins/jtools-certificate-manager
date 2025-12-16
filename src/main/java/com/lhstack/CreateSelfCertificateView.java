package com.lhstack;

import com.google.gson.Gson;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.LanguageTextField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.lhstack.actions.self.CopyToClipboardAction;
import com.lhstack.actions.self.ExportCertificateAction;
import com.lhstack.actions.self.ShowDetailAction;
import com.lhstack.components.DefaultContextMenuPopupHandler;
import com.lhstack.selfsign.SelfSignCertificateEntity;
import com.lhstack.selfsign.SelfSignCertificateHelper;
import com.lhstack.selfsign.SelfSignConfig;
import com.lhstack.state.ProjectState;
import com.lhstack.utils.NotifyUtils;
import com.lhstack.utils.PemUtils;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.jetbrains.annotations.NotNull;
import org.yaml.snakeyaml.Yaml;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 将证书内容以pem数据格式显示,不限于私钥,公钥等
 */
public class CreateSelfCertificateView extends JPanel implements Disposable {
    private final Project project;

    private final Map<String, LanguageTextField> languageTextFields;
    private final ProjectState.State state;
    private String template;

    private final List<Disposable> disposableList = new ArrayList<>();

    public CreateSelfCertificateView(Project project) {
        this.project = project;
        this.languageTextFields = new HashMap<>();
        this.state = ProjectState.getInstance(project).getState();
        this.init();
    }

    private void init() {
        this.setLayout(new BorderLayout());
        JPanel panel = new JPanel(new BorderLayout());
        JPanel mainPanel = createMainPan();
        JPanel buttonPanel = createButtonPanel();
        panel.add(mainPanel, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        this.add(panel, BorderLayout.CENTER);
    }

    private JPanel createButtonPanel() {
        JPanel jPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        jPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(200, 200, 200)));
        
        // 清空按钮
        JButton clearButton = new JButton("清空");
        clearButton.setToolTipText("清空所有生成的证书内容");
        clearButton.addActionListener(e -> {
            int result = Messages.showYesNoDialog(project, "确定要清空所有生成的证书内容吗?", "确认清空", Messages.getWarningIcon());
            if (result == Messages.YES) {
                languageTextFields.get("ca").setText("");
                languageTextFields.get("ca-key").setText("");
                languageTextFields.get("certificate").setText("");
                languageTextFields.get("certificate-key").setText("");
                state.setCaPem(null).setCaKeyPem(null).setCertificatePem(null).setCertificateKeyPem(null);
                NotifyUtils.notify("已清空所有证书内容", project);
            }
        });
        jPanel.add(clearButton);
        
        // 一键导出按钮 - 弹出选择对话框
        JButton exportButton = new JButton("导出证书...");
        exportButton.setToolTipText("选择导出方式");
        exportButton.addActionListener(e -> showExportDialog());
        jPanel.add(exportButton);
        
        return jPanel;
    }

    /**
     * 显示导出选择对话框
     */
    private void showExportDialog() {
        String ca = languageTextFields.get("ca").getText();
        String caKey = languageTextFields.get("ca-key").getText();
        String certificate = languageTextFields.get("certificate").getText();
        String certificateKey = languageTextFields.get("certificate-key").getText();
        
        boolean hasCa = StringUtils.isNotBlank(ca) && StringUtils.isNotBlank(caKey);
        boolean hasCert = StringUtils.isNotBlank(certificate) && StringUtils.isNotBlank(certificateKey);
        
        if (!hasCa && !hasCert) {
            Messages.showErrorDialog("没有可导出的证书,请先生成证书", "提示");
            return;
        }
        
        String[] options;
        if (hasCa && hasCert) {
            options = new String[]{
                "仅导出服务器证书 (用于部署到服务器)",
                "仅导出CA证书 (用于客户端信任)",
                "导出全部 (CA + 服务器证书)",
                "取消"
            };
        } else if (hasCa) {
            options = new String[]{
                "导出CA证书",
                "取消"
            };
        } else {
            options = new String[]{
                "导出服务器证书",
                "取消"
            };
        }
        
        int choice = JOptionPane.showOptionDialog(
                null,
                "请选择要导出的内容:\n\n" +
                "• 服务器证书: 部署到Web服务器(Nginx/Apache等)\n" +
                "• CA证书: 导入到客户端使其信任自签证书",
                "选择导出内容",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]
        );
        
        if (hasCa && hasCert) {
            switch (choice) {
                case 0: showFormatDialog(ExportType.SERVER_ONLY); break;
                case 1: showFormatDialog(ExportType.CA_ONLY); break;
                case 2: showFormatDialog(ExportType.ALL); break;
            }
        } else if (hasCa && choice == 0) {
            showFormatDialog(ExportType.CA_ONLY);
        } else if (hasCert && choice == 0) {
            showFormatDialog(ExportType.SERVER_ONLY);
        }
    }

    private enum ExportType { CA_ONLY, SERVER_ONLY, ALL }

    /**
     * 显示格式选择对话框
     */
    private void showFormatDialog(ExportType type) {
        String[] formats = {"PEM格式 (通用)", "JKS格式 (Java)", "PKCS12/P12格式 (通用)", "取消"};
        
        int choice = JOptionPane.showOptionDialog(
                null,
                "请选择导出格式:\n\n" +
                "• PEM: 文本格式,Nginx/Apache等常用\n" +
                "• JKS: Java KeyStore,Java应用使用\n" +
                "• P12: PKCS12格式,Windows/浏览器常用",
                "选择导出格式",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                formats,
                formats[0]
        );
        
        switch (choice) {
            case 0: exportAsPem(type); break;
            case 1: exportAsKeyStore(type, "JKS", "jks"); break;
            case 2: exportAsKeyStore(type, "PKCS12", "p12"); break;
        }
    }

    /**
     * 导出为PEM格式
     */
    private void exportAsPem(ExportType type) {
        String ca = languageTextFields.get("ca").getText();
        String caKey = languageTextFields.get("ca-key").getText();
        String certificate = languageTextFields.get("certificate").getText();
        String certificateKey = languageTextFields.get("certificate-key").getText();
        
        String defaultName;
        switch (type) {
            case CA_ONLY: defaultName = "ca-certs.zip"; break;
            case SERVER_ONLY: defaultName = "server-certs.zip"; break;
            default: defaultName = "all-certs.zip"; break;
        }
        
        FileChooser.chooseSaveFile("导出PEM证书", defaultName, project, virtualFile -> {
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(virtualFile.getPresentableUrl()))) {
                if (type == ExportType.CA_ONLY || type == ExportType.ALL) {
                    addZipEntry(zos, "ca/ca.pem", ca);
                    addZipEntry(zos, "ca/ca-key.pem", caKey);
                    addZipEntry(zos, "ca/README.txt", createCaReadme());
                }
                if (type == ExportType.SERVER_ONLY || type == ExportType.ALL) {
                    addZipEntry(zos, "server/server.pem", certificate);
                    addZipEntry(zos, "server/server-key.pem", certificateKey);
                    // 如果有CA,也导出证书链
                    if (StringUtils.isNotBlank(ca)) {
                        String fullchain = certificate + "\n" + ca;
                        addZipEntry(zos, "server/fullchain.pem", fullchain);
                    }
                    addZipEntry(zos, "server/README.txt", createServerReadme());
                }
                if (type == ExportType.ALL) {
                    addZipEntry(zos, "README.txt", createReadme());
                }
                NotifyUtils.notify("PEM证书导出成功", project);
            }
        }, "zip");
    }

    private String createCaReadme() {
        return "=== CA根证书说明 ===\n\n" +
                "文件说明:\n" +
                "- ca.pem: CA根证书(公钥)\n" +
                "- ca-key.pem: CA私钥(请妥善保管!)\n\n" +
                "使用方法:\n" +
                "1. 将 ca.pem 导入到客户端的信任证书库中\n" +
                "   - Windows: 双击ca.pem -> 安装证书 -> 受信任的根证书颁发机构\n" +
                "   - macOS: 双击ca.pem -> 添加到钥匙串 -> 设置为始终信任\n" +
                "   - Linux: 复制到 /usr/local/share/ca-certificates/ 并运行 update-ca-certificates\n" +
                "   - Java: keytool -import -trustcacerts -file ca.pem -alias myca -keystore cacerts\n\n" +
                "注意: CA私钥(ca-key.pem)用于签发新证书,请妥善保管,不要泄露!";
    }

    private String createServerReadme() {
        return "=== 服务器证书说明 ===\n\n" +
                "文件说明:\n" +
                "- server.pem: 服务器证书\n" +
                "- server-key.pem: 服务器私钥\n" +
                "- fullchain.pem: 完整证书链(服务器证书+CA证书)\n\n" +
                "Nginx配置示例:\n" +
                "  ssl_certificate /path/to/fullchain.pem;\n" +
                "  ssl_certificate_key /path/to/server-key.pem;\n\n" +
                "Apache配置示例:\n" +
                "  SSLCertificateFile /path/to/server.pem\n" +
                "  SSLCertificateKeyFile /path/to/server-key.pem\n" +
                "  SSLCertificateChainFile /path/to/ca.pem\n\n" +
                "注意: 私钥文件请妥善保管,不要泄露!";
    }

    /**
     * 通用KeyStore导出方法 - 支持按类型导出
     */
    private void exportAsKeyStore(ExportType type, String storeType, String extension) {
        String ca = languageTextFields.get("ca").getText();
        String caKey = languageTextFields.get("ca-key").getText();
        String certificate = languageTextFields.get("certificate").getText();
        String certificateKey = languageTextFields.get("certificate-key").getText();
        
        boolean hasCa = StringUtils.isNotBlank(ca) && StringUtils.isNotBlank(caKey);
        boolean hasCert = StringUtils.isNotBlank(certificate) && StringUtils.isNotBlank(certificateKey);
        
        // 验证所需证书是否存在
        if (type == ExportType.CA_ONLY && !hasCa) {
            Messages.showErrorDialog("CA证书不存在,请先生成或导入CA证书", "提示");
            return;
        }
        if (type == ExportType.SERVER_ONLY && !hasCert) {
            Messages.showErrorDialog("服务器证书不存在,请先生成服务器证书", "提示");
            return;
        }
        if (type == ExportType.ALL && (!hasCa || !hasCert)) {
            Messages.showErrorDialog("证书不完整,请先生成CA证书和服务器证书", "提示");
            return;
        }
        
        String defaultFileName;
        switch (type) {
            case CA_ONLY: defaultFileName = "ca-" + extension + ".zip"; break;
            case SERVER_ONLY: defaultFileName = "server-" + extension + ".zip"; break;
            default: defaultFileName = "all-certs-" + extension + ".zip"; break;
        }
        
        FileChooser.chooseSaveFile("导出" + storeType + "证书", defaultFileName, project, virtualFile -> {
            StringBuilder passwordInfo = new StringBuilder();
            passwordInfo.append("=== ").append(storeType).append(" 证书密码信息 ===\n\n");
            
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(virtualFile.getPresentableUrl()))) {
                // 导出CA KeyStore
                if ((type == ExportType.CA_ONLY || type == ExportType.ALL) && hasCa) {
                    String caPassword = generateShortPassword();
                    String folder = type == ExportType.ALL ? "ca/" : "";
                    
                    zos.putNextEntry(new ZipEntry(folder + "ca." + extension));
                    Certificate caObj = PemUtils.readCertificate(ca);
                    PrivateKey caPrivateKey = PemUtils.readPrivateKey(caKey);
                    KeyStore caStore = KeyStore.getInstance(storeType);
                    caStore.load(null, null);
                    caStore.setKeyEntry("ca", caPrivateKey, caPassword.toCharArray(), new Certificate[]{caObj});
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    caStore.store(baos, caPassword.toCharArray());
                    zos.write(baos.toByteArray());
                    zos.closeEntry();
                    
                    // 导出仅包含公钥的信任库(用于客户端导入)
                    zos.putNextEntry(new ZipEntry(folder + "ca-truststore." + extension));
                    KeyStore trustStore = KeyStore.getInstance(storeType);
                    trustStore.load(null, null);
                    trustStore.setCertificateEntry("ca", caObj);
                    baos = new ByteArrayOutputStream();
                    trustStore.store(baos, caPassword.toCharArray());
                    zos.write(baos.toByteArray());
                    zos.closeEntry();
                    
                    passwordInfo.append("CA证书:\n");
                    passwordInfo.append("  ca.").append(extension).append(" (含私钥): ").append(caPassword).append("\n");
                    passwordInfo.append("  ca-truststore.").append(extension).append(" (仅公钥,用于客户端信任): ").append(caPassword).append("\n\n");
                    
                    if (type == ExportType.CA_ONLY || type == ExportType.ALL) {
                        addZipEntry(zos, folder + "README.txt", createCaKeystoreReadme(storeType, extension));
                    }
                }
                
                // 导出服务器证书 KeyStore
                if ((type == ExportType.SERVER_ONLY || type == ExportType.ALL) && hasCert) {
                    String certPassword = generateShortPassword();
                    String folder = type == ExportType.ALL ? "server/" : "";
                    
                    zos.putNextEntry(new ZipEntry(folder + "server." + extension));
                    Certificate certObj = PemUtils.readCertificate(certificate);
                    PrivateKey certPrivateKey = PemUtils.readPrivateKey(certificateKey);
                    
                    // 如果有CA,创建证书链
                    Certificate[] certChain;
                    if (hasCa) {
                        Certificate caObj = PemUtils.readCertificate(ca);
                        certChain = new Certificate[]{certObj, caObj};
                    } else {
                        certChain = new Certificate[]{certObj};
                    }
                    
                    KeyStore certStore = KeyStore.getInstance(storeType);
                    certStore.load(null, null);
                    certStore.setKeyEntry("server", certPrivateKey, certPassword.toCharArray(), certChain);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    certStore.store(baos, certPassword.toCharArray());
                    zos.write(baos.toByteArray());
                    zos.closeEntry();
                    
                    passwordInfo.append("服务器证书:\n");
                    passwordInfo.append("  server.").append(extension).append(": ").append(certPassword).append("\n\n");
                    
                    addZipEntry(zos, folder + "README.txt", createServerKeystoreReadme(storeType, extension));
                }
                
                passwordInfo.append("注意: 私钥密码和存储密码相同\n");
                
                String passwordFile = type == ExportType.ALL ? "密码.txt" : "密码.txt";
                addZipEntry(zos, passwordFile, passwordInfo.toString());
                
                if (type == ExportType.ALL) {
                    addZipEntry(zos, "README.txt", createReadme());
                }
                
                NotifyUtils.notify(storeType + "证书导出成功", project);
            }
        }, "zip");
    }

    private String createCaKeystoreReadme(String storeType, String extension) {
        return "=== CA证书(" + storeType + "格式)说明 ===\n\n" +
                "文件说明:\n" +
                "- ca." + extension + ": CA证书(含私钥),用于签发新证书\n" +
                "- ca-truststore." + extension + ": CA信任库(仅公钥),用于客户端信任\n\n" +
                "客户端信任配置:\n" +
                "Java应用添加信任:\n" +
                "  -Djavax.net.ssl.trustStore=/path/to/ca-truststore." + extension + "\n" +
                "  -Djavax.net.ssl.trustStorePassword=<密码>\n\n" +
                "或在代码中加载:\n" +
                "  KeyStore trustStore = KeyStore.getInstance(\"" + storeType + "\");\n" +
                "  trustStore.load(new FileInputStream(\"ca-truststore." + extension + "\"), password);\n\n" +
                "注意: ca." + extension + "(含私钥)请妥善保管,不要泄露!";
    }

    private String createServerKeystoreReadme(String storeType, String extension) {
        return "=== 服务器证书(" + storeType + "格式)说明 ===\n\n" +
                "文件说明:\n" +
                "- server." + extension + ": 服务器证书(含私钥和证书链)\n\n" +
                "Spring Boot配置示例:\n" +
                "  server.ssl.key-store=classpath:server." + extension + "\n" +
                "  server.ssl.key-store-password=<密码>\n" +
                "  server.ssl.key-store-type=" + storeType + "\n\n" +
                "Tomcat配置示例:\n" +
                "  <Connector port=\"8443\" protocol=\"HTTP/1.1\" SSLEnabled=\"true\"\n" +
                "    keystoreFile=\"/path/to/server." + extension + "\"\n" +
                "    keystorePass=\"<密码>\"\n" +
                "    keystoreType=\"" + storeType + "\"/>\n\n" +
                "注意: 私钥文件请妥善保管,不要泄露!";
    }

    private String generateShortPassword() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private void addZipEntry(ZipOutputStream zos, String name, String content) throws Exception {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private String createReadme() {
        return "=== 自签名证书说明 ===\n\n" +
                "本证书包由 JTools Certificate Manager 插件生成\n\n" +
                "文件说明:\n" +
                "- ca.pem / ca.jks / ca.p12: CA根证书\n" +
                "- ca-key.pem: CA私钥\n" +
                "- certificate.pem / certificate.jks / certificate.p12: 服务器证书\n" +
                "- certificate-key.pem: 服务器证书私钥\n\n" +
                "使用说明:\n" +
                "1. 将CA证书导入到客户端的信任证书库中\n" +
                "2. 将服务器证书和私钥配置到服务器上\n\n" +
                "注意事项:\n" +
                "- 请妥善保管私钥文件,不要泄露\n" +
                "- 自签名证书仅用于开发测试环境\n" +
                "- 生产环境请使用正规CA签发的证书";
    }

    private JPanel createMainPan() {
        JBSplitter splitter = new JBSplitter(false);
        splitter.setFirstComponent(createLeftPanel());
        splitter.setSecondComponent(createRightPanel());
        return splitter;
    }

    private JComponent createRightPanel() {
        JBTabbedPane tabbedPane = new JBTabbedPane();
        tabbedPane.addTab("ca.pem", createTextFieldPanel("ca", state.getCaPem(), editorEx -> {
            editorEx.installPopupHandler(new DefaultContextMenuPopupHandler(
                    new ShowDetailAction(state::getCaPem, project),
                    new CopyToClipboardAction("CA证书", state::getCaPem, project),
                    new ExportCertificateAction(0, "ca", state::getCaPem, project)
            ));
        }));
        tabbedPane.addTab("ca.key", createTextFieldPanel("ca-key", state.getCaKeyPem(), editorEx -> {
            editorEx.installPopupHandler(new DefaultContextMenuPopupHandler(
                    new CopyToClipboardAction("CA私钥", state::getCaKeyPem, project),
                    new ExportCertificateAction(1, "ca-key", state::getCaKeyPem, project)
            ));
        }));
        tabbedPane.addTab("certificate.pem", createTextFieldPanel("certificate", state.getCertificatePem(), editorEx -> {
            editorEx.installPopupHandler(new DefaultContextMenuPopupHandler(
                    new ShowDetailAction(state::getCertificatePem, project),
                    new CopyToClipboardAction("服务器证书", state::getCertificatePem, project),
                    new ExportCertificateAction(0, "certificate", state::getCertificatePem, project)
            ));
        }));
        tabbedPane.addTab("certificate.key", createTextFieldPanel("certificate-key", state.getCertificateKeyPem(), editorEx -> {
            editorEx.installPopupHandler(new DefaultContextMenuPopupHandler(
                    new CopyToClipboardAction("服务器私钥", state::getCertificateKeyPem, project),
                    new ExportCertificateAction(1, "certificate-key", state::getCertificateKeyPem, project)
            ));
        }));
        return tabbedPane;
    }


    private JComponent createTextFieldPanel(String name, String initText, Consumer<EditorEx> editorExConsumer) {
        LanguageTextField languageTextField = new LanguageTextField(PlainTextLanguage.INSTANCE, project, Optional.ofNullable(initText).orElse(""), false) {
            @Override
            protected @NotNull EditorEx createEditor() {
                EditorEx editor = super.createEditor();
                EditorSettings settings = editor.getSettings();
                settings.setLineMarkerAreaShown(false);
                settings.setLineNumbersShown(false);
                settings.setUseSoftWraps(true);
                try {
                    editorExConsumer.accept(editor);
                } catch (Throwable ignored) {
                }
                return editor;
            }
        };
        disposableList.add(() -> {
            Editor editor = languageTextField.getEditor();
            if (editor != null && !editor.isDisposed()) {
                try {
                    EditorFactory.getInstance().releaseEditor(editor);
                } catch (Throwable ignored) {
                }
            }
        });
        languageTextField.setEnabled(false);
        languageTextFields.put(name, languageTextField);
        return new JBScrollPane(languageTextField);
    }

    private JComponent createLeftPanel() {
        SimpleToolWindowPanel simpleToolWindowPanel = new SimpleToolWindowPanel(true);

        DefaultActionGroup actionGroup = new DefaultActionGroup();
        actionGroup.add(createTemplateAction());
        actionGroup.add(createGenCaAction());
        actionGroup.add(createImportCaAction());
        actionGroup.add(genCertificateAction());
        ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar("SelfSignCertificate", actionGroup, false);
        actionToolbar.setTargetComponent(simpleToolWindowPanel);
        simpleToolWindowPanel.setToolbar(actionToolbar.getComponent());
        
        // 配置模块 - 查找YAML语言支持
        Language yamlLanguage = findYamlLanguage();
        
        LanguageTextField languageTextField = new LanguageTextField(yamlLanguage, project, Optional.ofNullable(state.getConfigYaml()).orElse(""), false) {
            @Override
            protected @NotNull EditorEx createEditor() {
                EditorEx editor = (EditorEx) EditorFactory.getInstance().createEditor(getDocument());
                editor.setHighlighter(HighlighterFactory.createHighlighter(project, getFileType()));
                ApplicationManager.getApplication().runReadAction(() -> {
                    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(getDocument());
                    if (psiFile != null) {
                        DaemonCodeAnalyzer.getInstance(project).setHighlightingEnabled(psiFile, true);
                    }
                });
                EditorSettings settings = editor.getSettings();
                settings.setLineMarkerAreaShown(false);
                settings.setLineCursorWidth(1);
                settings.setRightMargin(-1);
                settings.setFoldingOutlineShown(true);
                settings.setAutoCodeFoldingEnabled(true);
                settings.setLineNumbersShown(true);
                settings.setUseSoftWraps(false);
                disposableList.add(() -> {
                    if (!editor.isDisposed()) {
                        EditorFactory.getInstance().releaseEditor(editor);
                    }
                });
                return editor;
            }
        };
        languageTextField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void documentChanged(@NotNull DocumentEvent event) {
                state.setConfigYaml(languageTextField.getDocument().getText());
            }
        });
        languageTextFields.put("config", languageTextField);
        simpleToolWindowPanel.setContent(new JBScrollPane(languageTextField));
        return simpleToolWindowPanel;
    }

    private Language findYamlLanguage() {
        Language yamlLanguage = Language.findLanguageByID("yaml");
        if (yamlLanguage == null) {
            yamlLanguage = Language.findLanguageByID("yml");
        }
        if (yamlLanguage == null) {
            yamlLanguage = PlainTextLanguage.INSTANCE;
        }
        return yamlLanguage;
    }

    /**
     * 生成证书
     *
     * @return
     */
    private AnAction genCertificateAction() {
        return new AnAction(() -> "根据CA生成https证书", Icons.CERTIFICATE) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent event) {
                String caPem = languageTextFields.get("ca").getText();
                String caKeyPem = languageTextFields.get("ca-key").getText();
                if (StringUtils.isBlank(caPem) || StringUtils.isBlank(caKeyPem)) {
                    Messages.showErrorDialog("请先生成或导入CA,CA-key证书", "提示");
                    return;
                }
                LanguageTextField languageTextField = languageTextFields.get("config");
                if (languageTextField.getText().isBlank()) {
                    Messages.showErrorDialog("请添加ca配置,可点击导入模板配置按钮生成配置模板", "提示");
                    return;
                }
                try {
                    SelfSignConfig selfSignConfig = parseConfig(languageTextField.getText());
                    validateConfig(selfSignConfig);
                    SelfSignCertificateEntity entity = SelfSignCertificateHelper.genSelfCertificateFromCaPem(caPem, caKeyPem, selfSignConfig);
                    String certificatePem = PemUtils.toString(entity.getCertificate());
                    String certificateKeyPem = PemUtils.toString(entity.getCertificateKey());
                    state.setCertificatePem(certificatePem);
                    state.setCertificateKeyPem(certificateKeyPem);
                    languageTextFields.get("certificate").setText(certificatePem);
                    languageTextFields.get("certificate-key").setText(certificateKeyPem);
                    NotifyUtils.notify("证书生成成功,可通过点击右侧的certificate,certificate-key查看生成的证书内容", project);
                } catch (Throwable e) {
                    NotifyUtils.notifyError("证书生成出错: " + e.getMessage(), project);
                }
            }
        };
    }

    /**
     * 验证配置
     */
    private void validateConfig(SelfSignConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("配置不能为空");
        }
        if (config.getAlgorithm() == null || config.getAlgorithm().isBlank()) {
            throw new IllegalArgumentException("算法(algorithm)不能为空");
        }
        if (config.getCertificate() == null) {
            throw new IllegalArgumentException("证书配置(certificate)不能为空");
        }
        SelfSignConfig.Certificate cert = config.getCertificate();
        if (cert.getDn() == null || cert.getDn().isBlank()) {
            throw new IllegalArgumentException("证书DN不能为空");
        }
        if (cert.getValidityYear() == null || cert.getValidityYear() <= 0) {
            throw new IllegalArgumentException("证书有效期必须大于0");
        }
    }

    private AnAction createImportCaAction() {
        return new AnAction(() -> "导入CA,CA-Key证书", Icons.IMPORT2) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent event) {
                LanguageTextField ca = languageTextFields.get("ca");
                LanguageTextField caKey = languageTextFields.get("ca-key");
                FileChooser.chooseSingleFile("请选择ca证书", project).ifPresent(virtualFile -> {
                    try {
                        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
                        try (InputStream in = new FileInputStream(virtualFile.getPresentableUrl())) {
                            Certificate certificate = certificateFactory.generateCertificate(in);
                            String caPem = PemUtils.toString(certificate);
                            ca.setText(caPem);
                            state.setCaPem(caPem);
                            FileChooser.chooseSingleFile("请选择ca-key证书", project).ifPresent(caKeyFile -> {
                                String extension = caKeyFile.getExtension();
                                try {
                                    String caKeyPem = "";
                                    if ("pem".equalsIgnoreCase(extension)) {
                                        caKeyPem = Files.readString(caKeyFile.toNioPath());
                                        caKey.setText(caKeyPem);
                                    } else {
                                        byte[] privateKeyPemContent = Files.readAllBytes(caKeyFile.toNioPath());
                                        String algorithm = certificate.getPublicKey().getAlgorithm();
                                        try {
                                            KeyFactory keyFactory = KeyFactory.getInstance(algorithm, "BC");
                                            PEMParser pemParser = new PEMParser(new StringReader(new String(privateKeyPemContent, StandardCharsets.UTF_8)));
                                            Object o = pemParser.readObject();
                                            PrivateKey privateKey = null;
                                            if (o instanceof PEMKeyPair) {
                                                privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(((PEMKeyPair) o).getPrivateKeyInfo().getEncoded()));
                                                caKeyPem = PemUtils.toString(privateKey);
                                                caKey.setText(caKeyPem);
                                            }
                                        } catch (Throwable e) {
                                            KeyFactory keyFactory = KeyFactory.getInstance(algorithm, "BC");
                                            PrivateKey privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privateKeyPemContent));
                                            caKeyPem = PemUtils.toString(privateKey);
                                            caKey.setText(caKeyPem);
                                        }
                                    }
                                    state.setCaKeyPem(caKeyPem);
                                } catch (Throwable err) {
                                    Messages.showErrorDialog(err.getMessage(), "ca证书内容错误");
                                }
                            });
                        }
                    } catch (Throwable err) {
                        Messages.showErrorDialog(err.getMessage(), "ca证书内容错误");
                    }
                });


            }
        };
    }

    private AnAction createTemplateAction() {
        this.template = "";
        try (InputStream in = CreateSelfCertificateView.class.getClassLoader().getResourceAsStream("template/SelfSignCertificateTemplate.yaml")) {
            template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Throwable ignore) {

        }
        return new AnAction(() -> "导入模板配置", Icons.TEMPLATE) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent event) {
                LanguageTextField languageTextField = languageTextFields.get("config");
                if (languageTextField.getText().isBlank()) {
                    languageTextField.setText(template);
                } else {
                    int result = JOptionPane.showConfirmDialog(null, "当前配置文件中已存在内容了,确定导入模板并覆盖已有配置吗?", "警告", JOptionPane.OK_CANCEL_OPTION);
                    if (result == JOptionPane.OK_OPTION) {
                        languageTextField.setText(template);
                    }
                }
            }
        };
    }

    public SelfSignConfig parseConfig(String text) {
        Yaml yaml = new Yaml();
        Object object = yaml.load(text);
        Gson gson = new Gson();
        return gson.fromJson(gson.toJson(object), SelfSignConfig.class);
    }

    private AnAction createGenCaAction() {
        return new AnAction(() -> "生成CA证书", Icons.ROOT) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent event) {
                LanguageTextField languageTextField = languageTextFields.get("config");
                if (languageTextField.getText().isBlank()) {
                    Messages.showErrorDialog("请添加ca配置,可点击导入模板配置按钮生成配置模板", "提示");
                    return;
                }
                try {
                    SelfSignConfig selfSignConfig = parseConfig(languageTextField.getText());
                    validateCaConfig(selfSignConfig);
                    SelfSignCertificateEntity entity = SelfSignCertificateHelper.genCaCertificate(selfSignConfig);
                    String caPem = PemUtils.toString(entity.getCa());
                    String caKeyPem = PemUtils.toString(entity.getCaKey());
                    state.setCaPem(caPem).setCaKeyPem(caKeyPem);
                    languageTextFields.get("ca").setText(caPem);
                    languageTextFields.get("ca-key").setText(caKeyPem);
                    NotifyUtils.notify("CA证书生成成功,可在右侧tab栏中点击ca.pem,ca-key.pem查看", project);
                } catch (Throwable e) {
                    NotifyUtils.notifyError("CA证书生成失败: " + e.getMessage(), project);
                }
            }
        };
    }

    /**
     * 验证CA配置
     */
    private void validateCaConfig(SelfSignConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("配置不能为空");
        }
        if (config.getAlgorithm() == null || config.getAlgorithm().isBlank()) {
            throw new IllegalArgumentException("算法(algorithm)不能为空");
        }
        if (config.getCa() == null) {
            throw new IllegalArgumentException("CA配置不能为空");
        }
        SelfSignConfig.CA ca = config.getCa();
        if (ca.getDn() == null || ca.getDn().isBlank()) {
            throw new IllegalArgumentException("CA DN不能为空");
        }
        if (ca.getValidityYear() == null || ca.getValidityYear() <= 0) {
            throw new IllegalArgumentException("CA有效期必须大于0");
        }
    }


    @Override
    public void dispose() {
        disposableList.forEach(Disposable::dispose);
        disposableList.clear();
    }
}
