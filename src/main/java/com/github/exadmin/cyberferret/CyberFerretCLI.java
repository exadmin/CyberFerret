package com.github.exadmin.cyberferret;

import com.github.exadmin.cyberferret.async.RunnableScanner;
import com.github.exadmin.cyberferret.async.RunnableSigsLoader;
import com.github.exadmin.cyberferret.model.FoundItemsContainer;
import com.github.exadmin.cyberferret.model.FoundPathItem;
import com.github.exadmin.cyberferret.model.ItemType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class CyberFerretCLI {
    private static final Logger log = LoggerFactory.getLogger(CyberFerretCLI.class);
    private static final List<String> FORKED_REPOSITORIES = List.of(
            "k8s-conformance",
            "pg_hint_plan",
            "qubership-mistral",
            "qubership-integration-micro-engine",
            "release-drafter",
            "gatekeeper-library",
            "minio",
            "mistral-upstream",
            "robot-shop",
            "keycloak",
            "cassandra",
            "postgres",
            "kafka",
            "chart-releaser-action",
            "cassandra-exporter"
    );

    public static void main(String[] args) throws Exception {
        log.info("Start scanning directory {} using dictionary from  {}", args[0], args[1]);

        final Path rootPathToScan = Path.of(args[0]);
        final Path signaturesPath = Path.of(args[1]);

        RunnableSigsLoader sigsLoader = new RunnableSigsLoader();
        sigsLoader.setFileToLoad(signaturesPath);
        sigsLoader.run();

        RunnableScanner runnableScanner = new RunnableScanner();
        runnableScanner.setAllowedSigMap(sigsLoader.getAllowedSignaturesMap());
        runnableScanner.setSignaturesMap(sigsLoader.getRegExpMap());
        runnableScanner.setExcludeExtMap(sigsLoader.getExcludeExtsMap());

        // list directories to assume they are cloned repositories
        List<Path> subDirs = null;
        try (Stream<Path> paths = Files.list(rootPathToScan)) {
            subDirs = paths.filter(Files::isDirectory).toList();
        }

        final int prefixPathLength = rootPathToScan.toString().length();

        for (Path subDir : subDirs) {
            log.warn("\n");
            log.warn("***** ***** Start scanning");
            log.warn("***** ***** {}", subDir);
            log.warn("***** *****");

            // check if next directory is a fork - to skip it
            String subDirName = subDir.getFileName().toString().toLowerCase();
            if (FORKED_REPOSITORIES.contains(subDirName)) {
                log.info("Skipping forked repository {}", subDirName);
                continue;
            }

            FoundItemsContainer itemsContainer = new FoundItemsContainer();

            runnableScanner.setFoundItemsContainer(itemsContainer);
            runnableScanner.setDirToScan(subDir.toString());
            runnableScanner.run();

            List<FoundPathItem> items = itemsContainer.getFoundItemsCopy();

            // filter out ignored items
            Predicate<FoundPathItem> keepActual = element -> !(element.isIgnored() || element.isAllowedValue());
            Predicate<FoundPathItem> keepSignatures = element -> element.getType().equals(ItemType.SIGNATURE);
            items = items.stream()
                    .filter(keepSignatures)
                    .filter(keepActual)
                    .toList();

            for (FoundPathItem item : items) {
                log.warn("Found {} \"{}\" in \"{}\"", item.getVisualName(), item.getFoundString(), item.getFilePath().toString().substring(prefixPathLength));
            }
        }
    }
}
