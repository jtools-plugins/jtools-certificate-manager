package com.lhstack;

import com.intellij.openapi.fileChooser.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.lhstack.func.Consumer;

import java.io.File;
import java.util.Optional;

public class FileChooser {

    public static FileSaverDialog chooseSaveFile(String title, Project project, String... extensions) {
        FileSaverDescriptor fileSaverDescriptor = new FileSaverDescriptor(title, "保存文件", extensions);
        return FileChooserFactory.getInstance().createSaveFileDialog(fileSaverDescriptor, project);
    }

    public static void chooseSaveFile(String title, String saveFilename, Project project, Consumer<VirtualFile> consumer, String... extensions) {
        FileSaverDialog fileSaverDialog = chooseSaveFile(title, project, extensions);
        VirtualFileWrapper wrapper = fileSaverDialog.save(saveFilename);
        if (wrapper != null) {
            VirtualFile virtualFile = wrapper.getVirtualFile(true);
            if (virtualFile != null) {
                try {
                    consumer.accept(virtualFile);
                } catch (Throwable e) {
                    Messages.showErrorDialog(project, e.getMessage(), "保存文件失败");
                    FileUtil.delete(new File(virtualFile.getPresentableUrl()));
                }
            }
        }
    }

    public static Optional<VirtualFile[]> chooseFiles(String title, Project project) {
        try {
            FileChooserDialog fileChooser = FileChooserFactory.getInstance().createFileChooser(FileChooserDescriptorFactory
                    .createMultipleFilesNoJarsDescriptor()
                    .withTitle(title), project, null);
            VirtualFile[] choose = fileChooser.choose(project);
            if (choose.length > 0) {
                return Optional.of(choose);
            }
            return Optional.empty();
        } catch (Throwable e) {
            Messages.showErrorDialog(e + "", "错误");
            return Optional.empty();
        }
    }

    public static Optional<VirtualFile> chooseSingleFile(String title, Project project) {
        try {
            FileChooserDialog fileChooser = FileChooserFactory.getInstance().createFileChooser(FileChooserDescriptorFactory
                    .createSingleFileDescriptor()
                    .withTitle(title), project, null);
            VirtualFile[] choose = fileChooser.choose(project);
            if (choose.length > 0) {
                return Optional.of(choose[0]);
            }
            return Optional.empty();
        } catch (Throwable e) {
            Messages.showErrorDialog(e + "", "错误");
            return Optional.empty();
        }
    }
}
