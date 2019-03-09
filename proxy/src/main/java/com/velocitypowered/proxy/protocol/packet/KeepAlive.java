package com.velocitypowered.proxy.protocol.packet;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;

public class KeepAlive implements MinecraftPacket {

  private long randomId;

  public long getRandomId() {
    return randomId;
  }

  public void setRandomId(long randomId) {
    this.randomId = randomId;
  }

  @Override
  public String toString() {
    return "KeepAlive{"
        + "randomId=" + randomId
        + '}';
  }

  @Override
  public void decode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion version) {
    if (version.compareTo(ProtocolVersion.MINECRAFT_1_12_2) >= 0) {
      randomId = buf.readLong();
    } else if (version.compareTo(ProtocolVersion.MINECRAFT_1_7_6) <= 0) {
      randomId = buf.readInt();
    } else {
      randomId = ProtocolUtils.readVarInt(buf);
    }
}

  @Override
  public void encode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion version) {
    if (version.compareTo(ProtocolVersion.MINECRAFT_1_12_2) >= 0) {
      buf.writeLong(randomId);
    } else if (version.compareTo(ProtocolVersion.MINECRAFT_1_7_6) <= 0) {
      buf.writeInt((int) randomId);
    } else {
      ProtocolUtils.writeVarInt(buf, (int) randomId);
    }
  }

  @Override
  public boolean handle(MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
