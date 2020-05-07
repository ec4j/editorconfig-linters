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
package org.ec4j.linters;

import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.ec4j.core.ResourceProperties;
import org.ec4j.core.model.PropertyType;
import org.ec4j.core.model.PropertyType.IndentStyleValue;
import org.ec4j.lint.api.Delete;
import org.ec4j.lint.api.Edit;
import org.ec4j.lint.api.Insert;
import org.ec4j.lint.api.Linter;
import org.ec4j.lint.api.Location;
import org.ec4j.lint.api.Logger;
import org.ec4j.lint.api.Replace;
import org.ec4j.lint.api.Resource;
import org.ec4j.lint.api.Violation;
import org.ec4j.lint.api.ViolationHandler;
import org.ec4j.linters.xml.XmlLexer;
import org.ec4j.linters.xml.XmlParser;
import org.ec4j.linters.xml.XmlParser.ChardataContext;
import org.ec4j.linters.xml.XmlParser.CommentContext;
import org.ec4j.linters.xml.XmlParser.ContentContext;
import org.ec4j.linters.xml.XmlParser.DocumentContext;
import org.ec4j.linters.xml.XmlParser.ElementContext;
import org.ec4j.linters.xml.XmlParser.EndNameContext;
import org.ec4j.linters.xml.XmlParser.MiscContext;
import org.ec4j.linters.xml.XmlParser.ProcessingInstructionContext;
import org.ec4j.linters.xml.XmlParser.PrologContext;
import org.ec4j.linters.xml.XmlParser.ReferenceContext;
import org.ec4j.linters.xml.XmlParser.SeaWsContext;
import org.ec4j.linters.xml.XmlParser.StartEndNameContext;
import org.ec4j.linters.xml.XmlParser.StartNameContext;
import org.ec4j.linters.xml.XmlParser.TextContext;
import org.ec4j.linters.xml.XmlParserListener;

/**
 * A {@link Linter} specialized for XML files.
 * <p>
 * Supports the following {@code .editorconfig} properties:
 * <ul>
 * <li>{@code indent_style}</li>
 * <li>{@code indent_size}</li>
 * </ul>
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 * @since 0.0.1
 */
public class XmlLinter implements Linter {

    /**
     * An {@link XmlParserListener} implementation that detects formatting violations and reports them to the supplied
     * {@link #violationHandler}.
     *
     */
    static class FormatParserListener implements XmlParserListener {

        /**
         * An entry that can be stored on a stack
         */
        private static class ElementEntry {
            private final String elementName;

            private final Indent expectedIndent;

            private final Indent foundIndent;

            ElementEntry(String elementName, Indent foundIndent) {
                super();
                this.elementName = elementName;
                this.foundIndent = foundIndent;
                this.expectedIndent = foundIndent;
            }

            ElementEntry(String elementName, Indent foundIndent,
                    Indent expectedIndent) {
                super();
                this.elementName = elementName;
                this.foundIndent = foundIndent;
                this.expectedIndent = expectedIndent;
            }

            @Override
            public String toString() {
                return "<" + elementName + "> " + foundIndent;
            }
        }

        static class LastTerminalFinder extends AbstractParseTreeVisitor<Object> {

            private TerminalNode lastTerminal;

            public TerminalNode getLastTerminal() {
                return lastTerminal;
            }

            @Override
            public Object visitTerminal(TerminalNode node) {

                lastTerminal = node;
                return null;
            }

        }

        private final StringBuilder charBuffer = new StringBuilder();

        private int charBufferEndLineNumber;

        /** Helps to detect the initial whitespace in the document */
        private Boolean charBufferStartsAtStartOfDocument;

        /** The file being checked */
        private final Resource file;

        /** The indentation character, such as tab or space */
        private final char indentChar;

        /** The number of {@link #indentChar}s to use for one level of indentation */
        private final int indentSize;

        private final IndentStyleValue indentStyle;

        private Indent lastIndent = Indent.START;

        private final Linter linter;

        /** The element stack */
        private Deque<FormatParserListener.ElementEntry> stack = new java.util.ArrayDeque<FormatParserListener.ElementEntry>();

        /** The {@link ViolationHandler} for reporting found violations */
        private final ViolationHandler violationHandler;

