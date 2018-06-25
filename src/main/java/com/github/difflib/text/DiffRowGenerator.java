/*
 * SPDX-License-Identifier: Apache-1.1
 *
 * ====================================================================
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 1999-2003 The Apache Software Foundation.
 * Copyright (c) 2010 Dmitry Naumenko (dm.naumenko@gmail.com)
 * Copyright (c) 2017-2018 Tobias Warneke and contributors to java-diff-utils
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution, if
 *    any, must include the following acknowledgement:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowledgement may appear in the software itself,
 *    if and wherever such third-party acknowledgements normally appear.
 *
 * 4. The names "The Jakarta Project", "Commons", and "Apache Software
 *    Foundation" must not be used to endorse or promote products derived
 *    from this software without prior written permission. For written
 *    permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache"
 *    nor may "Apache" appear in their names without prior written
 *    permission of the Apache Software Foundation.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 */
package com.github.difflib.text;

import com.github.difflib.DiffUtils;
import com.github.difflib.algorithm.DiffException;
import com.github.difflib.patch.ChangeDelta;
import com.github.difflib.patch.Chunk;
import com.github.difflib.patch.DeleteDelta;
import com.github.difflib.patch.Delta;
import com.github.difflib.patch.InsertDelta;
import com.github.difflib.patch.Patch;
import com.github.difflib.text.DiffRow.Tag;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class for generating DiffRows for side-by-sidy view. You can customize the way of generating. For example, show
 * inline diffs on not, ignoring white spaces or/and blank lines and so on. All parameters for generating are optional.
 * If you do not specify them, the class will use the default values.
 *
 * These values are: showInlineDiffs = false; ignoreWhiteSpaces = true; ignoreBlankLines = true; ...
 *
 * For instantiating the DiffRowGenerator you should use the its builder. Like in example  <code>
 *    DiffRowGenerator generator = new DiffRowGenerator.Builder().showInlineDiffs(true).
 *      ignoreWhiteSpaces(true).columnWidth(100).build();
 * </code>
 */
public class DiffRowGenerator {
    public static final Pattern SPLIT_BY_WORD_PATTERN = Pattern.compile("\\s+|[,.\\[\\](){}/\\\\*+\\-#]");
    
    public static final BiPredicate<String, String> IGNORE_WHITESPACE_EQUALIZER = (original, revised)
            -> original.trim().replaceAll("\\s+", " ").equals(revised.trim().replaceAll("\\s+", " "));
    
    public static final BiPredicate<String, String> DEFAULT_EQUALIZER = Object::equals;
    
    /**
     * Splitting lines by word to achieve word by word diff checking.
     */
    public static final Function<String, List<String>> SPLITTER_BY_WORD = line -> splitStringPreserveDelimiter(line, SPLIT_BY_WORD_PATTERN);

    /**
     * Splitting lines by character to achieve char by char diff checking.
     */
    public static final Function<String, List<String>> SPLITTER_BY_CHARACTER = line -> {
        List<String> list = new ArrayList<>(line.length());
        for (Character character : line.toCharArray()) {
            list.add(character.toString());
        }
        return list;
    };
    
    private final boolean showInlineDiffs;
    private final boolean ignoreWhiteSpaces;
    private final Function<Boolean, String> oldTag;
    private final Function<Boolean, String> newTag;
    private final Function<String, List<String>> inlineDiffSplitter;
    private final int columnWidth;
    private final BiPredicate<String, String> equalizer;
    private final boolean mergeOriginalRevised;
    private final boolean reportLinesUnchanged;

    /**
     * This class used for building the DiffRowGenerator.
     *
     * @author dmitry
     *
     */
    public static class Builder {

        private boolean showInlineDiffs = false;
        private boolean ignoreWhiteSpaces = false;

        private Function<Boolean, String> oldTag = f -> f ? "<span class=\"editOldInline\">" : "</span>";
        private Function<Boolean, String> newTag = f -> f ? "<span class=\"editNewInline\">" : "</span>";

