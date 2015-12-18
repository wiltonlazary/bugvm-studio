/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.wizard;

import com.android.SdkConstants;
import com.android.assetstudiolib.*;
import com.android.assetstudiolib.vectordrawable.Svg2Vector;
import com.android.assetstudiolib.vectordrawable.VdPreview;
import com.android.resources.Density;
import com.android.tools.idea.rendering.ImageUtils;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import static java.awt.image.BufferedImage.TYPE_INT_ARGB;

public class AssetStudioAssetGenerator implements GraphicGeneratorContext {
  public static final String ATTR_TEXT = "text";
  public static final String ATTR_SCALING = "scaling";
  public static final String ATTR_SHAPE = "shape";
  public static final String ATTR_PADDING = "padding";
  public static final String ATTR_TRIM = "trim";
  public static final String ATTR_DOGEAR = "dogear";
  public static final String ATTR_FONT = "font";
  public static final String ATTR_FONT_SIZE = "fontSize";
  public static final String ATTR_SOURCE_TYPE = "sourceType";
  public static final String ATTR_IMAGE_PATH = "imagePath";
  public static final String ATTR_CLIPART_NAME = "clipartPath";
  public static final String ATTR_FOREGROUND_COLOR = "foregroundColor";
  public static final String ATTR_BACKGROUND_COLOR = "backgroundColor";
  public static final String ATTR_ASSET_TYPE = "assetType";
  public static final String ATTR_ASSET_THEME = "assetTheme";
  public static final String ATTR_ASSET_NAME = "assetName";
  public static final String ATTR_ERROR_LOG = "errorLog";

  public static final String ERROR_MESSAGE_EMPTY_PREVIEW_IMAGE = "Empty preview image!";

  private static final Logger LOG = Logger.getInstance("#" + AssetStudioAssetGenerator.class.getName());
  private static final String OUTPUT_DIRECTORY = "src/main/";
  private static Cache<String, BufferedImage> ourImageCache = CacheBuilder.newBuilder().build();

  private final ActionBarIconGenerator myActionBarIconGenerator;
  private final NotificationIconGenerator myNotificationIconGenerator;
  private final LauncherIconGenerator myLauncherIconGenerator;

  private AssetStudioContext myContext;

  public static final int SVG_PREVIEW_WIDTH = 256;
  /**
   * This is needed to migrate between "old" and "new" wizard frameworks
   */
  public interface AssetStudioContext {

    int getPadding();

    void setPadding(int padding);

    @Nullable
    SourceType getSourceType();

    void setSourceType(SourceType sourceType);

    @Nullable
    AssetType getAssetType();

    boolean isTrim();

    void setTrim(boolean trim);

    boolean isDogear();

    void setDogear(boolean dogEar);

    @Nullable
    String getImagePath();

    @Nullable
    String getText();

    void setText(String text);

    @Nullable
    String getClipartName();

    void setClipartName(String clipartName);

    Color getForegroundColor();

    void setForegroundColor(Color fg);

    @Nullable
    String getFont();

    void setFont(String font);

    int getFontSize();

    void setFontSize(int fontSize);

    Scaling getScaling();

    void setScaling(Scaling scaling);

    @Nullable
    String getAssetName();

    GraphicGenerator.Shape getShape();

    void setShape(GraphicGenerator.Shape shape);

    Color getBackgroundColor();

    void setBackgroundColor(Color bg);

    @Nullable
    String getAssetTheme();

    @Nullable
    public String getErrorLog();

    public void setErrorLog(String log);
  }

  public static class ImageGeneratorException extends Exception {
    public ImageGeneratorException(String message) {
      super(message);
    }
  }

  /**
   * Types of sources that the asset studio can use to generate icons from
   */
  public enum SourceType {
    IMAGE, CLIPART, TEXT, SVG
  }


  public enum Scaling {
    CENTER, CROP
  }

  /**
   * The type of asset to create: launcher icon, menu icon, etc.
   */
  public enum AssetType {
    /**
     * Launcher icon to be shown in the application list
     */
    LAUNCHER("Launcher Icons", "ic_launcher"),

    /**
     * Icons shown in the action bar
     */
    ACTIONBAR("Action Bar and Tab Icons", "ic_action_%s"),

    /**
     * Icons shown in a notification message
     */
    NOTIFICATION("Notification Icons", "ic_stat_%s");

    /**
     * Icons shown as part of tabs
     */
    //TAB("Pre-Android 3.0 Tab Icons", "ic_tab_%s"),

