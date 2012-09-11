package server;

import static helper.NioOption.child_keepAlive;
import static helper.NioOption.child_tcpNoDelay;
import static helper.NioOption.reuseAddress;

import helper.NioOption;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NioServer
{
	private static NioServerSocketChannelFactory sm_channelFactory = new NioServerSocketChannelFactory(
	        Executors.newCachedThreadPool(), Executors.newCachedThreadPool());
	private static final Logger log = LoggerFactory.getLogger(NioServer.class);

	private String m_sLocalIP;
	private int m_nLocalPort;
	private ServerBootstrap m_bootstrap;

	//------------------------------------------------------------------------
	public NioServer(String sLocalIP,
	        int nLocalPort, boolean bChildTCPDelay, boolean bChildKeepAlive,
	        boolean bReuseAddress)
	{
		// 1. create ServerFactory and ServerBootstrap
		m_bootstrap = new ServerBootstrap(sm_channelFactory);

		// 2. set options
		setOption(child_tcpNoDelay, !bChildTCPDelay);
		setOption(child_keepAlive, bChildKeepAlive);

		// 3. reuse server's side address
		setOption(reuseAddress, bReuseAddress);

		// 4. set pipeline
		//m_bootstrap.setPipelineFactory(pipelineFactory);

		// 5. bind 移到 start()，以便讓 start() 可以有機會運作
		m_sLocalIP = sLocalIP;
		m_nLocalPort = nLocalPort;
	}

	public NioServer(int nLocalPort)
	{
		this(null, nLocalPort, false, false, true);
	}

	// -----------------------------------------------------------------------------
	public void start()
	{
		log.debug("start server port=" + m_nLocalPort);

		// 5. bind
		if (m_sLocalIP == null)
			m_bootstrap.bind(new InetSocketAddress(m_nLocalPort));
		else
			m_bootstrap.bind(new InetSocketAddress(m_sLocalIP, m_nLocalPort));
	}

	public void stop()
	{
		log.debug("stop server port=" + m_nLocalPort);
		
		// 如果和其他 protocol 共用，會關到其他 protocol
		sm_channelFactory.releaseExternalResources();
	}

	public void setOption(NioOption op, Object value)
	{
		m_bootstrap.setOption(op.getOptionName(), value);
	}

	public void setPipelineFactory(ChannelPipelineFactory pipelineFactory)
    {
	    m_bootstrap.setPipelineFactory(pipelineFactory);
    }

}
