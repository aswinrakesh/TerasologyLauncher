/*
 * Copyright (c) 2013 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.terasologylauncher.updater;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasologylauncher.BuildType;
import org.terasologylauncher.Settings;
import org.terasologylauncher.gui.LauncherFrame;
import org.terasologylauncher.util.DownloadUtils;
import org.terasologylauncher.util.FileUtils;
import org.terasologylauncher.version.TerasologyGameVersion;

import javax.swing.JProgressBar;
import javax.swing.SwingWorker;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * @author MrBarsack
 * @author Skaldarnar
 */
public final class GameDownloader extends SwingWorker<Void, Void> {

    private static final Logger logger = LoggerFactory.getLogger(GameDownloader.class);

    private static final String ZIP_FILE = "Terasology.zip";

    private final JProgressBar progressBar;
    private final LauncherFrame frame;

    private final Settings settings;
    private final File terasologyDirectory;
    private final TerasologyGameVersion gameVersion;

    public GameDownloader(final JProgressBar progressBar, final LauncherFrame frame, final Settings settings,
                          final File terasologyDirectory, final TerasologyGameVersion gameVersion) {
        this.progressBar = progressBar;
        this.frame = frame;
        this.settings = settings;
        this.terasologyDirectory = terasologyDirectory;
        this.gameVersion = gameVersion;
        progressBar.setVisible(true);
        progressBar.setValue(0);
        progressBar.setString("Starting Download"); // TODO Use BundleUtils.getLabel
        addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(final PropertyChangeEvent evt) {
                if ("progress".equals(evt.getPropertyName())) {
                    progressBar.setString(null);
                    progressBar.setValue((Integer) evt.getNewValue());
                }
            }
        });
    }

    @Override
    protected Void doInBackground() {
        final String jobName;
        if (BuildType.STABLE == settings.getBuildType()) {
            jobName = DownloadUtils.TERASOLOGY_STABLE_JOB_NAME;
        } else {
            jobName = DownloadUtils.TERASOLOGY_NIGHTLY_JOB_NAME;
        }
        final Integer version;
        if (settings.isBuildVersionLatest(settings.getBuildType())) {
            version = gameVersion.getVersion(settings.getBuildType());
        } else {
            version = settings.getBuildVersion(settings.getBuildType());
        }

        // try to do the download
        URL url = null;
        File file = null;
        try {
            url = DownloadUtils.getDownloadURL(jobName, version, ZIP_FILE);
            final long dataSize = url.openConnection().getContentLength() / 1024 / 1024;

            InputStream in = null;
            OutputStream out = null;

            try {

                file = new File(terasologyDirectory, ZIP_FILE);

                in = url.openConnection().getInputStream();
                out = new FileOutputStream(file);

                final byte[] buffer = new byte[2048];

                int n;
                while ((n = in.read(buffer)) > 0) {
                    out.write(buffer, 0, n);
                    final long fileSizeMB = file.length() / 1024 / 1024;
                    float percentage = fileSizeMB / (float) dataSize;
                    percentage *= 100;

                    setProgress((int) percentage);
                }
            } finally {
                if (in != null) {
                    in.close();
                }
                if (out != null) {
                    out.close();
                }
            }

        } catch (MalformedURLException e) {
            logger.error("Could not download game!", e);
        } catch (IOException e) {
            logger.error("Could not download game!", e);
        }
        return null;
    }

    @Override
    protected void done() {
        logger.debug("Download is done");
        // unzip downloaded file
        progressBar.setValue(100);
        progressBar.setString("Extracting zip …"); // TODO Use BundleUtils.getLabel
        progressBar.setStringPainted(true);

        File zip = null;
        zip = new File(terasologyDirectory, ZIP_FILE);
        FileUtils.extractZip(zip);

        progressBar.setString("Updating game info …"); // TODO Use BundleUtils.getLabel
        progressBar.setStringPainted(true);

        GameData.forceReReadVersionFile(terasologyDirectory);
        frame.updateStartButton();

        if (zip != null) {
            zip.delete();
        }
        progressBar.setVisible(false);
    }
}
