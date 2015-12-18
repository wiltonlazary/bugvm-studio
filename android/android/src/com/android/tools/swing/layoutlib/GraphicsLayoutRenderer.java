/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.swing.layoutlib;

import com.android.SdkConstants;
import com.android.ide.common.rendering.HardwareConfigHelper;
import com.android.ide.common.rendering.LayoutLibrary;
import com.android.ide.common.rendering.RenderParamsFlags;
import com.android.ide.common.rendering.RenderSecurityManager;
import com.android.ide.common.rendering.api.*;
import com.android.ide.common.resources.ResourceResolver;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.rendering.*;
import com.android.tools.idea.rendering.multi.CompatibilityRenderTarget;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.uipreview.RenderingException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Class to render layouts to a {@link Graphics} instance. This renderer does not allow for much customization of the device and does not
 * include any kind of frames.
 * <p/>
 * <p/>This class will render a layout to a {@link Graphics} object and can be used to paint in controls that require live updates.
 * <p/>
 * <p/>Note: This class is not thread safe.
 */
public class GraphicsLayoutRenderer {
  private static final Logger LOG = Logger.getInstance(GraphicsLayoutRenderer.class);

  private static final int MIN_LAYOUTLIB_API_VERSION = 15;

  private final LayoutLibrary myLayoutLibrary;
  private final SessionParams mySessionParams;
  private final FakeImageFactory myImageFactory;
  private final DynamicHardwareConfig myHardwareConfig;
  private final Object myCredential;
  private final RenderSecurityManager mySecurityManager;
  /**
   * Invalidate the layout in the next render call
   */
  private boolean myInvalidate;
  /**
   * Contains the list of resource lookups required in the last render call. This is useful for clients
   * to know which styles and resources were used to render the layout.
   */
  private final List<ResourceValue> myResourceLookupChain;

  /*
   * The render session is lazily initialized. We need to wait until we have a valid Graphics2D
   * instance to launch it.
   */
  private RenderSession myRenderSession;

  private double myScale = 1.0;

  private GraphicsLayoutRenderer(@NotNull LayoutLibrary layoutLib,
                                 @NotNull SessionParams sessionParams,
                                 @NotNull RenderSecurityManager securityManager,
                                 @NotNull DynamicHardwareConfig hardwareConfig,
                                 @NotNull List<ResourceValue> resourceLookupChain,
                                 @NotNull Object credential) {
    myLayoutLibrary = layoutLib;
    mySecurityManager = securityManager;
    myHardwareConfig = hardwareConfig;
    mySessionParams = sessionParams;
    myImageFactory = new FakeImageFactory();
    myResourceLookupChain = resourceLookupChain;
    myCredential = credential;

    sessionParams.setFlag(RenderParamsFlags.FLAG_KEY_DISABLE_BITMAP_CACHING, Boolean.TRUE);
    mySessionParams.setImageFactory(myImageFactory);
  }

