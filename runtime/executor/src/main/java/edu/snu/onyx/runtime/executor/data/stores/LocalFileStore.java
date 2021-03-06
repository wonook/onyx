/*
 * Copyright (C) 2017 Seoul National University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.snu.onyx.runtime.executor.data.stores;

import edu.snu.onyx.common.exception.BlockFetchException;
import edu.snu.onyx.conf.JobConf;
import edu.snu.onyx.common.coder.Coder;
import edu.snu.onyx.runtime.common.data.KeyRange;
import edu.snu.onyx.runtime.executor.data.*;
import edu.snu.onyx.runtime.executor.data.metadata.LocalFileMetadata;
import edu.snu.onyx.runtime.executor.data.block.FileBlock;
import org.apache.reef.tang.annotations.Parameter;

import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;
import java.io.*;
import java.util.List;

/**
 * Stores blocks in local files.
 */
@ThreadSafe
public final class LocalFileStore extends LocalBlockStore implements FileStore {
  private final String fileDirectory;

  @Inject
  private LocalFileStore(@Parameter(JobConf.FileDirectory.class) final String fileDirectory,
                         final CoderManager coderManager) {
    super(coderManager);
    this.fileDirectory = fileDirectory;
    new File(fileDirectory).mkdirs();
  }

  /**
   * Creates a new block.
   *
   * @param blockId the ID of the block to create.
   * @see BlockStore#createBlock(String)
   */
  @Override
  public void createBlock(final String blockId) {
    removeBlock(blockId);

    final Coder coder = getCoderFromWorker(blockId);
    final LocalFileMetadata metadata = new LocalFileMetadata(false);

    final FileBlock block =
        new FileBlock(coder, DataUtil.blockIdToFilePath(blockId, fileDirectory), metadata);
    getBlockMap().put(blockId, block);
  }

  /**
   * Removes the file that the target block is stored.
   *
   * @param blockId of the block.
   * @return whether the block exists or not.
   */
  @Override
  public Boolean removeBlock(final String blockId) throws BlockFetchException {
    final FileBlock fileBlock = (FileBlock) getBlockMap().remove(blockId);
    if (fileBlock == null) {
      return false;
    }
    try {
      fileBlock.deleteFile();
    } catch (final IOException e) {
      throw new BlockFetchException(e);
    }
    return true;
  }

  /**
   * @see FileStore#getFileAreas(String, KeyRange)
   */
  @Override
  public List<FileArea> getFileAreas(final String blockId,
                                     final KeyRange keyRange) {
    try {
      final FileBlock block = (FileBlock) getBlockMap().get(blockId);
      if (block == null) {
        throw new IOException(String.format("%s does not exists", blockId));
      }
      return block.asFileAreas(keyRange);
    } catch (final IOException retrievalException) {
      throw new BlockFetchException(retrievalException);
    }
  }
}
