package server;

import helper.NioOption;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.ChannelGroupFuture;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.frame.LengthFieldPrepender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.Parameter;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.Uninterruptibles;

public class BroadcastServer
{
	private static final String sm_strDummy = Strings.repeat("helloworld", 10);
	private static final Logger log = LoggerFactory.getLogger(BroadcastServer.class);
	
	// handler
	//
	private final Recipients m_handler = new Recipients();
	private final LengthFieldPrepender m_lenPrePender = new LengthFieldPrepender(4);
	
	public void run(int nPort)
	{
		ServerBootstrap bootstrap = new ServerBootstrap();
		bootstrap.setOption(NioOption.child_tcpNoDelay.toString(), true);
		bootstrap.setFactory(new NioServerSocketChannelFactory());
		bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
			public ChannelPipeline getPipeline() throws Exception
			{
				return Channels.pipeline(m_lenPrePender, m_handler);
			}
		});
		
		bootstrap.bind(new InetSocketAddress(nPort));
		log.info("start server port={}", nPort);
	}
	
	public ChannelGroupFuture write(Object message)
    {
	    return m_handler.write(message);
    }
	
	//------------------------------------------------------------------------
	static class Recipients extends SimpleChannelUpstreamHandler
	{
		ChannelGroup m_recipients = new DefaultChannelGroup();
		
		@Override
		public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e)
		        throws Exception
		{
		    synchronized (m_recipients)
            {
    			m_recipients.add(e.getChannel());
            }
		}
		
		@Override
		public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
		        throws Exception
		{
		    synchronized (m_recipients)
            {
    			m_recipients.remove(e.getChannel());
            }
		    log.debug("[Close] {}", e.getChannel());
		}
		
		public ChannelGroupFuture write(Object message)
        {
		    log.debug("send msg to {} clients", m_recipients.size());
	        return m_recipients.write(message);
        }
	}
	
	//------------------------------------------------------------------------
	public static void main(String[] args)
    {
	    int nPort = Integer.parseInt(args[0]);
		final BroadcastServer server = new BroadcastServer();
		server.run(nPort);
		
		ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
		service.scheduleAtFixedRate(new Runnable()
		{
			public void run()
			{
				ChannelBuffer buf = ChannelBuffers.dynamicBuffer();
				buf.writeLong(System.currentTimeMillis());
				buf.writeBytes(sm_strDummy.getBytes());
				
				server.write(buf);
			}
		}, 100, 100, TimeUnit.MILLISECONDS);
		
		Uninterruptibles.joinUninterruptibly(Thread.currentThread());
    }
	
	static class Param 
	{
	    @Parameter(names="-port")
	    private int port = 9999;
	}
}
