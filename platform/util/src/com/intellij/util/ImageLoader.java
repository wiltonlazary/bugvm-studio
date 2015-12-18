/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.io.URLUtil;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.imgscalr.Scalr;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Deprecated
public class ImageLoader implements Serializable {
  public static final Component ourComponent = new Component() {
  };

  private static final Logger LOG = Logger.getInstance("#com.intellij.util.ImageLoader");

  private static boolean waitForImage(Image image) {
    if (image == null) return false;
    if (image.getWidth(null) > 0) return true;
    MediaTracker mediatracker = new MediaTracker(ourComponent);
    mediatracker.addImage(image, 1);
    try {
      mediatracker.waitForID(1, 5000);
    }
    catch (InterruptedException ex) {
      LOG.info(ex);
    }
    return !mediatracker.isErrorID(1);
  }

  @Nullable
  public static Image loadFromUrl(@NotNull URL url) {
    return loadFromUrl(url, true);
  }

  @Nullable
  public static Image loadFromUrl(@NotNull URL url, boolean allowFloatScaling) {
    final float scaleFactor = allowFloatScaling ? JBUI.scale(1f) : JBUI.scale(1f) > 1.5f ? 2f : 1f;
    assert scaleFactor >= 1.0f : "By design, only scale factors >= 1.0 are supported";

    // We can't check all 3rd party plugins and convince the authors to add @2x icons.
    // (scaleFactor > 1.0) != isRetina() => we should scale images manually.
    // Note we never scale images on Retina displays because scaling is handled by the system.
    final boolean scaleImages = (scaleFactor > 1.0f) && !UIUtil.isRetina();

    // For any scale factor > 1.0, always prefer retina images, because downscaling
    // retina images provides a better result than upscaling non-retina images.
    final boolean loadRetinaImages = UIUtil.isRetina() || scaleImages;

    for (Pair<String, Integer> each : getFileNames(url.toString(), UIUtil.isUnderDarcula(), loadRetinaImages)) {
      try {
        Image image = loadFromStream(URLUtil.openStream(new URL(each.first)), each.second);
        if (image != null && scaleImages) {
          if (each.first.contains("@2x"))
            image = scaleImage(image, scaleFactor / 2.0f);  // divide by 2.0 as Retina images are 2x the resolution.
          else
            image = scaleImage(image, scaleFactor);
        }
        return image;
      }
      catch (IOException ignore) {
        // Image file may not exist, try next one
      }
    }
    return null;
  }

  @NotNull
  private static Image scaleImage(Image image, float scale) {
    int width = (int)(scale * image.getWidth(null));
    int height = (int)(scale * image.getHeight(null));
    // Using "QUALITY" instead of "ULTRA_QUALITY" results in images that are less blurry
    // because ultra quality performs a few more passes when scaling, which introduces blurriness
    // when the scaling factor is relatively small (i.e. <= 3.0f) -- which is the case here.
    return Scalr.resize(ImageUtil.toBufferedImage(image), Scalr.Method.QUALITY, width, height);
  }

  @Nullable
  public static Image loadFromUrl(URL url, boolean dark, boolean retina) {
    for (Pair<String, Integer> each : getFileNames(url.toString(), dark, retina || JBUI.isHiDPI())) {
      try {
        return loadFromStream(URLUtil.openStream(new URL(each.first)), each.second);
      }
      catch (IOException ignore) {
      }
    }
    return null;
  }

  @Nullable
  public static Image loadFromResource(@NonNls @NotNull String s) {
    Class callerClass = ReflectionUtil.getGrandCallerClass();
    if (callerClass == null) return null;
    return loadFromResource(s, callerClass);
  }

  @Nullable
  public static Image loadFromResource(@NonNls @NotNull String path, @NotNull Class aClass) {
    for (Pair<String, Integer> each : getFileNames(path)) {
      InputStream stream = aClass.getResourceAsStream(each.first);
      if (stream == null) continue;
      Image image = loadFromStream(stream, each.second);
      if (image != null) return image;
    }
    return null;
  }

  public static List<Pair<String, Integer>> getFileNames(@NotNull String file) {
    return getFileNames(file, UIUtil.isUnderDarcula(), UIUtil.isRetina() || JBUI.scale(1.0f) >= 1.5f);
  }

  public static List<Pair<String, Integer>> getFileNames(@NotNull String file, boolean dark, boolean retina) {
    if (retina || dark) {
      List<Pair<String, Integer>> answer = new ArrayList<Pair<String, Integer>>(4);

      final String name = FileUtil.getNameWithoutExtension(file);
      final String ext = FileUtilRt.getExtension(file);
      if (dark && retina) {
        answer.add(Pair.create(name + "@2x_dark." + ext, 2));
      }

      if (dark) {
        answer.add(Pair.create(name + "_dark." + ext, 1));
      }

      if (retina) {
        answer.add(Pair.create(name + "@2x." + ext, 2));
      }

      answer.add(Pair.create(file, 1));

      return answer;
    }

    return Collections.singletonList(Pair.create(file, 1));
  }

  public static Image loadFromStream(@NotNull final InputStream inputStream) {
    return loadFromStream(inputStream, 1);
  }

  public static Image loadFromStream(@NotNull final InputStream inputStream, final int scale) {
    if (scale <= 0) throw new IllegalArgumentException("Scale must 1 or more");
    try {
      BufferExposingByteArrayOutputStream outputStream = new BufferExposingByteArrayOutputStream();
      try {
        byte[] buffer = new byte[1024];
        while (true) {
          final int n = inputStream.read(buffer);
          if (n < 0) break;
          outputStream.write(buffer, 0, n);
        }
      }
      finally {
        inputStream.close();
      }

      Image image = Toolkit.getDefaultToolkit().createImage(outputStream.getInternalBuffer(), 0, outputStream.size());

      waitForImage(image);

      if (UIUtil.isRetina() && scale > 1) {
        image = RetinaImage.createFrom(image, scale, ourComponent);
      }

      return image;
    }
    catch (Exception ex) {
      LOG.error(ex);
    }

    return null;
  }

  public static boolean isGoodSize(final Icon icon) {
    return IconLoader.isGoodSize(icon);
  }
}
