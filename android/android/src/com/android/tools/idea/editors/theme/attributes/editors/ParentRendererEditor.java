/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.editors.theme.attributes.editors;


import com.android.tools.idea.configurations.ThemeSelectionDialog;
import com.android.tools.idea.editors.theme.ParentThemesListModel;
import com.android.tools.idea.editors.theme.ThemeEditorContext;
import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.android.tools.idea.editors.theme.ThemeResolver;
import com.android.tools.idea.editors.theme.datamodels.ThemeEditorStyle;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.ui.ComboBox;

import java.awt.Component;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Custom Renderer and Editor for the theme parent attribute.
 * Uses a dropdown to offer the choice between Material Dark, Material Light or Other.
 * Deals with Other through a separate dialog window.
 */
public class ParentRendererEditor extends TypedCellEditor<ThemeEditorStyle, AttributeEditorValue> implements TableCellRenderer {
  private final ComboBox myComboBox;
  private @Nullable AttributeEditorValue myResultValue;
  private final ThemeEditorContext myContext;

  public ParentRendererEditor(@NotNull ThemeEditorContext context) {
    myContext = context;
    myComboBox = new ComboBox();
    //noinspection GtkPreferredJComboBoxRenderer
    myComboBox.setRenderer(new StyleListCellRenderer(context));
    myComboBox.addActionListener(new ParentChoiceListener());
  }

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    final Component component;

    myComboBox.removeAllItems();
    myComboBox.addItem(value);
    component = myComboBox;

    return component;
  }

  @Override
  public Component getEditorComponent(JTable table, ThemeEditorStyle value, boolean isSelected, int row, int column) {
    ImmutableList<ThemeEditorStyle> defaultThemes = ThemeEditorUtils.getDefaultThemes(new ThemeResolver(myContext.getConfiguration()));
    myComboBox.setModel(new ParentThemesListModel(defaultThemes, value));
    myResultValue = new AttributeEditorValue(value.getName(), false);
    return myComboBox;
  }

  @Override
  public AttributeEditorValue getEditorValue() {
    return myResultValue;
  }

  private class ParentChoiceListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
      Object selectedValue = myComboBox.getSelectedItem();
      if (ParentThemesListModel.SHOW_ALL_THEMES.equals(selectedValue)) {
        myComboBox.hidePopup();
        final ThemeSelectionDialog dialog = new ThemeSelectionDialog(myContext.getConfiguration());

        dialog.show();

        if (dialog.isOK()) {
          String theme = dialog.getTheme();
          myResultValue = theme == null ? null : new AttributeEditorValue(theme, false);
          stopCellEditing();
        }
        else {
          myResultValue = null;
          cancelCellEditing();
        }
      }
      else {
        if (selectedValue instanceof ThemeEditorStyle){
          myResultValue = new AttributeEditorValue(((ThemeEditorStyle)selectedValue).getName(), false);
        }
        stopCellEditing();
      }
    }
  }
}
