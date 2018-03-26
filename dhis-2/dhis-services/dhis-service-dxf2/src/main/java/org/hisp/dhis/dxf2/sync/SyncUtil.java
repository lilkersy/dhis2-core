package org.hisp.dhis.dxf2.sync;/*
 * Copyright (c) 2004-2018, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the HISP project nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hisp.dhis.dxf2.common.ImportSummariesResponseExtractor;
import org.hisp.dhis.dxf2.importsummary.ImportStatus;
import org.hisp.dhis.dxf2.importsummary.ImportSummaries;
import org.hisp.dhis.dxf2.importsummary.ImportSummary;
import org.hisp.dhis.dxf2.synch.AvailabilityStatus;
import org.hisp.dhis.dxf2.webmessage.WebMessageParseException;
import org.hisp.dhis.dxf2.webmessage.utils.WebMessageParseUtils;
import org.hisp.dhis.setting.SettingKey;
import org.hisp.dhis.setting.SystemSettingManager;
import org.hisp.dhis.system.util.CodecUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;

import java.util.Date;

import static org.apache.commons.lang3.StringUtils.isEmpty;

public class SyncUtil
{

    private static final Log log = LogFactory.getLog( SyncUtil.class );

    public static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String PING_PATH = "/api/system/ping";

    private SyncUtil()
    {
    }

    public static boolean runSyncRequestAndAnalyzeResponse( RestTemplate restTemplate, RequestCallback requestCallback, String syncUrl, SyncEndpoint endpoint )
    {
        ResponseExtractor<ImportSummaries> responseExtractor = new ImportSummariesResponseExtractor();
        ImportSummaries summaries = null;
        try
        {
            summaries = restTemplate.execute( syncUrl, HttpMethod.POST, requestCallback, responseExtractor );
        }
        catch ( HttpClientErrorException ex )
        {
            String responseBody = ex.getResponseBodyAsString();
            try
            {
                summaries = WebMessageParseUtils.fromWebMessageResponse( responseBody, ImportSummaries.class );
            }
            catch ( WebMessageParseException e )
            {
                log.error( "Parsing WebMessageResponse failed.", e );
                return false;
            }
        }
        catch ( HttpServerErrorException ex )
        {
            String responseBody = ex.getResponseBodyAsString();
            log.error( "Internal error happened during event data push: " + responseBody, ex );
            throw ex;
        }
        catch ( ResourceAccessException ex )
        {
            log.error( "Exception during event data push: " + ex.getMessage(), ex );
            throw ex;
        }

        log.info( "Sync summary: " + summaries );
        return analyzeResultsInImportSummaries( summaries, summaries, endpoint );
    }

    /**
     * Analyzes results in ImportSummaries. Returns true if everything is OK, false otherwise.
     * <p>
     * THIS METHOD USES RECURSION!!!
     *
     * @param summaries            ImportSummaries that should be analyzed
     * @param originalTopSummaries The top level ImportSummaries. Used only for logging purposes.
     * @param endpoint             Tells against which endpoint the request was run
     * @return true if everything is OK, false otherwise
     */
    private static boolean analyzeResultsInImportSummaries( ImportSummaries summaries, ImportSummaries originalTopSummaries, SyncEndpoint endpoint )
    {
        if ( summaries != null )
        {
            //TODO: I guess this is not needed. But should be checked during testing to be sure.
//            if ( originalTopSummaries == null )
//            {
//                originalTopSummaries = summaries;
//            }

            for ( ImportSummary summary : summaries.getImportSummaries() )
            {
                if ( !checkSummaryStatus( summary, originalTopSummaries, endpoint ) )
                {
                    return false;
                }

                //Based on against which endpoint the request was run, I need to check for errors Enrollments and Events, Events or no more checks are needed
                if ( endpoint == SyncEndpoint.TEIS_ENDPOINT )
                {
                    //uses recursion. Be sure, that it will reach the end in some reasonable time!!! Correct value of endpoint argument is critical here!
                    if ( !analyzeResultsInImportSummaries( summary.getEnrollments(), originalTopSummaries, SyncEndpoint.ENROLLMENTS_ENDPOINT ) )
                    {
                        return false;
                    }
                }
                else if ( endpoint == SyncEndpoint.ENROLLMENTS_ENDPOINT )
                {
                    //uses recursion. Be sure, that it will reach the end in some reasonable time!!! Correct value of endpoint argument is critical here!
                    if ( !analyzeResultsInImportSummaries( summary.getEvents(), originalTopSummaries, SyncEndpoint.EVENTS_ENDPOINT ) )
                    {
                        return false;
                    }
                }
            }
        }

        return true;
    }

