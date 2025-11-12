package com.github.exadmin.cyberferret.exclude;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class ExcludeFileModel {
    @JsonProperty("exclusions")
    private List<ExcludeSignatureItem> signatures;

    public ExcludeFileModel() {
        signatures = new ArrayList<>();
    }

    public List<ExcludeSignatureItem> getSignatures() {
        return signatures;
    }

    private void setSignatures(List<ExcludeSignatureItem> signatures) {
        this.signatures = signatures;
    }

    public boolean contains(String textHash, String relFileNameHash) {
        ExcludeSignatureItem temp = new ExcludeSignatureItem();
        temp.setTextHash(textHash);
        temp.setFileHash(relFileNameHash);
        return signatures.contains(temp);
    }

    public void remove(String textHash, String relFileNameHash) {
        ExcludeSignatureItem itemToRemove = null;
        for (ExcludeSignatureItem next : getSignatures()) {
            if (next.getTextHash().equals(textHash) && next.getFileHash().equals(relFileNameHash)) {
                itemToRemove = next;
                break;
            }
        }

        if (itemToRemove != null) getSignatures().remove(itemToRemove);
    }

    public ExcludeSignatureItem add(String textHash, String relFileNameHash) {
        ExcludeSignatureItem newItem = new ExcludeSignatureItem();
        newItem.setTextHash(textHash);
        newItem.setFileHash(relFileNameHash);
        getSignatures().add(newItem);

        return newItem;
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