        FormatParserListener(Linter linter, Resource file, IndentStyleValue indentStyle, int indetSize,
                ViolationHandler violationHandler) {
            super();
            this.linter = linter;
            this.file = file;
            this.indentStyle = indentStyle;
            this.indentChar = indentStyle.getIndentChar();
            this.indentSize = indetSize;
            this.violationHandler = violationHandler;
        }

        private void adjustIndentLength(Token start, Indent foundIndent, final int expectedIndent,
                int columnAdjustment) {
            /* proper indentation characters, but bad length */
            final int opValue = expectedIndent - foundIndent.getSize();

            final Edit fix;
            final int len = Math.abs(opValue);
            int col = start.getCharPositionInLine() //
                    + 1 // because getCharPositionInLine() is zero based
                    - columnAdjustment // because we want the column of '<' while we are on the first char of the name
            ;
            if (opValue > 0) {
                fix = Insert.repeat(indentChar, len);
            } else {
                fix = new Delete(len);
                col -= len;
            }
            final Location loc = new Location(start.getLine(), col);

            final Violation violation = new Violation(file, loc, fix, linter, PropertyType.indent_style.getName(),
                    indentStyle.name(), PropertyType.indent_size.getName(), String.valueOf(indentSize));
            violationHandler.handle(violation);
        }

        private void consumeText(ParserRuleContext ctx) {
            if (charBufferStartsAtStartOfDocument == null) {
                Token start = ctx.getStart();
                charBufferStartsAtStartOfDocument = start.getLine() == 1 && start.getCharPositionInLine() == 0;
            }
            charBuffer.append(ctx.getText());
            charBufferEndLineNumber = ctx.getStop().getLine();
        }

        @Override
        public void enterAttribute(XmlParser.AttributeContext ctx) {
        }

        @Override
        public void enterChardata(ChardataContext ctx) {
        }

        @Override
        public void enterComment(CommentContext ctx) {
        }

        @Override
        public void enterContent(ContentContext ctx) {
        }

        @Override
        public void enterDocument(DocumentContext ctx) {

        }

        @Override
        public void enterElement(ElementContext ctx) {
        }

        @Override
        public void enterEndName(EndNameContext ctx) {
            flushWs();
            final String qName = ctx.getText();
            if (stack.isEmpty()) {
                final Token start = ctx.getStart();
                throw new IllegalStateException("Stack must not be empty when closing the element " + qName
                        + " around line " + start.getLine() + " and column " + (start.getCharPositionInLine() + 1));
            }
            final ElementEntry startEntry = stack.pop();
            final int expectedIndent = startEntry.expectedIndent.getSize();
            if (lastIndent.getLineNumber() != startEntry.foundIndent.getLineNumber()) {
                if (lastIndent.getBadLength() > 0) {
                    /* the length of the indent is correct, but some indent chars need to get replaced */
                    replaceIndentCharsAndAdjustIndentLength(expectedIndent, lastIndent, ctx.getStart());
                } else if (lastIndent.getSize() != expectedIndent) {
                    /*
                     * diff should be zero unless we are on the same line as start element
                     */
                    /* the available indent chars are correct, but the lenght of indent needs to be adjusted */
                    adjustIndentLength(ctx.getStart(), lastIndent, expectedIndent, 2);
                }
            }
        }

        @Override
        public void enterEveryRule(ParserRuleContext ctx) {
        }

        @Override
        public void enterMisc(MiscContext ctx) {
        }

        @Override
        public void enterProcessingInstruction(ProcessingInstructionContext ctx) {
        }

        @Override
        public void enterProlog(PrologContext ctx) {
            flushWs();
        }

        @Override
        public void enterReference(ReferenceContext ctx) {
        }

        @Override
        public void enterSeaWs(SeaWsContext ctx) {
        }

        @Override
        public void enterStartEndName(StartEndNameContext ctx) {
            enterStartNameInternal(ctx, false);
        }

        @Override
        public void enterStartName(StartNameContext ctx) {
            enterStartNameInternal(ctx, true);
        }