    /**
     * Icons shown in menus
     */
    //MENU("Pre-Android 3.0 Menu Icons", "ic_menu_%s");
    /**
     * Display name to show to the user in the asset type selection list
     */
    private final String myDisplayName;
    /**
     * Default asset name format
     */
    private String myDefaultNameFormat;

    AssetType(String displayName, String defaultNameFormat) {
      myDisplayName = displayName;
      myDefaultNameFormat = defaultNameFormat;
    }

    /**
     * Returns the display name of this asset type to show to the user in the
     * asset wizard selection page etc
     */
    public String getDisplayName() {
      return myDisplayName;
    }

    @Override
    public String toString() {
      return getDisplayName();
    }

    /**
     * Returns the default format to use to suggest a name for the asset
     */
    public String getDefaultNameFormat() {
      return myDefaultNameFormat;
    }

    /**
     * Whether this asset type configures foreground scaling
     */
    public boolean needsForegroundScaling() {
      return this == LAUNCHER;
    }

    /**
     * Whether this asset type needs a shape parameter
     */
    public boolean needsShape() {
      return this == LAUNCHER;
    }

    /**
     * Whether this asset type needs foreground and background color parameters
     */
    public boolean needsColors() {
      return this == LAUNCHER;
    }

    /**
     * Whether this asset type needs an effects parameter
     */
    public boolean needsEffects() {
      return this == LAUNCHER;
    }

    /**
     * Whether this asset type needs a theme parameter
     */
    public boolean needsTheme() {
      return this == ACTIONBAR;
    }
  }

  public AssetStudioAssetGenerator(AssetStudioContext context) {
    this(context, new ActionBarIconGenerator(), new NotificationIconGenerator(), new LauncherIconGenerator());
  }

  public AssetStudioAssetGenerator(TemplateWizardState state) {
    this(new TemplateWizardContextAdapter(state), new ActionBarIconGenerator(), new NotificationIconGenerator(),
         new LauncherIconGenerator());
  }

  /**
   * Allows dependency injection for testing
   */
  @SuppressWarnings("UseJBColor") // These colors are for the graphics generator, not the plugin UI
  public AssetStudioAssetGenerator(@NotNull AssetStudioContext context,
                                   @Nullable ActionBarIconGenerator actionBarIconGenerator,
                                   @Nullable NotificationIconGenerator notificationIconGenerator,
                                   @Nullable LauncherIconGenerator launcherIconGenerator) {
    myContext = context;
    myActionBarIconGenerator = actionBarIconGenerator != null ? actionBarIconGenerator : new ActionBarIconGenerator();
    myNotificationIconGenerator = notificationIconGenerator != null ? notificationIconGenerator : new NotificationIconGenerator();
    myLauncherIconGenerator = launcherIconGenerator != null ? launcherIconGenerator : new LauncherIconGenerator();

    myContext.setText("Aa");
    myContext.setFont("Arial Black");
    myContext.setScaling(Scaling.CROP);
    myContext.setShape(GraphicGenerator.Shape.NONE);
    myContext.setFontSize(144);
    myContext.setSourceType(AssetStudioAssetGenerator.SourceType.IMAGE);
    myContext.setClipartName("android.png");
    myContext.setForegroundColor(Color.BLUE);
    myContext.setBackgroundColor(Color.WHITE);
    myContext.setTrim(false);
    myContext.setDogear(false);
    myContext.setPadding(0);
  }

  @Override
  @Nullable
  public BufferedImage loadImageResource(@NotNull final String path) {
    try {
      return ourImageCache.get(path, new Callable<BufferedImage>() {
        @Override
        public BufferedImage call() throws Exception {
          return getImage(path, true);
        }
      });
    }
    catch (ExecutionException e) {
      LOG.error(e);
      return null;
    }
  }

  /**
   * Generate images using the given wizard state
   *
   * @param previewOnly whether we are only generating previews
   * @return a map of image objects
   */
  @NotNull
  public Map<String, Map<String, BufferedImage>> generateImages(boolean previewOnly) throws ImageGeneratorException {
    Map<String, Map<String, BufferedImage>> categoryMap = new LinkedHashMap<String, Map<String, BufferedImage>>();
    generateImages(categoryMap, false, previewOnly);
    return categoryMap;
  }

