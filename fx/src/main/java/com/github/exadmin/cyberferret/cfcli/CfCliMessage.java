package com.github.exadmin.cyberferret.cfcli;

public record CfCliMessage(
        String type,
        String file,
        String folder,
        String key,
        String found,
        Long position) {

    public boolean isSignature() {
        return key != null;
    }
}