        private int columnWidth = 0;
        private boolean mergeOriginalRevised = false;
        private boolean reportLinesUnchanged = false;
        private Function<String, List<String>> inlineDiffSplitter = SPLITTER_BY_CHARACTER;

        private Builder() {
        }

        /**
         * Show inline diffs in generating diff rows or not.
         *
         * @param val the value to set. Default: false.
         * @return builder with configured showInlineDiff parameter
         */
        public Builder showInlineDiffs(boolean val) {
            showInlineDiffs = val;
            return this;
        }

        /**
         * Ignore white spaces in generating diff rows or not.
         *
         * @param val the value to set. Default: true.
         * @return builder with configured ignoreWhiteSpaces parameter
         */
        public Builder ignoreWhiteSpaces(boolean val) {
            ignoreWhiteSpaces = val;
            return this;
        }

        /**
         * Give the originial old and new text lines to Diffrow without any additional processing.
         *
         * @param val the value to set. Default: false.
         * @return builder with configured reportLinesUnWrapped parameter
         */
        public Builder reportLinesUnchanged(final boolean val) {
            reportLinesUnchanged = val;
            return this;
        }

        /**
         * Generator for Old-Text-Tags.
         *
         * @param tag the tag to set. Without angle brackets. Default: span.
         * @return builder with configured ignoreBlankLines parameter
         */
        public Builder oldTag(Function<Boolean, String> generator) {
            this.oldTag = generator;
            return this;
        }

        /**
         * Generator for New-Text-Tags.
         *
         * @param generator
         * @return
         */
        public Builder newTag(Function<Boolean, String> generator) {
            this.newTag = generator;
            return this;
        }

        /**
         * Set the column with of generated lines of original and revised texts.
         *
         * @param width the width to set. Making it < 0 doesn't have any sense. Default 80. @return builder with config
         * ured ignoreBlankLines parameter
         */
        public Builder columnWidth(int width) {
            if (width >= 0) {
                columnWidth = width;
            }
            return this;
        }

        /**
         * Build the DiffRowGenerator. If some parameters is not set, the default values are used.
         *
         * @return the customized DiffRowGenerator
         */
        public DiffRowGenerator build() {
            return new DiffRowGenerator(this);
        }

        /**
         * Merge the complete result within the original text. This makes sense for one line display.
         *
         * @param mergeOriginalRevised
         * @return
         */
        public Builder mergeOriginalRevised(boolean mergeOriginalRevised) {
            this.mergeOriginalRevised = mergeOriginalRevised;
            return this;
        }

        /**
         * Per default each character is separatly processed. This variant introduces processing by word, which should
         * deliver no in word changes.
         */
        public Builder inlineDiffByWord(boolean inlineDiffByWord) {
            inlineDiffSplitter = inlineDiffByWord?SPLITTER_BY_WORD:SPLITTER_BY_CHARACTER;
            return this;
        }
        
        
        public Builder inlineDiffBySplitter(Function<String, List<String>> inlineDiffSplitter) {
            this.inlineDiffSplitter = inlineDiffSplitter;
            return this;
        }
    }

    public static Builder create() {
        return new Builder();
    }

    private DiffRowGenerator(Builder builder) {
        showInlineDiffs = builder.showInlineDiffs;
        ignoreWhiteSpaces = builder.ignoreWhiteSpaces;
        oldTag = builder.oldTag;
        newTag = builder.newTag;
        columnWidth = builder.columnWidth;
        mergeOriginalRevised = builder.mergeOriginalRevised;
        inlineDiffSplitter = builder.inlineDiffSplitter;
        equalizer = ignoreWhiteSpaces ? IGNORE_WHITESPACE_EQUALIZER : DEFAULT_EQUALIZER;
        reportLinesUnchanged = builder.reportLinesUnchanged;
        
        Objects.requireNonNull(inlineDiffSplitter);
    }

    /**
     * Get the DiffRows describing the difference between original and revised texts using the given patch. Useful for
     * displaying side-by-side diff.
     *
     * @param original the original text
     * @param revised the revised text
     * @return the DiffRows between original and revised texts
     */
    public List<DiffRow> generateDiffRows(List<String> original, List<String> revised) throws DiffException {
        return generateDiffRows(original, DiffUtils.diff(original, revised, equalizer));
    }