  /**
   * Generate images using the given wizard state into the given map
   * @param categoryMap the map to store references to the resultant images in
   * @param clearMap if true, the map will be emptied before use
   * @param previewOnly whether we are only generating previews
   * @throws ImageGeneratorException
   */
  public void generateImages(Map<String, Map<String, BufferedImage>> categoryMap, boolean clearMap, boolean previewOnly)
      throws ImageGeneratorException {
    if (clearMap) {
      categoryMap.clear();
    }

    AssetType type = myContext.getAssetType();
    if (type == null) {
      // If we don't know what we're building, don't do it yet.
      return;
    }
    boolean trim = myContext.isTrim();
    boolean dogEar = myContext.isDogear();
    int padding = myContext.getPadding();

    SourceType sourceType = myContext.getSourceType();
    if (sourceType == null) {
      return;
    }

    BufferedImage sourceImage = null;
    switch (sourceType) {
      case SVG:  {
        // So here we should only generate one dpi image.
        String path = myContext.getImagePath();
        if (path == null || path.isEmpty()) {
          return;
        }
        // Get the parsing errors while generating the images.
        // Save the error log into context to be later picked up by the step UI.
        StringBuilder errorLog = new StringBuilder();
        sourceImage = getSvgImage(path, errorLog);
        myContext.setErrorLog(errorLog.toString());
        break;
      }

      case IMAGE: {
        String path = myContext.getImagePath();
        if (path == null || path.isEmpty()) {
          throw new ImageGeneratorException("Path to image is empty.");
        }

        try {
          sourceImage = getImage(path, false);
        }
        catch (FileNotFoundException e) {
          throw new ImageGeneratorException("Image file not found: " + path);
        }
        catch (IOException e) {
          throw new ImageGeneratorException("Unable to load image file: " + path);
        }
        break;
      }

      case CLIPART: {
        String clipartName = myContext.getClipartName();
        try {
          sourceImage = GraphicGenerator.getClipartImage(clipartName);
        }
        catch (IOException e) {
          throw new ImageGeneratorException("Unable to load clip art image: " + clipartName);
        }

        if (type.needsColors()) {
          Paint paint = myContext.getForegroundColor();
          sourceImage = Util.filledImage(sourceImage, paint);
        }
        break;
      }

      case TEXT: {
        TextRenderUtil.Options options = new TextRenderUtil.Options();
        options.font = Font.decode(myContext.getFont() + " " + myContext.getFontSize());
        options.foregroundColor = type.needsColors() ? myContext.getForegroundColor().getRGB() : 0xFFFFFFFF;
        sourceImage = TextRenderUtil.renderTextImage(myContext.getText(), 1, options);

        break;
      }
    }

    if (trim) {
      sourceImage = crop(sourceImage);
    }

    if (padding != 0) {
      sourceImage = Util.paddedImage(sourceImage, padding);
    }

    GraphicGenerator generator = null;
    GraphicGenerator.Options options = null;
    String baseName = Strings.nullToEmpty(myContext.getAssetName());

    switch (type) {
      case LAUNCHER: {
        generator = myLauncherIconGenerator;
        LauncherIconGenerator.LauncherOptions launcherOptions = new LauncherIconGenerator.LauncherOptions();
        launcherOptions.shape = myContext.getShape();
        launcherOptions.crop = Scaling.CROP.equals(myContext.getScaling());
        launcherOptions.style = GraphicGenerator.Style.SIMPLE;
        launcherOptions.backgroundColor = myContext.getBackgroundColor().getRGB();
        launcherOptions.isWebGraphic = !previewOnly;
        if (dogEar) {
          launcherOptions.isDogEar = true;
        }
        options = launcherOptions;
        }
        break;
      case ACTIONBAR: {
        generator = myActionBarIconGenerator;
        ActionBarIconGenerator.ActionBarOptions actionBarOptions = new ActionBarIconGenerator.ActionBarOptions();
        String themeName = myContext.getAssetTheme();
        if (!StringUtil.isEmpty(themeName)) {
          ActionBarIconGenerator.Theme theme = ActionBarIconGenerator.Theme.valueOf(themeName);
          if (theme != null) {
            switch (theme) {
              case HOLO_DARK:
                actionBarOptions.theme = ActionBarIconGenerator.Theme.HOLO_DARK;
                break;
              case HOLO_LIGHT:
                actionBarOptions.theme = ActionBarIconGenerator.Theme.HOLO_LIGHT;
                break;
              case CUSTOM:
                actionBarOptions.theme = ActionBarIconGenerator.Theme.CUSTOM;
                actionBarOptions.customThemeColor = myContext.getForegroundColor().getRGB();
                break;
            }
          }
        }
        actionBarOptions.sourceIsClipart = (sourceType == SourceType.CLIPART);

        options = actionBarOptions;
        }
        break;
      case NOTIFICATION:
        generator = myNotificationIconGenerator;
        NotificationIconGenerator.NotificationOptions notificationOptions = new NotificationIconGenerator.NotificationOptions();
        notificationOptions.version = NotificationIconGenerator.Version.V11;
        options = notificationOptions;
        break;
    }

    options.sourceImage = sourceImage;
    if (sourceType == SourceType.SVG) {
      options.density = Density.ANYDPI;
    }
    generator.generate(null, categoryMap, this, options, baseName);
  }

