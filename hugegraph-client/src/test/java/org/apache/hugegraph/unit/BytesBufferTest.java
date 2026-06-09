/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.apache.hugegraph.unit;

import java.util.Date;
import java.util.UUID;

import org.apache.hugegraph.serializer.direct.struct.HugeType;
import org.apache.hugegraph.serializer.direct.util.BytesBuffer;
import org.apache.hugegraph.serializer.direct.util.IdGenerator;
import org.apache.hugegraph.structure.constant.DataType;
import org.junit.Assert;
import org.junit.Test;

public class BytesBufferTest {

    @Test
    public void testPrimitiveRoundTripAndAutoResize() {
        BytesBuffer buffer = BytesBuffer.allocate(2);
        buffer.writeBoolean(true)
              .writeChar('H')
              .writeShort((short) 7)
              .writeInt(1024)
              .writeLong(9_999_999_999L)
              .writeFloat(1.5F)
              .writeDouble(2.25D)
              .write(new byte[]{3, 4});

        Assert.assertTrue(buffer.asByteBuffer().capacity() > 2);
        buffer.forReadWritten();

        Assert.assertTrue(buffer.readBoolean());
        Assert.assertEquals('H', buffer.readChar());
        Assert.assertEquals(7, buffer.readShort());
        Assert.assertEquals(1024, buffer.readInt());
        Assert.assertEquals(9_999_999_999L, buffer.readLong());
        Assert.assertEquals(1.5F, buffer.readFloat(), 0.0F);
        Assert.assertEquals(2.25D, buffer.readDouble(), 0.0D);
        Assert.assertArrayEquals(new byte[]{3, 4}, buffer.read(2));
        Assert.assertEquals(0, buffer.remaining());
    }

    @Test
    public void testBytesStringsAndEndingRoundTrip() {
        BytesBuffer buffer = BytesBuffer.allocate(4);
        buffer.writeBytes(new byte[]{1, 2, 3})
              .writeBigBytes(new byte[]{4, 5, 6, 7})
              .writeString("hello")
              .writeStringWithEnding("marko")
              .writeStringToRemaining("tail");

        buffer.forReadWritten();

        Assert.assertArrayEquals(new byte[]{1, 2, 3}, buffer.readBytes());
        Assert.assertArrayEquals(new byte[]{4, 5, 6, 7}, buffer.readBigBytes());
        Assert.assertEquals("hello", buffer.readString());
        Assert.assertEquals("marko", buffer.readStringWithEnding());
        Assert.assertEquals("tail", buffer.readStringFromRemaining());
    }

    @Test
    public void testUnsignedVarIntAndVarLongRoundTrip() {
        int[] ints = new int[]{0, 1, 127, 128, 16_384, Integer.MAX_VALUE, -1};
        long[] longs = new long[]{0L, 1L, 127L, 128L, 16_384L,
                                  Long.MAX_VALUE, -1L};
        BytesBuffer buffer = BytesBuffer.allocate(4);
        buffer.writeUInt8(255)
              .writeUInt16(65_535)
              .writeUInt32(4_294_967_295L);
        for (int value : ints) {
            buffer.writeVInt(value);
        }
        for (long value : longs) {
            buffer.writeVLong(value);
        }

        buffer.forReadWritten();

        Assert.assertEquals(255, buffer.readUInt8());
        Assert.assertEquals(65_535, buffer.readUInt16());
        Assert.assertEquals(4_294_967_295L, buffer.readUInt32());
        for (int value : ints) {
            Assert.assertEquals(value, buffer.readVInt());
        }
        for (long value : longs) {
            Assert.assertEquals(value, buffer.readVLong());
        }
    }

    @Test
    public void testWritePropertyUsesExpectedBinaryRepresentation() {
        UUID uuid = UUID.fromString("00000000-0000-0001-0000-000000000002");
        Date date = new Date(123456789L);
        BytesBuffer buffer = BytesBuffer.allocate(4);
        buffer.writeProperty(DataType.BOOLEAN, true);
        buffer.writeProperty(DataType.BYTE, (byte) 8);
        buffer.writeProperty(DataType.INT, 16);
        buffer.writeProperty(DataType.FLOAT, 1.25F);
        buffer.writeProperty(DataType.LONG, 32L);
        buffer.writeProperty(DataType.DATE, date);
        buffer.writeProperty(DataType.DOUBLE, 2.5D);
        buffer.writeProperty(DataType.TEXT, "text");
        buffer.writeProperty(DataType.UUID, uuid);

        buffer.forReadWritten();

        Assert.assertEquals(1, buffer.readVInt());
        Assert.assertEquals(8, buffer.readVInt());
        Assert.assertEquals(16, buffer.readVInt());
        Assert.assertEquals(1.25F, buffer.readFloat(), 0.0F);
        Assert.assertEquals(32L, buffer.readVLong());
        Assert.assertEquals(date.getTime(), buffer.readVLong());
        Assert.assertEquals(2.5D, buffer.readDouble(), 0.0D);
        Assert.assertEquals("text", buffer.readString());
        Assert.assertEquals(uuid.getMostSignificantBits(), buffer.readLong());
        Assert.assertEquals(uuid.getLeastSignificantBits(), buffer.readLong());
    }

    @Test
    public void testInvalidInputsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> {
            BytesBuffer.allocate(BytesBuffer.MAX_BUFFER_CAPACITY + 1);
        });
        assertThrows(IllegalStateException.class, () -> {
            BytesBuffer.wrap(new byte[1]).writeInt(1);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            BytesBuffer.wrap(new byte[]{(byte) 0x81, (byte) 0x81,
                                        (byte) 0x81, (byte) 0x81,
                                        (byte) 0x81, 0x00}).readVInt();
        });
        assertThrows(IllegalArgumentException.class, () -> {
            BytesBuffer.allocate(4).writeStringWithEnding("a\0b");
        });
        assertThrows(IllegalArgumentException.class, () -> {
            BytesBuffer.allocate(4).writeIndexId(IdGenerator.of("a\0b"),
                                                HugeType.SECONDARY_INDEX);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            BytesBuffer.allocate(4).writeId(IdGenerator.of(repeat('x', 129)));
        });
    }

    @Test
    public void testCopyPeekAndIdWritersAdvancePosition() {
        UUID uuid = UUID.fromString("00000000-0000-0001-0000-000000000002");
        BytesBuffer peek = BytesBuffer.allocate(1);
        peek.write((byte) 9).forReadWritten();
        Assert.assertEquals(9, peek.peek());

        BytesBuffer source = BytesBuffer.allocate(4);
        source.write((byte) 9)
              .writeId(IdGenerator.of(123L))
              .writeId(IdGenerator.of("person:1"))
              .writeId(IdGenerator.of(uuid))
              .writeIndexId(IdGenerator.of("name"), HugeType.SECONDARY_INDEX);

        Assert.assertTrue(source.position() > 0);

        BytesBuffer copy = BytesBuffer.allocate(1);
        copy.copyFrom(source);
        Assert.assertArrayEquals(source.bytes(), copy.bytes());
    }

    private interface ThrowingRunnable {

        void run();
    }

    private static <T extends Throwable> void assertThrows(Class<T> type,
                                                           ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Throwable e) {
            if (type.isInstance(e)) {
                return;
            }
            throw new AssertionError("Unexpected exception type", e);
        }
        throw new AssertionError("Expected exception " + type.getName());
    }

    private static String repeat(char value, int count) {
        char[] chars = new char[count];
        for (int i = 0; i < count; i++) {
            chars[i] = value;
        }
        return new String(chars);
    }
}
