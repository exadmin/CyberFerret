package com.github.exadmin.cyberferret.exclude;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExcludeFileModelTests {
    @Test
    public void normalAddingOfNewItems() {
        ExcludeFileModel model = new ExcludeFileModel();

        String textHash01 = "000001";
        String fileHash01 = "file01";

        model.add(textHash01, fileHash01);
        assertEquals(1, model.getSignatures().size());

        String textHash02 = "000002";
        String fileHash02 = "file02";
        model.add(textHash02, fileHash02);
        assertEquals(2, model.getSignatures().size());

        String textHash03 = "000003";
        String fileHash03 = "file03";
        model.add(textHash03, fileHash03);
        assertEquals(3, model.getSignatures().size());
    }
}