  @NotNull
  protected static GraphicsLayoutRenderer create(@NotNull AndroidFacet facet,
                                                 @NotNull AndroidPlatform platform,
                                                 @NotNull IAndroidTarget target,
                                                 @NotNull Project project,
                                                 @NotNull Configuration configuration,
                                                 @NotNull ILayoutPullParser parser,
                                                 @NotNull SessionParams.RenderingMode renderingMode) throws InitializationException {
    Module module = facet.getModule();
    AndroidModuleInfo moduleInfo = AndroidModuleInfo.get(facet);

    LayoutLibrary layoutLib;
    try {
      layoutLib = platform.getSdkData().getTargetData(target).getLayoutLibrary(project);

      if (layoutLib == null) {
        throw new InitializationException("getLayoutLibrary() returned null");
      }
    }
    catch (RenderingException e) {
      throw new InitializationException(e);
    }
    catch (IOException e) {
      throw new InitializationException(e);
    }

    if (layoutLib.getApiLevel() < MIN_LAYOUTLIB_API_VERSION) {
      throw new UnsupportedLayoutlibException("GraphicsLayoutRenderer requires at least layoutlib version " + MIN_LAYOUTLIB_API_VERSION);
    }

    AppResourceRepository appResources = AppResourceRepository.getAppResources(facet, true);

    // Security token used to disable the security manager. Only objects that have a reference to it are allowed to disable it.
    Object credential = new Object();
    RenderLogger logger = new RenderLogger("theme_editor", module, credential);
    final ActionBarCallback actionBarCallback = new ActionBarCallback();
    // TODO: Remove LayoutlibCallback dependency.
    //noinspection ConstantConditions
    LayoutlibCallbackImpl layoutlibCallback =
      new LayoutlibCallbackImpl(null, layoutLib, appResources, module, facet, logger, credential, null) {
        @Override
        public ActionBarCallback getActionBarCallback() {
          return actionBarCallback;
        }
      };

    // Load the local project R identifiers.
    layoutlibCallback.loadAndParseRClass();

    HardwareConfigHelper hardwareConfigHelper = new HardwareConfigHelper(configuration.getDevice());
    DynamicHardwareConfig hardwareConfig = new DynamicHardwareConfig(hardwareConfigHelper.getConfig());
    List<ResourceValue> resourceLookupChain = new ArrayList<ResourceValue>();
    // Create a resource resolver that will save the lookups on the passed List<>
    ResourceResolver resourceResolver = configuration.getResourceResolver().createRecorder(resourceLookupChain);
    final SessionParams params =
      new SessionParams(parser, renderingMode, module, hardwareConfig, resourceResolver,
                        layoutlibCallback, moduleInfo.getMinSdkVersion().getApiLevel(), moduleInfo.getTargetSdkVersion().getApiLevel(),
                        logger, target instanceof CompatibilityRenderTarget ? target.getVersion().getApiLevel() : 0);
    params.setForceNoDecor();
    params.setAssetRepository(new AssetRepositoryImpl(facet));

    RenderSecurityManager mySecurityManager = RenderSecurityManagerFactory.create(module, platform);
    return new GraphicsLayoutRenderer(layoutLib, params, mySecurityManager, hardwareConfig, resourceLookupChain, credential);

  }

  /**
   * Creates a new {@link GraphicsLayoutRenderer}.
   * @param configuration The configuration to use when rendering.
   * @param parser A layout pull-parser.
   * @throws InitializationException if layoutlib fails to initialize.
   */
  @NotNull
  public static GraphicsLayoutRenderer create(@NotNull Configuration configuration,
                                              @NotNull ILayoutPullParser parser,
                                              boolean hasHorizontalScroll,
                                              boolean hasVerticalScroll) throws InitializationException {
    AndroidFacet facet = AndroidFacet.getInstance(configuration.getModule());
    if (facet == null) {
      throw new InitializationException("Unable to get AndroidFacet");
    }

    Module module = facet.getModule();
    IAndroidTarget target = configuration.getTarget();

    if (target == null) {
      throw new InitializationException("Unable to get IAndroidTarget");
    }

    AndroidPlatform platform = AndroidPlatform.getInstance(module);
    if (platform == null) {
      throw new InitializationException("Unable to get AndroidPlatform");
    }

    SessionParams.RenderingMode renderingMode;
    if (hasVerticalScroll && hasHorizontalScroll) {
      renderingMode = SessionParams.RenderingMode.FULL_EXPAND;
    } else if (hasVerticalScroll) {
      renderingMode = SessionParams.RenderingMode.V_SCROLL;
    } else if (hasHorizontalScroll) {
      renderingMode = SessionParams.RenderingMode.H_SCROLL;
    } else {
      renderingMode = SessionParams.RenderingMode.NORMAL;
    }

    return create(facet, platform, target, module.getProject(), configuration, parser, renderingMode);
  }

