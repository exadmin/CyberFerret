package com.github.exadmin.sourcesscanner.exclude;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class ExcludeFileModel {
    @JsonProperty("signatures")
    private List<ExcludeSignatureItem> signatures;

    public ExcludeFileModel() {
        signatures = new ArrayList<>();
    }

    public List<ExcludeSignatureItem> getSignatures() {
        return signatures;
    }

    public void setSignatures(List<ExcludeSignatureItem> signatures) {
        this.signatures = signatures;
    }

    public boolean contains(String textHash, String relFileNameHash) {
        ExcludeSignatureItem temp = new ExcludeSignatureItem();
        temp.setTextHash(textHash);
        temp.setFileHash(relFileNameHash);
        return signatures.contains(temp);
    }

    public void doSortBeforeSaving() {
        signatures.sort((o1, o2) -> {
            int compareByFile = o1.getFileHash().compareTo(o2.getFileHash());
            if (compareByFile == 0) {
                return o1.getTextHash().compareTo(o2.getTextHash());
            }

            return compareByFile;
        });
    }
}