//    /**
//     * Analyzes TEI ImportSummaries. Returns true if everything is OK, false otherwise
//     *
//     * @param summaries
//     * @return true if everything is OK, false otherwise
//     */
//    private static boolean analyzeResultsInTEIImportSummaries( ImportSummaries summaries )
//    {
//        if ( summaries != null )
//        {
//            for ( ImportSummary summary : summaries.getImportSummaries() )
//            {
//                if ( !checkSummaryStatus( summary, summaries ) )
//                {
//                    return false;
//                }
//
//                //I am going to check for eventual errors in enrollments if they were part of sync payload
//                if ( !analyzeResultsInEnrollmentImportSummaries( summary.getEnrollments(), summaries ) )
//                {
//                    return false;
//                }
//            }
//        }
//
//        return true;
//    }
//
//    /**
//     * Analyzes Enrollment ImportSummaries. Returns true if everything is OK, false otherwise
//     *
//     * @param summaries
//     * @param originalTopSummaries
//     * @return true if everything is OK, false otherwise
//     */
//    private static boolean analyzeResultsInEnrollmentImportSummaries( ImportSummaries summaries, ImportSummaries originalTopSummaries )
//    {
//        for ( ImportSummary summary : summaries.getImportSummaries() )
//        {
//            if ( !checkSummaryStatus( summary, originalTopSummaries ) )
//            {
//                return false;
//            }
//
//            //I am going to check for eventual errors in events if they were part of sync payload
//            if ( !analyzeResultsInEventImportSummaries( summary.getEvents(), originalTopSummaries ) )
//            {
//                return false;
//            }
//        }
//        return true;
//    }
//
//    /**
//     * Analyzes Event ImportSummaries. Returns true if everything is OK, false otherwise
//     *
//     * @param summaries
//     * @param originalTopSummaries
//     * @return true if everything is OK, false otherwise
//     */
//    private static boolean analyzeResultsInEventImportSummaries( ImportSummaries summaries, ImportSummaries originalTopSummaries )
//    {
//        for ( ImportSummary summary : summaries.getImportSummaries() )
//        {
//            if ( !checkSummaryStatus( summary, originalTopSummaries ) )
//            {
//                return false;
//            }
//        }
//
//        return true;
//    }

    /**
     * Checks the ImportSummary. Returns true if everything is OK, false otherwise
     *
     * @param summary
     * @param topSummaries
     * @param endpoint
     * @return true if everything is OK, false otherwise
     */
    private static boolean checkSummaryStatus( ImportSummary summary, ImportSummaries topSummaries, SyncEndpoint endpoint )
    {
        if ( summary.getStatus() == ImportStatus.ERROR || summary.getStatus() == ImportStatus.WARNING )
        {
            log.error( "Sync against endpoint: " + endpoint.name() + " failed: " + topSummaries );
            return false;
        }

        return true;
    }

    public static AvailabilityStatus isRemoteServerAvailable( SystemSettingManager systemSettingManager, RestTemplate restTemplate )
    {
        if ( !isRemoteServerConfigured( systemSettingManager ) )
        {
            return new AvailabilityStatus( false, "Remote server is not configured", HttpStatus.BAD_GATEWAY );
        }

        String url = systemSettingManager.getSystemSetting( SettingKey.REMOTE_INSTANCE_URL ) + PING_PATH;
        String username = (String) systemSettingManager.getSystemSetting( SettingKey.REMOTE_INSTANCE_USERNAME );
        String password = (String) systemSettingManager.getSystemSetting( SettingKey.REMOTE_INSTANCE_PASSWORD );

        log.debug( String.format( "Remote server ping URL: %s, username: %s", url, username ) );

        HttpEntity<String> request = getBasicAuthRequestEntity( username, password );

        ResponseEntity<String> response = null;
        HttpStatus sc = null;
        String st = null;
        AvailabilityStatus status = null;

        try
        {
            response = restTemplate.exchange( url, HttpMethod.GET, request, String.class );
            sc = response.getStatusCode();
        }
        catch ( HttpClientErrorException | HttpServerErrorException ex )
        {
            sc = ex.getStatusCode();
            st = ex.getStatusText();
        }
        catch ( ResourceAccessException ex )
        {
            return new AvailabilityStatus( false, "Network is unreachable", HttpStatus.BAD_GATEWAY );
        }

        log.debug( "Response status code: " + sc );

        if ( HttpStatus.OK.equals( sc ) )
        {
            status = new AvailabilityStatus( true, "Authentication was successful", sc );
        }
        else if ( HttpStatus.FOUND.equals( sc ) )
        {
            status = new AvailabilityStatus( false, "No authentication was provided", sc );
        }
        else if ( HttpStatus.UNAUTHORIZED.equals( sc ) )
        {
            status = new AvailabilityStatus( false, "Authentication failed", sc );
        }
        else if ( HttpStatus.INTERNAL_SERVER_ERROR.equals( sc ) )
        {
            status = new AvailabilityStatus( false, "Remote server experienced an internal error", sc );
        }
        else
        {
            status = new AvailabilityStatus( false, "Server is not available: " + st, sc );
        }

        log.info( "Status: " + status );

        return status;
    }

    /**
     * Indicates whether a remote server has been properly configured.
     */
    private static boolean isRemoteServerConfigured( SystemSettingManager systemSettingManager )
    {
        String url = (String) systemSettingManager.getSystemSetting( SettingKey.REMOTE_INSTANCE_URL );
        String username = (String) systemSettingManager.getSystemSetting( SettingKey.REMOTE_INSTANCE_USERNAME );
        String password = (String) systemSettingManager.getSystemSetting( SettingKey.REMOTE_INSTANCE_PASSWORD );

        if ( isEmpty( url ) )
        {
            log.info( "Remote server URL not set" );
            return false;
        }

        if ( isEmpty( username ) || isEmpty( password ) )
        {
            log.info( "Remote server username or password not set" );
            return false;
        }

        return true;
    }

    /**
     * Creates an HTTP entity for requests with appropriate header for basic
     * authentication.
     */
    private static <T> HttpEntity<T> getBasicAuthRequestEntity( String username, String password )
    {
        HttpHeaders headers = new HttpHeaders();
        headers.set( HEADER_AUTHORIZATION, CodecUtils.getBasicAuthString( username, password ) );
        return new HttpEntity<>( headers );
    }

    /**
     * Sets the time of the last successful synchronization operation.
     *
     * @param systemSettingManager
     * @param settingKey
     * @param time
     */
    public static void setSyncSuccess( SystemSettingManager systemSettingManager, SettingKey settingKey, Date time )
    {
        systemSettingManager.saveSystemSetting( settingKey, time );
    }

    /**
     * Return the time of last successful sync.
     *
     * @param systemSettingManager
     * @param settingKey
     * @return
     */
    public static Date getLastSyncSuccess( SystemSettingManager systemSettingManager, SettingKey settingKey )
    {
        return (Date) systemSettingManager.getSystemSetting( settingKey );
    }
}