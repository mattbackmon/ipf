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
package org.openehealth.ipf.platform.camel.ihe.xds.commons.requests.query;

import static org.apache.commons.lang.Validate.notNull;

import org.openehealth.ipf.platform.camel.ihe.xds.commons.metadata.AvailabilityStatus;
import org.openehealth.ipf.platform.camel.ihe.xds.commons.metadata.Code;
import org.openehealth.ipf.platform.camel.ihe.xds.commons.metadata.Identifiable;
import org.openehealth.ipf.platform.camel.ihe.xds.commons.metadata.TimeRange;

/**
 * Represents a stored query for FindDocuments.
 * @author Jens Riemschneider
 */
public class FindDocumentsQuery extends StoredQuery {
    private final Identifiable patientId;
    private final QueryList<AvailabilityStatus> status;
    
    private final QueryList<Code> classCodes = new QueryList<Code>();
    private final QueryList<Code> practiceSettingCodes = new QueryList<Code>();
    private final QueryList<Code> healthCareFacilityTypeCodes = new QueryList<Code>();
    private final QueryList<Code> eventCodes = new QueryList<Code>();
    private final QueryList<Code> confidentialityCodes = new QueryList<Code>();
    private final QueryList<Code> formatCodes = new QueryList<Code>();
    private final QueryList<String> authorPersons = new QueryList<String>();
    private final TimeRange creationTime = new TimeRange();
    private final TimeRange serviceStartTime = new TimeRange();
    private final TimeRange serviceStopTime = new TimeRange();
    
    public FindDocumentsQuery(Identifiable patientId, QueryList<AvailabilityStatus> status) {
        notNull(patientId, "patientId cannot be null");
        notNull(status, "status cannot be null");
        
        this.patientId = patientId;
        this.status = new QueryList<AvailabilityStatus>(status);
    }

    public Identifiable getPatientId() {
        return patientId;
    }

    public QueryList<AvailabilityStatus> getStatus() {
        return status;
    }
    
    public TimeRange getCreationTime() {
        return creationTime;
    }

    public TimeRange getServiceStartTime() {
        return serviceStartTime;
    }

    public TimeRange getServiceStopTime() {
        return serviceStopTime;
    }

    public QueryList<Code> getClassCodes() {
        return classCodes;
    }
    
    public QueryList<Code> getPracticeSettingCodes() {
        return practiceSettingCodes;
    }
    
    public QueryList<Code> getHealthCareFacilityTypeCodes() {
        return healthCareFacilityTypeCodes;
    }
    
    public QueryList<Code> getEventCodes() {
        return eventCodes;
    }

    public QueryList<Code> getConfidentialityCodes() {
        return confidentialityCodes;
    }

    public QueryList<Code> getFormatCodes() {
        return formatCodes;
    }

    public QueryList<String> getAuthorPersons() {
        return authorPersons;
    }
}
