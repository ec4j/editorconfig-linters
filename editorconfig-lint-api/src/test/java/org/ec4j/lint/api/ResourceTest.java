/*
 * Copyright (c) 2017 EditorConfig Linters
 * project contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ec4j.lint.api;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ResourceTest {

    private Resource doc;
    private Path path;

    private final Path DOCUMENT_PATH = Paths.get("target/test-classes/"
            + ResourceTest.class.getPackage().getName().replace('.', File.separatorChar) + "/document.txt");

    private final String FIXED_TEXT = "Lorem ipsum dolor sit amet,\n" + //
            "consectetur adipiscing elit.\n" + //
            "Cras luctus justo ut mi laoreet,\n" + //
            "vel tristique mi pretium.\n";

    private final String INITIAL_TEXT = "ipsum dolor sit amet,\n" + //
            "  consectetur adipiscing elit.\n" + //
            "Cras luctus justo ut mi laoreet,\n" + //
            "vel tristique mi pretium.\n";

    private final String TEXT_AFTER_DELETION = "ipsum dolor sit amet,\n" + //
            "consectetur adipiscing elit.\n" + //
            "Cras luctus justo ut mi laoreet,\n" + //
            "vel tristique mi pretium.\n";

    private final String TEXT_AFTER_INSERTION = "Lorem ipsum dolor sit amet,\n" + //
            "  consectetur adipiscing elit.\n" + //
            "Cras luctus justo ut mi laoreet,\n" + //
            "vel tristique mi pretium.\n";

    private final String TEXT_AFTER_TERMINAL_INSERTION = "Lorem ipsum dolor sit amet,\n" + //
            "  consectetur adipiscing elit.\n" + //
            "Cras luctus justo ut mi laoreet,\n" + //
            "vel tristique mi pretium.\nPellentesque...";

    @After
    public void after() throws IOException {
        doc.replace(0, doc.length(), INITIAL_TEXT);
        doc.store();
    }

    @Test
    public void asString() {

        Assert.assertEquals(INITIAL_TEXT, doc.getText());
        Assert.assertFalse(doc.changed());

    }

    @Before
    public void before() throws IOException {
        doc = load();
    }

    @Test
    public void delete() {

        Assert.assertEquals(INITIAL_TEXT, doc.getText());
        Assert.assertFalse(doc.changed());

        int offset = doc.findLineStart(2);
        doc.delete(offset, offset + 2);

        Assert.assertEquals(TEXT_AFTER_DELETION, doc.getText());
        Assert.assertTrue(doc.changed());

    }

    @Test
    public void toLocation() {
        assertLocation("", 0, 1, 1);
        assertLocation(" ", 0, 1, 1);
        assertLocation("  ", 1, 1, 2);
        assertLocation(" ", 1, 1, 2);
        assertLocation("\n", 0, 1, 1);
        assertLocation("\r\n", 0, 1, 1);
        assertLocation("\r\n", 1, 1, 2);
        assertLocation("\r\n", 2, 2, 1);
        assertLocation("\r\n ", 2, 2, 1);
    }

    private static void assertLocation(String source, int offset, int expectedLine, int expectedColumn) {
        final Location actual = new Resource(Paths.get("foo"), Paths.get("foo"), StandardCharsets.UTF_8, source)
                .findLocation(offset);
        Assert.assertEquals(new Location(expectedLine, expectedColumn), actual);
    }

    @Test
    public void findLineStartCr() {
        String text = doc.getText().replace('\n', '\r');
        doc.text.replace(0, doc.text.length(), text);
        Assert.assertEquals(0, doc.findLineStart(1));
        Assert.assertEquals(22, doc.findLineStart(2));
        Assert.assertEquals(112, doc.findLineStart(5));
    }

    @Test
    public void findLineStartCrLf() {
        String text = doc.getText().replace("\n", "\r\n");
        doc.text.replace(0, doc.text.length(), text);
        Assert.assertEquals(0, doc.findLineStart(1));
        Assert.assertEquals(23, doc.findLineStart(2));
        Assert.assertEquals(116, doc.findLineStart(5));
    }

    @Test
    public void findLineStartLf() {
        Assert.assertEquals(0, doc.findLineStart(1));
        Assert.assertEquals(22, doc.findLineStart(2));
        Assert.assertEquals(112, doc.findLineStart(5));
    }

    @Test
    public void fix() {

        Assert.assertEquals(INITIAL_TEXT, doc.getText());
        Assert.assertFalse(doc.changed());

        new Insert("Lorem ", "").perform(doc, 0);
        int offset = doc.findLineStart(2);
        new Delete(2).perform(doc, offset);

        Assert.assertEquals(FIXED_TEXT, doc.getText());
        Assert.assertTrue(doc.changed());

    }

    @Test
    public void insertReplace() {

        Assert.assertEquals(INITIAL_TEXT, doc.getText());
        Assert.assertFalse(doc.changed());

        int offset = doc.findLineStart(1);
        doc.insert(offset, "Lorem ");

        Assert.assertEquals(TEXT_AFTER_INSERTION, doc.getText());
        Assert.assertTrue(doc.changed());

        offset = doc.findLineStart(5);
        doc.insert(offset, "Pellentesque...");
        Assert.assertEquals(TEXT_AFTER_TERMINAL_INSERTION, doc.getText());
        Assert.assertTrue(doc.changed());

        /* Undo the last insertion through replace */
        offset = doc.findLineStart(5);
        doc.replace(offset, offset + "Pellentesque...".length(), "");
        Assert.assertEquals(TEXT_AFTER_INSERTION, doc.getText());
        Assert.assertTrue(doc.changed());

    }

    private Resource load() throws IOException {
        final String uuid = UUID.randomUUID().toString().replace("-", "");
        path = Paths.get("target/document-" + uuid + ".txt");
        Files.copy(DOCUMENT_PATH, path);
        return new Resource(path, path, StandardCharsets.UTF_8);
    }

    @Test
    public void openReader() throws IOException {
        Reader r = null;
        char[] cbuf = new char[1024];
        try {
            r = doc.openReader();
            StringBuilder sb = new StringBuilder();
            int len;
            while ((len = r.read(cbuf, 0, cbuf.length)) >= 0) {
                sb.append(cbuf, 0, len);
            }
            Assert.assertEquals(doc.getText(), sb.toString());
        } finally {
            if (r != null) {
                r.close();
            }
        }
    }

    @Test
    public void store() throws IOException {

        Assert.assertEquals(INITIAL_TEXT, doc.getText());
        Assert.assertFalse(doc.changed());

        int offset = doc.findLineStart(2);
        doc.delete(offset, offset + 2);

        Assert.assertEquals(TEXT_AFTER_DELETION, doc.getText());
        Assert.assertTrue(doc.changed());

        doc.store();

        Resource reloadedDoc = new Resource(path, path, StandardCharsets.UTF_8);
        Assert.assertEquals(TEXT_AFTER_DELETION, reloadedDoc.getText());
        Assert.assertFalse(reloadedDoc.changed());

    }

}
