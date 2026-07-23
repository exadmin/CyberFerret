package com.github.exadmin.cyberferret.exclude;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    public void codecShouldKeepCurrentJsonShape() {
        ExcludeFileModel model = new ExcludeFileModel();
        model.add("000001", "file01");
        model.add("000002", "file02");

        String json = ExcludeFileJsonCodec.toJson(model);

        assertTrue(json.contains("\"exclusions\" : ["));
        assertTrue(json.contains("\"t-hash\" : \"000001\""));
        assertTrue(json.contains("\"f-hash\" : \"file02\""));
    }

    @Test
    public void codecShouldReadCurrentJsonShape() {
        String json = """
                {
                  "exclusions" : [ {
                    "t-hash" : "000001",
                    "f-hash" : "file01"
                  }, {
                    "t-hash" : "000002",
                    "f-hash" : "file02"
                  } ]
                }
                """;

        ExcludeFileModel model = ExcludeFileJsonCodec.fromJson(json);

        assertEquals(2, model.getSignatures().size());
        assertEquals("000001", model.getSignatures().get(0).getTextHash());
        assertEquals("file02", model.getSignatures().get(1).getFileHash());
    }

    @Test
    public void codecShouldReturnEmptyModelForInvalidJson() {
        ExcludeFileModel model = ExcludeFileJsonCodec.fromJson("{\"unexpected\":true}");
        assertNotNull(model);
    }
}
