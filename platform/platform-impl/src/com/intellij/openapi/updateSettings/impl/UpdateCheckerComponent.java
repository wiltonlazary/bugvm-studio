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
package com.intellij.openapi.updateSettings.impl;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.IdeBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginsAdvertiser;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Alarm;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;

/**
 * @author yole
 */
public class UpdateCheckerComponent implements ApplicationComponent {
  private static final long CHECK_INTERVAL = DateFormatUtil.DAY;

  private final Alarm myCheckForUpdatesAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
  private final Runnable myCheckRunnable = new Runnable() {
    @Override
    public void run() {
      UpdateChecker.updateAndShowResult().doWhenDone(new Runnable() {
        @Override
        public void run() {
          queueNextCheck(CHECK_INTERVAL);
        }
      });
    }
  };
  private final UpdateSettings mySettings;

  public UpdateCheckerComponent(@NotNull final Application app, @NotNull UpdateSettings settings) {
    mySettings = settings;

    if (mySettings.isSecureConnection() && !mySettings.canUseSecureConnection()) {
      mySettings.setSecureConnection(false);

      boolean tooOld = !SystemInfo.isJavaVersionAtLeast("1.7");
      final String title = IdeBundle.message("update.notifications.title");
      final String message = IdeBundle.message(tooOld ? "update.sni.not.available.message" : "update.sni.disabled.message");
      app.invokeLater(new Runnable() {
        @Override
        public void run() {
          UpdateChecker.NOTIFICATIONS.createNotification(title, message, NotificationType.WARNING, new NotificationListener.Adapter() {
            @Override
            protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e) {
              notification.expire();
              app.invokeLater(new Runnable() {
                @Override
                public void run() {
                  ShowSettingsUtil.getInstance().showSettingsDialog(null, UpdateSettingsConfigurable.class);
                }
              }, ModalityState.NON_MODAL);
            }
          }).notify(null);
        }
      }, ModalityState.NON_MODAL);
    }

    scheduleOnStartCheck(app);
  }

  private void scheduleOnStartCheck(@NotNull Application app) {
    if (!mySettings.isCheckNeeded()) {
      return;
    }

    app.getMessageBus().connect(app).subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener.Adapter() {
      @Override
      public void appFrameCreated(String[] commandLineArgs, @NotNull Ref<Boolean> willOpenProject) {
        String currentBuild = ApplicationInfo.getInstance().getBuild().asString();
        long timeToNextCheck = mySettings.getLastTimeChecked() + CHECK_INTERVAL - System.currentTimeMillis();

        if (StringUtil.compareVersionNumbers(mySettings.getLasBuildChecked(), currentBuild) < 0 || timeToNextCheck <= 0) {
          myCheckRunnable.run();
        }
        else {
          queueNextCheck(timeToNextCheck);
        }
      }
    });
  }

  private void queueNextCheck(long interval) {
    myCheckForUpdatesAlarm.addRequest(myCheckRunnable, interval);
  }

  @Override
  public void initComponent() {
    PluginsAdvertiser.ensureDeleted();
  }

  @Override
  public void disposeComponent() {
    Disposer.dispose(myCheckForUpdatesAlarm);
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "UpdateCheckerComponent";
  }

  public void queueNextCheck() {
    queueNextCheck(CHECK_INTERVAL);
  }

  public void cancelChecks() {
    myCheckForUpdatesAlarm.cancelAllRequests();
  }
}