        void enterStartNameInternal(ParserRuleContext ctx, boolean pushToStack) {
            flushWs();
            final String qName = ctx.getText();
            ElementEntry currentEntry = new ElementEntry(qName, lastIndent);
            if (!stack.isEmpty()) {
                final ElementEntry parentEntry = stack.peek();
                /*
                 * note that we use parentEntry.expectedIndent rather than parentEntry.foundIndent this is to make the
                 * messages more useful
                 */
                final int indentDiff = currentEntry.foundIndent.getSize() - parentEntry.expectedIndent.getSize();
                final int expectedIndent = parentEntry.expectedIndent.getSize() + indentSize;
                final int badLength = lastIndent.getBadLength();
                if (indentDiff == 0 && currentEntry.foundIndent.getLineNumber() == parentEntry.foundIndent.getLineNumber()) {
                    /*
                     * Zero indentDiff acceptable only if current is on the same line as parent. This is OK, so do
                     * nothing
                     */
                } else if (badLength > 0) {
                    /* some indent chars need to get replaced and length of the indent might not be correct */
                    replaceIndentCharsAndAdjustIndentLength(expectedIndent, lastIndent, ctx.getStart());

                    if (pushToStack && indentDiff != indentSize) {
                        /* reset the expected indent in the entry we'll push */
                        currentEntry = new ElementEntry(qName, lastIndent,
                                new Indent(lastIndent.getLineNumber(), expectedIndent, -1, 0));
                    }
                } else if (indentDiff != indentSize) {
                    adjustIndentLength(ctx.getStart(), currentEntry.foundIndent, expectedIndent, 1);

                    if (pushToStack) {
                        /* reset the expected indent in the entry we'll push */
                        currentEntry = new ElementEntry(qName, lastIndent,
                                new Indent(lastIndent.getLineNumber(), expectedIndent, -1, 0));
                    }
                }
            }
            if (pushToStack) {
                stack.push(currentEntry);
            }
        }

        @Override
        public void enterText(TextContext ctx) {
        }

        @Override
        public void exitAttribute(XmlParser.AttributeContext ctx) {
        }

        @Override
        public void exitChardata(ChardataContext ctx) {
        }

        @Override
        public void exitComment(CommentContext ctx) {
            flushWs();
        }

        @Override
        public void exitContent(ContentContext ctx) {
        }

        @Override
        public void exitDocument(DocumentContext ctx) {
        }

        @Override
        public void exitElement(ElementContext ctx) {
        }

        @Override
        public void exitEndName(EndNameContext ctx) {
        }

        @Override
        public void exitEveryRule(ParserRuleContext ctx) {
        }

        @Override
        public void exitMisc(MiscContext ctx) {
        }

        @Override
        public void exitProcessingInstruction(ProcessingInstructionContext ctx) {
            flushWs();
        }

        @Override
        public void exitProlog(PrologContext ctx) {
            flushWs();
        }

        @Override
        public void exitReference(ReferenceContext ctx) {
        }

        @Override
        public void exitSeaWs(SeaWsContext ctx) {
            consumeText(ctx);
        }

        @Override
        public void exitStartEndName(StartEndNameContext ctx) {
        }

        @Override
        public void exitStartName(StartNameContext ctx) {
        }

        @Override
        public void exitText(TextContext ctx) {
            consumeText(ctx);
        }

        /**
         * Sets {@link lastIndent} based on {@link #charBuffer} and resets {@link #charBuffer}.
         */
        private void flushWs() {
            int indentLength = 0;
            final int len = charBuffer.length();
            BitSet edits = null;
            int editsLength = 0;
            /*
             * Count characters from end of ignorable whitespace to first end of line we hit
             */
            for (int i = len - 1; i >= 0; i--) {
                char ch = charBuffer.charAt(i);
                switch (ch) {
                    case '\n':
                    case '\r':
                        lastIndent = Indent.of(charBufferEndLineNumber, indentLength, edits, editsLength);
                        charBuffer.setLength(0);
                        charBufferStartsAtStartOfDocument = null;
                        return;
                    case ' ':
                    case '\t':
                        if (ch != indentChar) {
                            if (edits == null) {
                                edits = new BitSet();
                            }
                            edits.set(editsLength);
                        }
                        if (edits != null) {
                            editsLength++;
                        }
                        indentLength++;
                        break;
                    default:
                        /*
                         * No end of line foundIndent in the trailing whitespace. Leave the foundIndent from previous
                         * ignorable whitespace unchanged
                         */
                        charBuffer.setLength(0);
                        charBufferStartsAtStartOfDocument = null;
                        return;
                }
            }
            if (charBufferStartsAtStartOfDocument != null && charBufferStartsAtStartOfDocument.booleanValue()) {
                lastIndent = Indent.of(charBufferEndLineNumber, indentLength, edits, editsLength);
            }
            charBuffer.setLength(0);
            charBufferStartsAtStartOfDocument = null;
        }

