package com.lhstack

import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.TableView
import com.intellij.util.ui.AbstractTableCellEditor
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel

import javax.swing.AbstractAction
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer
import java.awt.Component
import java.awt.FlowLayout
import java.awt.event.ActionEvent


SwingUtilities.invokeLater {

    JPanel renderPanel = new JPanel(new FlowLayout(FlowLayout.LEFT))
    renderPanel.add(new JButton("查看"))
    renderPanel.add(new JButton("删除"))

    ColumnInfo[] columnInfos = new ColumnInfo[]{
            new ColumnInfo<String, String>("名称") {

                @Override
                String valueOf(String s) {
                    return s
                }
            },
            new ColumnInfo<String, String>("操作") {

                @Override
                String valueOf(String s) {
                    return s
                }

                @Override
                TableCellRenderer getCustomizedRenderer(String o, TableCellRenderer renderer) {

                    return new TableCellRenderer() {
                        @Override
                        Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                            return renderPanel
                        }
                    }
                }

                @Override
                TableCellEditor getEditor(String s) {
                    def showButton = new JButton("查看")
                    def delButton = new JButton("删除")
                    showButton.addActionListener(new AbstractAction() {
                        @Override
                        void actionPerformed(ActionEvent e) {
                            log.info("show1: $s")
                        }
                    })

                    delButton.addActionListener(new AbstractAction() {
                        @Override
                        void actionPerformed(ActionEvent e) {
                            log.info("delete1: $s")
                        }
                    })
                    JPanel editorPanel = new JPanel(new FlowLayout(FlowLayout.LEFT))
                    editorPanel.add(showButton)
                    editorPanel.add(delButton)

                    return new AbstractTableCellEditor() {
                        @Override
                        Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
                            return editorPanel
                        }

                        @Override
                        Object getCellEditorValue() {
                            return s
                        }
                    }
                }

                @Override
                boolean isCellEditable(String s) {
                    return true
                }
            }
    }

    ListTableModel<String> listTableModel = new ListTableModel<>(columnInfos)
    listTableModel.setItems(Arrays.asList("1", "2", "3", "4", "5"))
    TableView<String> tableView = new TableView<>(listTableModel)
    tableView.setColumnSelectionAllowed(false)
    tableView.setRowSelectionAllowed(false)
    tableView.setCellSelectionEnabled(false)
    JBScrollPane jbScrollPane = new JBScrollPane(tableView)

    JDialog dialog = new JDialog();
    dialog.setTitle("Test");
    dialog.setContentPane(jbScrollPane);
    dialog.setSize(800, 600);
    dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    dialog.setModal(true);
    dialog.setLocationRelativeTo(null);
    dialog.setVisible(true);
}