  /**
   * Converts a dimension from model coordinates to view coordinates (possibly scaled).
   */
  @NotNull
  private Dimension modelToView(int width, int height) {
    return new Dimension((int)(width * myScale), (int)(height * myScale));
  }

  /**
   * Converts a dimension from model coordinates to view coordinates (possibly scaled).
   */
  @NotNull
  private Dimension modelToView(@NotNull Dimension d) {
    return modelToView(d.width, d.height);
  }

  /**
   * Converts a dimension from view coordinates (possibly scaled) to model coordinates.
   */
  @NotNull
  private Dimension viewToModel(int width, int height) {
    return new Dimension((int)(width / myScale), (int)(height / myScale));
  }

  /**
   * Converts a point from view coordinates (possibly scaled) to model coordinates.
   */
  @NotNull
  private Dimension viewToModel(@NotNull Dimension d) {
    return viewToModel(d.width, d.height);
  }

  /**
   * Converts a point from view coordinates (possibly scaled) to model coordinates.
   */
  @NotNull
  private Point viewToModel(@NotNull Point p) {
    return new Point((int)(p.x / myScale), (int)(p.y / myScale));
  }

  /**
   * Sets the rendering scale. If scale is greater than 1.0, the rendered result will be bigger, while if it's less than 1.0 it will be
   * smaller. The scale does not affect the size set in {@link #setSize} so the resulting image will have the same width and height.
   */
  public void setScale(double scale) {
    myScale = scale;

    // Adjust screen size to new scale.
    setSize(new Dimension(myHardwareConfig.getScreenWidth(), myHardwareConfig.getScreenHeight()));
  }


  public void setSize(int width, int height) {
    Dimension dimen = viewToModel(width, height);
    // The minimum render size we allow is 1,1 since 0,0 wouldn't render anything.
    myHardwareConfig.setScreenSize(Math.max(dimen.width, 1), Math.max(dimen.height, 1));
    myInvalidate = true;
  }

  public void setSize(Dimension dimen) {
    setSize(dimen.width, dimen.height);
  }

  /**
   * Render the layout to the passed {@link Graphics2D} instance using the defined viewport.
   * <p/>
   * <p/>Please note that this method is not thread safe so, if used from multiple threads, it's the caller's responsibility to synchronize
   * the access to it.
   */
  public boolean render(@NotNull final Graphics2D graphics) {
    if (!SystemInfo.isMac) {
      // Do not enable anti-aliasing on MAC. It doesn't improve much and causes has performance issues when filling the background using
      // alpha values.
      graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }
    myImageFactory.setGraphics(graphics);

    AffineTransform oldTransform = graphics.getTransform();
    if (myScale != 1.0) {
      graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      AffineTransform scaleTransform = new AffineTransform(oldTransform);
      scaleTransform.scale(myScale, myScale);
      graphics.setTransform(scaleTransform);
    }

    Result result = null;

    try {
      result = RenderService.runRenderAction(new Callable<Result>() {
        @Override
        public Result call() {
          mySecurityManager.setActive(true, myCredential);
          try {
            if (myRenderSession == null) {
              myResourceLookupChain.clear();
              myRenderSession = initRenderSession();
              return myRenderSession != null ? myRenderSession.getResult() : null;
              // initRenderSession will call render so we do not need to do it here.
            }
            else {
              return myRenderSession.render(RenderParams.DEFAULT_TIMEOUT, myInvalidate);
            }
          }
          finally {
            mySecurityManager.setActive(false, myCredential);
          }
        }
      });
    }
    catch (Exception e) {
      LOG.error("Exception running render action", e);
    }


    if (myScale != 1.0) {
      graphics.setTransform(oldTransform);
    }

    // We need to log the errors after disabling the security manager since the logger will cause a security exception when trying to
    // access the system properties.
    if (result != null && result.getStatus() != Result.Status.SUCCESS) {
      if (result.getException() != null) {
        LOG.error(result.getException());
      }
      else {
        LOG.error("Render error (no exception). Status=" + result.getStatus().name());
      }
      return false;
    }

    myInvalidate = false;
    return true;
  }

