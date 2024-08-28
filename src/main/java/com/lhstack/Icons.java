package com.lhstack;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

public interface Icons {


    Icon IMPORT = IconLoader.findIcon(Icons.class.getClassLoader().getResource("icons/import.svg"));

    Icon DETAIL = IconLoader.findIcon(Icons.class.getClassLoader().getResource("icons/detail.svg"));

    Icon DELETE = IconLoader.findIcon(Icons.class.getClassLoader().getResource("icons/delete.svg"));

    Icon EXPORT = IconLoader.findIcon(Icons.class.getClassLoader().getResource("icons/export.svg"));

    Icon EMPTY_CERTIFICATE = IconLoader.findIcon(Icons.class.getClassLoader().getResource("icons/empty_certificate.svg"));

    Icon ADD = IconLoader.findIcon(Icons.class.getClassLoader().getResource("icons/add.svg"));

    Icon SAVE = IconLoader.findIcon(Icons.class.getClassLoader().getResource("icons/save.svg"));

    Icon RESAVE = IconLoader.findIcon(Icons.class.getClassLoader().getResource("icons/resave.svg"));

    Icon ROOT = IconLoader.findIcon(Icons.class.getClassLoader().getResource("icons/root.svg"));

    Icon TEMPLATE = IconLoader.findIcon("icons/template.svg", Icons.class);

    Icon IMPORT2 = IconLoader.findIcon(Icons.class.getClassLoader().getResource("icons/import2.svg"));

    Icon CERTIFICATE = IconLoader.findIcon(Icons.class.getClassLoader().getResource("icons/certificate.svg"));

    Icon SHOW = IconLoader.findIcon(Icons.class.getClassLoader().getResource("icons/show.svg"));

    Icon CERTIFICATE_CHAIN = IconLoader.findIcon(Icons.class.getClassLoader().getResource("icons/certificate_chain.svg"));
}
