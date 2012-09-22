package server;

import helper.NioOption;

import java.net.InetSocketAddress;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.frame.LengthFieldBasedFrameDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.Parameter;
import com.google.common.base.Strings;
import com.google.common.net.HostAndPort;

public class TestClient {
	private static final Logger log = LoggerFactory.getLogger(TestClient.class);
	private static final ChannelFactory sm_channelFactory = new NioClientSocketChannelFactory();

	private final String m_strName;
	private long m_lDelayMillis = 200;

	public TestClient(String strName) {
		m_strName = strName;
	}

	// ------------------------------------------------------------------------
	public TestClient setDelayMillis(long lMillis) {
		m_lDelayMillis = lMillis;
		return this;
	}

	public void run(HostAndPort hp) {
		log.info("{} connect {})", m_strName, hp);

		ClientBootstrap bootstrap = new ClientBootstrap();
		bootstrap.setFactory(sm_channelFactory);
		bootstrap.setOption(NioOption.reuseAddress.toString(), true);
		bootstrap.setOption(NioOption.tcpNoDelay.toString(), true);
		bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
			public ChannelPipeline getPipeline() throws Exception {
				return Channels
						.pipeline(new LengthFieldBasedFrameDecoder(1000, 0, 4,
								0, 4), new DetectDelay(m_strName,
								m_lDelayMillis));
			}
		});

		bootstrap
				.connect(new InetSocketAddress(hp.getHostText(), hp.getPort()));
	}

	@Override
	public String toString() {
		return m_strName;
	}

	// ------------------------------------------------------------------------
	static class DetectDelay extends SimpleChannelUpstreamHandler {
		final String m_strName;
		final long m_lDelayMillis;

		public DetectDelay(String strName, long lDelayMillis) {
			m_strName = strName;
			m_lDelayMillis = lDelayMillis;
		}

		@Override
		public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
				throws Exception {
			if (!(e.getMessage() instanceof ChannelBuffer))
				return;

			long lCurr = System.currentTimeMillis();

			ChannelBuffer buf = (ChannelBuffer) e.getMessage();

			long lServer = buf.readLong();
			if (lCurr - lServer > m_lDelayMillis) {
				log.error("{} delay {} millis", m_strName, lCurr - lServer);
			}
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
				throws Exception {
			log.error("{} exception", m_strName, e.getCause());
		}

		@Override
		public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
				throws Exception {
			log.info("{} closed", m_strName);
		}
	}

	// ------------------------------------------------------------------------
	public static void main(String[] args) {
		// Param param = new Param();
		// new JCommander(param, args);
		String strIp = args[0];
		int nPort = Integer.parseInt(args[1]);
		int nClientCount = Integer.parseInt(args[2]);
		int nDelayMillis = Integer.parseInt(args[3]);

		for (int i = 0; i < nClientCount; i++) {
			new TestClient(Strings.padStart("" + i, 6, '-')).setDelayMillis(
					nDelayMillis).run(HostAndPort.fromParts(strIp, nPort));
		}
	}

	static class Param {
		@Parameter(names = "-ip")
		String ip = "localhost";

		@Parameter(names = "-port")
		int port = 9999;
	}

}
