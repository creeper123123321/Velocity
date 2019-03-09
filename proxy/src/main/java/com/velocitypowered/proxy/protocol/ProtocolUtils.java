package com.velocitypowered.proxy.protocol;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.util.GameProfile;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public enum ProtocolUtils {
  ;
  private static final int DEFAULT_MAX_STRING_SIZE = 65536; // 64KiB

  /**
   * Reads a Minecraft-style VarInt from the specified {@code buf}.
   *
   * @param buf the buffer to read from
   * @return the decoded VarInt
   */
  public static int readVarInt(ByteBuf buf) {
    int i = 0;
    int j = 0;
    while (true) {
      int k = buf.readByte();
      i |= (k & 0x7F) << j++ * 7;
      if (j > 5) {
        throw new RuntimeException("VarInt too big");
      }
      if ((k & 0x80) != 128) {
        break;
      }
    }
    return i;
  }

  /**
   * Writes a Minecraft-style VarInt to the specified {@code buf}.
   *
   * @param buf the buffer to read from
   * @param value the integer to write
   */
  public static void writeVarInt(ByteBuf buf, int value) {
    while (true) {
      if ((value & 0xFFFFFF80) == 0) {
        buf.writeByte(value);
        return;
      }

      buf.writeByte(value & 0x7F | 0x80);
      value >>>= 7;
    }
  }

  public static String readString(ByteBuf buf) {
    return readString(buf, DEFAULT_MAX_STRING_SIZE);
  }

  /**
   * Reads a VarInt length-prefixed string from the {@code buf}, making sure to not go over {@code
   * cap} size.
   *
   * @param buf the buffer to read from
   * @param cap the maximum size of the string, in UTF-8 character length
   * @return the decoded string
   */
  public static String readString(ByteBuf buf, int cap) {
    int length = readVarInt(buf);
    checkArgument(length >= 0, "Got a negative-length string (%s)", length);
    // `cap` is interpreted as a UTF-8 character length. To cover the full Unicode plane, we must
    // consider the length of a UTF-8 character, which can be up to a 4 bytes. We do an initial
    // sanity check and then check again to make sure our optimistic guess was good.
    checkArgument(length <= cap * 4, "Bad string size (got %s, maximum is %s)", length, cap);
    checkState(buf.isReadable(length),
        "Trying to read a string that is too long (wanted %s, only have %s)", length,
        buf.readableBytes());
    String str = buf.toString(buf.readerIndex(), length, StandardCharsets.UTF_8);
    buf.skipBytes(length);
    checkState(str.length() <= cap, "Got a too-long string (got %s, max %s)",
        str.length(), cap);
    return str;
  }

  /**
   * Writes the specified {@code str} to the {@code buf} with a VarInt prefix.
   *
   * @param buf the buffer to write to
   * @param str the string to write
   */
  public static void writeString(ByteBuf buf, String str) {
    int size = ByteBufUtil.utf8Bytes(str);
    writeVarInt(buf, size);
    ByteBufUtil.writeUtf8(buf, str);
  }

  public static byte[] readByteArray(ByteBuf buf) {
    return readByteArray(buf, DEFAULT_MAX_STRING_SIZE);
  }

  /**
   * Reads a VarInt length-prefixed byte array from the {@code buf}, making sure to not go over
   * {@code cap} size.
   *
   * @param buf the buffer to read from
   * @param cap the maximum size of the string, in UTF-8 character length
   * @return the byte array
   */
  public static byte[] readByteArray(ByteBuf buf, int cap) {
    int length = readVarInt(buf);
    checkArgument(length >= 0, "Got a negative-length array (%s)", length);
    checkArgument(length <= cap, "Bad array size (got %s, maximum is %s)", length, cap);
    checkState(buf.isReadable(length),
        "Trying to read an array that is too long (wanted %s, only have %s)", length,
        buf.readableBytes());
    byte[] array = new byte[length];
    buf.readBytes(array);
    return array;
  }

  public static void writeByteArray(ByteBuf buf, byte[] array) {
    writeVarInt(buf, array.length);
    buf.writeBytes(array);
  }

  /**
   * Reads a VarShort length-prefixed byte array from the {@code buf}, making sure to not go over
   * {@code cap} size.
   *
   * @param buf the buffer to read from
   * @param cap the maximum size of the string, in UTF-8 character length
   * @return the byte array
   */
  public static byte[] readByteArrayOneSeven(ByteBuf buf, int cap) {
    int length = readVarShort(buf);
    checkArgument(length >= 0, "Got a negative-length array (%s)", length);
    checkArgument(length <= cap, "Bad array size (got %s, maximum is %s)", length, cap);
    checkState(buf.isReadable(length),
        "Trying to read an array that is too long (wanted %s, only have %s)", length,
        buf.readableBytes());
    byte[] array = new byte[length];
    buf.readBytes(array);
    return array;
  }

  public static void writeByteArrayOneSeven(ByteBuf buf, byte[] array) {
    writeVarShort(buf, array.length);
    buf.writeBytes(array);
  }

  /**
   * Reads a Forge-style VarShort from the specified {@code buf}.
   *
   * @param buf the buffer to read from
   * @return the decoded VarShort
   */
  public static int readVarShort(ByteBuf buf) {
    int value = buf.readShort();
    if (value < 0) {
      value &= 0x7FFF;
      value |= buf.readUnsignedByte() << 15;
    }
    return value;
  }

  /**
   * Writes a Forge-style VarShort to the specified {@code buf}.
   *
   * @param buf the buffer to read from
   * @param value the VarShort to write
   */
  public static void writeVarShort(ByteBuf buf, int value) {
    int first = value & 0x7FFF;
    value >>= 15;
    if (value != 0) {
      first |= 0x8000;
    }
    buf.writeShort(first);
    if (value != 0) {
      buf.writeByte(value);
    }
  }

  /**
   * Reads an VarInt-prefixed array of VarInt integers from the {@code buf}.
   *
   * @param buf the buffer to read from
   * @return an array of integers
   */
  public static int[] readIntegerArray(ByteBuf buf) {
    int len = readVarInt(buf);
    checkArgument(len >= 0, "Got a negative-length integer array (%s)", len);
    int[] array = new int[len];
    for (int i = 0; i < len; i++) {
      array[i] = readVarInt(buf);
    }
    return array;
  }

  /**
   * Reads an UUID from the {@code buf}.
   *
   * @param buf the buffer to read from
   * @return the UUID from the buffer
   */
  public static UUID readUuid(ByteBuf buf) {
    long msb = buf.readLong();
    long lsb = buf.readLong();
    return new UUID(msb, lsb);
  }

  public static void writeUuid(ByteBuf buf, UUID uuid) {
    buf.writeLong(uuid.getMostSignificantBits());
    buf.writeLong(uuid.getLeastSignificantBits());
  }

  /**
   * Writes a list of {@link com.velocitypowered.api.util.GameProfile.Property} to the buffer.
   *
   * @param buf the buffer to write to
   * @param properties the properties to serialize
   */
  public static void writeProperties(ByteBuf buf, List<GameProfile.Property> properties) {
    writeVarInt(buf, properties.size());
    for (GameProfile.Property property : properties) {
      writeString(buf, property.getName());
      writeString(buf, property.getValue());
      String signature = property.getSignature();
      if (signature != null) {
        buf.writeBoolean(true);
        writeString(buf, signature);
      } else {
        buf.writeBoolean(false);
      }
    }
  }

  /**
   * Reads a list of {@link com.velocitypowered.api.util.GameProfile.Property} from the buffer.
   *
   * @param buf the buffer to read from
   * @return the read properties
   */
  public static List<GameProfile.Property> readProperties(ByteBuf buf) {
    List<GameProfile.Property> properties = new ArrayList<>();
    int size = readVarInt(buf);
    for (int i = 0; i < size; i++) {
      String name = readString(buf);
      String value = readString(buf);
      String signature = "";
      boolean hasSignature = buf.readBoolean();
      if (hasSignature) {
        signature = readString(buf);
      }
      properties.add(new GameProfile.Property(name, value, signature));
    }
    return properties;
  }

  public enum Direction {
    SERVERBOUND,
    CLIENTBOUND;

    public StateRegistry.PacketRegistry.ProtocolRegistry getProtocolRegistry(StateRegistry state,
        ProtocolVersion version) {
      return (this == SERVERBOUND ? state.serverbound : state.clientbound)
          .getProtocolRegistry(version);
    }
  }
}
