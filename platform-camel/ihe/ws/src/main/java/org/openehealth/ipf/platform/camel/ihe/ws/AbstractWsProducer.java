/*
 * Copyright 2009 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *     
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openehealth.ipf.platform.camel.ihe.ws;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultProducer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.endpoint.ClientImpl;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.headers.Header;
import org.apache.cxf.jaxws.context.WrappedMessageContext;
import org.apache.cxf.ws.addressing.*;
import org.openehealth.ipf.commons.ihe.ws.JaxWsClientFactory;
import org.openehealth.ipf.commons.ihe.ws.WsTransactionConfiguration;
import org.openehealth.ipf.commons.ihe.ws.correlation.AsynchronyCorrelator;
import org.openehealth.ipf.platform.camel.core.util.Exchanges;

import javax.xml.ws.BindingProvider;
import javax.xml.ws.soap.SOAPFaultException;
import java.util.UUID;

import static org.apache.commons.lang3.Validate.notNull;
import static org.openehealth.ipf.platform.camel.ihe.ws.HeaderUtils.processIncomingHeaders;
import static org.openehealth.ipf.platform.camel.ihe.ws.HeaderUtils.processUserDefinedOutgoingHeaders;

/**
 * Camel producer used to make calls to a Web Service.
 *
 * @author Jens Riemschneider
 * @author Dmytro Rud
 */
public abstract class AbstractWsProducer extends DefaultProducer {
    private static final Log LOG = LogFactory.getLog(AbstractWsProducer.class);

    private final JaxWsClientFactory clientFactory;
    private final Class<?> requestClass;

    
    /**
     * Constructs the producer.
     * 
     * @param endpoint
     *          the endpoint that creates this producer.
     * @param clientFactory
     *          the factory for clients to produce messages for the service.
     */
    @SuppressWarnings("unchecked")
    public AbstractWsProducer(
            AbstractWsEndpoint endpoint,
            JaxWsClientFactory clientFactory,
            Class<?> requestClass)
    {
        super(endpoint);
        notNull(clientFactory, "client factory cannot be null");
        notNull(requestClass, "request class cannot be null");
        this.clientFactory = clientFactory;
        this.requestClass = requestClass;
    }

    
    @Override
    public void process(Exchange exchange) throws Exception {
        LOG.debug("Calling web service on '" + getWsTransactionConfiguration().getServiceName() + "' with " + exchange);
        
        // prepare
        Object body = exchange.getIn().getMandatoryBody(requestClass);
        Object client = getClient();
        configureClient(client);
        BindingProvider bindingProvider = (BindingProvider) client;
        WrappedMessageContext requestContext = (WrappedMessageContext) bindingProvider.getRequestContext();
        cleanRequestContext(requestContext);

        enrichRequestContext(exchange, requestContext);
        processUserDefinedOutgoingHeaders(requestContext, exchange.getIn(), true);

        // set request encoding based on Camel exchange property
        String requestEncoding = exchange.getProperty(Exchange.CHARSET_NAME, String.class);
        if (requestEncoding != null) {
            requestContext.put(org.apache.cxf.message.Message.ENCODING, requestEncoding);
        }

        // get and analyse WS-Addressing asynchrony configuration
        String replyToUri =
                getWsTransactionConfiguration().isAllowAsynchrony()
                    ? exchange.getIn().getHeader(AbstractWsEndpoint.WSA_REPLYTO_HEADER_NAME, String.class)
                    : null; 
        if ((replyToUri != null) && replyToUri.trim().isEmpty()) {
            replyToUri = null;
        }

        // for asynchronous interaction: configure WSA headers and store correlation data
        if ((replyToUri != null) ||
            Boolean.TRUE.equals(requestContext.get(AsynchronyCorrelator.FORCE_CORRELATION)))
        {
            String messageId = "urn:uuid:" + UUID.randomUUID().toString();
            configureWSAHeaders(messageId, replyToUri, requestContext);

            AbstractWsEndpoint endpoint = (AbstractWsEndpoint) getEndpoint();
            AsynchronyCorrelator correlator = endpoint.getCorrelator();
            correlator.storeServiceEndpointUri(messageId, endpoint.getEndpointUri());
            
            String correlationKey = exchange.getIn().getHeader(
                    AbstractWsEndpoint.CORRELATION_KEY_HEADER_NAME,
                    String.class);
            if (correlationKey != null) {
                correlator.storeCorrelationKey(messageId, correlationKey);
            }
        }
        
        // invoke
        exchange.setPattern((replyToUri == null) ? ExchangePattern.InOut : ExchangePattern.InOnly);
        Object result = null;
        try {
             result = callService(client, body);
        } catch (SOAPFaultException fault) {
            // handle http://www.w3.org/TR/2006/NOTE-soap11-ror-httpbinding-20060321/
            // see also: https://issues.apache.org/jira/browse/CXF-3768
            if ((replyToUri == null) ||
                (fault.getCause() == null) ||
                ! fault.getCause().getClass().getName().equals("com.ctc.wstx.exc.WstxEOFException"))
            {
                throw fault;
            }
        }

        // for synchronous interaction: handle response
        if (replyToUri == null) {
            Message responseMessage = Exchanges.resultMessage(exchange);
            responseMessage.getHeaders().putAll(exchange.getIn().getHeaders());
            WrappedMessageContext responseContext = (WrappedMessageContext) bindingProvider.getResponseContext();
            processIncomingHeaders(responseContext, responseMessage);
            enrichResponseMessage(responseMessage, responseContext);

            // set Camel exchange property based on response encoding
            exchange.setProperty(Exchange.CHARSET_NAME,
                    responseContext.get(org.apache.cxf.message.Message.ENCODING));

            responseMessage.setBody(result);
        } 
    }