    private String preprocessLine(String line) {
        if (columnWidth == 0) {
            return StringUtils.normalize(line);
        } else {
            return StringUtils.wrapText(StringUtils.normalize(line), columnWidth);
        }
    }

    private DiffRow buildDiffRow(Tag type, String orgline, String newline) {
        if (reportLinesUnchanged) {
            return new DiffRow(type, orgline, newline);
        } else {
            String wrapOrg = preprocessLine(orgline);
            if (Tag.DELETE == type) {
                if (mergeOriginalRevised || showInlineDiffs) {
                    wrapOrg = oldTag.apply(true) + wrapOrg + oldTag.apply(false);
                }
            }
            String wrapNew = preprocessLine(newline);
            if (Tag.INSERT == type) {
                if (mergeOriginalRevised) {
                    wrapOrg = newTag.apply(true) + wrapNew + newTag.apply(false);
                } else if (showInlineDiffs) {
                    wrapNew = newTag.apply(true) + wrapNew + newTag.apply(false);
                }
            }
            return new DiffRow(type, wrapOrg, wrapNew);
        }
    }

    private DiffRow buildDiffRowWithoutNormalizing(Tag type, String orgline, String newline) {
        return new DiffRow(type,
                StringUtils.wrapText(orgline, columnWidth),
                StringUtils.wrapText(newline, columnWidth));
    }

    /**
     * Generates the DiffRows describing the difference between original and revised texts using the given patch. Useful
     * for displaying side-by-side diff.
     *
     * @param original the original text
     * @param revised the revised text
     * @param patch the given patch
     * @return the DiffRows between original and revised texts
     */
    public List<DiffRow> generateDiffRows(final List<String> original, Patch<String> patch) throws DiffException {
        List<DiffRow> diffRows = new ArrayList<>();
        int endPos = 0;
        final List<Delta<String>> deltaList = patch.getDeltas();
        for (int i = 0; i < deltaList.size(); i++) {
            Delta<String> delta = deltaList.get(i);
            Chunk<String> orig = delta.getOriginal();
            Chunk<String> rev = delta.getRevised();

            for (String line : original.subList(endPos, orig.getPosition())) {
                diffRows.add(buildDiffRow(Tag.EQUAL, line, line));
            }

            // Inserted DiffRow
            if (delta instanceof InsertDelta) {
                endPos = orig.last() + 1;
                for (String line : (List<String>) rev.getLines()) {
                    diffRows.add(buildDiffRow(Tag.INSERT, "", line));
                }
                continue;
            }

            // Deleted DiffRow
            if (delta instanceof DeleteDelta) {
                endPos = orig.last() + 1;
                for (String line : (List<String>) orig.getLines()) {
                    diffRows.add(buildDiffRow(Tag.DELETE, line, ""));
                }
                continue;
            }

            if (showInlineDiffs) {
                diffRows.addAll(generateInlineDiffs(delta));
            } else {
                for (int j = 0; j < Math.max(orig.size(), rev.size()); j++) {
                    diffRows.add(buildDiffRow(Tag.CHANGE,
                            orig.getLines().size() > j ? orig.getLines().get(j) : "",
                            rev.getLines().size() > j ? rev.getLines().get(j) : ""));
                }
            }
            endPos = orig.last() + 1;
        }

        // Copy the final matching chunk if any.
        for (String line : original.subList(endPos, original.size())) {
            diffRows.add(buildDiffRow(Tag.EQUAL, line, line));
        }
        return diffRows;
    }

