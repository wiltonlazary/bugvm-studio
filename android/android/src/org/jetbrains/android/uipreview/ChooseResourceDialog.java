/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.uipreview;

import com.android.SdkConstants;
import com.android.resources.ResourceType;
import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.android.tools.idea.rendering.AppResourceRepository;
import com.android.tools.idea.rendering.ResourceHelper;
import com.android.tools.idea.rendering.ResourceNameValidator;
import com.intellij.icons.AllIcons;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.ColorPickerListener;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.intellij.images.fileTypes.ImageFileTypeManager;
import org.jetbrains.android.actions.CreateResourceFileAction;
import org.jetbrains.android.actions.CreateXmlResourceDialog;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.refactoring.AndroidBaseLayoutRefactoringAction;
import org.jetbrains.android.refactoring.AndroidExtractStyleAction;
import org.jetbrains.android.resourceManagers.FileResourceProcessor;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.ToolTipManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;
import java.io.IOException;

/**
 * Resource Chooser, with previews. Based on ResourceDialog in the android-designer.
 * <P>
 * TODO:
 * <ul>
 *   <li> Finish color parsing</li>
 *   <li> Perform validation (such as cyclic layout resource detection for layout selection)</li>
 *   <li> Render drawables using layoutlib, e.g. drawable XML files, .9.png's, etc.</li>
 *   <li> Offer to create more resource types</li>
 * </ul>
 */
public class ChooseResourceDialog extends DialogWrapper implements TreeSelectionListener {
  private static final String RESOURCE_NAME_DEFAULT_TEXT = "Enter the resource name";
  private static final String RESOURCE_NAME_REQUIRED = "The resource name is required for this attribute";
  private static final String ANDROID = "@android:";
  private static final String TYPE_KEY = "ResourceType";

  private static final String TEXT = "Text";
  private static final String COMBO = "Combo";
  private static final String IMAGE = "Image";
  private static final String NONE = "None";

  private static final Icon RESOURCE_ITEM_ICON = AllIcons.Css.Property;

  private final Module myModule;
  @Nullable private final XmlTag myTag;

  private final JBTabbedPane myContentPanel;
  private final ResourcePanel myProjectPanel;
  private final ResourcePanel mySystemPanel;
  private JComponent myColorPickerPanel;

  private ColorPicker myColorPicker;
  private ResourcePickerListener myResourcePickerListener;

  private boolean myAllowCreateResource = true;
  private final Action myNewResourceAction = new AbstractAction("New Resource", AllIcons.General.ComboArrowDown) {
    @Override
    public void actionPerformed(ActionEvent e) {
      JComponent component = (JComponent)e.getSource();
      ActionPopupMenu popupMenu = createNewResourcePopupMenu();
      popupMenu.getComponent().show(component, 0, component.getHeight());
    }
  };
  private final AnAction myNewResourceValueAction = new AnAction() {
    @Override
    public void actionPerformed(AnActionEvent e) {
      ResourceType type = (ResourceType)getTemplatePresentation().getClientProperty(TYPE_KEY);
      createNewResourceValue(type);
    }
  };
  private final AnAction myNewResourceFileAction = new AnAction() {
    @Override
    public void actionPerformed(AnActionEvent e) {
      ResourceType type = (ResourceType)getTemplatePresentation().getClientProperty(TYPE_KEY);
      createNewResourceFile(type);
    }
  };
  private final AnAction myExtractStyleAction = new AnAction("Extract Style...") {
    @Override
    public void actionPerformed(AnActionEvent e) {
      extractStyle();
    }
  };

  private String myResultResourceName;

  public static ResourceType[] COLOR_TYPES = {ResourceType.COLOR, ResourceType.DRAWABLE, ResourceType.MIPMAP};
  private boolean myOverwriteResource = false;
  private JTextField myResourceNameField;
  private JLabel myResourceNameMessage;
  private ResourceNameValidator myValidator;
  private ResourceNameVisibility myResourceNameVisibility;

  public boolean overwriteResource() {
    return myOverwriteResource;
  }

