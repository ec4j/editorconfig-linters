/**
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

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.ec4j.core.Resource.Charsets;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class BomTest {

    @Test
    public void readStore() throws IOException {
        final Charset charset = Charsets.forName("utf-8-bom");

        final Path srcPath = Paths.get("src/test/resources/bom/utf-8-bom-good.txt");
        final Path workPath = Paths.get("target/resources/bom/utf-8-bom-good.txt");
        Files.createDirectories(workPath.getParent());
        Files.deleteIfExists(workPath);
        Files.copy(srcPath, workPath);
        final Resource doc = new Resource(workPath, workPath, charset);

        Assert.assertEquals("hello world", doc.getText());

        doc.delete(5, doc.length());
        Assert.assertEquals("hello", doc.getText());

        doc.store();

        final byte[] actual = Files.readAllBytes(workPath);
        final byte[] expected = new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'h', 'e', 'l', 'l', 'o' };
        Assert.assertArrayEquals(expected, actual);
    }
}
