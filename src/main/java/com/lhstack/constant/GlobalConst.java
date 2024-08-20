package com.lhstack.constant;

import com.intellij.openapi.util.Key;
import com.intellij.ui.jcef.JBCefBrowser;

import javax.swing.*;

public interface GlobalConst {

    /**
     * 缓存acme浏览器
     */
    Key<JBCefBrowser> ACME_BROWSER = Key.create("ACME_BROWSER");

    /**
     * acme顶部菜单
     */
    Key<JComponent> ACME_TOP_COMPONENT = Key.create("ACME_TOP_COMPONENT");
}
