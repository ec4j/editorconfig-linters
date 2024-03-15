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

/**
 * A deletion inside a file.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class Delete implements Edit {

    private final int length;

    public Delete(int length) {
        super();
        this.length = length;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Delete other = (Delete) obj;
        if (length != other.length)
            return false;
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public void perform(Resource document, int offset) {
        document.delete(offset, offset + length);
    }

    /** {@inheritDoc} */
    @Override
    public String getMessage() {
        return "Delete " + length + " " + (length == 1 ? "character" : "characters");
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + length;
        return result;
    }

}