  public interface ResourcePickerListener {
    void resourceChanged(String resource);
  }

  public ChooseResourceDialog(@NotNull Module module, @NotNull ResourceType[] types, @Nullable String value, @Nullable XmlTag tag) {
    this(module, types, value, tag, ResourceNameVisibility.HIDE, null);
  }

  public ChooseResourceDialog(@NotNull Module module, @NotNull ResourceType[] types, @Nullable String value, @Nullable XmlTag tag, ResourceNameVisibility resourceNameVisibility, @Nullable String colorName) {
    super(module.getProject());
    myModule = module;
    myTag = tag;
    myResourceNameVisibility = resourceNameVisibility;

    setTitle("Resources");

    AndroidFacet facet = AndroidFacet.getInstance(module);
    myProjectPanel = new ResourcePanel(facet, types, false);
    mySystemPanel = new ResourcePanel(facet, types, true);

    myContentPanel = new JBTabbedPane();
    myContentPanel.addTab("Project", myProjectPanel.myComponent);
    myContentPanel.addTab("System", mySystemPanel.myComponent);

    myProjectPanel.myTreeBuilder.expandAll(null);
    mySystemPanel.myTreeBuilder.expandAll(null);

    boolean doSelection = value != null;

    if (types == COLOR_TYPES) {
      Color color = ResourceHelper.parseColor(value);
      myColorPicker = new ColorPicker(myDisposable, color, true, new ColorPickerListener() {
        @Override
        public void colorChanged(Color color) {
          notifyResourcePickerListeners(ResourceHelper.colorToString(color));
        }

        @Override
        public void closed(@Nullable Color color) { }
      });
      myColorPicker.pickARGB();

      JPanel colorPickerContent = new JPanel(new BorderLayout());
      myColorPickerPanel = new JBScrollPane(colorPickerContent);
      myColorPickerPanel.setBorder(null);
      colorPickerContent.add(myColorPicker);
      myContentPanel.addTab("Color", myColorPickerPanel);

      if (myResourceNameVisibility != ResourceNameVisibility.HIDE) {
        ResourceDialogSouthPanel resourceDialogSouthPanel = new ResourceDialogSouthPanel();
        myResourceNameField = resourceDialogSouthPanel.getResourceNameField();
        myResourceNameField.getDocument().addDocumentListener(new ValidatingDocumentListener());
        if (colorName != null) {
          myResourceNameField.setText(colorName);
        }
        myResourceNameMessage = resourceDialogSouthPanel.getResourceNameMessage();
        Color backgroundColor = EditorColorsManager.getInstance().getGlobalScheme().getColor(EditorColors.NOTIFICATION_BACKGROUND);
        myResourceNameMessage.setBackground(backgroundColor == null ? JBColor.YELLOW : backgroundColor);
        colorPickerContent.add(resourceDialogSouthPanel.getFullPanel(), BorderLayout.SOUTH);
        updateResourceNameStatus();
      }

      if (color != null) {
        myContentPanel.setSelectedIndex(2);
        doSelection = false;
      }
      myValidator = ResourceNameValidator.create(false, AppResourceRepository.getAppResources(module, true), ResourceType.COLOR, false);
    }
    if (doSelection && value.startsWith("@")) {
      value = StringUtil.replace(value, "+", "");
      int index = value.indexOf('/');
      if (index != -1) {
        ResourcePanel panel;
        String type;
        String name = value.substring(index + 1);
        if (value.startsWith(ANDROID)) {
          panel = mySystemPanel;
          type = value.substring(ANDROID.length(), index);
        }
        else {
          panel = myProjectPanel;
          type = value.substring(1, index);
        }
        myContentPanel.setSelectedComponent(panel.myComponent);
        panel.select(type, name);
      }
    }

    myContentPanel.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        valueChanged(null);
      }
    });

    valueChanged(null);
    init();
  }

  public enum ResourceNameVisibility {
    /**
     * Don't show field with resource name at all.
     */
    HIDE,

    /**
     * Force creation of named color.
     */
    FORCE
  }

  private String getErrorString() {
    myOverwriteResource = false;
    String result = null;
    if (myValidator != null && myResourceNameField != null) {
      String enteredName = myResourceNameField.getText();
      if (myValidator.doesResourceExist(enteredName)) {
        result = String.format("Saving this color will override existing resource %1$s.", enteredName);
        myOverwriteResource = true;
      } else {
        result = myValidator.getErrorText(enteredName);
      }
    }

    return result;
  }

  @NotNull
  private String getResourceNameMessage() {
    if (myResourceNameVisibility == ResourceNameVisibility.FORCE) {
      return RESOURCE_NAME_REQUIRED;
    } else {
      return RESOURCE_NAME_DEFAULT_TEXT;
    }
  }

  public void updateResourceNameStatus() {
    if (myResourceNameVisibility == ResourceNameVisibility.HIDE) {
      setOKActionEnabled(true);
      return;
    }

    final String errorText = getErrorString();
    if (errorText == null) {
      myResourceNameMessage.setText(getResourceNameMessage());
      myResourceNameMessage.setForeground(JBColor.BLACK);
      myResourceNameMessage.setOpaque(false);
      setOKActionEnabled(true);
    }
    else {
      myResourceNameMessage.setText(errorText);
      myResourceNameMessage.setForeground(myOverwriteResource ? JBColor.BLACK : JBColor.RED);
      myResourceNameMessage.setOpaque(myOverwriteResource);
      setOKActionEnabled(myOverwriteResource);
    }
  }

  private class ValidatingDocumentListener implements DocumentListener {
    @Override
    public void insertUpdate(DocumentEvent e) { check(); }

    @Override
    public void removeUpdate(DocumentEvent e) { check(); }

    @Override
    public void changedUpdate(DocumentEvent e) { check(); }

    private void check() {
      if (myValidator == null) {
        return;
      }

      updateResourceNameStatus();
    }
  }

  public void setResourcePickerListener(ResourcePickerListener resourcePickerListener) {
    myResourcePickerListener = resourcePickerListener;
  }

  protected void notifyResourcePickerListeners(String resource) {
    if (myResourcePickerListener != null) {
      myResourcePickerListener.resourceChanged(resource);
    }
  }

  private ActionPopupMenu createNewResourcePopupMenu() {
    ActionManager actionManager = ActionManager.getInstance();
    DefaultActionGroup actionGroup = new DefaultActionGroup();

    ResourceGroup resourceGroup = getSelectedElement(myProjectPanel.myTreeBuilder, ResourceGroup.class);
    if (resourceGroup == null) {
      resourceGroup = getSelectedElement(myProjectPanel.myTreeBuilder, ResourceItem.class).getGroup();
    }

    if (AndroidResourceUtil.XML_FILE_RESOURCE_TYPES.contains(resourceGroup.getType())) {
      myNewResourceFileAction.getTemplatePresentation().setText("New " + resourceGroup + " File...");
      myNewResourceFileAction.getTemplatePresentation().putClientProperty(TYPE_KEY, resourceGroup.getType());
      actionGroup.add(myNewResourceFileAction);
    }
    if (AndroidResourceUtil.VALUE_RESOURCE_TYPES.contains(resourceGroup.getType())) {
      String title = "New " + resourceGroup + " Value...";
      if (resourceGroup.getType() == ResourceType.LAYOUT) {
        title = "New Layout Alias";
      }
      myNewResourceValueAction.getTemplatePresentation().setText(title);
      myNewResourceValueAction.getTemplatePresentation().putClientProperty(TYPE_KEY, resourceGroup.getType());
      actionGroup.add(myNewResourceValueAction);
    }
    if (myTag != null && ResourceType.STYLE.equals(resourceGroup.getType())) {
      final boolean enabled = AndroidBaseLayoutRefactoringAction.getLayoutViewElement(myTag) != null &&
                              AndroidExtractStyleAction.doIsEnabled(myTag);
      myExtractStyleAction.getTemplatePresentation().setEnabled(enabled);
      actionGroup.add(myExtractStyleAction);
    }

    return actionManager.createActionPopupMenu(ActionPlaces.UNKNOWN, actionGroup);
  }

  private void createNewResourceValue(ResourceType resourceType) {
    CreateXmlResourceDialog dialog = new CreateXmlResourceDialog(myModule, resourceType, null, null, true);
    dialog.setTitle("New " + StringUtil.capitalize(resourceType.getDisplayName()) + " Value Resource");
    if (!dialog.showAndGet()) {
      return;
    }

    Module moduleToPlaceResource = dialog.getModule();
    if (moduleToPlaceResource == null) {
      return;
    }

    String fileName = dialog.getFileName();
    List<String> dirNames = dialog.getDirNames();
    String resValue = dialog.getValue();
    String resName = dialog.getResourceName();
    if (!AndroidResourceUtil.createValueResource(moduleToPlaceResource, resName, resourceType, fileName, dirNames, resValue)) {
      return;
    }

    PsiDocumentManager.getInstance(myModule.getProject()).commitAllDocuments();

    myResultResourceName = "@" + resourceType.getName() + "/" + resName;
    close(OK_EXIT_CODE);
  }

  private void createNewResourceFile(ResourceType resourceType) {
    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    XmlFile newFile = CreateResourceFileAction.createFileResource(facet, resourceType, null, null, null, true, null);

    if (newFile != null) {
      String name = newFile.getName();
      int index = name.lastIndexOf('.');
      if (index != -1) {
        name = name.substring(0, index);
      }
      myResultResourceName = "@" + resourceType.getName() + "/" + name;
      close(OK_EXIT_CODE);
    }
  }

  private void extractStyle() {
    final String resName = AndroidExtractStyleAction.doExtractStyle(myModule, myTag, false, null);
    if (resName == null) {
      return;
    }
    myResultResourceName = "@style/" + resName;
    close(OK_EXIT_CODE);
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myProjectPanel.myTree;
  }

  @Override
  protected JComponent createCenterPanel() {
    return myContentPanel;
  }

  @NotNull
  @Override
  protected Action[] createLeftSideActions() {
    return myAllowCreateResource ? new Action[]{myNewResourceAction} : new Action[0];
  }

  public ChooseResourceDialog setAllowCreateResource(boolean allowCreateResource) {
    myAllowCreateResource = allowCreateResource;
    return this;
  }

  public boolean getAllowCreateResource() {
    return myAllowCreateResource;
  }

  @Override
  protected void dispose() {
    super.dispose();
    Disposer.dispose(myProjectPanel.myTreeBuilder);
    Disposer.dispose(mySystemPanel.myTreeBuilder);
  }

  public String getResourceName() {
    return myResultResourceName;
  }

  @Override
  protected void doOKAction() {
    valueChanged(null);
    if (myContentPanel.getSelectedComponent() == myColorPickerPanel && myResourceNameVisibility != ResourceNameVisibility.HIDE) {
      String colorName = myResourceNameField.getText();

      if (myOverwriteResource) {
        ThemeEditorUtils.changeColor(myModule, colorName, myResultResourceName);
      } else {
        ThemeEditorUtils.createColor(myModule, colorName, myResultResourceName);
      }

      myResultResourceName = SdkConstants.COLOR_RESOURCE_PREFIX + colorName;
    }
    super.doOKAction();
  }

  @Nullable
  private static <T> T getSelectedElement(AbstractTreeBuilder treeBuilder, Class<T> elementClass) {
    Set<T> elements = treeBuilder.getSelectedElements(elementClass);
    return elements.isEmpty() ? null : elements.iterator().next();
  }

  @Override
  public void valueChanged(@Nullable TreeSelectionEvent e) {
    Component selectedComponent = myContentPanel.getSelectedComponent();

    if (selectedComponent == myColorPickerPanel) {
      Color color = myColorPicker.getColor();
      myNewResourceAction.setEnabled(false);
      myResultResourceName = ResourceHelper.colorToString(color);
      updateResourceNameStatus();
    }
    else {
      boolean isProjectPanel = selectedComponent == myProjectPanel.myComponent;
      ResourcePanel panel = isProjectPanel ? myProjectPanel : mySystemPanel;
      ResourceItem element = getSelectedElement(panel.myTreeBuilder, ResourceItem.class);
      setOKActionEnabled(element != null);
      myNewResourceAction.setEnabled(isProjectPanel && !panel.myTreeBuilder.getSelectedElements().isEmpty());

      if (element == null) {
        myResultResourceName = null;
      }
      else {
        String prefix = panel == myProjectPanel ? "@" : ANDROID;
        myResultResourceName = prefix + element.getName();
      }

      panel.showPreview(element);
    }
    notifyResourcePickerListeners(myResultResourceName);
  }

  private class ResourcePanel {
    public final Tree myTree;
    public final AbstractTreeBuilder myTreeBuilder;
    public final JBSplitter myComponent;

    private final JPanel myPreviewPanel;
    private final JTextArea myTextArea;
    private final JTextArea myComboTextArea;
    private final JComboBox myComboBox;
    private final JLabel myImageComponent;
    private final JLabel myNoPreviewComponent;

    private final ResourceGroup[] myGroups;
    private final ResourceManager myManager;

    public ResourcePanel(AndroidFacet facet, ResourceType[] types, boolean system) {
      myTree = new Tree();
      myTree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode()));
      myTree.setScrollsOnExpand(true);
      myTree.setRootVisible(false);
      myTree.setShowsRootHandles(true);
      new DoubleClickListener() {
        @Override
        protected boolean onDoubleClick(MouseEvent e) {
          if (!myTreeBuilder.getSelectedElements(ResourceItem.class).isEmpty()) {
            close(OK_EXIT_CODE);
            return true;
          }
          return false;
        }
      }.installOn(myTree);

      ToolTipManager.sharedInstance().registerComponent(myTree);
      TreeUtil.installActions(myTree);

      myManager = facet.getResourceManager(system ? AndroidUtils.SYSTEM_RESOURCE_PACKAGE : null);
      myGroups = new ResourceGroup[types.length];

      for (int i = 0; i < types.length; i++) {
        myGroups[i] = new ResourceGroup(types[i], myManager);
      }

      myTreeBuilder =
        new AbstractTreeBuilder(myTree, (DefaultTreeModel)myTree.getModel(), new TreeContentProvider(myGroups), null);
      myTreeBuilder.initRootNode();

      TreeSelectionModel selectionModel = myTree.getSelectionModel();
      selectionModel.setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
      selectionModel.addTreeSelectionListener(ChooseResourceDialog.this);

      myTree.setCellRenderer(new NodeRenderer());
      new TreeSpeedSearch(myTree, TreeSpeedSearch.NODE_DESCRIPTOR_TOSTRING, true);

      myComponent = new JBSplitter(true, 0.8f);
      myComponent.setSplitterProportionKey("android.resource_dialog_splitter");

      myComponent.setFirstComponent(ScrollPaneFactory.createScrollPane(myTree));

      myPreviewPanel = new JPanel(new CardLayout());
      myComponent.setSecondComponent(myPreviewPanel);

      myTextArea = new JTextArea(5, 20);
      myTextArea.setEditable(false);
      myPreviewPanel.add(ScrollPaneFactory.createScrollPane(myTextArea), TEXT);

      myComboTextArea = new JTextArea(5, 20);
      myComboTextArea.setEditable(false);

      myComboBox = new JComboBox();
      myComboBox.setMaximumRowCount(15);
      myComboBox.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          java.util.List<ResourceElement> resources = (java.util.List<ResourceElement>)myComboBox.getClientProperty(COMBO);
          myComboTextArea.setText(getResourceElementValue(resources.get(myComboBox.getSelectedIndex())));
        }
      });

      JPanel comboPanel = new JPanel(new BorderLayout(0, 1) {
        @Override
        public void layoutContainer(Container target) {
          super.layoutContainer(target);
          Rectangle bounds = myComboBox.getBounds();
          Dimension size = myComboBox.getPreferredSize();
          size.width += 20;
          myComboBox.setBounds((int)bounds.getMaxX() - size.width, bounds.y, size.width, size.height);
        }
      });
      comboPanel.add(ScrollPaneFactory.createScrollPane(myComboTextArea), BorderLayout.CENTER);
      comboPanel.add(myComboBox, BorderLayout.SOUTH);
      myPreviewPanel.add(comboPanel, COMBO);

      myImageComponent = new JLabel();
      myImageComponent.setHorizontalAlignment(SwingConstants.CENTER);
      myImageComponent.setVerticalAlignment(SwingConstants.CENTER);
      myPreviewPanel.add(myImageComponent, IMAGE);

      myNoPreviewComponent = new JLabel("No Preview");
      myNoPreviewComponent.setHorizontalAlignment(SwingConstants.CENTER);
      myNoPreviewComponent.setVerticalAlignment(SwingConstants.CENTER);
      myPreviewPanel.add(myNoPreviewComponent, NONE);
    }

    public void showPreview(@Nullable ResourceItem element) {
      CardLayout layout = (CardLayout)myPreviewPanel.getLayout();

      if (element == null || element.getGroup().getType() == ResourceType.ID) {
        layout.show(myPreviewPanel, NONE);
        return;
      }

      try {
        VirtualFile file = element.getFile();
        if (file == null) {
          String value = element.getPreviewString();
          if (value == null) {
            java.util.List<ResourceElement> resources = element.getPreviewResources();

            if (resources == null) {
              long time = System.currentTimeMillis();
              resources = myManager.findValueResources(element.getGroup().getType().getName(), element.toString());
              if (ApplicationManagerEx.getApplicationEx().isInternal()) {
                System.out.println("Time: " + (System.currentTimeMillis() - time)); // XXX
              }

              int size = resources.size();
              if (size == 1) {
                value = getResourceElementValue(resources.get(0));
                element.setPreviewString(value);
              }
              else if (size > 1) {
                resources = new ArrayList<ResourceElement>(resources);
                Collections.sort(resources, new Comparator<ResourceElement>() {
                  @Override
                  public int compare(ResourceElement element1, ResourceElement element2) {
                    PsiDirectory directory1 = element1.getXmlTag().getContainingFile().getParent();
                    PsiDirectory directory2 = element2.getXmlTag().getContainingFile().getParent();

                    if (directory1 == null && directory2 == null) {
                      return 0;
                    }
                    if (directory2 == null) {
                      return 1;
                    }
                    if (directory1 == null) {
                      return -1;
                    }

                    return directory1.getName().compareTo(directory2.getName());
                  }
                });

                DefaultComboBoxModel model = new DefaultComboBoxModel();
                String defaultSelection = null;
                for (int i = 0; i < size; i++) {
                  ResourceElement resource = resources.get(i);
                  PsiDirectory directory = resource.getXmlTag().getContainingFile().getParent();
                  String name = directory == null ? "unknown-" + i : directory.getName();
                  model.addElement(name);
                  if (defaultSelection == null && "values".equalsIgnoreCase(name)) {
                    defaultSelection = name;
                  }
                }
                element.setPreviewResources(resources, model, defaultSelection);

                showComboPreview(element);
                return;
              }
              else {
                layout.show(myPreviewPanel, NONE);
                return;
              }
            }
            else {
              showComboPreview(element);
              return;
            }
          }
          if (value == null) {
            layout.show(myPreviewPanel, NONE);
            return;
          }

          myTextArea.setText(value);
          layout.show(myPreviewPanel, TEXT);
        }
        else if (ImageFileTypeManager.getInstance().isImage(file)) {
          Icon icon = element.getPreviewIcon();
          if (icon == null) {
            icon = new SizedIcon(100, 100, new ImageIcon(file.getPath()));
            element.setPreviewIcon(icon);
          }
          myImageComponent.setIcon(icon);
          layout.show(myPreviewPanel, IMAGE);
        }
        else if (file.getFileType() == XmlFileType.INSTANCE) {
          String value = element.getPreviewString();
          if (value == null) {
            value = new String(file.contentsToByteArray());
            element.setPreviewString(value);
          }
          myTextArea.setText(value);
          myTextArea.setEditable(false);
          layout.show(myPreviewPanel, TEXT);
        }
        else {
          layout.show(myPreviewPanel, NONE);
        }
      }
      catch (IOException e) {
        layout.show(myPreviewPanel, NONE);
      }
    }

    private void showComboPreview(ResourceItem element) {
      java.util.List<ResourceElement> resources = element.getPreviewResources();
      String selection = (String)myComboBox.getSelectedItem();
      if (selection == null) {
        selection = element.getPreviewComboDefaultSelection();
      }

      int index = element.getPreviewComboModel().getIndexOf(selection);
      if (index == -1) {
        index = 0;
      }

      myComboBox.setModel(element.getPreviewComboModel());
      myComboBox.putClientProperty(COMBO, resources);
      myComboBox.setSelectedIndex(index);
      myComboTextArea.setText(getResourceElementValue(resources.get(index)));

      CardLayout layout = (CardLayout)myPreviewPanel.getLayout();
      layout.show(myPreviewPanel, COMBO);
    }

    private void select(String type, String name) {
      for (ResourceGroup group : myGroups) {
        if (type.equalsIgnoreCase(group.getName())) {
          for (ResourceItem item : group.getItems()) {
            if (name.equals(item.toString())) {
              myTreeBuilder.select(item);
              return;
            }
          }
          return;
        }
      }
    }
  }

  private static String getResourceElementValue(ResourceElement element) {
    String text = element.getRawText();
    if (StringUtil.isEmpty(text)) {
      return element.getXmlTag().getText();
    }
    return text;
  }

  private static class ResourceGroup {
    private java.util.List<ResourceItem> myItems = new ArrayList<ResourceItem>();
    private final ResourceType myType;

    public ResourceGroup(ResourceType type, ResourceManager manager) {
      myType = type;

      final String resourceType = type.getName();

      Collection<String> resourceNames = manager.getValueResourceNames(resourceType);
      for (String resourceName : resourceNames) {
        myItems.add(new ResourceItem(this, resourceName, null, RESOURCE_ITEM_ICON));
      }
      final Set<String> fileNames = new HashSet<String>();

      manager.processFileResources(resourceType, new FileResourceProcessor() {
        @Override
        public boolean process(@NotNull VirtualFile resFile, @NotNull String resName, @NotNull String resFolderType) {
          if (fileNames.add(resName)) {
            myItems.add(new ResourceItem(ResourceGroup.this, resName, resFile, resFile.getFileType().getIcon()));
          }
          return true;
        }
      });

      if (type == ResourceType.ID) {
        for (String id : manager.getIds(true)) {
          if (!resourceNames.contains(id)) {
            myItems.add(new ResourceItem(this, id, null, RESOURCE_ITEM_ICON));
          }
        }
      }

      Collections.sort(myItems, new Comparator<ResourceItem>() {
        @Override
        public int compare(ResourceItem resource1, ResourceItem resource2) {
          return resource1.toString().compareTo(resource2.toString());
        }
      });
    }

    public ResourceType getType() {
      return myType;
    }

    public String getName() {
      return myType.getName();
    }

    public java.util.List<ResourceItem> getItems() {
      return myItems;
    }

    @Override
    public String toString() {
      return myType.getDisplayName();
    }
  }

  private static class ResourceItem {
    private final ResourceGroup myGroup;
    private final String myName;
    private final VirtualFile myFile;
    private final Icon myIcon;
    private String myPreviewString;
    private java.util.List<ResourceElement> myPreviewResources;
    private DefaultComboBoxModel myPreviewComboModel;
    private String myDefaultSelection;
    private Icon myPreviewIcon;

    public ResourceItem(@NotNull ResourceGroup group, @NotNull String name, @Nullable VirtualFile file, Icon icon) {
      myGroup = group;
      myName = name;
      myFile = file;
      myIcon = icon;
    }

    public ResourceGroup getGroup() {
      return myGroup;
    }

    public String getName() {
      return myGroup.getName() + "/" + myName;
    }

    public VirtualFile getFile() {
      return myFile;
    }

    public Icon getIcon() {
      return myIcon;
    }

    public String getPreviewString() {
      return myPreviewString;
    }

    public void setPreviewString(String previewString) {
      myPreviewString = previewString;
    }

    public java.util.List<ResourceElement> getPreviewResources() {
      return myPreviewResources;
    }

    public DefaultComboBoxModel getPreviewComboModel() {
      return myPreviewComboModel;
    }

    public String getPreviewComboDefaultSelection() {
      return myDefaultSelection;
    }

    public void setPreviewResources(java.util.List<ResourceElement> previewResources,
                                    DefaultComboBoxModel previewComboModel,
                                    String defaultSelection) {
      myPreviewResources = previewResources;
      myPreviewComboModel = previewComboModel;
      myDefaultSelection = defaultSelection;
    }

    public Icon getPreviewIcon() {
      return myPreviewIcon;
    }

    public void setPreviewIcon(Icon previewIcon) {
      myPreviewIcon = previewIcon;
    }

    @Override
    public String toString() {
      return myName;
    }
  }

  private static class TreeContentProvider extends AbstractTreeStructure {
    private final Object myTreeRoot = new Object();
    private final ResourceGroup[] myGroups;

    public TreeContentProvider(ResourceGroup[] groups) {
      myGroups = groups;
    }

    @Override
    public Object getRootElement() {
      return myTreeRoot;
    }

    @Override
    public Object[] getChildElements(Object element) {
      if (element == myTreeRoot) {
        return myGroups;
      }
      if (element instanceof ResourceGroup) {
        ResourceGroup group = (ResourceGroup)element;
        return group.getItems().toArray();
      }
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    @Override
    public Object getParentElement(Object element) {
      if (element instanceof ResourceItem) {
        ResourceItem resource = (ResourceItem)element;
        return resource.getGroup();
      }
      return null;
    }

    @NotNull
    @Override
    public NodeDescriptor createDescriptor(Object element, NodeDescriptor parentDescriptor) {
      TreeNodeDescriptor descriptor = new TreeNodeDescriptor(parentDescriptor, element, element == null ? null : element.toString());
      if (element instanceof ResourceGroup) {
        descriptor.setIcon(AllIcons.Nodes.TreeClosed);
      }
      else if (element instanceof ResourceItem) {
        descriptor.setIcon(((ResourceItem)element).getIcon());
      }
      return descriptor;
    }

    @Override
    public boolean hasSomethingToCommit() {
      return false;
    }

    @Override
    public void commit() {
    }
  }

  // Copied from com.intellij.designer.componentTree.TreeNodeDescriptor
  public static final class TreeNodeDescriptor extends NodeDescriptor {
    private final Object myElement;

    public TreeNodeDescriptor(@Nullable NodeDescriptor parentDescriptor, Object element) {
      super(null, parentDescriptor);
      myElement = element;
    }

    public TreeNodeDescriptor(@Nullable NodeDescriptor parentDescriptor, Object element, String name) {
      this(parentDescriptor, element);
      myName = name;
    }

    @Override
    public boolean update() {
      return true;
    }

    @Override
    public Object getElement() {
      return myElement;
    }
  }

  public static class SizedIcon implements Icon {
    private final int myWidth;
    private final int myHeight;
    private final Image myImage;

    public SizedIcon(int maxWidth, int maxHeight, Image image) {
      myWidth = Math.min(maxWidth, image.getWidth(null));
      myHeight = Math.min(maxHeight, image.getHeight(null));
      myImage = image;
    }

    public SizedIcon(int maxWidth, int maxHeight, ImageIcon icon) {
      this(maxWidth, maxHeight, icon.getImage());
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      g.drawImage(myImage, x, y, myWidth, myHeight, null);
    }

    @Override
    public int getIconWidth() {
      return myWidth;
    }

    @Override
    public int getIconHeight() {
      return myHeight;
    }
  }
}
