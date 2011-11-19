/**
 * Copyright (c) 2002-2011 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.server.smack;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.channel.socket.oio.OioServerSocketChannelFactory;
import org.neo4j.server.smack.core.*;
import org.neo4j.server.smack.http.NettyHttpPipelineFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class SmackServer {

    private int port;
    private String host;
    private Router router = new Router();
    private ServerBootstrap netty;
    private OutputPipelineBootstrap outputPipeline;
    private InputPipelineBootstrap inputPipeline;
    private ExecutionHandler executionHandler;
    private ServerSocketChannelFactory channelFactory;
    private ChannelGroup openChannels = new DefaultChannelGroup("SmackServer");

    public SmackServer(String host, int port, ExecutionHandler executionHandler) {
        this.host = host;
        this.port = port;
        this.executionHandler = executionHandler;
    }
    
    public void start() {
        router.compileRoutes();
        
        // OUTPUT PIPELINE
        
        SerializationHandler serializationHandler = new SerializationHandler();
        outputPipeline = new OutputPipelineBootstrap(serializationHandler);
        outputPipeline.start();
        
        executionHandler.setOutputBuffer(outputPipeline.getRingBuffer());
        executionHandler.start();

        // INPUT PIPELINE
        
        RoutingHandler routingHandler = new RoutingHandler(router);
        DeserializationHandler deserializationHandler = new DeserializationHandler();
        
        inputPipeline = new InputPipelineBootstrap(routingHandler, deserializationHandler, executionHandler);
        inputPipeline.start();

        
        // NETTY 
        
        // Potential config setting: Pick between old-fashioned or async sockets
        // OioServerSocketChannelFactory vs NioServerSocketChannelFactory
        // Old sockets are superior when handling < 1000 clients
        channelFactory = 
            new OioServerSocketChannelFactory(
                    Executors.newCachedThreadPool(),
                    Executors.newCachedThreadPool());
        netty = new ServerBootstrap(channelFactory);

        // Set up the event pipeline factory.
        netty.setPipelineFactory(new NettyHttpPipelineFactory(inputPipeline.getRingBuffer(), openChannels));

        // Bind and start to accept incoming connections.
        openChannels.add(netty.bind(new InetSocketAddress(host, port)));

    }
    
    public void stop() {
        if (openChannels!=null) openChannels.close().awaitUninterruptibly();
        if (channelFactory!=null) channelFactory.releaseExternalResources();
        if (executionHandler!=null) executionHandler.stop();
        if (inputPipeline!=null) inputPipeline.stop();
        if (outputPipeline!=null) outputPipeline.stop();
    }
    
    public void addRoute(String route, RoutingDefinition target) {
        router.addRoute(route, target);
    }
    
    public void addRoute(String route, Endpoint target) {
        router.addRoute(route, target);
    }
    
    public void addRoute(String route, Object target) {
        router.addRoute(route, target);
    }

    public void setMaxThreads(int maxThreads) {
        // TODO Auto-generated method stub
        
    }
    
}