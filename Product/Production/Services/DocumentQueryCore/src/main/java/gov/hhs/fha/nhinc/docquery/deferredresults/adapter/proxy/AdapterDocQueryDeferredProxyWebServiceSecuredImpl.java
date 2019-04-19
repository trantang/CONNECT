/*
 * Copyright (c) 2009-2019, United States Government, as represented by the Secretary of Health and Human Services.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above
 *       copyright notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the documentation
 *       and/or other materials provided with the distribution.
 *     * Neither the name of the United States Government nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE UNITED STATES GOVERNMENT BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package gov.hhs.fha.nhinc.docquery.deferredresults.adapter.proxy;

import gov.hhs.fha.nhinc.common.nhinccommon.AssertionType;
import gov.hhs.fha.nhinc.docquery.deferred.impl.AdapterResponseHelper;
import gov.hhs.fha.nhinc.docquery.deferredresults.descriptor.AdapterDocQueryDeferredSecuredServicePortDescriptor;
import gov.hhs.fha.nhinc.dq.adapterdeferredresultoptionsecured.AdapterDocQueryDeferredResultsOptionSecuredPortType;
import gov.hhs.fha.nhinc.event.error.ErrorEventException;
import gov.hhs.fha.nhinc.messaging.client.CONNECTClient;
import gov.hhs.fha.nhinc.messaging.client.CONNECTClientFactory;
import gov.hhs.fha.nhinc.messaging.service.port.ServicePortDescriptor;
import gov.hhs.fha.nhinc.nhinclib.NhincConstants;
import gov.hhs.fha.nhinc.nhinclib.NullChecker;
import javax.xml.ws.WebServiceException;
import oasis.names.tc.ebxml_regrep.xsd.query._3.AdhocQueryResponse;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryResponseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AdapterDocQueryDeferredProxyWebServiceSecuredImpl extends BaseAdapterDocQueryDeferredProxy {

    private static final Logger LOG = LoggerFactory.getLogger(AdapterDocQueryDeferredProxyWebServiceSecuredImpl.class);

    @Override
    public RegistryResponseType respondingGatewayCrossGatewayQueryResults(AdhocQueryResponse msg,
        AssertionType assertion) {

        LOG.debug("Running through Secured Adapter Proxy");

        RegistryResponseType response = null;
        try {
            // get the Adopter Endpoint URL
            String url = getEndPointFromConnectionManagerByAdapterAPILevel(assertion,
                NhincConstants.ADAPTER_DOC_QUERY_DEFERRED_RESULTS_SECURED_SERVICE);

            // Call the service
            if (NullChecker.isNotNullish(url)) {
                if (msg == null) {
                    throw new IllegalArgumentException("Request Message must be provided");
                } else {
                    final ServicePortDescriptor<AdapterDocQueryDeferredResultsOptionSecuredPortType> portDescriptor =
                        new AdapterDocQueryDeferredSecuredServicePortDescriptor();

                    final CONNECTClient<AdapterDocQueryDeferredResultsOptionSecuredPortType> client = CONNECTClientFactory.getInstance()
                            .getCONNECTClientSecured(portDescriptor, url, assertion);

                    response  = (RegistryResponseType) client.invokePort(AdapterDocQueryDeferredResultsOptionSecuredPortType.class,
                            "respondingGatewayCrossGatewayQueryDeferredResultsSecured", msg);
                }
            } else {
                throw new WebServiceException("Could not determine URL for Doc Query Deferred Results Adapter endpoint");
            }
        } catch (final Exception ex) {
            response = AdapterResponseHelper.createFailureWithMessage("Unable to call Doc Query Deferred Results Adapter");
            throw new ErrorEventException(ex, response, "Unable to call Doc Query Deferred Results Adapter");
        }

        return response;
    }
}