  /**
   * Returns the initialised render session. This will also do the an initial render of the layout.
   */
  private
  @Nullable
  RenderSession initRenderSession() {
    // createSession() might access the PSI tree so we need to run it inside as a read action.
    return ApplicationManager.getApplication().runReadAction(new Computable<RenderSession>() {
      @Override
      public RenderSession compute() {
        // createSession will also render the layout for the first time.
        return myLayoutLibrary.createSession(mySessionParams);
      }
    });
  }

  /**
   * Returns the list of attribute names used to render the layout
   */
  @NotNull
  public Set<String> getUsedAttrs() {
    HashSet<String> usedAttrs = new HashSet<String>();
    for(ResourceValue value : myResourceLookupChain) {
      if (!(value instanceof ItemResourceValue) || value.getName() == null) {
        // Only selects resources that are also attributes
        continue;
      }
      ItemResourceValue itemValue = (ItemResourceValue)value;
      usedAttrs.add((itemValue.isFrameworkAttr() ? SdkConstants.PREFIX_ANDROID : "") + itemValue.getName());
    }

    return Collections.unmodifiableSet(usedAttrs);
  }

  @Nullable
  private static ViewInfo viewAtPoint(@NotNull Point parentPosition, @NotNull ViewInfo view, @NotNull Point p) {
    int x = parentPosition.x + view.getLeft();
    int y = parentPosition.y + view.getTop();
    Rectangle rect = new Rectangle(x,
                                   y,
                                   view.getRight() - view.getLeft(),
                                   view.getBottom() - view.getTop());
    if (rect.contains(p)) {
      for (ViewInfo childView : view.getChildren()) {
        if (childView.getCookie() == null) {
          continue;
        }

        ViewInfo hitView = viewAtPoint(rect.getLocation(), childView, p);

        if (hitView != null) {
          return hitView;
        }
      }

      return view;
    }

    return null;
  }

  /**
   * Find the view at a given point.
   */
  @Nullable
  public ViewInfo findViewAtPoint(@NotNull Point p) {
    if (myRenderSession == null) {
      return null;
    }

    p = viewToModel(p);

    Point base = new Point();
    for (ViewInfo view : myRenderSession.getRootViews()) {
      ViewInfo hitView = viewAtPoint(base, view, p);
      if (hitView != null) {
        return hitView;
      }
    }

    return null;
  }

  public Dimension getPreferredSize() {
    return modelToView(myImageFactory.getRequestedWidth(), myImageFactory.getRequestedHeight());
  }

  public void dispose() {
    if (myRenderSession != null) {
      myRenderSession.dispose();
      myRenderSession = null;
    }
  }

  /**
   * {@link HardwareConfig} that allows changing the screen size of the device on the fly.
   * <p/>
   * <p/>This allows to pass the HardwareConfig to the LayoutLib and then dynamically modify the size
   * for every render call.
   */
  static class DynamicHardwareConfig extends HardwareConfig {
    private int myWidth;
    private int myHeight;

    public DynamicHardwareConfig(HardwareConfig delegate) {
      super(delegate.getScreenWidth(), delegate.getScreenHeight(), delegate.getDensity(), delegate.getXdpi(), delegate.getYdpi(),
            delegate.getScreenSize(), delegate.getOrientation(), delegate.getScreenRoundness(), delegate.hasSoftwareButtons());

      myWidth = delegate.getScreenWidth();
      myHeight = delegate.getScreenHeight();
    }

    public void setScreenSize(int width, int height) {
      myWidth = width;
      myHeight = height;
    }

    @Override
    public int getScreenWidth() {
      return myWidth;
    }

    @Override
    public int getScreenHeight() {
      return myHeight;
    }
  }
}
