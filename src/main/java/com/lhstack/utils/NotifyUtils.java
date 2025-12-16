package com.lhstack.utils;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;

public class NotifyUtils {

    private static final String GROUP_ID = "证书管理";

    public static void notify(String content, Project project) {
        notify(content, NotificationType.INFORMATION, project);
    }

    public static void notifyWarning(String content, Project project) {
        notify(content, NotificationType.WARNING, project);
    }

    public static void notifyError(String content, Project project) {
        notify(content, NotificationType.ERROR, project);
    }

    private static void notify(String content, NotificationType type, Project project) {
        Notifications.Bus.notify(new Notification(GROUP_ID, GROUP_ID, content, type), project);
    }
}
