package com.lhstack;

import com.google.gson.Gson;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.LanguageTextField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
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
import org.jetbrains.yaml.YAMLLanguage;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 将证书内容以pem数据格式显示,不限于私钥,公钥等
 */
public class CreateSelfCertificateView extends JPanel {
    private final Project project;

    private final Map<String, LanguageTextField> languageTextFields;
    private final ProjectState.State state;
    private String template;

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
        JPanel jPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton button = new JButton("导出证书内容");
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    String ca = languageTextFields.get("ca").getText();
                    String caKey = languageTextFields.get("ca-key").getText();
                    String certificate = languageTextFields.get("certificate").getText();
                    String certificateKey = languageTextFields.get("certificate-key").getText();
                    if (StringUtils.isAnyBlank(ca, caKey, certificate, certificateKey)) {
                        Messages.showErrorDialog("生成的证书不完整,无法导出,请检查你是否生成/导入了CA相关证书,通过CA生成了证书", "提示");
                    } else {
                        FileChooser.chooseSaveFile("证书导出", "certs.zip", project, virtualFile -> {
                            ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(virtualFile.getPresentableUrl()));
                            zipOutputStream.putNextEntry(new ZipEntry("ca.pem"));
                            zipOutputStream.write(ca.getBytes(StandardCharsets.UTF_8));
                            zipOutputStream.closeEntry();

                            zipOutputStream.putNextEntry(new ZipEntry("ca-key.pem"));
                            zipOutputStream.write(caKey.getBytes(StandardCharsets.UTF_8));
                            zipOutputStream.closeEntry();

                            zipOutputStream.putNextEntry(new ZipEntry("certificate.pem"));
                            zipOutputStream.write(certificate.getBytes(StandardCharsets.UTF_8));
                            zipOutputStream.closeEntry();

                            zipOutputStream.putNextEntry(new ZipEntry("certificate-key.pem"));
                            zipOutputStream.write(certificateKey.getBytes(StandardCharsets.UTF_8));
                            zipOutputStream.closeEntry();
                            zipOutputStream.close();
                            NotifyUtils.notify("导出证书成功", project);
                        }, "zip");
                    }
                }
            }
        });
        jPanel.add(button);
        JButton exportJksButton = new JButton("导出证书为jks");
        exportJksButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        String ca = languageTextFields.get("ca").getText();
                        String caKey = languageTextFields.get("ca-key").getText();
                        String certificate = languageTextFields.get("certificate").getText();
                        String certificateKey = languageTextFields.get("certificate-key").getText();
                        if (StringUtils.isAnyBlank(ca, caKey, certificate, certificateKey)) {
                            Messages.showErrorDialog("生成的证书不完整,无法导出,请检查你是否生成/导入了CA相关证书,通过CA生成了证书", "提示");
                        } else {
                            FileChooser.chooseSaveFile("jks证书导出", "certs-jks.zip", project, virtualFile -> {
                                String caKeyPassword = UUID.randomUUID().toString();
                                String caStorePassword = UUID.randomUUID().toString();
                                ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(virtualFile.getPresentableUrl()));
                                zipOutputStream.putNextEntry(new ZipEntry("ca.jks"));
                                Certificate certificateObj = PemUtils.readCertificate(ca);
                                PrivateKey privateKey = PemUtils.readPrivateKey(caKey);
                                KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
                                store.load(null, null);
                                store.setKeyEntry("ca", privateKey, caKeyPassword.toCharArray(), new Certificate[]{certificateObj});
                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                store.store(baos, caStorePassword.toCharArray());
                                zipOutputStream.write(baos.toByteArray());
                                zipOutputStream.closeEntry();

                                String certificateKeyPassword = UUID.randomUUID().toString();
                                String certificateStorePassword = UUID.randomUUID().toString();
                                zipOutputStream.putNextEntry(new ZipEntry("certificate.jks"));
                                certificateObj = PemUtils.readCertificate(certificate);
                                privateKey = PemUtils.readPrivateKey(certificateKey);
                                store = KeyStore.getInstance(KeyStore.getDefaultType());
                                store.load(null, null);
                                store.setKeyEntry("certificate", privateKey, certificateKeyPassword.toCharArray(), new Certificate[]{certificateObj});
                                baos = new ByteArrayOutputStream();
                                store.store(baos, certificateStorePassword.toCharArray());
                                zipOutputStream.write(baos.toByteArray());
                                zipOutputStream.closeEntry();

                                zipOutputStream.putNextEntry(new ZipEntry("密码.txt"));
                                String passwordText = String.format("ca.jks私钥密码: %s\nca.jks存储密码: %s\ncertificate.jks私钥密码: %s\ncertificate.jks存储密码: %s", caKeyPassword, caStorePassword, certificateKeyPassword, certificateStorePassword);
                                zipOutputStream.write(passwordText.getBytes(StandardCharsets.UTF_8));
                                zipOutputStream.closeEntry();
                                zipOutputStream.close();
                                NotifyUtils.notify("导出证书成功", project);
                            }, "zip");
                        }
                    }
                }
            }
        });
        jPanel.add(exportJksButton);
        return jPanel;
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
                    new ExportCertificateAction(0, "ca", state::getCaPem, project)
            ));
        }));
        tabbedPane.addTab("ca.key", createTextFieldPanel("ca-key", state.getCaKeyPem(), editorEx -> {
            editorEx.installPopupHandler(new DefaultContextMenuPopupHandler(
                    new ExportCertificateAction(1, "ca-key", state::getCaKeyPem, project)
            ));
        }));
        tabbedPane.addTab("certificate.pem", createTextFieldPanel("certificate", state.getCertificatePem(), editorEx -> {
            editorEx.installPopupHandler(new DefaultContextMenuPopupHandler(
                    new ShowDetailAction(state::getCertificatePem, project),
                    new ExportCertificateAction(0, "certificate", state::getCertificatePem, project)
            ));
        }));
        tabbedPane.addTab("certificate.key", createTextFieldPanel("certificate-key", state.getCertificateKeyPem(), editorEx -> {
            editorEx.installPopupHandler(new DefaultContextMenuPopupHandler(
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
                settings.setLineMarkerAreaShown(true);
                settings.setLineNumbersShown(true);
                editorExConsumer.accept(editor);
                return editor;
            }
        };
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

        simpleToolWindowPanel.setToolbar(ActionManager.getInstance().createActionToolbar("SelfSignCertificate", actionGroup, false).getComponent());
        //配置模块
        LanguageTextField languageTextField = new LanguageTextField(YAMLLanguage.INSTANCE, project, Optional.ofNullable(state.getConfigYaml()).orElse(""), false) {
            @Override
            protected @NotNull EditorEx createEditor() {
                EditorEx editor = super.createEditor();
                EditorSettings settings = editor.getSettings();
                settings.setLineMarkerAreaShown(true);
                settings.setLineNumbersShown(true);
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
                } else {
                    LanguageTextField languageTextField = languageTextFields.get("config");
                    if (languageTextField.getText().isBlank()) {
                        Messages.showErrorDialog("请添加ca配置,可点击导入模板配置按钮生成配置模板", "提示");
                    } else {
                        try {
                            SelfSignConfig selfSignConfig = parseConfig(languageTextField.getText());
                            SelfSignCertificateEntity entity = SelfSignCertificateHelper.genSelfCertificateFromCaPem(caPem, caKeyPem, selfSignConfig);
                            String certificatePem = PemUtils.toString(entity.getCertificate());
                            String certificateKeyPem = PemUtils.toString(entity.getCertificateKey());
                            state.setCertificatePem(certificatePem);
                            state.setCertificateKeyPem(certificateKeyPem);
                            languageTextFields.get("certificate").setText(certificatePem);
                            languageTextFields.get("certificate-key").setText(certificateKeyPem);
                            NotifyUtils.notify("证书生成成功,可通过点击右侧的certificate,certificate-key查看生成的证书内容", project);
                        } catch (Throwable e) {
                            Messages.showErrorDialog(e.getMessage(), "证书生成出错");
                        }
                    }

                }
            }
        };
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
                } else {
                    try {
                        SelfSignConfig selfSignConfig = parseConfig(languageTextField.getText());
                        SelfSignConfig.CA ca = selfSignConfig.getCa();
                        SelfSignCertificateEntity entity = SelfSignCertificateHelper.genCaCertificate(selfSignConfig);
                        String caPem = PemUtils.toString(entity.getCa());
                        String caKeyPem = PemUtils.toString(entity.getCaKey());
                        state.setCaPem(caPem).setCaKeyPem(caKeyPem);
                        languageTextFields.get("ca").setText(caPem);
                        languageTextFields.get("ca-key").setText(caKeyPem);
                        NotifyUtils.notify("CA证书生成成功,可在右侧tab栏中点击ca.pem,ca-key.pem查看", project);
                    } catch (Throwable e) {
                        Messages.showErrorDialog(e.getMessage(), "CA证书生成失败");
                    }
                }
            }
        };
    }


}
