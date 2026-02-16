package io.github.kawamuray.wasmtime;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class ImportTypeTest {
    @Test
    public void testTagImportAccessor() {
        TagType tagType = new TagType();
        ImportType imp = new ImportType(ImportType.Type.TAG, tagType, "m", "n");
        assertEquals(ImportType.Type.TAG, imp.type());
        assertEquals(tagType, imp.tag());
    }

    @Test
    public void testTagImportWrongAccessorFails() {
        TagType tagType = new TagType();
        ImportType imp = new ImportType(ImportType.Type.TAG, tagType, "m", "n");
        assertThrows(RuntimeException.class, imp::func);
    }
}
