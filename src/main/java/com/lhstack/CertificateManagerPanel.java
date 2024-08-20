package com.lhstack;

import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.components.JBTextArea;
import dev.coolrequest.tool.CoolToolPanel;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class CertificateManagerPanel implements CoolToolPanel {

    private Project project;

    private static final Map<String, JPanel> panels = new HashMap<>();

    @Override
    public JPanel createPanel() {
        return panels.computeIfAbsent(project.getLocationHash(), key -> createViews());
    }

    private JPanel createViews() {
        try {
            JBTabbedPane tabbedPane = new JBTabbedPane();
            tabbedPane.addTab("jks证书管理", new CertificateManagerView(project));
            tabbedPane.addTab("创建自签证书", new CreateSelfCertificateView(project));
            tabbedPane.addTab("Acme客户端", new AcmeClientView(project));
            JPanel panel = new JPanel(new BorderLayout());
            panel.add(tabbedPane, BorderLayout.CENTER);
            return panel;
        } catch (Throwable e) {
            JPanel jPanel = new JPanel(new BorderLayout());
            jPanel.add(new JBTextArea(e.getMessage()), BorderLayout.CENTER);
            return jPanel;
        }
    }


    @Override
    public void showTool() {

    }

    @Override
    public void closeTool() {

    }
}
