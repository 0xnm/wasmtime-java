package io.github.kawamuray.wasmtime;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class ExternTest {
    @Test
    public void testTagExternFactoryAndAccessor() {
        Tag tag = new Tag(0);
        Extern ext = Extern.fromTag(tag);
        assertEquals(Extern.Type.TAG, ext.type());
        assertEquals(tag, ext.tag());
    }

    @Test
    public void testTagExternWrongAccessorFails() {
        Extern ext = Extern.fromTag(new Tag(0));
        assertThrows(RuntimeException.class, ext::func);
    }
}
