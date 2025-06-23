package com.github.exadmin.cyberferret.async;

import com.github.exadmin.cyberferret.fxui.FxConstants;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;

public class RunnableCheckOnlineDictionary extends ARunnable {
    private static final Logger log = LoggerFactory.getLogger(RunnableCheckOnlineDictionary.class);

    @Override
    protected void _run() throws Exception {
        log.info("Checking if new online dictionary exists");

        File savePath = new File(FxConstants.DICTIONARY_FILE_PATH_ENCRYPTED);

        if (savePath.exists()) {
            boolean wasDeleted = savePath.delete();
            if (wasDeleted) log.info("Previous version of downloaded copy was cleaned by path {}", savePath);
        }

        log.info("Downloading latest online dictionary");
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(FxConstants.CYBER_FERRET_ONLINE_DICTIONARY_URL);
            try (CloseableHttpResponse response = client.execute(request);
                 InputStream inputStream = response.getEntity().getContent();
                 FileWriter writer = new FileWriter(savePath)) {

                int byteRead;
                while ((byteRead = inputStream.read()) != -1) {
                    writer.write(byteRead);
                }
                log.info("File was downloaded successfully and saved in {}", savePath.getAbsoluteFile());
            }
        } catch (IOException ex) {
            log.error("Error while downloading online dictionary file", ex);
        }
    }

    @Override
    protected Logger getLog() {
        return null;
    }
}
