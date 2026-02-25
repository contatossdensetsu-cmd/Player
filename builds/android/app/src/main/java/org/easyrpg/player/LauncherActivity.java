package org.easyrpg.player;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import org.easyrpg.player.game_browser.Game;
import org.easyrpg.player.game_browser.GameBrowserHelper;
import org.easyrpg.player.game_browser.ProjectType;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class LauncherActivity extends AppCompatActivity {
    private static final String TAG = "LauncherActivity";
    private static final String DATA_URL = "https://pub-89c1e73ddec14540b09bfc3545e6e293.r2.dev/ss_densetsu_data.zip";
    private ProgressBar progressBar;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher);

        progressBar = findViewById(R.id.progress_bar);
        statusText = findViewById(R.id.status_text);

        checkAndStart();
    }

    private void checkAndStart() {
        File gameDir = new File(getFilesDir(), "game");
        if (new File(gameDir, "RPG_RT.ini").exists()) {
            launchGame();
        } else {
            downloadAndExtract();
        }
    }

    private void downloadAndExtract() {
        new Thread(() -> {
            try {
                File tempZip = new File(getCacheDir(), "data.zip");
                downloadFile(DATA_URL, tempZip);

                runOnUiThread(() -> statusText.setText("Extracting game data..."));
                extractZip(tempZip, new File(getFilesDir(), "game"));

                tempZip.delete();

                runOnUiThread(this::launchGame);
            } catch (Exception e) {
                Log.e(TAG, "Error in download/extract", e);
                runOnUiThread(() -> statusText.setText("Error: " + e.getMessage()));
            }
        }).start();
    }

    private void downloadFile(String urlStr, File outputFile) throws IOException {
        runOnUiThread(() -> statusText.setText("Downloading game data..."));
        URL url = new URL(urlStr);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.connect();

        int fileLength = connection.getContentLength();

        try (InputStream input = new BufferedInputStream(url.openStream());
             OutputStream output = new FileOutputStream(outputFile)) {

            byte[] data = new byte[8192];
            long total = 0;
            int count;
            while ((count = input.read(data)) != -1) {
                total += count;
                if (fileLength > 0) {
                    int progress = (int) (total * 100 / fileLength);
                    runOnUiThread(() -> progressBar.setProgress(progress));
                }
                output.write(data, 0, count);
            }
        }
    }

    private void extractZip(File zipFile, File targetDir) throws IOException {
        if (!targetDir.exists()) targetDir.mkdirs();

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                File file = new File(targetDir, ze.getName());
                if (ze.isDirectory()) {
                    file.mkdirs();
                } else {
                    file.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private void launchGame() {
        File gameDir = new File(getFilesDir(), "game");
        // We look for the folder that contains RPG_RT.ini
        File actualGameDir = findGameRoot(gameDir);
        if (actualGameDir == null) {
            statusText.setText("Error: RPG_RT.ini not found in extracted data");
            return;
        }

        File saveDir = new File(getFilesDir(), "save");
        if (!saveDir.exists()) saveDir.mkdirs();

        Game project = new Game(actualGameDir.getAbsolutePath(), saveDir.getAbsolutePath(), null, ProjectType.SUPPORTED.ordinal());
        project.setStandalone(true);
        GameBrowserHelper.launchGame(this, project);
        finish();
    }

    private File findGameRoot(File dir) {
        if (new File(dir, "RPG_RT.ini").exists()) {
            return dir;
        }
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    File found = findGameRoot(f);
                    if (found != null) return found;
                }
            }
        }
        return null;
    }
}
