package com.velocitypowered.proxy.protocol.packet;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import org.checkerframework.checker.nullness.qual.Nullable;

public class ServerLoginSuccess implements MinecraftPacket {

  private @Nullable UUID uuid;
  private @Nullable String username;

  public UUID getUuid() {
    if (uuid == null) {
      throw new IllegalStateException("No UUID specified!");
    }
    return uuid;
  }

  public void setUuid(UUID uuid) {
    this.uuid = uuid;
  }

  public String getUsername() {
    if (username == null) {
      throw new IllegalStateException("No username specified!");
    }
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  @Override
  public String toString() {
    return "ServerLoginSuccess{"
        + "uuid=" + uuid
        + ", username='" + username + '\''
        + '}';
  }

  @Override
  public void decode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion version) {
    String stringUuid = ProtocolUtils.readString(buf, 36);
    if (version.compareTo(ProtocolVersion.MINECRAFT_1_7_2) <= 0) {
      stringUuid = stringUuid.replaceAll(
          "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})",
          "$1-$2-$3-$4-$5");
    }
    uuid = UUID.fromString(stringUuid);
    username = ProtocolUtils.readString(buf, 16);
  }

  @Override
  public void encode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion version) {
    if (uuid == null) {
      throw new IllegalStateException("No UUID specified!");
    }
    if (version.compareTo(ProtocolVersion.MINECRAFT_1_7_2) <= 0) {
      ProtocolUtils.writeString(buf, uuid.toString().replace("-", ""));
    } else {
      ProtocolUtils.writeString(buf, uuid.toString());
    }
    if (username == null) {
      throw new IllegalStateException("No username specified!");
    }
    ProtocolUtils.writeString(buf, username);
  }

  @Override
  public boolean handle(MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
