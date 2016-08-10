/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.emc.pravega.common.util;

import java.nio.ByteBuffer;

import lombok.Getter;

/**
 * Convenience class wrapping byteBuffer to provide a circular buffer. This works by maintaining
 * two byte buffers backed by the same array. The position of the readBuffer corresponds to the
 * point up to which data has been read. The position of the writeBuffer corresponds to the point up
 * to
 * which data has been written. Each buffer's limit is either the end or the buffer or the position
 * of the other, depending on where the data has wrapped.
 */
public class CircularBuffer {

    private final ByteBuffer readBuffer;
    private final ByteBuffer fillBuffer;
    @Getter
    private final int capacity;

    public CircularBuffer(int capacity) {
        this.capacity = capacity;
        byte[] buffer = new byte[capacity];
        readBuffer = ByteBuffer.wrap(buffer);
        fillBuffer = ByteBuffer.wrap(buffer);
        clear();
    }

    public void clear() {
        readBuffer.position(0).limit(0);
        fillBuffer.position(0).limit(fillBuffer.capacity());
    }

    /**
     * @return the number of bytes put into toFill
     */
    public int read(ByteBuffer toFill) {
        int origionalPos = toFill.position();
        while (dataAvailable() > 0 && toFill.hasRemaining()) {
            readHelper(toFill);
        }
        return toFill.position() - origionalPos;
    }

    private void readHelper(ByteBuffer toFill) {
        int readLimit = readBuffer.limit();
        int toRead = Math.min(toFill.remaining(), readBuffer.remaining());
        readBuffer.limit(readBuffer.position() + toRead);
        toFill.put(readBuffer);
        readBuffer.limit(readLimit);
        if (readBuffer.position() == capacity) {
            readBuffer.position(0);
            readBuffer.limit(fillBuffer.position());
            fillBuffer.limit(capacity);
        }
        if (fillBuffer.position() < readBuffer.position()) {
            fillBuffer.limit(readBuffer.position());
        }
    }

    /**
     * @return the number of bytes read from fillFrom
     */
    public int fill(ByteBuffer fillFrom) {
        int origionalPos = fillFrom.position();
        while (capacityAvailable() > 0 && fillFrom.hasRemaining()) {
            fillHelper(fillFrom);
        }
        return fillFrom.position() - origionalPos;
    }

    private void fillHelper(ByteBuffer fillFrom) {
        int fillLimit = fillBuffer.limit();
        int toAdd = Math.min(fillFrom.remaining(), fillBuffer.remaining());
        fillBuffer.limit(fillBuffer.position() + toAdd);

        int limit = fillFrom.limit();
        fillFrom.limit(fillFrom.position() + toAdd);
        fillBuffer.put(fillFrom);
        fillFrom.limit(limit);
        fillBuffer.limit(fillLimit);

        if (fillBuffer.position() == capacity) {
            fillBuffer.position(0);
            fillBuffer.limit(readBuffer.position());
            readBuffer.limit(capacity);
        }
        if (readBuffer.position() < fillBuffer.position()) {
            readBuffer.limit(fillBuffer.position());
        }
    }

    /**
     * @return the number of bytes that can be read
     */
    public int dataAvailable() {
        if (readBuffer.position() < fillBuffer.position()) {
            return readBuffer.remaining();
        } else if (readBuffer.position() > fillBuffer.position()) {
            return capacity - fillBuffer.remaining();
        } else {
            if (readBuffer.hasRemaining()) {
                return readBuffer.remaining() + fillBuffer.position();
            } else {
                return 0;
            }
        }
    }

    public int capacityAvailable() {
        if (fillBuffer.position() < readBuffer.position()) {
            return fillBuffer.remaining();
        } else if (fillBuffer.position() > readBuffer.position()) {
            return capacity - readBuffer.remaining();
        } else {
            if (fillBuffer.hasRemaining()) {
                return fillBuffer.remaining() + readBuffer.position();
            } else {
                return 0;
            }
        }
    }
}