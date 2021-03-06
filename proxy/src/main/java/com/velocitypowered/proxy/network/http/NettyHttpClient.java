package com.velocitypowered.proxy.network.http;

import com.google.common.base.VerifyException;
import com.velocitypowered.proxy.VelocityServer;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.pool.AbstractChannelPoolMap;
import io.netty.channel.pool.ChannelPoolHandler;
import io.netty.channel.pool.ChannelPoolMap;
import io.netty.channel.pool.FixedChannelPool;
import io.netty.channel.pool.SimpleChannelPool;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import javax.net.ssl.SSLEngine;

public class NettyHttpClient {

  private final ChannelPoolMap<HostAndSsl, SimpleChannelPool> poolMap;
  private final String userAgent;

  /**
   * Initializes the HTTP client.
   *
   * @param server the Velocity server
   */
  public NettyHttpClient(VelocityServer server) {
    this.userAgent = server.getVersion().getName() + "/" + server.getVersion().getVersion();
    Bootstrap bootstrap = server.initializeGenericBootstrap();
    this.poolMap = new AbstractChannelPoolMap<HostAndSsl, SimpleChannelPool>() {
      @Override
      protected SimpleChannelPool newPool(HostAndSsl key) {
        return new FixedChannelPool(bootstrap.remoteAddress(key.address), new ChannelPoolHandler() {
          @Override
          public void channelReleased(Channel channel) throws Exception {
            channel.pipeline().remove("collector");
          }

          @Override
          public void channelAcquired(Channel channel) throws Exception {
            // We don't do anything special when acquiring channels. The channel handler cleans up
            // after each connection is used.
          }

          @Override
          public void channelCreated(Channel channel) throws Exception {
            if (key.ssl) {
              SslContext context = SslContextBuilder.forClient().protocols("TLSv1.2").build();
              // Unbelievably, Java doesn't automatically check the CN to make sure we're talking
              // to the right host! Therefore, we provide the intended host name and port, along
              // with asking Java very nicely if it could check the hostname in the certificate
              // for us.
              SSLEngine engine = context.newEngine(channel.alloc(), key.address.getHostString(),
                  key.address.getPort());
              engine.getSSLParameters().setEndpointIdentificationAlgorithm("HTTPS");
              channel.pipeline().addLast("ssl", new SslHandler(engine));
            }
            channel.pipeline().addLast("http", new HttpClientCodec());
          }
        }, 8);
      }
    };
  }

  /**
   * Attempts an HTTP GET request to the specified URL.
   * @param url the URL to fetch
   * @return a future representing the response
   */
  public CompletableFuture<SimpleHttpResponse> get(URL url) {
    String host = url.getHost();
    int port = url.getPort();
    boolean ssl = url.getProtocol().equals("https");
    if (port == -1) {
      port = ssl ? 443 : 80;
    }

    HostAndSsl key = new HostAndSsl(InetSocketAddress.createUnresolved(host, port), ssl);

    CompletableFuture<SimpleHttpResponse> reply = new CompletableFuture<>();
    poolMap.get(key)
        .acquire()
        .addListener(future -> {
          if (future.isSuccess()) {
            Channel channel = (Channel) future.getNow();
            if (channel == null) {
              throw new VerifyException("Null channel retrieved from pool!");
            }
            channel.pipeline().addLast("collector", new SimpleHttpResponseCollector(reply));

            DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,
                HttpMethod.GET, url.getPath() + "?" + url.getQuery());
            request.headers().add(HttpHeaderNames.HOST, url.getHost());
            request.headers().add(HttpHeaderNames.USER_AGENT, userAgent);
            channel.writeAndFlush(request);

            // Make sure to release this connection
            reply.whenComplete((resp, err) -> poolMap.get(key).release(channel));
          } else {
            reply.completeExceptionally(future.cause());
          }
        });
    return reply;
  }

  private static class HostAndSsl {
    private final InetSocketAddress address;
    private final boolean ssl;

    private HostAndSsl(InetSocketAddress address, boolean ssl) {
      this.address = address;
      this.ssl = ssl;
    }

    @Override
    public String toString() {
      return "HostAndSsl{"
          + "address=" + address
          + ", ssl=" + ssl
          + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      HostAndSsl that = (HostAndSsl) o;
      return ssl == that.ssl
          && Objects.equals(address, that.address);
    }

    @Override
    public int hashCode() {
      return Objects.hash(address, ssl);
    }
  }
}
