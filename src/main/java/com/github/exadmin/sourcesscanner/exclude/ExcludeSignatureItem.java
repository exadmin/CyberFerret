package com.github.exadmin.sourcesscanner.exclude;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class ExcludeSignatureItem {
    @JsonProperty("text-hash")
    private String textHash;

    @JsonProperty("file-hash")
    private String fileHash;

    public ExcludeSignatureItem() {
        textHash = "";
        fileHash = "";
    }

    public String getTextHash() {
        return textHash;
    }

    public void setTextHash(String textHash) {
        this.textHash = textHash;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ExcludeSignatureItem that = (ExcludeSignatureItem) o;
        return Objects.equals(textHash, that.textHash) && Objects.equals(fileHash, that.fileHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(textHash, fileHash);
    }
}
