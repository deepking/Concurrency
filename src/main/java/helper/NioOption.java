package helper;


public enum NioOption
{
	//------------------------------------------------
	// channel config
	bufferFactory,  // Returns the default ChannelBufferFactory used to create a new ChannelBuffer.
	pipelineFactory, // Returns the ChannelPipelineFactory which be used when a child channel is created.
    connectTimeoutMillis, // connect timeout of the channel in milliseconds.
	//------------------------------------------------
    keepAlive, // SO_KEEPALIVE option, Turn on socket keepalive. Valid for Socket.
    reuseAddress,	// SO_REUSEADDR, Enable reuse address for a socket. Valid for Socket, ServerSocket, DatagramSocket.
    soLinger,	// Specify a linger-on-close timeout, Valid for Sockets
    tcpNoDelay, // SO_TCPNODELAY option, Valid for Sockets
    trafficClass, // traffic class
    receiveBufferSize, // SO_RCVBUF 
    				   // Get the size of the buffer actually used by the platform when receiving in data on this socket.
    				   // Valid for all sockets: Socket, ServerSocket, DatagramSocket. 
    sendBufferSize, // SO_SNDBUF, Set a hint the size of the underlying buffers for outgoing network I/O. Valid for all sockets: Socket, ServerSocket, DatagramSocket.
    // SO_TIMEOUT: Specify a timeout on blocking socket operations. (Don't block forever! Valid for all sockets: Socket, ServerSocket, DatagramSocket.
    // SO_BROADCAST: Enables a socket to send broadcast messages. Valid for DatagramSocket.
    // SO_OOBINLINE: Enable inline reception of TCP urgent data. Valid for Socket.
    // IP_MULTICAST_IF: Specify the outgoing interface for multicast packets (on multihomed hosts). Valid for MulticastSockets.
    // IP_MULTICAST_LOOP: Enables or disables local loopback of multicast datagrams. Valid for MulticastSocket.
    // IP_TOS: Sets the type-of-service or traffic class field in the IP header for a TCP or UDP socket. Valid for Socket, DatagramSocket
    // setPerformancePreferences(int connectionTime, int latency, int bandwidth) 
	writeBufferHighWaterMark,
	writeBufferLowWaterMark,
	writeSpinCount,
	
	backlog, // server socket 使用
	//------------------------------------------------
    child_tcpNoDelay,
    child_keepAlive,
    ;
    //------------------------------------------------
    public String getOptionName()
    {
    	String sName = name();
    	if (sName.startsWith("child_"))
    		sName = "child." + sName.substring(6);
    	return sName;
    }
}