  /**
   * Outputs final-rendered images to disk, rooted at the given variant directory.
   */
  public void outputImagesIntoVariantRoot(@NotNull File variantDir) {
    try {
      Map<String, Map<String, BufferedImage>> images = generateImages(false);
      for (Map<String, BufferedImage> density : images.values()) {
        for (Map.Entry<String, BufferedImage> image : density.entrySet()) {
          // TODO: The output directory needs to take flavor and build type into account, which will need to be configurable by the user
          File file = new File(variantDir, image.getKey());
          try {
            VirtualFile directory = VfsUtil.createDirectories(file.getParentFile().getAbsolutePath());
            VirtualFile imageFile = directory.findChild(file.getName());
            if (imageFile == null || !imageFile.exists()) {
              imageFile = directory.createChildData(this, file.getName());
            }
            OutputStream outputStream = imageFile.getOutputStream(this);
            try {
              ImageIO.write(image.getValue(), "PNG", outputStream);
            }
            finally {
              outputStream.close();
            }

          }
          catch (IOException e) {
            LOG.error(e);
          }
        }
      }
    } catch (Exception e) {
      LOG.error(e);
    }

  }

  /**
   * Outputs final-rendered images to disk, rooted at the given module directory
   */
  public void outputImagesIntoDefaultVariant(@NotNull File contentRoot) {
    File directory = new File(contentRoot, OUTPUT_DIRECTORY);
    outputImagesIntoVariantRoot(directory);
  }

  @NotNull
  private static BufferedImage getSvgImage(@NotNull String path, StringBuilder errorLog) {
    String xmlFileContent = generateVectorXml(new File(path), errorLog);
    BufferedImage image = VdPreview.getPreviewFromVectorXml(SVG_PREVIEW_WIDTH, xmlFileContent,
                                                            errorLog);

    if (image == null) {
      //noinspection UndesirableClassUsage
      image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
      // This sentence is also used as a flag to turn on / off the next button in vector step.
      errorLog.insert(0, ERROR_MESSAGE_EMPTY_PREVIEW_IMAGE + "\n");
    }
    return image;
  }

  public void outputXmlToRes(File targetResDir) {
    String currentFilePath = myContext.getImagePath();
    // At output step, we can ignore the errors since they have been exposed in the previous step.
    String xmlFileContent = generateVectorXml(new File(currentFilePath), null);

    String xmlFileName = myContext.getAssetName();
    // Here get the XML file content, and write into targetResDir / drawable / ***.xml
    File file = new File(targetResDir, SdkConstants.FD_RES_DRAWABLE + File.separator +
                                       xmlFileName + SdkConstants.DOT_XML);
    try {
      VirtualFile directory = VfsUtil.createDirectories(file.getParentFile().getAbsolutePath());
      VirtualFile xmlFile = directory.findChild(file.getName());
      if (xmlFile == null || !xmlFile.exists()) {
        xmlFile = directory.createChildData(this, file.getName());
      }

      VfsUtil.saveText(xmlFile, xmlFileContent);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  @Nullable
  private static String generateVectorXml(@NotNull File inputSvgFile, StringBuilder error) {
    OutputStream outStream = new ByteArrayOutputStream();
    String parseError = Svg2Vector.parseSvgToXml(inputSvgFile, outStream);
    if (error != null) {
      error.append(Strings.nullToEmpty(parseError));
    }
    String vectorXmlContent = outStream.toString();
    // Return null content to make sure the preview image will be gone!
    return vectorXmlContent;
  }


  @NotNull
  protected static BufferedImage getImage(@NotNull String path, boolean isPluginRelative)
      throws IOException {
    BufferedImage image;
    if (isPluginRelative) {
      image = GraphicGenerator.getStencilImage(path);
    }
    else {
      image = ImageIO.read(new File(path));
    }

    if (image == null) {
      //noinspection UndesirableClassUsage
      image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    }

    return image;
  }

  @NotNull
  protected static BufferedImage crop(@NotNull BufferedImage sourceImage) {
    BufferedImage cropped = ImageUtils.cropBlank(sourceImage, null, TYPE_INT_ARGB);
    return cropped != null ? cropped : sourceImage;
  }
}
