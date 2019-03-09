package com.velocitypowered.proxy.protocol.util;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.util.ProxyVersion;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.packet.PluginMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;

public class PluginMessageUtil {

  private static final String BRAND_CHANNEL_LEGACY = "MC|Brand";
  private static final String BRAND_CHANNEL = "minecraft:brand";
  private static final String REGISTER_CHANNEL_LEGACY = "REGISTER";
  private static final String REGISTER_CHANNEL = "minecraft:register";
  private static final String UNREGISTER_CHANNEL_LEGACY = "UNREGISTER";
  private static final String UNREGISTER_CHANNEL = "minecraft:unregister";

  private PluginMessageUtil() {
    throw new AssertionError();
  }

  /**
   * Determines whether or not this is a brand plugin message. This is shown on the client.
   *
   * @param message the plugin message
   * @return whether or not this is a brand plugin message
   */
  public static boolean isMcBrand(PluginMessage message) {
    checkNotNull(message, "message");
    return message.getChannel().equals(BRAND_CHANNEL_LEGACY) || message.getChannel()
        .equals(BRAND_CHANNEL);
  }

  /**
   * Determines whether or not this plugin message is being used to register plugin channels.
   *
   * @param message the plugin message
   * @return whether we are registering plugin channels or not
   */
  public static boolean isRegister(PluginMessage message) {
    checkNotNull(message, "message");
    return message.getChannel().equals(REGISTER_CHANNEL_LEGACY) || message.getChannel()
        .equals(REGISTER_CHANNEL);
  }

  /**
   * Determines whether or not this plugin message is being used to unregister plugin channels.
   *
   * @param message the plugin message
   * @return whether we are unregistering plugin channels or not
   */
  public static boolean isUnregister(PluginMessage message) {
    checkNotNull(message, "message");
    return message.getChannel().equals(UNREGISTER_CHANNEL_LEGACY) || message.getChannel()
        .equals(UNREGISTER_CHANNEL);
  }

  /**
   * Fetches all the channels in a register or unregister plugin message.
   *
   * @param message the message to get the channels from
   * @return the channels, as an immutable list
   */
  public static List<String> getChannels(PluginMessage message) {
    checkNotNull(message, "message");
    checkArgument(isRegister(message) || isUnregister(message), "Unknown channel type %s",
        message.getChannel());
    String channels = new String(message.getData(), StandardCharsets.UTF_8);
    return ImmutableList.copyOf(channels.split("\0"));
  }

  /**
   * Constructs a channel (un)register packet.
   *
   * @param protocolVersion the client/server's protocol version
   * @param channels the channels to register
   * @return the plugin message to send
   */

  public static PluginMessage constructChannelsPacket(ProtocolVersion protocolVersion,
      Collection<String> channels) {
    Preconditions.checkNotNull(channels, "channels");
    String channelName = protocolVersion.compareTo(ProtocolVersion.MINECRAFT_1_13) >= 0
        ? REGISTER_CHANNEL : REGISTER_CHANNEL_LEGACY;
    PluginMessage message = new PluginMessage();
    message.setChannel(channelName);
    message.setData(String.join("\0", channels).getBytes(StandardCharsets.UTF_8));
    return message;
  }

  /**
   * Rewrites the brand message to indicate the presence of Velocity.
   *
   * @param message the plugin message
   * @param version the proxy version
   * @return the rewritten plugin message
   */
  public static PluginMessage rewriteMinecraftBrand(PluginMessage message, ProxyVersion version,
      ProtocolVersion protocolVersion) {
    checkNotNull(message, "message");
    checkNotNull(version, "version");
    checkArgument(isMcBrand(message), "message is not a brand plugin message");

    String toAppend = " (" + version.getName() + ")";

    byte[] rewrittenData;
    if (protocolVersion.compareTo(ProtocolVersion.MINECRAFT_1_7_6) > 0) {
      ByteBuf rewrittenBuf = Unpooled.buffer();
      try {
        String currentBrand = ProtocolUtils.readString(Unpooled.wrappedBuffer(message.getData()));
        ProtocolUtils.writeString(rewrittenBuf, currentBrand + toAppend);
        rewrittenData = new byte[rewrittenBuf.readableBytes()];
        rewrittenBuf.readBytes(rewrittenData);
      } finally {
        rewrittenBuf.release();
      }
    } else {
      rewrittenData = (new String(message.getData(), Charsets.UTF_8) + toAppend)
          .getBytes(Charsets.UTF_8);
    }

    PluginMessage newMsg = new PluginMessage();
    newMsg.setChannel(message.getChannel());
    newMsg.setData(rewrittenData);
    return newMsg;
  }

}
