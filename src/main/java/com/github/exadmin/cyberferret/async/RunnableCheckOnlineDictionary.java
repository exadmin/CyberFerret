package com.github.exadmin.cyberferret.async;

import com.github.exadmin.cyberferret.fxui.FxConstants;
import com.github.exadmin.cyberferret.utils.GitUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

public class RunnableCheckOnlineDictionary extends ARunnable {
    private static final Duration DICTIONARY_REFRESH_INTERVAL = Duration.ofHours(4);

    public RunnableCheckOnlineDictionary(boolean isCLIMode) {
        super(isCLIMode);
    }

    @Override
    protected void _run() {
        logInfo("Checking if new online dictionary exists");

        String prefix = "";
        if (isCLIMode()) prefix = GitUtils.getGlobalConfigValue("core.hooksPath");
        if (prefix == null) prefix = "";
        Path path = Paths.get(prefix, FxConstants.DICTIONARY_FILE_PATH_ENCRYPTED);
        File savePath = path.toFile();

        if (savePath.exists()) {
            if (isCLIMode()) {
                try {
                    long lastModifiedMillis = Files.getLastModifiedTime(savePath.toPath()).toMillis();
                    long ageMillis = System.currentTimeMillis() - lastModifiedMillis;
                    if (ageMillis >= 0 && ageMillis < DICTIONARY_REFRESH_INTERVAL.toMillis()) {
                        logInfo("Skipping dictionary download, local cache is still fresh: {}", savePath.getAbsolutePath());
                        return;
                    }
                } catch (IOException ex) {
                    logError("Failed to read change date for {}", savePath.getAbsolutePath(), ex);
                }
            }

            boolean wasDeleted = savePath.delete();
            if (wasDeleted) logInfo("Previous version of downloaded copy was cleaned by path {}", savePath);
        }

        downloadOnlineDictionary(savePath);
    }

    protected void downloadOnlineDictionary(File savePath) {
        logInfo("Downloading latest online dictionary");
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(FxConstants.CYBER_FERRET_ONLINE_DICTIONARY_URL);
            try (CloseableHttpResponse response = client.execute(request);
                 InputStream inputStream = response.getEntity().getContent();
                 FileWriter writer = new FileWriter(savePath)) {

                int byteRead;
                while ((byteRead = inputStream.read()) != -1) {
                    writer.write(byteRead);
                }
                logInfo("File was downloaded successfully and saved in {}", savePath.getAbsoluteFile());
            }
        } catch (IOException ex) {
            logError("Error while downloading online dictionary file", ex);
        }
    }
}
