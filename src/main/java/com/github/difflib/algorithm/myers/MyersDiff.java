/*
 * SPDX-License-Identifier: Apache-1.1
 *
 * ====================================================================
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 1999-2003 The Apache Software Foundation.
 * Copyright (c) 1996-2006 Juancarlo Añez
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
package com.github.difflib.algorithm.myers;

import com.github.difflib.algorithm.Change;
import com.github.difflib.algorithm.DiffAlgorithm;
import com.github.difflib.algorithm.DiffAlgorithmListener;
import com.github.difflib.algorithm.DiffException;
import com.github.difflib.algorithm.DifferentiationFailedException;
import com.github.difflib.patch.DeltaType;
import com.github.difflib.patch.Patch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * A clean-room implementation of Eugene Myers greedy differencing algorithm.
 */
public final class MyersDiff<T> implements DiffAlgorithm<T> {

    private final BiPredicate<T, T> DEFAULT_EQUALIZER = Object::equals;
    private final BiPredicate<T, T> equalizer;

    public MyersDiff() {
        equalizer = DEFAULT_EQUALIZER;
    }

    public MyersDiff(final BiPredicate<T, T> equalizer) {
        Objects.requireNonNull(equalizer, "equalizer must not be null");
        this.equalizer = equalizer;
    }

    /**
     * {@inheritDoc}
     *
     * Return empty diff if get the error while procession the difference.
     */
    @Override
    public List<Change> diff(final List<T> original, final List<T> revised, DiffAlgorithmListener progress) throws DiffException {
        Objects.requireNonNull(original, "original list must not be null");
        Objects.requireNonNull(revised, "revised list must not be null");

        if (progress != null) {
            progress.diffStart();
        }
        PathNode path = buildPath(original, revised, progress);
        List<Change> result = buildRevision(path, original, revised);
        if (progress != null) {
            progress.diffEnd();
        }
        return result;
    }

    /**
     * Computes the minimum diffpath that expresses de differences between the original and revised
     * sequences, according to Gene Myers differencing algorithm.
     *
     * @param orig The original sequence.
     * @param rev The revised sequence.
     * @return A minimum {@link PathNode Path} accross the differences graph.
     * @throws DifferentiationFailedException if a diff path could not be found.
     */
    private PathNode buildPath(final List<T> orig, final List<T> rev, DiffAlgorithmListener progress)
            throws DifferentiationFailedException {
        Objects.requireNonNull(orig, "original sequence is null");
        Objects.requireNonNull(rev, "revised sequence is null");

        // these are local constants
        final int N = orig.size();
        final int M = rev.size();

        final int MAX = N + M + 1;
        final int size = 1 + 2 * MAX;
        final int middle = size / 2;
        final PathNode diagonal[] = new PathNode[size];

        diagonal[middle + 1] = new PathNode(0, -1, true, true, null);
        for (int d = 0; d < MAX; d++) {
            if (progress != null) {
                progress.diffStep(d, MAX);
            }
            for (int k = -d; k <= d; k += 2) {
                final int kmiddle = middle + k;
                final int kplus = kmiddle + 1;
                final int kminus = kmiddle - 1;
                PathNode prev;
                int i;

                if ((k == -d) || (k != d && diagonal[kminus].i < diagonal[kplus].i)) {
                    i = diagonal[kplus].i;
                    prev = diagonal[kplus];
                } else {
                    i = diagonal[kminus].i + 1;
                    prev = diagonal[kminus];
                }

                diagonal[kminus] = null; // no longer used

                int j = i - k;

                PathNode node = new PathNode(i, j, false, false, prev);

                while (i < N && j < M && equalizer.test(orig.get(i), rev.get(j))) {
                    i++;
                    j++;
                }

                if (i != node.i) {
                    node = new PathNode(i, j, true, false, node);
                }

                diagonal[kmiddle] = node;

                if (i >= N && j >= M) {
                    return diagonal[kmiddle];
                }
            }
            diagonal[middle + d - 1] = null;
        }
        // According to Myers, this cannot happen
        throw new DifferentiationFailedException("could not find a diff path");
    }

    /**
     * Constructs a {@link Patch} from a difference path.
     *
     * @param path The path.
     * @param orig The original sequence.
     * @param rev The revised sequence.
     * @return A {@link Patch} script corresponding to the path.
     * @throws DifferentiationFailedException if a {@link Patch} could not be built from the given
     * path.
     */
    private List<Change> buildRevision(PathNode actualPath, List<T> orig, List<T> rev) {
        Objects.requireNonNull(actualPath, "path is null");
        Objects.requireNonNull(orig, "original sequence is null");
        Objects.requireNonNull(rev, "revised sequence is null");

        PathNode path = actualPath;
        List<Change> changes = new ArrayList<>();
        if (path.isSnake()) {
            path = path.prev;
        }
        while (path != null && path.prev != null && path.prev.j >= 0) {
            if (path.isSnake()) {
                throw new IllegalStateException("bad diffpath: found snake when looking for diff");
            }
            int i = path.i;
            int j = path.j;

            path = path.prev;
            int ianchor = path.i;
            int janchor = path.j;

            if (ianchor == i && janchor != j) {
                changes.add(new Change(DeltaType.INSERT, ianchor, i, janchor, j));
            } else if (ianchor != i && janchor == j) {
                changes.add(new Change(DeltaType.DELETE, ianchor, i, janchor, j));
            } else {
                changes.add(new Change(DeltaType.CHANGE, ianchor, i, janchor, j));
            }
//            Chunk<T> original = new Chunk<>(ianchor, copyOfRange(orig, ianchor, i));
//            Chunk<T> revised = new Chunk<>(janchor, copyOfRange(rev, janchor, j));
//            Delta<T> delta = null;
//            if (original.size() == 0 && revised.size() != 0) {
//                delta = new InsertDelta<>(original, revised);
//            } else if (original.size() > 0 && revised.size() == 0) {
//                delta = new DeleteDelta<>(original, revised);
//            } else {
//                delta = new ChangeDelta<>(original, revised);
//            }
//
//            patch.addDelta(delta);
            if (path.isSnake()) {
                path = path.prev;
            }
        }
        return changes;
    }
}
