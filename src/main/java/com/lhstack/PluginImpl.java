package com.lhstack;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.components.JBTextArea;
import com.lhstack.tools.plugins.IPlugin;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class PluginImpl implements IPlugin {

    private static final Map<String, JPanel> panels = new HashMap<>();

    @Override
    public Icon pluginIcon() {
        return IconLoader.findIcon("logo.svg", PluginImpl.class);
    }

    @Override
    public Icon pluginTabIcon() {
        return IconLoader.findIcon("pluginTab.svg", PluginImpl.class);
    }

    @Override
    public JComponent createPanel(Project project) {
        return panels.computeIfAbsent(project.getLocationHash(), key -> createViews(project));
    }

    private JPanel createViews(Project project) {
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
            String stackTrace = Arrays.stream(e.getStackTrace()).map(StackTraceElement::toString).collect(Collectors.joining("\n"));
            jPanel.add(new JBScrollPane(new JBTextArea(e + "\n" + stackTrace)), BorderLayout.CENTER);
            return jPanel;
        }
    }

    @Override
    public String pluginName() {
        return "证书管理";
    }

    @Override
    public String pluginDesc() {
        return "这是一个用于管理证书的插件";
    }

    @Override
    public String pluginVersion() {
        return "0.0.1";
    }
}
