package com.github.exadmin.cyberferret.async;

import com.github.exadmin.cyberferret.AppConstants;
import com.github.exadmin.cyberferret.utils.GitUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.HttpStatus;
import org.apache.http.HttpEntity;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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
        if (isCLIMode()) prefix = GitUtils.getConfigValue("core.hooksPath");
        if (prefix == null) prefix = "";
        Path path = Paths.get(prefix, AppConstants.DICTIONARY_FILE_PATH_ENCRYPTED);
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
        }

        downloadOnlineDictionary(savePath);
    }

    protected void downloadOnlineDictionary(File savePath) {
        logInfo("Downloading latest online dictionary");
        Path tempPath = null;
        try (CloseableHttpClient client = createHttpClient()) {
            HttpGet request = createDictionaryRequest();
            try (CloseableHttpResponse response = client.execute(request)) {
                int responseStatusCode = response.getStatusLine().getStatusCode();
                if (responseStatusCode != HttpStatus.SC_OK) {
                    logError("Error while downloading online dictionary file, response code {}", responseStatusCode);
                    return;
                }

                HttpEntity responseEntity = response.getEntity();
                if (responseEntity == null) {
                    logError("Error while downloading online dictionary file, response entity is empty");
                    return;
                }

                tempPath = createTempFileNearTarget(savePath.toPath());
                try (InputStream inputStream = responseEntity.getContent()) {
                    Files.copy(inputStream, tempPath, StandardCopyOption.REPLACE_EXISTING);
                }
                moveWithReplace(tempPath, savePath.toPath());
                logInfo("File was downloaded successfully and saved in {}", savePath.getAbsoluteFile());
            }
        } catch (IOException ex) {
            logError("Error while downloading online dictionary file. Using old one if exist.", ex);
        } finally {
            safeDeleteTempFile(tempPath);
        }
    }

    protected CloseableHttpClient createHttpClient() {
        return HttpClients.createDefault();
    }

    protected HttpGet createDictionaryRequest() {
        HttpGet request = new HttpGet(AppConstants.CYBER_FERRET_ONLINE_DICTIONARY_URL);
        int timeoutMs = AppConstants.ONLINE_DICTIONARY_DOWNLOAD_TIMEOUT_SEC * 1000;
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(timeoutMs)
                .setSocketTimeout(timeoutMs)
                .setConnectionRequestTimeout(timeoutMs)
                .build();
        request.setConfig(requestConfig);
        return request;
    }

    private Path createTempFileNearTarget(Path targetPath) throws IOException {
        Path absoluteTargetPath = targetPath.toAbsolutePath();
        Path parentDir = absoluteTargetPath.getParent();
        if (parentDir != null) {
            return Files.createTempFile(parentDir, "dictionary-download-", ".tmp");
        }
        return Files.createTempFile("dictionary-download-", ".tmp");
    }

    private void moveWithReplace(Path sourcePath, Path targetPath) throws IOException {
        try {
            Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ignored) {
            Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void safeDeleteTempFile(Path tempPath) {
        if (tempPath == null) return;
        try {
            Files.deleteIfExists(tempPath);
        } catch (IOException ex) {
            logError("Could not delete temporary dictionary file {}", tempPath, ex);
        }
    }
}
