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

import java.util.concurrent.atomic.AtomicReference;

import org.ec4j.lint.api.Logger.LogLevel;
import org.ec4j.lint.api.Logger.LogLevelSupplier;
import org.junit.Assert;
import org.junit.Test;

public class LoggerTest {

    private static void assertOrdinalGt(LogLevel logLevel1, LogLevel logLevel2) {
        Assert.assertTrue("Expected " + logLevel1 + ".ordinal() > " + logLevel2 + ".ordinal()",
                logLevel1.ordinal() > logLevel2.ordinal());
    }

    @Test
    public void logLevelSupplier() {
        final StringBuilder log = new StringBuilder();
        final AtomicReference<LogLevel> logLevel = new AtomicReference<>(LogLevel.TRACE);
        final LogLevelSupplier logLevelSupplier = new LogLevelSupplier() {
            public LogLevel getLogLevel() {
                return logLevel.get();
            }
        };

        final Logger logger = new Logger.AppendableLogger(logLevelSupplier, log);

        logger.trace("trace pass");
        logLevel.set(LogLevel.DEBUG);
        logger.trace("trace filtered");

        logger.debug("debug pass");
        logLevel.set(LogLevel.INFO);
        logger.debug("debug filtered");

        logger.info("info pass");
        logLevel.set(LogLevel.WARN);
        logger.info("info filtered");

        logger.warn("warn pass");
        logLevel.set(LogLevel.ERROR);
        logger.warn("warn filtered");

        logger.error("error pass");
        logLevel.set(LogLevel.NONE);
        logger.error("error filtered");

        Assert.assertEquals("trace pass\ndebug pass\ninfo pass\nwarn pass\nerror pass\n", log.toString());

    }

    @Test
    public void ordinals() {
        assertOrdinalGt(LogLevel.TRACE, LogLevel.DEBUG);
        assertOrdinalGt(LogLevel.DEBUG, LogLevel.INFO);
        assertOrdinalGt(LogLevel.INFO, LogLevel.WARN);
        assertOrdinalGt(LogLevel.WARN, LogLevel.ERROR);
        assertOrdinalGt(LogLevel.ERROR, LogLevel.NONE);
    }
}
