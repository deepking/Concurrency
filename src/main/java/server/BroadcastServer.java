package server;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.bootstrap.ServerBootstrap;
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
import org.jboss.netty.handler.codec.string.StringEncoder;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.Uninterruptibles;

public class BroadcastServer
{
	private static final String sm_strDummy = Strings.repeat("helloworld", 10);
	
	private final Recipients m_handler = new Recipients();
	private final StringEncoder m_encoder = new StringEncoder(Charsets.UTF_8);

	public static void main(String[] args)
    {
		final BroadcastServer server = new BroadcastServer();
		server.run(9999);
		
		ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
		service.scheduleAtFixedRate(new Runnable()
		{
			public void run()
			{
				StringBuilder sb = new StringBuilder();
				sb.append(String.valueOf(System.currentTimeMillis()));
				sb.append(" ");
				sb.append(sm_strDummy);
				
				server.write(sb.toString());
			}
		}, 200, 200, TimeUnit.MILLISECONDS);
		
		Uninterruptibles.joinUninterruptibly(Thread.currentThread());
    }
	
	public void run(int nPort)
	{
		ServerBootstrap bootstrap = new ServerBootstrap();
		bootstrap.setOption(NioOption.child_tcpNoDelay.toString(), true);
		bootstrap.setFactory(new NioServerSocketChannelFactory());
		bootstrap.setPipelineFactory(new ChannelPipelineFactory()
		{
			public ChannelPipeline getPipeline() throws Exception
			{
				return Channels.pipeline(m_encoder, m_handler);
			}
		});
		
		
		bootstrap.bind(new InetSocketAddress(nPort));
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
			m_recipients.add(e.getChannel());
		}
		
		@Override
		public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
		        throws Exception
		{
			m_recipients.remove(e.getChannel());
		}

		public ChannelGroupFuture write(Object message)
        {
	        return m_recipients.write(message);
        }
	}
}
