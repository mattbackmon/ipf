/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openehealth.ipf.commons.ihe.xca;

import org.apache.commons.lang.Validate;
import org.apache.cxf.frontend.ServerFactoryBean;
import org.apache.cxf.interceptor.InterceptorProvider;
import org.openehealth.ipf.commons.ihe.ws.ItiServiceFactory;
import org.openehealth.ipf.commons.ihe.ws.ItiServiceInfo;
import org.openehealth.ipf.commons.ihe.ws.correlation.AsynchronyCorrelator;
import org.openehealth.ipf.commons.ihe.ws.cxf.asyncaudit.AsyncAuditResponseInterceptor;
import org.openehealth.ipf.commons.ihe.ws.cxf.audit.WsAuditStrategy;
import org.openehealth.ipf.commons.ihe.ws.cxf.payload.InPayloadExtractorInterceptor;
import static org.openehealth.ipf.commons.ihe.ws.cxf.payload.StringPayloadHolder.PayloadType.SOAP_BODY;

/**
 * Service factory for receivers of asynchronous XCA responses.
 * @author Dmytro Rud
 */
public class XcaAsyncResponseServiceFactory extends ItiServiceFactory {
    private final WsAuditStrategy auditStrategy;
    private final AsynchronyCorrelator correlator;

    /**
     * Constructs the factory.
     * @param serviceInfo
     *          the info about the service to produce.
     * @param auditStrategy
     *          the auditing strategy to use.
     * @param serviceAddress
     *          the address of the service that it should be published with.
     * @param correlator
     *          correlator for asynchronous interactions.
     * @param customInterceptors
     *          user-defined custom CXF interceptors.
     */
    public XcaAsyncResponseServiceFactory(
            ItiServiceInfo serviceInfo,
            WsAuditStrategy auditStrategy,
            String serviceAddress,
            AsynchronyCorrelator correlator,
            InterceptorProvider customInterceptors) 
    {
        super(serviceInfo, serviceAddress, customInterceptors, null);
        
        Validate.notNull(correlator);
        this.correlator = correlator;
        this.auditStrategy = auditStrategy;
    }

    
    @Override
    protected void configureInterceptors(ServerFactoryBean svrFactory) {
        super.configureInterceptors(svrFactory);

        // install auditing-related interceptors if the user has not switched auditing off
        if (auditStrategy != null) {
            if (serviceInfo.isAuditRequestPayload()) {
                svrFactory.getInInterceptors().add(new InPayloadExtractorInterceptor(SOAP_BODY));
            }

            AsyncAuditResponseInterceptor auditInterceptor =
                new AsyncAuditResponseInterceptor(auditStrategy, true, correlator, true);
            svrFactory.getInInterceptors().add(auditInterceptor);
            svrFactory.getInFaultInterceptors().add(auditInterceptor);
        }
    }

}
