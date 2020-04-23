package org.hisp.dhis.dxf2.events.event.preprocess.update;

/*
 * Copyright (c) 2004-2020, University of Oslo
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

import static org.hisp.dhis.event.EventStatus.ACTIVE;
import static org.hisp.dhis.event.EventStatus.COMPLETED;
import static org.hisp.dhis.event.EventStatus.SCHEDULE;
import static org.hisp.dhis.event.EventStatus.SKIPPED;
import static org.hisp.dhis.user.User.getSafeUsername;
import static org.hisp.dhis.user.UserCredentials.USERNAME_MAX_LENGTH;
import static org.hisp.dhis.util.DateUtils.parseDate;
import static org.springframework.util.StringUtils.isEmpty;

import java.util.Date;

import org.hisp.dhis.category.CategoryOptionCombo;
import org.hisp.dhis.dxf2.common.ImportOptions;
import org.hisp.dhis.dxf2.events.event.Event;
import org.hisp.dhis.dxf2.events.event.preprocess.PreProcessor;
import org.hisp.dhis.dxf2.events.event.validation.ValidationContext;
import org.hisp.dhis.dxf2.importsummary.ImportConflict;
import org.hisp.dhis.dxf2.importsummary.ImportSummary;
import org.hisp.dhis.organisationunit.OrganisationUnit;
import org.hisp.dhis.program.ProgramStageInstance;
import org.hisp.dhis.util.DateUtils;

public class ProgramInstanceUpdatePreProcessor
    implements
    PreProcessor
{
    @Override
    public void process( final Event event, final ValidationContext ctx )
    {
        final ProgramStageInstance programStageInstance = ctx.getProgramStageInstanceMap().get( event.getEvent() );
        final ImportOptions importOptions = ctx.getImportOptions();
        final OrganisationUnit organisationUnit = ctx.getOrganisationUnitMap().get( event.getUid() );
        final CategoryOptionCombo categoryOptionCombo = ctx.getCategoryOptionComboMap().get( event.getUid() );

        Date dueDate = new Date();
        if ( event.getDueDate() != null )
        {
            dueDate = parseDate( event.getDueDate() );
        }

        if ( event.getEventDate() != null )
        {
            programStageInstance.setExecutionDate( parseDate( event.getEventDate() ) );
        }

        if ( categoryOptionCombo != null )
        {
            programStageInstance.setAttributeOptionCombo( categoryOptionCombo );
        }

        final String storedBy = getValidUsername( event.getStoredBy(), null,
            importOptions.getUser() != null ? importOptions.getUser().getUsername() : "[Unknown]" );

        if ( event.getStatus() == ACTIVE )
        {
            programStageInstance.setStatus( ACTIVE );
            programStageInstance.setCompletedBy( null );
            programStageInstance.setCompletedDate( null );
        }
        else if ( programStageInstance.getStatus() != event.getStatus() && event.getStatus() == COMPLETED )
        {
            final String completedBy = getValidUsername( event.getCompletedBy(), null,
                importOptions.getUser() != null ? importOptions.getUser().getUsername() : "[Unknown]" );

            programStageInstance.setCompletedBy( completedBy );

            Date completedDate = new Date();

            if ( event.getCompletedDate() != null )
            {
                completedDate = DateUtils.parseDate( event.getCompletedDate() );
            }

            programStageInstance.setCompletedDate( completedDate );
            programStageInstance.setStatus( COMPLETED );
        }
        else if ( event.getStatus() == SKIPPED )
        {
            programStageInstance.setStatus( SKIPPED );
        }
        else if ( event.getStatus() == SCHEDULE )
        {
            programStageInstance.setStatus( SCHEDULE );
        }

        programStageInstance.setStoredBy( storedBy );
        programStageInstance.setDueDate( dueDate );
        programStageInstance.setOrganisationUnit( organisationUnit );
        programStageInstance.setGeometry( event.getGeometry() );

        if ( programStageInstance.getProgramStage().isEnableUserAssignment() )
        {
            programStageInstance.setAssignedUser( ctx.getAssignedUserMap().get( event.getUid() ) );
        }
    }

    private String getValidUsername( final String userName, final ImportSummary importSummary,
        final String fallbackUsername )
    {
        String validUsername = userName;

        if ( isEmpty( validUsername ) )
        {
            validUsername = getSafeUsername( fallbackUsername );
        }
        else if ( validUsername.length() > USERNAME_MAX_LENGTH )
        {
            if ( importSummary != null )
            {
                // TODO: luciano this should be moved to the new logic
                importSummary.getConflicts().add( new ImportConflict( "Username", validUsername + " is more than "
                    + USERNAME_MAX_LENGTH + " characters, using current username instead" ) );
            }

            validUsername = getSafeUsername( fallbackUsername );
        }

        return validUsername;
    }
}