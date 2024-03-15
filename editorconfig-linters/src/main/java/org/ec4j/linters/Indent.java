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
package org.ec4j.linters;

import java.util.BitSet;

import org.ec4j.lint.api.LintUtils;

/**
 * An indent occurrence within a file characterized by {@link #lineNumber} and {@link #size}.
 */
public class Indent {

    /**
     * An {@link Indent} usable at the beginning of a typical XML file.
     */
    public static final Indent START = new Indent(1, 0, -1, 0);

    public static Indent of(int lineNumber, int size, BitSet edits, int editsLength) {
        if (editsLength == 0) {
            return new Indent(lineNumber, size, -1, 0);
        } else {
            int badStartColumn = 1;
            int badLength = editsLength;
            for (int i = editsLength - 1; i >= 0; i--) {
                if (!edits.get(i)) {
                    badLength--;
                    badStartColumn++;
                } else {
                    break;
                }
            }
            return new Indent(lineNumber, size, badStartColumn, badLength);
        }
    }

    /**
     * The number of elements that need to be replaced or removed.
     */
    private final int badLength;

    /**
     * The 1-based column number where the first bad indent character is located. Values lower than {@code 1}
     * mean that there are no elements to be replaced or removed.
     */
    private final int badStartColumn;

    /**
     * The line number where this {@link Indent} occurs. The first line number in a file is {@code 1}.
     */
    private final int lineNumber;

    /** The number of spaces in this {@link Indent}. */
    private final int size;

    Indent(int lineNumber, int size, int badStartColumn, int badLength) {
        super();
        this.lineNumber = LintUtils.validateLineOrColumnNumber(lineNumber, "line");
        this.size = LintUtils.validateLength(size, "size");
        this.badStartColumn = badStartColumn;
        this.badLength = LintUtils.validateLength(badLength, "badLenght");
    }

    @Override
    public String toString() {
        return "Indent [lineNumber=" + lineNumber + ", size=" + size + ", badLength=" + badLength
                + ", badStartColumn=" + badStartColumn + "]";
    }

    public int getBadLength() {
        return badLength;
    }

    public int getBadStartColumn() {
        assert badLength == 0 || badStartColumn >= 1 : "badStartColumn must be greater or equal to 1 unless badLength is 0";
        return badStartColumn;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public int getSize() {
        return size;
    }

}
