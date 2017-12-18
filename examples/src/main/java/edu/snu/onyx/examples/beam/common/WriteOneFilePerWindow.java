/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.snu.onyx.examples.beam.common;

import static com.google.common.base.Verify.verifyNotNull;

import javax.annotation.Nullable;

import org.apache.beam.sdk.io.FileBasedSink;
import org.apache.beam.sdk.io.FileBasedSink.FilenamePolicy;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.io.fs.ResolveOptions.StandardResolveOptions;
import org.apache.beam.sdk.io.fs.ResourceId;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.windowing.BoundedWindow;
import org.apache.beam.sdk.transforms.windowing.IntervalWindow;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PDone;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

/**
 * A {@link DoFn} that writes elements to files with names deterministically derived from the lower
 * and upper bounds of their key (an {@link IntervalWindow}).
 *
 * <p>This is test utility code, not for end-users, so examples can be focused on their primary
 * lessons.
 */
public final class WriteOneFilePerWindow extends PTransform<PCollection<String>, PDone> {
  private static final DateTimeFormatter FORMATTER = ISODateTimeFormat.hourMinute();
  private String filenamePrefix;
  @Nullable
  private Integer numShards;

  public WriteOneFilePerWindow(final String filenamePrefix, final Integer numShards) {
    this.filenamePrefix = filenamePrefix;
    this.numShards = numShards;
  }

  @Override
  public PDone expand(final PCollection<String> input) {
    // filenamePrefix may contain a directory and a filename component. Pull out only the filename
    // component from that path for the PerWindowFiles.
    String prefix = "";
    final ResourceId resource = FileBasedSink.convertToFileResourceIfPossible(filenamePrefix);
    if (!resource.isDirectory()) {
      prefix = verifyNotNull(
          resource.getFilename(),
          "A non-directory resource should have a non-null filename: %s",
          resource);
    }

    TextIO.Write write = TextIO.write()
        .to(resource.getCurrentDirectory())
        .withFilenamePolicy(new PerWindowFiles(prefix))
        .withWindowedWrites();
    if (numShards != null) {
      write = write.withNumShards(numShards);
    }
    return input.apply(write);
  }

  /**
   * A {@link FilenamePolicy} produces a base file name for a write based on metadata about the data
   * being written. This always includes the shard number and the total number of shards. For
   * windowed writes, it also includes the window and pane index (a sequence number assigned to each
   * trigger firing).
   */
  public static final class PerWindowFiles extends FilenamePolicy {

    private final String prefix;

    public PerWindowFiles(final String prefix) {
      this.prefix = prefix;
    }

    public String filenamePrefixForWindow(final IntervalWindow window,
                                          final WindowedContext context,
                                          final String extension) {
      return String.format(
          "%s-%s-%s-%s-of-%s%s",
          prefix, FORMATTER.print(window.start()), FORMATTER.print(window.end()),
          context.getShardNumber(), context.getNumShards(), extension);
    }

    @Override
    public ResourceId windowedFilename(
        final ResourceId outputDirectory, final WindowedContext context, final String extension) {
      final BoundedWindow window = context.getWindow();
      final String filename = (window instanceof IntervalWindow)
          ? filenamePrefixForWindow((IntervalWindow) window, context, extension) : prefix;

      return outputDirectory.resolve(filename, StandardResolveOptions.RESOLVE_FILE);
    }

    @Override
    public ResourceId unwindowedFilename(
        final ResourceId outputDirectory, final Context context, final String extension) {
      throw new UnsupportedOperationException("Unsupported.");
    }
  }
}