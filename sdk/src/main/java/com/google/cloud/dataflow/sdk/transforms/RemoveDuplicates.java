/*
 * Copyright (C) 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.dataflow.sdk.transforms;

import com.google.cloud.dataflow.sdk.values.KV;
import com.google.cloud.dataflow.sdk.values.PCollection;

/**
 * {@code RemoveDuplicates<T>} takes a {@code PCollection<T>} and
 * returns a {@code PCollection<T>} that has all the elements of the
 * input but with duplicate elements removed such that each element is
 * unique within each window.
 *
 * <p>Two values of type {@code T} are compared for equality <b>not</b> by
 * regular Java {@link Object#equals}, but instead by first encoding
 * each of the elements using the {@code PCollection}'s {@code Coder}, and then
 * comparing the encoded bytes.  This admits efficient parallel
 * evaluation.
 *
 * <p>Optionally, a function may be provided that maps each element to a representative
 * value.  In this case, two elements will be considered duplicates if they have equal
 * representative values, with equality being determined as above.
 *
 * <p>By default, the {@code Coder} of the output {@code PCollection}
 * is the same as the {@code Coder} of the input {@code PCollection}.
 *
 * <p>Each output element is in the same window as its corresponding input
 * element, and has the timestamp of the end of that window.  The output
 * {@code PCollection} has the same
 * {@link com.google.cloud.dataflow.sdk.transforms.windowing.WindowFn}
 * as the input.
 *
 * <p>Does not preserve any order the input PCollection might have had.
 *
 * <p>Example of use:
 * <pre> {@code
 * PCollection<String> words = ...;
 * PCollection<String> uniqueWords =
 *     words.apply(RemoveDuplicates.<String>create());
 * } </pre>
 *
 * @param <T> the type of the elements of the input and output
 * {@code PCollection}s
 */
public class RemoveDuplicates<T> extends PTransform<PCollection<T>,
                                                    PCollection<T>> {
  /**
   * Returns a {@code RemoveDuplicates<T>} {@code PTransform}.
   *
   * @param <T> the type of the elements of the input and output
   * {@code PCollection}s
   */
  public static <T> RemoveDuplicates<T> create() {
    return new RemoveDuplicates<T>();
  }

  /**
   * Returns a {@code RemoveDuplicates<T, IdT>} {@code PTransform}.
   *
   * @param <T> the type of the elements of the input and output
   * {@code PCollection}s
   * @param <IdT> the type of the representative value used to dedup
   */
  public static <T, IdT> WithRepresentativeValues<T, IdT> withRepresentativeValueFn(
      SerializableFunction<T, IdT> fn) {
    return new WithRepresentativeValues<T, IdT>(fn);
  }

  @Override
  public PCollection<T> apply(PCollection<T> in) {
    return in
        .apply(ParDo.named("CreateIndex")
            .of(new DoFn<T, KV<T, Void>>() {
                  @Override
                  public void processElement(ProcessContext c) {
                    c.output(KV.of(c.element(), (Void) null));
                  }
                }))
        .apply(Combine.<T, Void>perKey(
            new SerializableFunction<Iterable<Void>, Void>() {
              @Override
              public Void apply(Iterable<Void> iter) {
                return null; // ignore input
                }
            }))
        .apply(Keys.<T>create());
  }

  private static class WithRepresentativeValues<T, IdT>
      extends PTransform<PCollection<T>, PCollection<T>> {
    private SerializableFunction<T, IdT> fn;

    private WithRepresentativeValues(SerializableFunction<T, IdT> fn) {
      this.fn = fn;
    }

    @Override
    public PCollection<T> apply(PCollection<T> in) {
      return in
          .apply(WithKeys.of(fn))
          .apply(Combine.<IdT, T, T>perKey(
              new Combine.BinaryCombineFn<T>() {
                @Override
                public T apply(T left, T right) {
                  return left;
                }
              }))
          .apply(Values.<T>create());
    }
  }
}
