package server;

import helper.NioOption;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.common.base.Strings;
import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.Uninterruptibles;

/**
 * record message bytes and discard
 * 
 * @author chengyi
 */
public class TestClient {
	private static final Logger log = LoggerFactory.getLogger(TestClient.class);
	private static final ChannelFactory sm_channelFactory = new NioClientSocketChannelFactory();

	private final String m_strName;
	private long m_lDelayMillis = 200;
	private AtomicLong m_count = null;

	public TestClient(String strName) {
		m_strName = strName;
	}

	// ------------------------------------------------------------------------
	public TestClient setDelayMillis(long lMillis) {
		m_lDelayMillis = lMillis;
		return this;
	}

	public TestClient setMsgCount(AtomicLong count) {
		m_count = count;
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
				EndpointHandler handler = new EndpointHandler(m_strName,
						m_lDelayMillis);
				handler.setCount(m_count);
				return Channels.pipeline(handler);
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
	static class EndpointHandler extends SimpleChannelUpstreamHandler {
		final String m_strName;
		final long m_lDelayMillis;
		long m_lLastDetectTimeMillis = System.currentTimeMillis();
		AtomicLong m_count = null;

		public EndpointHandler(String strName, long lDelayMillis) {
			m_strName = strName;
			m_lDelayMillis = lDelayMillis;
		}

		public void setCount(AtomicLong count) {
			m_count = count;
		}

		@Override
		public void channelInterestChanged(ChannelHandlerContext ctx,
				ChannelStateEvent e) throws Exception {
			log.info("channel State={} {}", e, ctx.getChannel());
		}

		@Override
		public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
				throws Exception {
			if (m_count != null) {
				ChannelBuffer buf = (ChannelBuffer) e.getMessage();
				m_count.addAndGet(buf.readableBytes());
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

	//------------------------------------------------------------------------
	public static void main(String[] args) {
		Param param = new Param();
		new JCommander(param, args);

		String strIp = param.ip;
		int nPort = param.port;
		int nClientCount = param.clientCount;
		AtomicLong count = new AtomicLong();

		for (int i = 0; i < nClientCount; i++) {
			if (param.creationPeriodMillis > 0)
				Uninterruptibles.sleepUninterruptibly(param.creationPeriodMillis, TimeUnit.MILLISECONDS);
			
			new TestClient(Strings.padStart("" + i, 6, '-'))
			.setMsgCount(count).run(HostAndPort.fromParts(strIp, nPort));
		}
		
		while (true) {
			Uninterruptibles.sleepUninterruptibly(10, TimeUnit.SECONDS);
			long lCount = count.getAndSet(0);
			log.info("Throughput " + lCount / 10);
		}
	}

	static class Param {
		@Parameter(names = "-ip")
		String ip = "localhost";

		@Parameter(names = "-port")
		int port = 9999;

		/**
		 * delay client creation
		 */
		@Parameter(names = "-delayMillis")
		int creationPeriodMillis = 0;

		@Parameter(names = "-clientCount")
		int clientCount = 1;
	}
}
