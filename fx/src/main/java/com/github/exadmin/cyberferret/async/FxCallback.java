package com.github.exadmin.cyberferret.async;

public interface FxCallback {
    void showMessage(FxCallbackType type, String message);

    enum FxCallbackType {
        ERROR, WARNING, INFO
    }
}
