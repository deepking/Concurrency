package server;

import helper.NioOption;
import helper.Scheduler;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.ChannelGroupFuture;
import org.jboss.netty.channel.group.ChannelGroupFutureListener;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.frame.LengthFieldPrepender;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.logging.Slf4JLoggerFactory;
import org.jboss.netty.util.DefaultObjectSizeEstimator;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.ObjectSizeEstimator;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.TimerTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.Uninterruptibles;

public class BroadcastServer
{
    private static final Logger log = LoggerFactory
            .getLogger(BroadcastServer.class);

    // handler
    //
    private final Recipients m_handler = new Recipients();
    private final LengthFieldPrepender m_lenPrePender = new LengthFieldPrepender(
            4);
    private final AtomicInteger m_count = new AtomicInteger(1);

    private final ChannelFactory m_factory;

    public BroadcastServer(ChannelFactory factory)
    {
        m_factory = factory;
    }

    public void run(Param param)
    {
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.setOption(NioOption.child_tcpNoDelay.toString(), true);
        bootstrap.setOption(NioOption.reuseAddress.toString(), true);
        bootstrap.setFactory(m_factory);
        bootstrap.setPipelineFactory(new ChannelPipelineFactory()
        {
            public ChannelPipeline getPipeline() throws Exception
            {
                return Channels.pipeline(m_lenPrePender, m_handler);
            }
        });
        bootstrap.setOption(NioOption.writeBufferHighWaterMark.toString(), param.writeBufferHighWaterMark);
        //bootstrap.setOption(NioOption.sendBufferSize.toString(), param.sendBufSize);
        bootstrap.setOption("child.writeBufferHighWaterMark", param.writeBufferHighWaterMark);
        bootstrap.setOption("child.writeBufferLowWaterMark", param.writeBufferHighWaterMark);
        //bootstrap.setOption("child.sendBufferSize", param.sendBufSize);
        bootstrap.setOption("child.writeSpinCount", param.writeSpinCount);

System.out.println(bootstrap);

        Channel channel = bootstrap.bind(new InetSocketAddress(param.port));
        log.info("start server port={} channel={}", param.port, channel);
        log.info("default thread={}", Runtime.getRuntime()
                .availableProcessors() * 2);
    }

    public ChannelGroupFuture write(Object message)
    {
        final Stopwatch sw = new Stopwatch().start();
        ChannelGroupFuture f = m_handler.write(message);
        f.addListener(new ChannelGroupFutureListener()
        {
            @Override
            public void operationComplete(ChannelGroupFuture future)
                    throws Exception
            {
                long lMillis = sw.elapsedMillis();
                log.trace("writeComplete {} ms, {}", lMillis,
                        m_count.getAndIncrement());
            }
        });

        return f;
    }

    // ------------------------------------------------------------------------
    static class Recipients extends SimpleChannelUpstreamHandler
    {
        ChannelGroup m_recipients = new DefaultChannelGroup();
        ObjectSizeEstimator estimator = new DefaultObjectSizeEstimator();

        @Override
        public void channelInterestChanged(ChannelHandlerContext ctx,
                ChannelStateEvent e) throws Exception
        {

            log.info("channel State={}", ctx.getChannel().getInterestOps());
        }

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

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
                throws Exception
        {
            log.warn("exception", e.getCause());
        }

        public ChannelGroupFuture write(Object message)
        {
            // log.debug("send {} bytes to {} clients",
            // estimator.estimateSize(message),
            // m_recipients.size());

            return m_recipients.write(message);
        }
    }

    // ------------------------------------------------------------------------
    public static void main(String[] args)
    {
        InternalLoggerFactory.setDefaultFactory(new Slf4JLoggerFactory());
        
        // int nPort = Integer.parseInt(args[0]);
        final Param param = new Param();
        JCommander jcommander = new JCommander(param);
        jcommander.setProgramName("server");
        jcommander.usage();
        jcommander.parse(args);

        final BroadcastServer server = new BroadcastServer(
                new NioServerSocketChannelFactory(
                        Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool(), param.workerCount));
        server.run(param);

        final byte[] bytes = new byte[param.sendByteSize];

        Scheduler scheduler = new Scheduler(new HashedWheelTimer());
        scheduler.schedule(new TimerTask()
        {
            @Override
            public void run(Timeout timeout) throws Exception
            {
                ChannelBuffer len = ChannelBuffers.dynamicBuffer();
                len.writeLong(System.currentTimeMillis());
                ChannelBuffer buf = ChannelBuffers.wrappedBuffer(len.array(), bytes);

                for (int i = 0; i < param.sendCountPerPeriod; i++)
                {
                    server.write(buf);
                    System.out.println("XXX");
                }
                
            }
        }, param.sendPeriodMillis, param.sendPeriodMillis, TimeUnit.MILLISECONDS);

        Uninterruptibles.joinUninterruptibly(Thread.currentThread());
    }

    static class Param
    {
        @Parameter(names = "-port")
        private int port = 9999;

        @Parameter(names = "-sendPeriodMillis")
        private int sendPeriodMillis = 25;
        
        @Parameter(names = "-sendCountPerPeriod")
        private int sendCountPerPeriod = 40;

        @Parameter(names = "-sendByteSize")
        private int sendByteSize = 128;

        @Parameter(names = "-sendBufSize")
        private int sendBufSize = 4 * 1024;
        
        /**
         * default 64 * 1024
         */
        @Parameter(names = "-writeBufferHighWaterMark")
        private int writeBufferHighWaterMark = 64*1024;
        
        /**
         * default 32 * 1024
         */
        @Parameter(names = "-writeBufferLowWaterMark")
        private int writeBufferLowWaterMark = 32*1024;
        
        /**
         * default 16
         */
        @Parameter(names = "-writeSpinCount")
        private int writeSpinCount = 16;

        @Parameter(names = "-workerCount")
        private int workerCount = 32;

    }
}
