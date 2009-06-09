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
package org.openehealth.ipf.platform.camel.ihe.xdsb.iti42.component;

import org.apache.camel.Exchange;
import org.openehealth.ipf.platform.camel.core.util.Exchanges;
import org.openehealth.ipf.platform.camel.ihe.xdsb.commons.ItiServiceInfo;
import org.openehealth.ipf.platform.camel.ihe.xdsb.commons.DefaultItiProducer;
import org.openehealth.ipf.platform.camel.ihe.xdsb.commons.cxf.audit.AuditStrategy;
import org.openehealth.ipf.platform.camel.ihe.xdsb.commons.stub.ebrs.rs.RegistryResponseType;
import org.openehealth.ipf.platform.camel.ihe.xdsb.commons.stub.ebrs.lcm.SubmitObjectsRequest;
import org.openehealth.ipf.platform.camel.ihe.xdsb.iti42.audit.Iti42ClientAuditStrategy;
import org.openehealth.ipf.platform.camel.ihe.xdsb.iti42.service.Iti42PortType;

/**
 * The producer implementation for the ITI-42 component.
 */
public class Iti42Producer extends DefaultItiProducer<Iti42PortType> {
    
    /**
     * Constructs the producer.
     * @param endpoint
     *          the endpoint creating this producer.
     * @param serviceInfo
     *          info about the service being called by this producer.
     */
    public Iti42Producer(Iti42Endpoint endpoint, ItiServiceInfo<Iti42PortType> serviceInfo) {
        super(endpoint, serviceInfo);
    }

    
    @Override
    protected void callService(Exchange exchange) {
        SubmitObjectsRequest body =
                exchange.getIn().getBody(SubmitObjectsRequest.class);
        RegistryResponseType result = getClient().documentRegistryRegisterDocumentSetB(body);
        Exchanges.resultMessage(exchange).setBody(result);
    }

    
    @Override
    protected AuditStrategy createAuditStrategy() {
        return new Iti42ClientAuditStrategy();
    }
}