    /**
     * Sends the given request body to a Web Service via the given client proxy.
     */
    protected abstract Object callService(Object client, Object body) throws Exception;

    
    /**
     * Enriches the given Web Service request context
     * on the basis of the given Camel exchange, and vice versa.
     */
    protected void enrichRequestContext(Exchange exchange, WrappedMessageContext requestContext) {
        // does nothing per default
    }

    
    /**
     * Enriches the given response message from the Web Service request context data.
     */
    protected void enrichResponseMessage(Message message, WrappedMessageContext responseContext) {
        // does nothing per default
    }

    
    /**
     * Sets thread safety & timeout options of the given CXF client.  
     */
    protected void configureClient(Object o) {
        ClientImpl client = (ClientImpl) ClientProxy.getClient(o);
        client.setThreadLocalRequestContext(true);
        client.setSynchronousTimeout(Integer.MAX_VALUE);
    }
    
    
    /**
     * Request context is shared among subsequent requests, so we have to clean it.
     */
    protected void cleanRequestContext(WrappedMessageContext requestContext) {
        requestContext.remove(JAXWSAConstants.CLIENT_ADDRESSING_PROPERTIES);
        requestContext.remove(org.apache.cxf.message.Message.PROTOCOL_HEADERS);
        requestContext.remove(Header.HEADER_LIST);
    }
    
    
    /**
     * Initializes WS-Addressing headers MessageID and ReplyTo, 
     * and stores them into the given message context.
     */
    private static void configureWSAHeaders(String messageId, String replyToUri, WrappedMessageContext context) {
        // obtain headers' container
        AddressingProperties apropos = (AddressingProperties) context.get(JAXWSAConstants.CLIENT_ADDRESSING_PROPERTIES);
        if (apropos == null) {
            apropos = new AddressingPropertiesImpl();
            context.put(JAXWSAConstants.CLIENT_ADDRESSING_PROPERTIES, apropos);
        }

        // MessageID header
        AttributedURIType uri = new AttributedURIType();
        uri.setValue(messageId);
        apropos.setMessageID(uri);
        LOG.debug("Set WS-Addressing message ID: " + messageId);

        // ReplyTo header
        if (replyToUri != null) {
            AttributedURIType uri2 = new AttributedURIType();
            uri2.setValue(replyToUri);
            EndpointReferenceType endpointReference = new EndpointReferenceType();
            endpointReference.setAddress(uri2);
            apropos.setReplyTo(endpointReference);
        }
    }
    
    
    /**
     * Retrieves the client stub for the Web Service.
     * <p>
     * This method caches the client stub instance and therefore requires thread
     * synchronization.
     * 
     * @return the client stub.
     */
    protected Object getClient() {
        return clientFactory.getClient();
    }


    /**
     * @return the info describing the Web Service.
     */
    public WsTransactionConfiguration getWsTransactionConfiguration() {
        return clientFactory.getWsTransactionConfiguration();
    }
}