        private void replaceIndentCharsAndAdjustIndentLength(int expectedIndent, Indent lastIndent, Token start) {
            int badLength = lastIndent.getBadLength();
            if (badLength == 0) {
                throw new IllegalStateException(
                        "Avoid calling replaceIndentCharsAndAdjustIndentLength() when lastIndent.getBadLength() == 0");
            }

            /* diff stores how much is missing to the expected size; or too much - then diff will be negative */
            final int diff = expectedIndent - lastIndent.getSize();

            final Edit fix;
            final int column;
            final int deletionLength = -diff;
            if (deletionLength >= 0 && deletionLength >= badLength) {
                /* We need to shorten and a deletion will be enough */
                fix = new Delete(deletionLength);
                /*
                 * Let's figure out where the deletion should start;
                 * make sure that the deletion ends in the indent region
                 */
                final int overflow = lastIndent.getBadStartColumn() - 1 + deletionLength - lastIndent.getSize();
                if (overflow > 0) {
                    column = lastIndent.getBadStartColumn() - overflow;
                    assert column >= 1 : "column must be >= 1";
                } else {
                    column = lastIndent.getBadStartColumn();
                }
            } else {
                /* We need to add some chars and replace the whole bad region */
                fix = Replace.indent(badLength, indentStyle, badLength + diff);
                column = lastIndent.getBadStartColumn();
            }
            final Location loc = new Location(start.getLine(), column);
            final Violation violation = new Violation(file, loc, fix, linter, PropertyType.indent_style.getName(),
                    indentStyle.name(), PropertyType.indent_size.getName(), String.valueOf(indentSize));
            violationHandler.handle(violation);
        }

        @Override
        public void visitErrorNode(ErrorNode node) {
        }

        @Override
        public void visitTerminal(TerminalNode node) {
        }

    }

    private static final List<String> DEFAULT_EXCLUDES = Collections.emptyList();

    private static final List<String> DEFAULT_INCLUDES = Collections
            .unmodifiableList(Arrays.asList("**/*.xml", "**/*.xsl"));

    @Override
    public List<String> getDefaultExcludes() {
        return DEFAULT_EXCLUDES;
    }

    @Override
    public List<String> getDefaultIncludes() {
        return DEFAULT_INCLUDES;
    }

    @Override
    public void process(Resource resource, ResourceProperties properties, ViolationHandler violationHandler)
            throws IOException {
        final Logger log = violationHandler.getLogger();
        final IndentStyleValue indentStyle = properties.getValue(PropertyType.indent_style, null, false);
        final Integer indentSize = properties.getValue(PropertyType.indent_size, null, false);
        if (log.isTraceEnabled()) {
            log.trace("Checking indent_style value '{}' in {}", indentStyle, resource);
            log.trace("Checking indent_size value '{}' in {}", indentSize, resource);
        }
        if (indentStyle == null && indentSize == null) {
            /* nothing to do */
        } else if (indentStyle != null && indentSize != null) {
            try (Reader in = resource.openReader()) {
                XmlParser parser = new XmlParser(
                        new CommonTokenStream(new XmlLexer(CharStreams.fromReader(in, resource.toString()))));

                ParseTree rootContext = parser.document();
                ParseTreeWalker walker = new ParseTreeWalker();
                walker.walk(
                        new FormatParserListener(this, resource, indentStyle, indentSize.intValue(), violationHandler),
                        rootContext);
            }
        } else {
            log.warn(this.getClass().getName() + " expects both indent_style and indent_size to be set for file '{}'",
                    resource);
        }
    }

}
