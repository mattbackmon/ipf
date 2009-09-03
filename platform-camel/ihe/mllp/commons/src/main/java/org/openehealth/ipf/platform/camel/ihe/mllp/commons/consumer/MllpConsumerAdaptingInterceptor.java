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
package org.openehealth.ipf.platform.camel.ihe.mllp.commons.consumer;

import static org.openehealth.ipf.platform.camel.core.util.Exchanges.resultMessage;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openehealth.ipf.modules.hl7.AckTypeCode;
import org.openehealth.ipf.modules.hl7.HL7v2Exception;
import org.openehealth.ipf.modules.hl7.message.MessageUtils;
import org.openehealth.ipf.modules.hl7dsl.MessageAdapter;
import org.openehealth.ipf.platform.camel.core.util.Exchanges;
import org.openehealth.ipf.platform.camel.ihe.mllp.commons.MllpAdaptingException;
import org.openehealth.ipf.platform.camel.ihe.mllp.commons.MllpComponent;
import org.openehealth.ipf.platform.camel.ihe.mllp.commons.MllpEndpoint;
import org.openehealth.ipf.platform.camel.ihe.mllp.commons.MllpMarshalUtils;
import org.openehealth.ipf.platform.camel.ihe.mllp.commons.MllpTransactionConfiguration;

import ca.uhn.hl7v2.model.Message;


/**
 * Consumer-side Camel interceptor which creates a {@link MessageAdapter} 
 * from various possible response types.
 *  
 * @author Dmytro Rud
 */
public class MllpConsumerAdaptingInterceptor extends AbstractMllpConsumerInterceptor {
    private static final transient Log LOG = LogFactory.getLog(MllpConsumerAdaptingInterceptor.class);

    public MllpConsumerAdaptingInterceptor(MllpEndpoint endpoint, Processor wrappedProcessor) {
        super(endpoint, wrappedProcessor);
    }
    
    
    /**
     * Converts response to a {@link MessageAdapter}, throws 
     * a {@link MllpAdaptingException} on failure.  
     */
    public void process(Exchange exchange) throws Exception {
        MessageAdapter originalAdapter = exchange.getIn().getHeader(ORIGINAL_MESSAGE_ADAPTER_HEADER_NAME, MessageAdapter.class); 
        Message originalMessage = (Message) originalAdapter.getTarget();
        MllpTransactionConfiguration config = getMllpEndpoint().getTransactionConfiguration();
        
        // run the route
        try {
            getWrappedProcessor().process(exchange);
            checkExchangeFailed(exchange, originalMessage);
        } catch(Exception e) {
            LOG.error("Message processing failed", e);
            resultMessage(exchange).setBody(MllpMarshalUtils.createNak(e, originalMessage, config));
        }

        org.apache.camel.Message m = Exchanges.resultMessage(exchange);
        Object body = m.getBody();

        // try to convert route response from a known type
        MessageAdapter msg = MllpMarshalUtils.extractMessageAdapter(
                m,
                getMllpEndpoint().getConfiguration().getCharsetName(),
                getMllpEndpoint().getParser());
        
        // additionally: an Exception in the body?
        if((msg == null) && (body instanceof Exception)) {
            msg = MllpMarshalUtils.createNak((Exception) body, originalMessage, config);
        }
        
        // no known data type --> determine user's intention on the basis of a header 
        if(msg == null) {
            msg = analyseMagicHeader(m, originalMessage);
        }

        // unable to create a MessageAdaper :-(
        if(msg == null) {
            String className = (body == null) ? "null" : body.getClass().getName();
            throw new MllpAdaptingException("Do not know how to handle " + className +
                    " value returned from the PIX/PDQ route");
        }
        
        m.setBody(msg);
    }


    /**
     * Considers a specific header to determine whether the route author want us to generate
     * an automatic acknowledgment, and generates the latter when the author really does.   
     */
    private MessageAdapter analyseMagicHeader(org.apache.camel.Message m, Message originalMessage) {
        Object header = m.getHeader(MllpComponent.ACK_TYPE_CODE_HEADER);
        if(header == AckTypeCode.AA) {
            return new MessageAdapter((Message) MessageUtils.ack(originalMessage)); 
        } else if((header == AckTypeCode.AE) || (header == AckTypeCode.AR)) {
            HL7v2Exception exception = new HL7v2Exception(
                    "Error in PIX/PDQ route, output type not supported", 
                    getMllpEndpoint().getTransactionConfiguration().getResponseErrorDefaultErrorCode());
            Message nak = (Message) MessageUtils.nak(originalMessage, exception, (AckTypeCode) header); 
            return new MessageAdapter(nak);
        }
        return null;
    }
    
    
    /**
     * Checks whether the given exchange has failed.  
     * If yes, substitutes the exception object with a HL7 NAK  
     * and marks the exchange as successful. 
     */
    private void checkExchangeFailed(Exchange exchange, Message original) throws Exception {
        if (exchange.isFailed()) {
            Throwable t; 
            if(exchange.getException() != null) {
                t = exchange.getException();
                exchange.setException(null);
            } else {
                org.apache.camel.Message m = resultMessage(exchange);
                t = m.getBody(Throwable.class);
                m.setBody(null);
            }
            LOG.error("Message processing failed", t);
            resultMessage(exchange).setBody(MllpMarshalUtils.createNak(
                    t, 
                    original, 
                    getMllpEndpoint().getTransactionConfiguration()));
        }
    }
    
}
