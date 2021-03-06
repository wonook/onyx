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
package edu.snu.onyx.runtime.executor.data.blocktransfer;

import edu.snu.onyx.common.coder.Coder;
import edu.snu.onyx.common.ir.edge.executionproperty.DataStoreProperty;
import edu.snu.onyx.runtime.common.data.KeyRange;
import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Optional;
import java.util.Spliterator;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Input stream for block transfer.
 *
 * Decodes and stores inbound data elements from other executors. Three threads are involved.
 * <ul>
 *   <li>Netty {@link io.netty.channel.EventLoopGroup} receives data from other executors and adds them
 *   by {@link #append(ByteBuf)}</li>
 *   <li>{@link BlockTransfer#inboundExecutorService} decodes {@link ByteBuf}s into elements</li>
 *   <li>User thread may use {@link java.util.Iterator} to iterate over this object for their own work.</li>
 * </ul>
 *
 * @param <T> the type of element
 */
public final class BlockInputStream<T> implements Iterable<T>, BlockStream {

  private static final Logger LOG = LoggerFactory.getLogger(BlockInputStream.class);

  private final String senderExecutorId;
  private final boolean encodePartialBlock;
  private final Optional<DataStoreProperty.Value> blockStoreValue;
  private final String blockId;
  private final String runtimeEdgeId;
  private final KeyRange keyRange;
  private Coder<T> coder;
  private ExecutorService executorService;

  private final CompletableFuture<BlockInputStream<T>> completeFuture = new CompletableFuture<>();
  private final ByteBufInputStream byteBufInputStream = new ByteBufInputStream();
  private final ClosableBlockingIterable<T> elementQueue = new ClosableBlockingIterable<>();
  private volatile boolean started = false;

  @Override
  public String toString() {
    return String.format("BlockInputStream(%s of %s%s from %s, %s, encodePartial: %b)",
        keyRange.toString(), blockId, blockStoreValue.isPresent() ? " in " + blockStoreValue.get() : "",
        senderExecutorId, runtimeEdgeId, encodePartialBlock);
  }

  /**
   * Creates a block input stream.
   *
   * @param senderExecutorId   the id of the remote executor
   * @param encodePartialBlock whether the sender should start encoding even when the whole block has not
   *                           been written yet
   * @param blockStoreValue    the block store
   * @param blockId            the block id
   * @param runtimeEdgeId      the runtime edge id
   * @param keyRange          the key range
   */
  BlockInputStream(final String senderExecutorId,
                   final boolean encodePartialBlock,
                   final Optional<DataStoreProperty.Value> blockStoreValue,
                   final String blockId,
                   final String runtimeEdgeId,
                   final KeyRange keyRange) {
    this.senderExecutorId = senderExecutorId;
    this.encodePartialBlock = encodePartialBlock;
    this.blockStoreValue = blockStoreValue;
    this.blockId = blockId;
    this.runtimeEdgeId = runtimeEdgeId;
    this.keyRange = keyRange;
  }

  /**
   * Sets {@link Coder} and {@link ExecutorService} to de-serialize bytes into block.
   *
   * @param cdr     the coder
   * @param service the executor service
   */
  void setCoderAndExecutorService(final Coder<T> cdr, final ExecutorService service) {
    this.coder = cdr;
    this.executorService = service;
  }

  /**
   * Supply {@link ByteBuf} to this stream.
   *
   * @param byteBuf the {@link ByteBuf} to supply
   */
  void append(final ByteBuf byteBuf) {
    if (byteBuf.readableBytes() > 0) {
      byteBufInputStream.byteBufQueue.put(byteBuf);
    } else {
      // ignore empty data frames
      byteBuf.release();
    }
  }

  /**
   * Mark as {@link #append(ByteBuf)} event is no longer expected.
   */
  void markAsEnded() {
    byteBufInputStream.byteBufQueue.close();
  }

  /**
   * Start decoding {@link ByteBuf}s into elements, if it has not been started.
   */
  void startDecodingThreadIfNeeded() {
    if (started) {
      return;
    }
    started = true;
    executorService.submit(() -> {
      try {
        final long startTime = System.currentTimeMillis();
        while (!byteBufInputStream.isEnded()) {
          elementQueue.add(coder.decode(byteBufInputStream));
        }
        final long endTime = System.currentTimeMillis();
        elementQueue.close();
        if (!completeFuture.isCompletedExceptionally()) {
          completeFuture.complete(this);
          // If encodePartialBlock option is on, the elapsed time is not only determined by the speed of decoder
          // but also by the rate of byte stream through the network.
          // Before investigating on low rate of decoding, check the rate of the byte stream.
          LOG.debug("Decoding task took {} ms to complete for {}", endTime - startTime, toString());
        }
      } catch (final Exception e) {
        LOG.error(String.format("An exception in decoding thread for %s", toString()), e);
        throw new RuntimeException(e);
      }
    });
  }

  /**
   * Reports exception and closes this stream.
   *
   * @param cause the cause of exception handling
   */
  void onExceptionCaught(final Throwable cause) {
    LOG.error(String.format("A channel exception closes %s", toString()), cause);
    markAsEnded();
    if (!started) {
      // There's no decoding thread to close the element queue.
      elementQueue.close();
    }
    completeFuture.completeExceptionally(cause);
  }

  @Override
  public String getRemoteExecutorId() {
    return senderExecutorId;
  }

  @Override
  public boolean isEncodePartialBlockEnabled() {
    return encodePartialBlock;
  }

  @Override
  public Optional<DataStoreProperty.Value> getBlockStore() {
    return blockStoreValue;
  }

  @Override
  public String getBlockId() {
    return blockId;
  }

  @Override
  public String getRuntimeEdgeId() {
    return runtimeEdgeId;
  }

  public KeyRange getKeyRange() {
    return keyRange;
  }

  /**
   * Returns an {@link Iterator} for this {@link Iterable}.
   * The end of this {@link Iterable} can possibly mean an error during the block transfer.
   * Consider using {@link #completeFuture} and {@link CompletableFuture#isCompletedExceptionally()} to check it.
   *
   * @return an {@link Iterator} for this {@link Iterable}
   */
  @Override
  public Iterator<T> iterator() {
    return elementQueue.iterator();
  }

  @Override
  public void forEach(final Consumer<? super T> consumer) {
    elementQueue.forEach(consumer);
  }

  @Override
  public Spliterator<T> spliterator() {
    return elementQueue.spliterator();
  }

  /**
   * Gets a {@link CompletableFuture} that completes with the block transfer being done.
   * This future is completed by one of the decoding thread. Consider using separate {@link ExecutorService} when
   * chaining a task to this future.
   *
   * @return a {@link CompletableFuture} that completes with the block transfer being done
   */
  public CompletableFuture<BlockInputStream<T>> getCompleteFuture() {
    return completeFuture;
  }

  /**
   * An {@link InputStream} implementation that reads data from a composition of {@link ByteBuf}s.
   */
  private static final class ByteBufInputStream extends InputStream {

    private final ClosableBlockingQueue<ByteBuf> byteBufQueue = new ClosableBlockingQueue<>();

    @Override
    public int read() throws IOException {
      try {
        final ByteBuf head = byteBufQueue.peek();
        if (head == null) {
          // end of stream event
          return -1;
        }
        final int b = head.readUnsignedByte();
        if (head.readableBytes() == 0) {
          // remove and release header if no longer required
          byteBufQueue.take();
          head.release();
        }
        return b;
      } catch (final InterruptedException e) {
        throw new IOException(e);
      }
    }

    @Override
    public int read(final byte[] bytes, final int baseOffset, final int maxLength) throws IOException {
      if (bytes == null) {
        throw new NullPointerException();
      }
      if (baseOffset < 0 || maxLength < 0 || maxLength > bytes.length - baseOffset) {
        throw new IndexOutOfBoundsException();
      }
      try {
        // the number of bytes that has been read so far
        int readBytes = 0;
        // the number of bytes to read
        int capacity = maxLength;
        while (capacity > 0) {
          final ByteBuf head = byteBufQueue.peek();
          if (head == null) {
            // end of stream event
            return readBytes == 0 ? -1 : readBytes;
          }
          final int toRead = Math.min(head.readableBytes(), capacity);
          head.readBytes(bytes, baseOffset + readBytes, toRead);
          if (head.readableBytes() == 0) {
            byteBufQueue.take();
            head.release();
          }
          readBytes += toRead;
          capacity -= toRead;
        }
        return readBytes;
      } catch (final InterruptedException e) {
        throw new IOException(e);
      }
    }

    @Override
    public long skip(final long n) throws IOException {
      if (n <= 0) {
        return 0;
      }
      try {
        // the number of bytes that has been skipped so far
        long skippedBytes = 0;
        // the number of bytes to skip
        long toSkip = n;
        while (toSkip > 0) {
          final ByteBuf head = byteBufQueue.peek();
          if (head == null) {
            // end of stream event
            return skippedBytes;
          }
          if (head.readableBytes() > toSkip) {
            head.skipBytes((int) toSkip);
            skippedBytes += toSkip;
            return skippedBytes;
          } else {
            // discard the whole ByteBuf
            skippedBytes += head.readableBytes();
            toSkip -= head.readableBytes();
            byteBufQueue.take();
            head.release();
          }
        }
        return skippedBytes;
      } catch (final InterruptedException e) {
        throw new IOException(e);
      }
    }

    @Override
    public int available() throws IOException {
      try {
        final ByteBuf head = byteBufQueue.peek();
        if (head == null) {
          return 0;
        } else {
          return head.readableBytes();
        }
      } catch (final InterruptedException e) {
        throw new IOException(e);
      }
    }

    /**
     * Returns whether or not the end of this stream is reached.
     *
     * @return whether or not the end of this stream is reached
     * @throws InterruptedException when interrupted while waiting
     */
    private boolean isEnded() throws InterruptedException {
      return byteBufQueue.peek() == null;
    }
  }
}
