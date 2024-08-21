package com.lhstack;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.jcef.JBCefBrowser;
import com.lhstack.constant.GlobalConst;
import com.lhstack.utils.ProjectUtils;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefBeforeDownloadCallback;
import org.cef.callback.CefContextMenuParams;
import org.cef.callback.CefDownloadItem;
import org.cef.callback.CefMenuModel;
import org.cef.handler.CefContextMenuHandlerAdapter;
import org.cef.handler.CefDownloadHandlerAdapter;
import org.cef.handler.CefKeyboardHandlerAdapter;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.network.CefRequest;

import javax.swing.*;
import java.awt.*;
import java.nio.charset.StandardCharsets;

public class AcmeClientView extends JPanel {


    private final Project project;

    private String cameHtml;

    public AcmeClientView(Project project) throws Exception {
        this.project = project;
        this.init();
    }

    private void init() throws Exception {
        this.setLayout(new BorderLayout());
        byte[] bytes = AcmeClientView.class.getClassLoader().getResourceAsStream("acme.html").readAllBytes();
        cameHtml = new String(bytes, StandardCharsets.UTF_8);
        JBCefBrowser browser = ProjectUtils.getOrCreate(project, GlobalConst.ACME_BROWSER, this::createBrowser);
        this.add(browser.getComponent(), BorderLayout.CENTER);
    }


    public JBCefBrowser createBrowser() {
        JBCefBrowser jbCefBrowser = JBCefBrowser.createBuilder().setOffScreenRendering(false).build();
        CefBrowser cefBrowser = jbCefBrowser.getCefBrowser();
        jbCefBrowser.getJBCefClient().addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadStart(CefBrowser browser, CefFrame frame, CefRequest.TransitionType transitionType) {
                browser.executeJavaScript("function scrollToTop() { window.scrollTo(0, 0); }", browser.getURL(), 0);
            }
        }, cefBrowser);
        jbCefBrowser.getJBCefClient().addDownloadHandler(new CefDownloadHandlerAdapter() {
            @Override
            public void onBeforeDownload(CefBrowser browser, CefDownloadItem downloadItem, String suggestedName, CefBeforeDownloadCallback callback) {
                callback.Continue(suggestedName, true);
            }
        }, cefBrowser);
        jbCefBrowser.loadHTML(cameHtml);
        jbCefBrowser.getJBCefClient().addKeyboardHandler(new CefKeyboardHandlerAdapter() {
            @Override
            public boolean onKeyEvent(CefBrowser browser, CefKeyEvent event) {
                if (event.windows_key_code == 116 && event.type == CefKeyEvent.EventType.KEYEVENT_RAWKEYDOWN) {
                    // 捕获 F5 键按下事件
                    browser.reload();
                    browser.executeJavaScript("scrollToTop();", browser.getURL(), 0);
                    return true;
                }
                return false;
            }
        }, cefBrowser);

        jbCefBrowser.getJBCefClient().addContextMenuHandler(new CefContextMenuHandlerAdapter() {
            @Override
            public void onBeforeContextMenu(CefBrowser browser, CefFrame frame, CefContextMenuParams params, CefMenuModel model) {
                model.clear();
                model.addItem(100, "在新窗口中打开");
                model.addItem(101, "打开开发者工具");
            }

            @Override
            public boolean onContextMenuCommand(CefBrowser browser, CefFrame frame, CefContextMenuParams params, int commandId, int eventFlags) {
                if (commandId == 100) {
                    try {
                        SwingUtilities.invokeLater(() -> {
                            ApplicationManager.getApplication().runWriteAction(() -> {
                                JFrame jFrame = new JFrame();
                                jFrame.setTitle("AcmeClientView");
                                jFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                                jFrame.setSize(800, 600);
                                jFrame.setLocationRelativeTo(null);
                                jFrame.add(createBrowser().getComponent());
                                jFrame.setVisible(true);
                            });
                        });
                    } catch (Throwable e) {
                        browser.executeJavaScript(String.format("console.log(`%s`)", e.getMessage()), browser.getURL(), 0);
                    }
                }
                if (commandId == 101) {
                    jbCefBrowser.openDevtools();
                }
                return true;
            }
        }, cefBrowser);
        return jbCefBrowser;
    }
}
