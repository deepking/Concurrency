package server;

import helper.NioOption;

import java.net.InetSocketAddress;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.net.HostAndPort;

public class TestClient
{
	private static final Logger log = LoggerFactory.getLogger(TestClient.class);
	private static final ChannelFactory sm_channelFactory = new NioClientSocketChannelFactory();
	
	private final String m_strName;
	
	public TestClient(String strName)
	{
		m_strName = strName;
	}
	
	public void run(HostAndPort hp)
	{
		ClientBootstrap bootstrap = new ClientBootstrap();
		bootstrap.setFactory(sm_channelFactory);
		bootstrap.setOption(NioOption.reuseAddress.toString(), true);
		bootstrap.setOption(NioOption.tcpNoDelay.toString(), true);
		bootstrap.setPipelineFactory(new ChannelPipelineFactory()
		{
			public ChannelPipeline getPipeline() throws Exception
			{
				return Channels.pipeline(new DetectDelay(this.toString()));
			}
		});
		
		bootstrap.connect(new InetSocketAddress(hp.getHostText(), hp.getPort()));
	}
	
	@Override
	public String toString()
	{
		return m_strName;
	}
	
	//------------------------------------------------------------------------
    static class DetectDelay extends SimpleChannelUpstreamHandler
    {
    	final String m_strName;
    	
    	public DetectDelay(String strName)
        {
    		m_strName = strName;
        }
    	
    	@Override
    	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
    	        throws Exception
    	{
    		if (!(e.getMessage() instanceof ChannelBuffer))
    			return;
    		
    		long lCurr = System.currentTimeMillis();
    		
    		ChannelBuffer buf = (ChannelBuffer) e.getMessage();
    		
    		int nLen = buf.bytesBefore((byte)' ');
    		byte[] bytes = new byte[nLen];
    		buf.readBytes(bytes);
    		
    		long lServer = Long.parseLong(new String(bytes));
    		
    		if (lCurr - lServer > 100)
    		{
    			log.error("{} delay {} millis", m_strName, lCurr - lServer);
    		}
    	}
    }
    
	//------------------------------------------------------------------------
    public static void main(String[] args)
    {
	    new TestClient("client1").run(HostAndPort.fromParts("localhost", 9999));
    }
    
}
