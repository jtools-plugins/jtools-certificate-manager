package com.lhstack;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public class ItemColumn<T> extends ColumnInfo<Item, T> {

    private final Function<Item, T> mapper;

    public ItemColumn(@NlsContexts.ColumnName String name, Function<Item, T> mapper) {
        super(name);
        this.mapper = mapper;
    }

    @Override
    public @Nullable T valueOf(Item item) {
        return mapper.apply(item);
    }

    public static <T> ColumnInfo<Item, T> create(String name, Function<Item, T> mapper) {
        return new ItemColumn<>(name, mapper) {
//            @Override
//            public TableCellRenderer getCustomizedRenderer(Item o, TableCellRenderer renderer) {
//                return new TableCellRenderer() {
//                    @Override
//                    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
//                        return new JLabel(String.valueOf(value), JLabel.CENTER);
//                    }
//                };
//            }
        };
    }
}