    /**
     * Add the inline diffs for given delta
     *
     * @param delta the given delta
     */
    private List<DiffRow> generateInlineDiffs(Delta<String> delta) throws DiffException {
        List<String> orig = StringUtils.normalize(delta.getOriginal().getLines());
        List<String> rev = StringUtils.normalize(delta.getRevised().getLines());
        List<String> origList;
        List<String> revList;
        String joinedOrig = String.join("\n", orig);
        String joinedRev = String.join("\n", rev);

        origList = inlineDiffSplitter.apply(joinedOrig);
        revList = inlineDiffSplitter.apply(joinedRev);

        List<Delta<String>> inlineDeltas = DiffUtils.diff(origList, revList).getDeltas();

        Collections.reverse(inlineDeltas);
        for (Delta<String> inlineDelta : inlineDeltas) {
            Chunk<String> inlineOrig = inlineDelta.getOriginal();
            Chunk<String> inlineRev = inlineDelta.getRevised();
            if (inlineDelta instanceof DeleteDelta) {
                wrapInTag(origList, inlineOrig.getPosition(), inlineOrig
                        .getPosition()
                        + inlineOrig.size() + 1, oldTag);
            } else if (inlineDelta instanceof InsertDelta) {
                if (mergeOriginalRevised) {
                    origList.addAll(inlineOrig.getPosition(),
                            revList.subList(inlineRev.getPosition(), inlineRev.getPosition()
                                    + inlineRev.size()));
                    wrapInTag(origList, inlineOrig.getPosition(), inlineOrig.getPosition()
                            + inlineRev.size() + 1, newTag);
                } else {
                    wrapInTag(revList, inlineRev.getPosition(), inlineRev.getPosition()
                            + inlineRev.size() + 1, newTag);
                }
            } else if (inlineDelta instanceof ChangeDelta) {
                if (mergeOriginalRevised) {
                    origList.addAll(inlineOrig.getPosition() + inlineOrig.size(),
                            revList.subList(inlineRev.getPosition(), inlineRev.getPosition()
                                    + inlineRev.size()));
                    wrapInTag(origList, inlineOrig.getPosition() + inlineOrig.size(), inlineOrig.getPosition() + inlineOrig.size()
                            + inlineRev.size() + 1, newTag);
                } else {
                    wrapInTag(revList, inlineRev.getPosition(), inlineRev.getPosition()
                            + inlineRev.size() + 1, newTag);
                }
                wrapInTag(origList, inlineOrig.getPosition(), inlineOrig
                        .getPosition()
                        + inlineOrig.size() + 1, oldTag);
            }
        }
        StringBuilder origResult = new StringBuilder();
        StringBuilder revResult = new StringBuilder();
        for (String character : origList) {
            origResult.append(character);
        }
        for (String character : revList) {
            revResult.append(character);
        }

        List<String> original = Arrays.asList(origResult.toString().split("\n"));
        List<String> revised = Arrays.asList(revResult.toString().split("\n"));
        List<DiffRow> diffRows = new ArrayList<>();
        for (int j = 0; j < Math.max(original.size(), revised.size()); j++) {
            diffRows.
                    add(buildDiffRowWithoutNormalizing(Tag.CHANGE,
                            original.size() > j ? original.get(j) : "",
                            revised.size() > j ? revised.get(j) : ""));
        }
        return diffRows;
    }

    /**
     * Wrap the elements in the sequence with the given tag
     *
     * @param startPosition the position from which tag should start. The counting start from a zero.
     * @param endPosition the position before which tag should should be closed.
     * @param tag the tag name without angle brackets, just a word
     * @param cssClass the optional css class
     */
    public static void wrapInTag(List<String> sequence, int startPosition,
            int endPosition, Function<Boolean, String> generator) {
        sequence.add(startPosition, generator.apply(true));
        sequence.add(endPosition, generator.apply(false));
    }

    protected final static List<String> splitStringPreserveDelimiter(String str, Pattern SPLIT_PATTERN) {
        List<String> list = new ArrayList<>();
        if (str != null) {
            Matcher matcher = SPLIT_PATTERN.matcher(str);
            int pos = 0;
            while (matcher.find()) {
                if (pos < matcher.start()) {
                    list.add(str.substring(pos, matcher.start()));
                }
                list.add(matcher.group());
                pos = matcher.end();
            }
            if (pos < str.length()) {
                list.add(str.substring(pos));
            }
        }
        return list;
    }
}
