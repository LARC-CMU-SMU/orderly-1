/*  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package com.gotometrics.orderly;

import java.io.IOException;

import org.apache.hadoop.hbase.io.ImmutableBytesWritable;

/** Base class for translating objects to/from sort-order preserving byte 
 * arrays. 
 *
 * <p>In contrast to other common object serialization methods, 
 * <code>RowKey</code> serializations use a byte array representation that 
 * preserves the object's natural sort ordering. Sorting the raw byte arrays 
 * yields the same sort order as sorting the actual objects themselves, without 
 * requiring the object to be instantiated. Using the serialized byte arrays 
 * as row keys in key-value stores such as HBase will sort rows in the natural 
 * sort order of the object.</p>
 *
 * <h1> Key types </h1>
 * Primitive (single-value) key types are: variable-length signed/unsigned 
 * integers and longs, fixed-width signed/unsigned integers and longs, 
 * float/double, bigdecimal, and utf-8/text/String character strings.
 *
 * <p>Composite (multi-value) row key support is provided using struct row keys.
 * You may have an arbitrary number of fields of any type, and each field
 * may have its own sort order.</p>
 *
 * <h1> Order </h1>
 * All keys may be sorted in ascending or descending order.
 *
 * <h1> NULL </h1>
 * Most keys support NULL values (only fixed-width integer/long types do not). 
 * All keys with NULL support treat the NULL value as comparing less than any
 * non-NULL value for sort ordering purposes.
 *
 * <h1> Termination </h1>
 * Some row keys, such as character strings, require an explicit termination
 * byte during serialization to indicate the end of the serialized value.
 * This terminator byte can be omitted in some situations, such as during an
 * ascending sort where the only serialized bytes come from the string row key.
 * Omitting the explicit terminator byte is known as implicit termination, 
 * because the end of the serialized byte array implicitly terminates the 
 * serialized value. The {@link #setMustTerminate} method can be used to 
 * control when termination is required.
 *
 * <p>If a row key is not forced to terminate, then during deserialization it
 * will read bytes up until the end of the serialized byte array. This is safe
 * if the row key serialized all of the bytes up to the end of the byte array
 * (which is the common case). However, if the user has created a custom 
 * serialized format where their own extra bytes are appended to the byte array,
 * then this would produce incorrect results and explicit termination should
 * be forced.</p>
 *
 * <p>The JavaDoc of each
 * row key class describes the effects of implicit and explicit termination
 * of the class's serialization. Note that the <code>mustTerminate</code> flag 
 * only affects serialization. For all row key types, deserialization and skip 
 * methods are able to detect values encoded in both implicit and explicit 
 * terminated formats, regardless of what the <code>mustTerminate</code> flag
 * is set to.</p>
 */
public abstract class RowKey 
{
  protected Order order;
  protected boolean mustTerminate;
  private ImmutableBytesWritable w;

  public RowKey() { this.order = Order.ASCENDING; }

  /** Sets the sort order of the row key - ascending or descending.
   * @param order sort otder
   * @return the rowkey 
   */ 
  public RowKey setOrder(Order order) { this.order = order; return this; }

  /** Gets the sort order of the row key - ascending or descending 
   * @return the sort order
   * */
  public Order getOrder() { return order; }

  /** Returns true if the row key serialization must be explicitly terminated 
   * in some fashion (such as a terminator byte or a self-describing length).
   * If this is false, the end of the byte array may serve as an implicit 
   * terminator. Defaults to false.
   * @return must terminate
   */
  public boolean mustTerminate() { return mustTerminate; }

  /** Sets the mustTerminate flag for this row key. If this flag is false,
   * the end of the byte array can be used to terminate encoded values. You
   * should only set this value if you are adding a custom byte value suffix
   * to a row key.
   * @param mustTerminate mustTerminate flag
   * @return the row key
   */
  public RowKey setMustTerminate(boolean mustTerminate) {
    this.mustTerminate = mustTerminate;
    return this;
  }

  /** Returns true if termination is required */
  boolean terminate() { return mustTerminate || order == Order.DESCENDING; }

  /** Gets the class of the object used for serialization.
   * @see #serialize
   * @return Class of the object used for serialization
   */
  public abstract Class<?> getSerializedClass();

  /** Gets the class of the object used for deserialization.
   * @see #deserialize
   * @return class of the object
   */
  public Class<?> getDeserializedClass() { return getSerializedClass(); }

  /** Gets the length of the byte array when serializing an object.
   * @param o object to serialize
   * @return the length of the byte array used to serialize o
   * @throws IOException IO Exception
   */
  public abstract int getSerializedLength(Object o) throws IOException;

  /** Serializes an object o to a byte array. When this
   * method returns, the byte array's position will be adjusted by the number 
   * of bytes written. The offset (length) of the byte array is incremented 
   * (decremented) by the number of bytes used to serialize o.
   * @param o object to serialize
   * @param w byte array used to store the serialized object
   * @throws IOException IO Exception
   */
  public abstract void serialize(Object o, ImmutableBytesWritable w) 
    throws IOException;

  public void serialize(Object o, byte[] b) throws IOException {
    serialize(o, b, 0); 
  }

  public void serialize(Object o, byte[] b, int offset) throws IOException {
    if (w == null) 
      w = new ImmutableBytesWritable();
    w.set(b, offset, b.length - offset);
    serialize(o, w);
  }

  public byte[] serialize(Object o) throws IOException {
    byte[] b = new byte[getSerializedLength(o)];
    serialize(o, b, 0);
    return b;
  }

  /** Skips over a serialized key in the byte array. When this
   * method returns, the byte array's position will be adjusted by the number of
   * bytes in the serialized key. The offset (length) of the byte array is 
   * incremented (decremented) by the number of bytes in the serialized key.
   * @param w the byte array containing the serialized key
   * @throws IOException IO Exception
   */
  public abstract void skip(ImmutableBytesWritable w) throws IOException;

  /** Deserializes a key from the byte array. The returned object is an 
   * instance of the class returned by {@link #getSerializedClass}. When this
   * method returns, the byte array's position will be adjusted by the number of
   * bytes in the serialized key. The offset (length) of the byte array is 
   * incremented (decremented) by the number of bytes in the serialized key.
   * @param w the byte array used for key deserialization
   * @return the deserialized key from the current position in the byte array
   * @throws IOException IO Exception
   */
  public abstract Object deserialize(ImmutableBytesWritable w)
    throws IOException;

  public Object deserialize(byte[] b) throws IOException { 
    return deserialize(b, 0);
  }

  public Object deserialize(byte[] b, int offset) throws IOException {
    if (w == null)
      w = new ImmutableBytesWritable();
    w.set(b, offset, b.length - offset);
    return deserialize(w);
  }

  /** Orders serialized byte b by XOR'ing it with the sort order mask. This
   * allows descending sort orders to invert the byte values of the serialized
   * byte stream.
   * @param b byte to be masked
   * @return the serialized byte b
   */
  protected byte mask(byte b) {
    return (byte) (b ^ order.mask());
  }
}
