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
package com.github.difflib;

import com.github.difflib.algorithm.DiffAlgorithm;
import com.github.difflib.algorithm.DiffAlgorithmListener;
import com.github.difflib.algorithm.DiffException;
import com.github.difflib.algorithm.myers.MyersDiff;
import com.github.difflib.patch.Delta;
import com.github.difflib.patch.Patch;
import com.github.difflib.patch.PatchFailedException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import static java.util.stream.Collectors.joining;

/**
 * Implements the difference and patching engine
 *
 * @author <a href="dm.naumenko@gmail.com">Dmitry Naumenko</a>
 */
public final class DiffUtils {

    /**
     * Computes the difference between the original and revised list of elements with default diff algorithm
     *
     * @param original The original text. Must not be {@code null}.
     * @param revised The revised text. Must not be {@code null}.
     * @param progress progress listener
     * @return The patch describing the difference between the original and revised sequences. Never {@code null}.
     * @throws com.github.difflib.algorithm.DiffException
     */
    public static <T> Patch<T> diff(List<T> original, List<T> revised, DiffAlgorithmListener progress) throws DiffException {
        return DiffUtils.diff(original, revised, new MyersDiff<>(), progress);
    }
    
    public static <T> Patch<T> diff(List<T> original, List<T> revised) throws DiffException {
        return DiffUtils.diff(original, revised, new MyersDiff<>(), null);
    }

    /**
     * Computes the difference between the original and revised text.
     */
    public static Patch<String> diff(String originalText, String revisedText, DiffAlgorithmListener progress) throws DiffException {
        return DiffUtils.diff(Arrays.asList(originalText.split("\n")), Arrays.asList(revisedText.split("\n")), progress);
    }

    /**
     * Computes the difference between the original and revised list of elements with default diff algorithm
     *
     * @param original The original text. Must not be {@code null}.
     * @param revised The revised text. Must not be {@code null}.
     *
     * @param equalizer the equalizer object to replace the default compare algorithm (Object.equals). If {@code null}
     * the default equalizer of the default algorithm is used..
     * @return The patch describing the difference between the original and revised sequences. Never {@code null}.
     */
    public static <T> Patch<T> diff(List<T> original, List<T> revised,
            BiPredicate<T, T> equalizer) throws DiffException {
        if (equalizer != null) {
            return DiffUtils.diff(original, revised,
                    new MyersDiff<>(equalizer));
        }
        return DiffUtils.diff(original, revised, new MyersDiff<>());
    }

    /**
     * Computes the difference between the original and revised list of elements with default diff algorithm
     *
     * @param original The original text. Must not be {@code null}.
     * @param revised The revised text. Must not be {@code null}.
     * @param algorithm The diff algorithm. Must not be {@code null}.
     * @param progress The diff algorithm listener.
     * @return The patch describing the difference between the original and revised sequences. Never {@code null}.
     */
    public static <T> Patch<T> diff(List<T> original, List<T> revised,
            DiffAlgorithm<T> algorithm, DiffAlgorithmListener progress) throws DiffException {
        Objects.requireNonNull(original, "original must not be null");
        Objects.requireNonNull(revised, "revised must not be null");
        Objects.requireNonNull(algorithm, "algorithm must not be null");

        return Patch.generate(original, revised, algorithm.diff(original, revised, progress));
    }
    
    /**
     * Computes the difference between the original and revised list of elements with default diff algorithm
     *
     * @param original The original text. Must not be {@code null}.
     * @param revised The revised text. Must not be {@code null}.
     * @param algorithm The diff algorithm. Must not be {@code null}.
     * @return The patch describing the difference between the original and revised sequences. Never {@code null}.
     */
     public static <T> Patch<T> diff(List<T> original, List<T> revised,
            DiffAlgorithm<T> algorithm) throws DiffException {
         return diff(original, revised, algorithm, null);
     }

    /**
     * Computes the difference between the given texts inline. This one uses the "trick" to make out of texts lists of
     * characters, like DiffRowGenerator does and merges those changes at the end together again.
     *
     * @param original
     * @param revised
     * @return
     */
    public static Patch<String> diffInline(String original, String revised) throws DiffException {
        List<String> origList = new ArrayList<>();
        List<String> revList = new ArrayList<>();
        for (Character character : original.toCharArray()) {
            origList.add(character.toString());
        }
        for (Character character : revised.toCharArray()) {
            revList.add(character.toString());
        }
        Patch<String> patch = DiffUtils.diff(origList, revList);
        for (Delta<String> delta : patch.getDeltas()) {
            delta.getOriginal().setLines(compressLines(delta.getOriginal().getLines(), ""));
            delta.getRevised().setLines(compressLines(delta.getRevised().getLines(), ""));
        }
        return patch;
    }

    private static List<String> compressLines(List<String> lines, String delimiter) {
        if (lines.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.singletonList(lines.stream().collect(joining(delimiter)));
    }

    /**
     * Patch the original text with given patch
     *
     * @param original the original text
     * @param patch the given patch
     * @return the revised text
     * @throws PatchFailedException if can't apply patch
     */
    public static <T> List<T> patch(List<T> original, Patch<T> patch)
            throws PatchFailedException {
        return patch.applyTo(original);
    }

    /**
     * Unpatch the revised text for a given patch
     *
     * @param revised the revised text
     * @param patch the given patch
     * @return the original text
     */
    public static <T> List<T> unpatch(List<T> revised, Patch<T> patch) {
        return patch.restore(revised);
    }

    private DiffUtils() {
    }
}
